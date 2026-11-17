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

package bisq.core.util;

import bisq.core.monetary.Price;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferMaker;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.TradeStatistics3;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;

import org.bitcoinj.core.Coin;

import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PriceUtilTest {
    private static final Price AVERAGE = Price.valueOf("BSQ", 2000);

    @Test
    void priceWithinTheRangeIsNotOutside() {
        assertFalse(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 2000), AVERAGE, 2));
        assertFalse(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 3999), AVERAGE, 2));
        assertFalse(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 1001), AVERAGE, 2));
    }

    // A price at a bound is inside: only a price above the average times the factor or below the average divided by
    // the factor is outside.
    @Test
    void priceAtABoundIsNotOutside() {
        assertFalse(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 4000), AVERAGE, 2));
        assertFalse(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 1000), AVERAGE, 2));
    }

    @Test
    void priceBeyondABoundIsOutside() {
        assertTrue(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 4001), AVERAGE, 2));
        assertTrue(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 999), AVERAGE, 2));
        assertTrue(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 8001), AVERAGE, 4));
        assertTrue(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 499), AVERAGE, 4));
        assertFalse(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 8000), AVERAGE, 4));
        assertFalse(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 500), AVERAGE, 4));
    }

    // Without a price of the offer or without an average, as on a node without BSQ trade statistics, nothing is
    // outside, so that no offer is hidden.
    @Test
    void missingPriceOrAverageIsNeverOutside() {
        assertFalse(PriceUtil.isOutsidePriceRange(null, AVERAGE, 2));
        assertFalse(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 0), AVERAGE, 2));
        assertFalse(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 1), Price.valueOf("BSQ", 0), 2));
        assertFalse(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", Long.MAX_VALUE), Price.valueOf("BSQ", 0), 2));
    }

    @Test
    void pricesOfDifferentCurrenciesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PriceUtil.isOutsidePriceRange(Price.valueOf("XMR", 2000), AVERAGE, 2));
    }

    // The price of a BSQ offer and the average from BSQ trade statistics have the same unit: an offer at the price of
    // the only statistic is at the average.
    @Test
    void bsqOfferPriceAndNinetyDayAverageHaveTheSameUnit() {
        long now = System.currentTimeMillis();
        TradeStatistics3 statistic = new TradeStatistics3("BSQ", 2000, Coin.parseCoin("0.1").getValue(),
                "BLOCK_CHAINS", now - TimeUnit.DAYS.toMillis(89), null, null, (TreeMap<String, String>) null);
        PriceUtil priceUtil = new PriceUtil(mock(PriceFeedService.class), tradeStatisticsManager(statistic),
                preferences());

        Price average = priceUtil.getBsq90DayAveragePrice();
        Offer offerAtAverage = bsqOffer(2000);

        assertEquals(offerAtAverage.getPrice().getValue(), average.getValue());
        assertFalse(PriceUtil.isOutsidePriceRange(offerAtAverage.getPrice(), average, 2));
        assertFalse(PriceUtil.isOutsidePriceRange(bsqOffer(4000).getPrice(), average, 2));
        assertTrue(PriceUtil.isOutsidePriceRange(bsqOffer(4001).getPrice(), average, 2));
        assertTrue(PriceUtil.isOutsidePriceRange(bsqOffer(999).getPrice(), average, 2));
    }

    @Test
    void statisticsOlderThanNinetyDaysAreNotInTheAverage() {
        long now = System.currentTimeMillis();
        TradeStatistics3 statistic = new TradeStatistics3("BSQ", 2000, Coin.parseCoin("0.1").getValue(),
                "BLOCK_CHAINS", now - TimeUnit.DAYS.toMillis(91), null, null, (TreeMap<String, String>) null);
        PriceUtil priceUtil = new PriceUtil(mock(PriceFeedService.class), tradeStatisticsManager(statistic),
                preferences());

        assertEquals(0, priceUtil.getBsq90DayAveragePrice().getValue());
    }

    private static Offer bsqOffer(long price) {
        return make(OfferMaker.btcUsdOffer.but(
                with(OfferMaker.baseCurrencyCode, "BSQ"),
                with(OfferMaker.counterCurrencyCode, "BTC"),
                with(OfferMaker.price, price)));
    }

    // The default of the trim threshold
    private static Preferences preferences() {
        Preferences preferences = mock(Preferences.class);
        when(preferences.getBsqAverageTrimThreshold()).thenReturn(0.05);
        return preferences;
    }

    private static TradeStatisticsManager tradeStatisticsManager(TradeStatistics3... tradeStatistics) {
        TradeStatisticsManager tradeStatisticsManager = mock(TradeStatisticsManager.class);
        when(tradeStatisticsManager.getNavigableTradeStatisticsSet())
                .thenReturn(new TreeSet<>(List.of(tradeStatistics)));
        return tradeStatisticsManager;
    }
}
