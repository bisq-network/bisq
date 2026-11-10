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
import bisq.core.trade.statistics.TradeStatistics3;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;

import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;

import java.util.Date;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AveragePriceUtilTest {
    private static final long NOW = System.currentTimeMillis();

    // Positive control: 0.1 BTC for 5000 BSQ, and 0.1 BTC for 5000 USD afterwards, so 1 BSQ costs 1 USD
    @Test
    void averagesOfNormalStatistics() {
        TradeStatistics3 bsq = statistics("BSQ", 2000, Coin.parseCoin("0.1").getValue(), NOW - hours(2));
        TradeStatistics3 usd = statistics("USD", Price.parse("USD", "50000").getValue(),
                Coin.parseCoin("0.1").getValue(), NOW - hours(1));

        Tuple2<Price, Price> averages = averages(bsq, usd);

        assertEquals(Price.parse("USD", "1").getValue(), averages.first.getValue());
        assertEquals(2000, averages.second.getValue());
    }

    // Both statistics pass isValid, the USD one with the highest valid price, but the BSQ price in USD cannot be
    // represented as a long. It is reported as 0, as without BSQ trades, instead of failing every view which shows it.
    @Test
    void usdAverageWhichDoesNotFitIntoALongIsZero() {
        TradeStatistics3 bsq = statistics("BSQ", 10_000_000_000_000_000L, Coin.parseCoin("2").getValue(),
                NOW - hours(2));
        TradeStatistics3 usd = statistics("USD", 1_000_000_000_000_000_000L, 1, NOW - hours(1));
        assertTrue(bsq.isValid() && usd.isValid());

        Tuple2<Price, Price> averages = assertDoesNotThrow(() -> averages(bsq, usd));

        assertEquals(0, averages.first.getValue());
    }

    // Before 2021-11-01 there is no amount cap. The BSQ volumes of these statistics sum up to 2^64 + 1, which a long
    // sum wraps to 1. The BTC average still is the price of the statistics. No current window reaches back that far.
    @Test
    void btcAverageOfVolumesBeyondTheLongRange() {
        long date = 1_600_000_000_000L;
        TradeStatistics3 first = statistics("BSQ", 1, 92_233_720_368L, date);
        TradeStatistics3 second = statistics("BSQ", 1, 92_233_720_368L, date + 1);
        TradeStatistics3 third = statistics("BSQ", 100_000_000, 109_551_617L, date + 2);
        assertTrue(first.isValid() && second.isValid() && third.isValid());

        Tuple2<Price, Price> averages = assertDoesNotThrow(() -> AveragePriceUtil.getAveragePriceTuple(preferences(),
                tradeStatisticsManager(first, second, third), 30, new Date(date + TimeUnit.DAYS.toMillis(1))));

        assertEquals(1, averages.second.getValue());
    }

    private static Tuple2<Price, Price> averages(TradeStatistics3... tradeStatistics) {
        return AveragePriceUtil.getAveragePriceTuple(preferences(), tradeStatisticsManager(tradeStatistics), 30);
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

    private static TradeStatistics3 statistics(String currency, long price, long amount, long date) {
        String paymentMethod = currency.equals("BSQ") ? "BLOCK_CHAINS" : "SEPA";
        return new TradeStatistics3(currency, price, amount, paymentMethod, date, null, null,
                (TreeMap<String, String>) null);
    }

    private static long hours(int hours) {
        return TimeUnit.HOURS.toMillis(hours);
    }
}
