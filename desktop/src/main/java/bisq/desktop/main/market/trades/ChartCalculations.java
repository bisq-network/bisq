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

import bisq.desktop.main.market.trades.charts.CandleData;
import bisq.desktop.util.DisplayUtils;

import bisq.core.locale.CurrencyUtil;
import bisq.core.monetary.Altcoin;
import bisq.core.trade.statistics.TradeStatistics3;

import bisq.common.util.MathUtils;

import org.bitcoinj.core.Coin;

import com.google.common.annotations.VisibleForTesting;

import javafx.scene.chart.XYChart;

import javafx.util.Pair;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import lombok.Getter;

import static bisq.desktop.main.market.trades.TradesChartsViewModel.MAX_TICKS;

public class ChartCalculations {
    static final ZoneId ZONE_ID = ZoneId.systemDefault();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Async
    ///////////////////////////////////////////////////////////////////////////////////////////

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

    static CompletableFuture<UpdateChartResult> getUpdateChartResult(List<TradeStatistics3> tradeStatisticsByCurrency,
                                                                     TradesChartsViewModel.TickUnit tickUnit,
                                                                     Map<TradesChartsViewModel.TickUnit, Map<Long, Long>> usdAveragePriceMapsPerTickUnit,
                                                                     String currencyCode) {
        return CompletableFuture.supplyAsync(() -> {
            // Generate date range and create sets for all ticks
            Map<Long, Pair<Date, Set<TradeStatistics3>>> itemsPerInterval = getItemsPerInterval(tradeStatisticsByCurrency, tickUnit);

            Map<Long, Long> usdAveragePriceMap = usdAveragePriceMapsPerTickUnit.get(tickUnit);
            AtomicLong averageUsdPrice = new AtomicLong(0);

            // create CandleData for defined time interval
            List<CandleData> candleDataList = itemsPerInterval.entrySet().stream()
                    .filter(entry -> entry.getKey() >= 0 && !entry.getValue().getValue().isEmpty())
                    .map(entry -> {
                        long tickStartDate = entry.getValue().getKey().getTime();
                        // If we don't have a price we take the previous one
                        if (usdAveragePriceMap.containsKey(tickStartDate)) {
                            averageUsdPrice.set(usdAveragePriceMap.get(tickStartDate));
                        }
                        return getCandleData(entry.getKey(), entry.getValue().getValue(), averageUsdPrice.get(), tickUnit, currencyCode, itemsPerInterval);
                    })
                    .sorted(Comparator.comparingLong(o -> o.tick))
                    .collect(Collectors.toList());

            List<XYChart.Data<Number, Number>> priceItems = candleDataList.stream()
                    .map(e -> new XYChart.Data<Number, Number>(e.tick, e.open, e))
                    .collect(Collectors.toList());

            List<XYChart.Data<Number, Number>> volumeItems = candleDataList.stream()
                    .map(candleData -> new XYChart.Data<Number, Number>(candleData.tick, candleData.accumulatedAmount, candleData))
                    .collect(Collectors.toList());

            List<XYChart.Data<Number, Number>> volumeInUsdItems = candleDataList.stream()
                    .map(candleData -> new XYChart.Data<Number, Number>(candleData.tick, candleData.volumeInUsd, candleData))
                    .collect(Collectors.toList());

            return new UpdateChartResult(itemsPerInterval, priceItems, volumeItems, volumeInUsdItems);
        });
    }

    @Getter
    static class UpdateChartResult {
        private final Map<Long, Pair<Date, Set<TradeStatistics3>>> itemsPerInterval;
        private final List<XYChart.Data<Number, Number>> priceItems;
        private final List<XYChart.Data<Number, Number>> volumeItems;
        private final List<XYChart.Data<Number, Number>> volumeInUsdItems;

