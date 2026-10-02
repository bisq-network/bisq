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
import bisq.core.user.Preferences;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// After the shutdown the price feed must not start a request, whoever asks for it.
public class PriceFeedServiceTest {
    @Test
    public void noRequestIsStartedAfterShutDown() throws IOException {
        PriceHttpClient httpClient = mock(PriceHttpClient.class);
        PriceFeedNodeAddressProvider nodeAddressProvider = mock(PriceFeedNodeAddressProvider.class);
        when(nodeAddressProvider.getBaseUrl()).thenReturn("http://localhost:8078/");
        PriceFeedService priceFeedService = new PriceFeedService(httpClient, mock(FeeService.class),
                nodeAddressProvider, mock(Preferences.class));

        priceFeedService.shutDown();
        priceFeedService.startRequestingPrices();
        priceFeedService.requestPriceFeed(price -> fail("no price expected"),
                (errorMessage, throwable) -> fail("no request expected"));

        verify(httpClient, after(1000).never()).get(any(), any(), any());
    }
}
