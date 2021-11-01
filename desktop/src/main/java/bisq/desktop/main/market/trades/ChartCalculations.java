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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ChartCalculations {
    static final ZoneId ZONE_ID = ZoneId.systemDefault();

    static CompletableFuture<Map<TradesChartsViewModel.TickUnit, Map<Long, Long>>> getUsdAveragePriceMapsPerTickUnit(Set<TradeStatistics3> tradeStatisticsSet) {
        return CompletableFuture.supplyAsync(() -> {
            Map<TradesChartsViewModel.TickUnit, Map<Long, Long>> usdAveragePriceMapsPerTickUnit = new HashMap<>();
            Map<TradesChartsViewModel.TickUnit, Map<Long, List<TradeStatistics3>>> dateMapsPerTickUnit = new HashMap<>();
            for (TradesChartsViewModel.TickUnit tick : TradesChartsViewModel.TickUnit.values()) {
                dateMapsPerTickUnit.put(tick, new HashMap<>());
            }

            tradeStatisticsSet.stream()
                    .filter(e -> e.getCurrency().equals("USD"))
                    .forEach(tradeStatistics -> {
                        for (TradesChartsViewModel.TickUnit tick : TradesChartsViewModel.TickUnit.values()) {
                            long time = roundToTick(tradeStatistics.getLocalDateTime(), tick).getTime();
                            Map<Long, List<TradeStatistics3>> map = dateMapsPerTickUnit.get(tick);
                            map.putIfAbsent(time, new ArrayList<>());
                            map.get(time).add(tradeStatistics);
                        }
                    });

            dateMapsPerTickUnit.forEach((tick, map) -> {
                HashMap<Long, Long> priceMap = new HashMap<>();
                map.forEach((date, tradeStatisticsList) -> priceMap.put(date, getAveragePrice(tradeStatisticsList)));
                usdAveragePriceMapsPerTickUnit.put(tick, priceMap);
            });
            return usdAveragePriceMapsPerTickUnit;
        });
    }

    static CompletableFuture<List<TradeStatistics3>> getTradeStatisticsForCurrency(Set<TradeStatistics3> tradeStatisticsSet,
                                                                                   String currencyCode,
                                                                                   boolean showAllTradeCurrencies) {
        return CompletableFuture.supplyAsync(() -> {
            return tradeStatisticsSet.stream()
                    .filter(e -> showAllTradeCurrencies || e.getCurrency().equals(currencyCode))
                    .collect(Collectors.toList());
        });
    }

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
