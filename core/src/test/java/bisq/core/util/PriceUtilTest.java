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
import bisq.core.offer.OfferDirection;
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
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import static bisq.core.offer.OfferDirection.BUY;
import static bisq.core.offer.OfferDirection.SELL;
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
        assertFalse(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 2000), AVERAGE, 2, BUY));
        assertFalse(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 2000), AVERAGE, 2, SELL));
        assertFalse(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 3999), AVERAGE, 2, BUY));
        assertFalse(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 1001), AVERAGE, 2, SELL));
    }

    // A price at a bound is inside: only a price above the average times the factor where the taker buys BSQ, or
    // below the average divided by the factor where the taker sells BSQ, is outside.
    @Test
    void priceAtABoundIsNotOutside() {
        assertFalse(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 4000), AVERAGE, 2, BUY));
        assertFalse(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 1000), AVERAGE, 2, SELL));
    }

    @Test
    void priceAtDecimalUpperBoundIsNotOutside() {
        assertFalse(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 2002), AVERAGE, 1.001, BUY));
        assertTrue(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 2003), AVERAGE, 1.001, BUY));
    }

    @Test
    void priceAtDecimalLowerBoundIsNotOutside() {
        Price average = Price.valueOf("BSQ", 2260);
        assertFalse(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 2000), average, 1.13, SELL));
        assertTrue(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 1999), average, 1.13, SELL));
    }

    // The maker of an offer with the direction BUY buys BTC and sells BSQ, so a high price is worse for its taker. The
    // taker of an offer with the direction SELL sells BSQ, so a low price is worse for it.
    @Test
    void priceWorseForTheTakerBeyondABoundIsOutside() {
        assertTrue(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 4001), AVERAGE, 2, BUY));
        assertTrue(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 999), AVERAGE, 2, SELL));
        assertTrue(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 8001), AVERAGE, 4, BUY));
        assertTrue(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 499), AVERAGE, 4, SELL));
        assertFalse(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 8000), AVERAGE, 4, BUY));
        assertFalse(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 500), AVERAGE, 4, SELL));
    }

    // Any peer can move the average with invented statistics. If a price better for the taker were outside too, such
    // an average could hide the fair offers and leave only offers worse for the taker visible.
    @Test
    void priceBetterForTheTakerIsNeverOutside() {
        assertFalse(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 999), AVERAGE, 2, BUY));
        assertFalse(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 1), AVERAGE, 2, BUY));
        assertFalse(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 4001), AVERAGE, 2, SELL));
        assertFalse(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", Long.MAX_VALUE), AVERAGE, 2, SELL));
    }

    // Without a price of the offer or without an average, as on a node without BSQ trade statistics, nothing is
    // outside, so that no offer is hidden.
    @Test
    void missingPriceOrAverageIsNeverOutside() {
        for (OfferDirection direction : OfferDirection.values()) {
            assertFalse(PriceUtil.isOutsidePriceRange(null, AVERAGE, 2, direction));
            assertFalse(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 0), AVERAGE, 2, direction));
            assertFalse(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", 1), Price.valueOf("BSQ", 0), 2,
                    direction));
            assertFalse(PriceUtil.isOutsidePriceRange(Price.valueOf("BSQ", Long.MAX_VALUE), Price.valueOf("BSQ", 0), 2,
                    direction));
        }
    }

    @Test
    void pricesOfDifferentCurrenciesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PriceUtil.isOutsidePriceRange(Price.valueOf("XMR", 2000), AVERAGE, 2, BUY));
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
        Offer offerAtAverage = bsqOffer(2000, BUY);

        assertEquals(offerAtAverage.getPrice().getValue(), average.getValue());
        assertFalse(PriceUtil.isOutsidePriceRange(offerAtAverage.getPrice(), average, 2, BUY));
        assertFalse(PriceUtil.isOutsidePriceRange(bsqOffer(4000, BUY).getPrice(), average, 2, BUY));
        assertTrue(PriceUtil.isOutsidePriceRange(bsqOffer(4001, BUY).getPrice(), average, 2, BUY));
        assertTrue(PriceUtil.isOutsidePriceRange(bsqOffer(999, SELL).getPrice(), average, 2, SELL));
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

    // The predicate reads the factor of the preferences and the direction of the offer, and leaves offers of other
    // currencies alone
    @Test
    void bsqOffersOutsideTheRangeOfThePreferencesMatch() {
        TradeStatistics3 statistic = new TradeStatistics3("BSQ", 2000, Coin.parseCoin("0.1").getValue(),
                "BLOCK_CHAINS", System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1), null, null,
                (TreeMap<String, String>) null);
        Preferences preferences = preferences();
        when(preferences.getBsqOfferPriceRangeFactor()).thenReturn(4d);
        PriceUtil priceUtil = new PriceUtil(mock(PriceFeedService.class), tradeStatisticsManager(statistic),
                preferences);

        Predicate<Offer> isOutsidePriceRange = priceUtil.getOutsideBsqOfferPriceRangePredicate();

        assertFalse(isOutsidePriceRange.test(bsqOffer(8000, BUY)));
        assertFalse(isOutsidePriceRange.test(bsqOffer(500, SELL)));
        assertTrue(isOutsidePriceRange.test(bsqOffer(8001, BUY)));
        assertTrue(isOutsidePriceRange.test(bsqOffer(499, SELL)));
        assertFalse(isOutsidePriceRange.test(bsqOffer(499, BUY)));
        assertFalse(isOutsidePriceRange.test(bsqOffer(8001, SELL)));
        assertFalse(isOutsidePriceRange.test(make(OfferMaker.btcUsdOffer.but(with(OfferMaker.price, 1L)))));
    }

    private static Offer bsqOffer(long price, OfferDirection direction) {
        return make(OfferMaker.btcUsdOffer.but(
                with(OfferMaker.baseCurrencyCode, "BSQ"),
                with(OfferMaker.counterCurrencyCode, "BTC"),
                with(OfferMaker.direction, direction),
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
