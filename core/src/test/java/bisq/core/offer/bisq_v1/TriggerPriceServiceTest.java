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

package bisq.core.offer.bisq_v1;

import bisq.core.locale.Res;
import bisq.core.monetary.Price;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.provider.PriceFeedNodeAddressProvider;
import bisq.core.provider.PriceHttpClient;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.mempool.MempoolService;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;

import bisq.network.p2p.P2PService;

import javafx.beans.property.SimpleIntegerProperty;

import javafx.collections.FXCollections;

import java.time.Instant;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static bisq.core.offer.OfferMaker.btcUsdOffer;
import static bisq.core.offer.OfferMaker.direction;
import static bisq.core.offer.OfferMaker.price;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TriggerPriceServiceTest {
    @BeforeAll
    static void setUp() {
        Res.setBaseCurrencyCode("BTC");
        Res.setBaseCurrencyName("Bitcoin");
    }

    // A sell offer at a fixed price of 10 USD with a trigger price of 5 USD, which a market price of 1 USD falls below
    private static OpenOffer openOffer() {
        return new OpenOffer(make(btcUsdOffer.but(with(direction, OfferDirection.SELL), with(price, 100_000L))),
                50_000);
    }

    private static MarketPrice externalPrice(long ageInSeconds) {
        return new MarketPrice("USD", 1, Instant.now().getEpochSecond() - ageInSeconds, true);
    }

    @Test
    void priceFromTradeStatisticsDoesNotTriggerAnOffer() {
        PriceFeedService priceFeedService = new PriceFeedService(mock(PriceHttpClient.class), mock(FeeService.class),
                mock(PriceFeedNodeAddressProvider.class), mock(Preferences.class));
        priceFeedService.setBisqMarketPrice("USD", Price.parse("USD", "1"));

        assertFalse(TriggerPriceService.wasTriggered(priceFeedService.getMarketPrice("USD"), openOffer()));
    }

    @Test
    void outdatedExternalPriceDoesNotTriggerAnOffer() {
        assertFalse(TriggerPriceService.wasTriggered(externalPrice(MarketPrice.MARKET_PRICE_MAX_AGE_SEC + 60),
                openOffer()));
    }

    // Positive control
    @Test
    void recentExternalPriceTriggersAnOffer() {
        assertTrue(TriggerPriceService.wasTriggered(externalPrice(60), openOffer()));
    }

    // The service checks the offers on every price feed update, which a received trade statistic also causes
    @Test
    void priceFeedUpdateDeactivatesAnOfferOnlyWithARecentExternalPrice() {
        OpenOffer openOffer = openOffer();
        OpenOfferManager openOfferManager = mock(OpenOfferManager.class);
        when(openOfferManager.getObservableList()).thenReturn(FXCollections.observableArrayList(openOffer));
        P2PService p2PService = mock(P2PService.class);
        when(p2PService.isBootstrapped()).thenReturn(true);
        PriceFeedService priceFeedService = mock(PriceFeedService.class);
        SimpleIntegerProperty updateCounter = new SimpleIntegerProperty(0);
        when(priceFeedService.updateCounterProperty()).thenReturn(updateCounter);
        TriggerPriceService triggerPriceService = new TriggerPriceService(p2PService, openOfferManager,
                mock(MempoolService.class), priceFeedService);
        triggerPriceService.onAllServicesInitialized(message -> {
        });

        when(priceFeedService.getMarketPrice("USD")).thenReturn(new MarketPrice("USD", 1, 0, false));
        updateCounter.set(updateCounter.get() + 1);
        verify(openOfferManager, never()).deactivateOpenOffer(any(), any(), any());

        when(priceFeedService.getMarketPrice("USD")).thenReturn(externalPrice(60));
        updateCounter.set(updateCounter.get() + 1);
        verify(openOfferManager, times(1)).deactivateOpenOffer(any(), any(), any());
    }
}
