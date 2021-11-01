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

package bisq.desktop.main.market.trades;

import bisq.core.trade.statistics.TradeStatistics3;

import bisq.common.util.MathUtils;

import org.bitcoinj.core.Coin;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import java.util.Date;
import java.util.List;

public class ChartCalculations {
    static final ZoneId ZONE_ID = ZoneId.systemDefault();

    static long getAveragePrice(List<TradeStatistics3> tradeStatisticsList) {
        long accumulatedAmount = 0;
        long accumulatedVolume = 0;
        for (TradeStatistics3 tradeStatistics : tradeStatisticsList) {
            accumulatedAmount += tradeStatistics.getAmount();
            accumulatedVolume += tradeStatistics.getTradeVolume().getValue();
        }

        double accumulatedVolumeAsDouble = MathUtils.scaleUpByPowerOf10((double) accumulatedVolume, Coin.SMALLEST_UNIT_EXPONENT);
        return MathUtils.roundDoubleToLong(accumulatedVolumeAsDouble / (double) accumulatedAmount);
    }

    static Date roundToTick(LocalDateTime localDate, TradesChartsViewModel.TickUnit tickUnit) {
        switch (tickUnit) {
            case YEAR:
                return Date.from(localDate.withMonth(1).withDayOfYear(1).withHour(0).withMinute(0).withSecond(0).withNano(0).atZone(ZONE_ID).toInstant());
            case MONTH:
                return Date.from(localDate.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0).atZone(ZONE_ID).toInstant());
            case WEEK:
                int dayOfWeek = localDate.getDayOfWeek().getValue();
                LocalDateTime firstDayOfWeek = ChronoUnit.DAYS.addTo(localDate, 1 - dayOfWeek);
                return Date.from(firstDayOfWeek.withHour(0).withMinute(0).withSecond(0).withNano(0).atZone(ZONE_ID).toInstant());
            case DAY:
                return Date.from(localDate.withHour(0).withMinute(0).withSecond(0).withNano(0).atZone(ZONE_ID).toInstant());
            case HOUR:
                return Date.from(localDate.withMinute(0).withSecond(0).withNano(0).atZone(ZONE_ID).toInstant());
            case MINUTE_10:
                return Date.from(localDate.withMinute(localDate.getMinute() - localDate.getMinute() % 10).withSecond(0).withNano(0).atZone(ZONE_ID).toInstant());
            default:
                return Date.from(localDate.atZone(ZONE_ID).toInstant());
        }
    }
}
