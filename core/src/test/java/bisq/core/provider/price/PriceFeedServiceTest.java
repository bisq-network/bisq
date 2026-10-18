/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.provider.price;

import bisq.core.provider.PriceFeedNodeAddressProvider;
import bisq.core.provider.PriceHttpClient;
import bisq.core.provider.fee.FeeService;
import bisq.core.testutil.ManualTimer;
import bisq.core.user.Preferences;

import bisq.common.UserThread;

import java.time.Instant;

import java.io.IOException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PriceFeedServiceTest {
    private static final String BASE_URL = "http://localhost:8078/";

    private final PriceHttpClient httpClient = mock(PriceHttpClient.class);
    private final PriceFeedNodeAddressProvider nodeAddressProvider = mock(PriceFeedNodeAddressProvider.class);
    private PriceFeedService priceFeedService;

    @BeforeEach
    public void setUp() {
        ManualTimer.clear();
        UserThread.setTimerClass(ManualTimer.class);
        when(httpClient.getBaseUrl()).thenReturn(BASE_URL);
        when(nodeAddressProvider.getBaseUrl()).thenReturn(BASE_URL);
        priceFeedService = new PriceFeedService(httpClient, mock(FeeService.class), nodeAddressProvider,
                mock(Preferences.class));
    }

    @AfterEach
    public void tearDown() {
        UserThread.resetForTests();
        ManualTimer.clear();
    }

    // The API answers from the cache, so the price feed has to keep it recent without a caller asking.
    @Test
    public void startRequestingPricesRepeatsTheRequest() throws Exception {
        when(httpClient.get(any(), any(), any())).thenReturn("{\"data\":[{\"currencyCode\":\"USD\",\"price\":50000.0," +
                "\"timestampSec\":" + Instant.now().getEpochSecond() + ",\"provider\":\"test\"}],\"bitcoinFeesTs\":0}");
        UserThread.setExecutor(Runnable::run);
        priceFeedService.setCurrencyCode("USD");
        // Only the applied response counts: the UserThread is shared, so a command of another test can reach it first
        CountDownLatch responseApplied = new CountDownLatch(1);
        priceFeedService.updateCounterProperty().addListener((observable, oldValue, newValue) ->
                responseApplied.countDown());

        priceFeedService.startRequestingPrices();
        assertTrue(responseApplied.await(10, TimeUnit.SECONDS), "response was not applied");
        ManualTimer.firePendingTimers();

        verify(httpClient, timeout(1000).times(2)).get(any(), any(), any());
        // The provider answered, so the repeat stays with it.
        verify(nodeAddressProvider, never()).selectNextProviderBaseUrl();
    }

    // After the shutdown the price feed must not start a request, whoever asks for it. No request is started
    // before the shutdown either, as PriceRequest.shutDown would stop the executor that all tests share.
    @Test
    public void noRequestIsStartedAfterShutDown() throws IOException {
        priceFeedService.shutDown();
        priceFeedService.startRequestingPrices();
        priceFeedService.requestPriceFeed(price -> fail("no price expected"),
                (errorMessage, throwable) -> fail("no request expected"));

        verify(httpClient, after(1000).never()).get(any(), any(), any());
    }
}
