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

package bisq.core.api;

import bisq.core.api.exception.NotAvailableException;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;

import java.time.Instant;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// The market price request answers from the price feed cache and must not wait for a price provider.
public class CorePriceServiceTest {
    private PriceFeedService priceFeedService;
    private CorePriceService corePriceService;

    @BeforeEach
    public void setUp() {
        priceFeedService = mock(PriceFeedService.class);
        corePriceService = new CorePriceService(mock(Preferences.class), priceFeedService,
                mock(TradeStatisticsManager.class));
    }

    @Test
    public void recentExternalPriceIsReturnedWithoutARequest() {
        when(priceFeedService.getMarketPrice("USD")).thenReturn(new MarketPrice("USD", 61234.56789, now(), true));
        AtomicReference<Double> result = new AtomicReference<>();

        corePriceService.getMarketPrice("usd", result::set);

        assertEquals(61234.5679, result.get());
        verify(priceFeedService, never()).requestPriceFeed(any(), any());
        verify(priceFeedService, never()).setCurrencyCode(any());
    }

    @Test
    public void cryptoCurrencyPriceIsRoundedToEightDecimals() {
        when(priceFeedService.getMarketPrice("XMR")).thenReturn(new MarketPrice("XMR", 0.0123456789, now(), true));
        AtomicReference<Double> result = new AtomicReference<>();

        corePriceService.getMarketPrice("XMR", result::set);

        assertEquals(0.01234568, result.get());
    }

    @Test
    public void missingPriceIsNotAvailable() {
        assertThrows(NotAvailableException.class,
                () -> corePriceService.getMarketPrice("USD", price -> fail("no price expected")));
    }

    @Test
    public void outdatedPriceIsNotAvailable() {
        long outdated = now() - MarketPrice.MARKET_PRICE_MAX_AGE_SEC - 60;
        when(priceFeedService.getMarketPrice("USD")).thenReturn(new MarketPrice("USD", 61234.5, outdated, true));

        assertThrows(NotAvailableException.class,
                () -> corePriceService.getMarketPrice("USD", price -> fail("no price expected")));
    }

    @Test
    public void priceNotFromAProviderIsNotAvailable() {
        when(priceFeedService.getMarketPrice("BSQ")).thenReturn(new MarketPrice("BSQ", 0.00001234, now(), false));

        assertThrows(NotAvailableException.class,
                () -> corePriceService.getMarketPrice("BSQ", price -> fail("no price expected")));
    }

    private static long now() {
        return Instant.now().getEpochSecond();
    }
}