        public UpdateChartResult(Map<Long, Pair<Date, Set<TradeStatistics3>>> itemsPerInterval,
                                 List<XYChart.Data<Number, Number>> priceItems,
                                 List<XYChart.Data<Number, Number>> volumeItems,
                                 List<XYChart.Data<Number, Number>> volumeInUsdItems) {

            this.itemsPerInterval = itemsPerInterval;
            this.priceItems = priceItems;
            this.volumeItems = volumeItems;
            this.volumeInUsdItems = volumeInUsdItems;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    static Map<Long, Pair<Date, Set<TradeStatistics3>>> getItemsPerInterval(List<TradeStatistics3> tradeStatisticsByCurrency,
                                                                            TradesChartsViewModel.TickUnit tickUnit) {
        // Generate date range and create sets for all ticks
        Map<Long, Pair<Date, Set<TradeStatistics3>>> itemsPerInterval = new HashMap<>();
        Date time = new Date();
        for (long i = MAX_TICKS + 1; i >= 0; --i) {
            Pair<Date, Set<TradeStatistics3>> pair = new Pair<>((Date) time.clone(), new HashSet<>());
            itemsPerInterval.put(i, pair);
            // We adjust the time for the next iteration
            time.setTime(time.getTime() - 1);
            time = roundToTick(time, tickUnit);
        }

        // Get all entries for the defined time interval
        tradeStatisticsByCurrency.forEach(tradeStatistics -> {
            for (long i = MAX_TICKS; i > 0; --i) {
                Pair<Date, Set<TradeStatistics3>> pair = itemsPerInterval.get(i);
                if (tradeStatistics.getDate().after(pair.getKey())) {
                    pair.getValue().add(tradeStatistics);
                    break;
                }
            }
        });
        return itemsPerInterval;
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

    static Date roundToTick(Date time, TradesChartsViewModel.TickUnit tickUnit) {
        return roundToTick(time.toInstant().atZone(ChartCalculations.ZONE_ID).toLocalDateTime(), tickUnit);
    }

    private static long getAveragePrice(List<TradeStatistics3> tradeStatisticsList) {
        long accumulatedAmount = 0;
        long accumulatedVolume = 0;
        for (TradeStatistics3 tradeStatistics : tradeStatisticsList) {
            accumulatedAmount += tradeStatistics.getAmount();
            accumulatedVolume += tradeStatistics.getTradeVolume().getValue();
        }

        double accumulatedVolumeAsDouble = MathUtils.scaleUpByPowerOf10((double) accumulatedVolume, Coin.SMALLEST_UNIT_EXPONENT);
        return MathUtils.roundDoubleToLong(accumulatedVolumeAsDouble / (double) accumulatedAmount);
    }

    @VisibleForTesting
    static CandleData getCandleData(long tick, Set<TradeStatistics3> set,
                                    long averageUsdPrice,
                                    TradesChartsViewModel.TickUnit tickUnit,
                                    String currencyCode,
                                    Map<Long, Pair<Date, Set<TradeStatistics3>>> itemsPerInterval) {
        long open = 0;
        long close = 0;
        long high = 0;
        long low = 0;
        long accumulatedVolume = 0;
        long accumulatedAmount = 0;
        long numTrades = set.size();
        List<Long> tradePrices = new ArrayList<>();
        for (TradeStatistics3 item : set) {
            long tradePriceAsLong = item.getTradePrice().getValue();
            // Previously a check was done which inverted the low and high for cryptocurrencies.
            low = (low != 0) ? Math.min(low, tradePriceAsLong) : tradePriceAsLong;
            high = (high != 0) ? Math.max(high, tradePriceAsLong) : tradePriceAsLong;

            accumulatedVolume += item.getTradeVolume().getValue();
            accumulatedAmount += item.getTradeAmount().getValue();
            tradePrices.add(tradePriceAsLong);
        }
        Collections.sort(tradePrices);

        List<TradeStatistics3> list = new ArrayList<>(set);
        list.sort(Comparator.comparingLong(TradeStatistics3::getDateAsLong));
        if (list.size() > 0) {
            open = list.get(0).getTradePrice().getValue();
            close = list.get(list.size() - 1).getTradePrice().getValue();
        }

        long averagePrice;
        Long[] prices = new Long[tradePrices.size()];
        tradePrices.toArray(prices);
        long medianPrice = MathUtils.getMedian(prices);
        boolean isBullish;
        if (CurrencyUtil.isCryptoCurrency(currencyCode)) {
            isBullish = close < open;
            double accumulatedAmountAsDouble = MathUtils.scaleUpByPowerOf10((double) accumulatedAmount, Altcoin.SMALLEST_UNIT_EXPONENT);
            averagePrice = MathUtils.roundDoubleToLong(accumulatedAmountAsDouble / (double) accumulatedVolume);
        } else {
            isBullish = close > open;
            double accumulatedVolumeAsDouble = MathUtils.scaleUpByPowerOf10((double) accumulatedVolume, Coin.SMALLEST_UNIT_EXPONENT);
            averagePrice = MathUtils.roundDoubleToLong(accumulatedVolumeAsDouble / (double) accumulatedAmount);
        }

        Date dateFrom = new Date(getTimeFromTickIndex(tick, itemsPerInterval));
        Date dateTo = new Date(getTimeFromTickIndex(tick + 1, itemsPerInterval));
        String dateString = tickUnit.ordinal() > TradesChartsViewModel.TickUnit.DAY.ordinal() ?
                DisplayUtils.formatDateTimeSpan(dateFrom, dateTo) :
                DisplayUtils.formatDate(dateFrom) + " - " + DisplayUtils.formatDate(dateTo);

        // We do not need precision, so we scale down before multiplication otherwise we could get an overflow.
        averageUsdPrice = (long) MathUtils.scaleDownByPowerOf10((double) averageUsdPrice, 4);
        long volumeInUsd = averageUsdPrice * (long) MathUtils.scaleDownByPowerOf10((double) accumulatedAmount, 4);
        // We store USD value without decimals as its only total volume, no precision is needed.
        volumeInUsd = (long) MathUtils.scaleDownByPowerOf10((double) volumeInUsd, 4);
        return new CandleData(tick, open, close, high, low, averagePrice, medianPrice, accumulatedAmount, accumulatedVolume,
                numTrades, isBullish, dateString, volumeInUsd);
    }

    static long getTimeFromTickIndex(long tick, Map<Long, Pair<Date, Set<TradeStatistics3>>> itemsPerInterval) {
        if (tick > MAX_TICKS + 1 ||
                itemsPerInterval.get(tick) == null) {
            return 0;
        }
        return itemsPerInterval.get(tick).getKey().getTime();
    }
}
