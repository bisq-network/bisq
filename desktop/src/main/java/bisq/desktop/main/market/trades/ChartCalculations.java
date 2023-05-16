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

import bisq.desktop.main.market.trades.TradesChartsViewModel.TickUnit;
import bisq.desktop.main.market.trades.charts.CandleData;
import bisq.desktop.util.DisplayUtils;

import bisq.core.locale.CurrencyUtil;
import bisq.core.monetary.Altcoin;
import bisq.core.trade.statistics.TradeStatistics3;

import bisq.common.util.MathUtils;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSortedSet;

import javafx.scene.chart.XYChart;

import javafx.util.Pair;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import lombok.Getter;

import static bisq.desktop.main.market.trades.TradesChartsViewModel.MAX_TICKS;

public class ChartCalculations {
    @VisibleForTesting
    static final ZoneId ZONE_ID = ZoneId.systemDefault();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Async
    ///////////////////////////////////////////////////////////////////////////////////////////

    static CompletableFuture<Map<TickUnit, Map<Long, Long>>> getUsdAveragePriceMapsPerTickUnit(NavigableSet<TradeStatistics3> sortedTradeStatisticsSet) {
        return CompletableFuture.supplyAsync(() -> {
            Map<TickUnit, Map<Long, PriceAccumulator>> priceAccumulatorMapsPerTickUnit = new HashMap<>();
            for (TickUnit tick : TickUnit.values()) {
                priceAccumulatorMapsPerTickUnit.put(tick, new HashMap<>());
            }

            // Stream the trade statistics in reverse chronological order
            TickUnit[] tickUnits = TickUnit.values();
            sortedTradeStatisticsSet.descendingSet().stream()
                    .filter(e -> e.getCurrency().equals("USD"))
                    .forEach(tradeStatistics -> {
                        for (TickUnit tickUnit : tickUnits) {
                            Map<Long, PriceAccumulator> map = priceAccumulatorMapsPerTickUnit.get(tickUnit);
                            if (map.size() > MAX_TICKS) {
                                // No more prices are needed once more than MAX_TICKS candles have been spanned
                                // (and tick size is decreasing so we may break out of the whole loop)
                                break;
                            }
                            long time = roundToTick(tradeStatistics.getLocalDateTime(), tickUnit).getTime();
                            map.computeIfAbsent(time, t -> new PriceAccumulator()).add(tradeStatistics);
                        }
                    });

            Map<TickUnit, Map<Long, Long>> usdAveragePriceMapsPerTickUnit = new HashMap<>();
            priceAccumulatorMapsPerTickUnit.forEach((tickUnit, map) -> {
                HashMap<Long, Long> priceMap = new HashMap<>();
                map.forEach((date, accumulator) -> priceMap.put(date, accumulator.getAveragePrice()));
                usdAveragePriceMapsPerTickUnit.put(tickUnit, priceMap);
            });
            return usdAveragePriceMapsPerTickUnit;
        });
    }

    static CompletableFuture<List<TradeStatistics3>> getTradeStatisticsForCurrency(Set<TradeStatistics3> tradeStatisticsSet,
                                                                                   String currencyCode,
                                                                                   boolean showAllTradeCurrencies) {
        return CompletableFuture.supplyAsync(() -> tradeStatisticsSet.stream()
                .filter(e -> showAllTradeCurrencies || e.getCurrency().equals(currencyCode))
                .collect(Collectors.toList()));
    }

    static CompletableFuture<UpdateChartResult> getUpdateChartResult(List<TradeStatistics3> tradeStatisticsByCurrency,
                                                                     TickUnit tickUnit,
                                                                     Map<TickUnit, Map<Long, Long>> usdAveragePriceMapsPerTickUnit,
                                                                     String currencyCode) {
        return CompletableFuture.supplyAsync(() -> {
            // Generate date range and create sets for all ticks
            List<Pair<Date, Set<TradeStatistics3>>> itemsPerInterval = getItemsPerInterval(tradeStatisticsByCurrency, tickUnit);

            Map<Long, Long> usdAveragePriceMap = usdAveragePriceMapsPerTickUnit.get(tickUnit);
            AtomicLong averageUsdPrice = new AtomicLong(0);

            // create CandleData for defined time interval
            List<CandleData> candleDataList = IntStream.range(0, itemsPerInterval.size())
                    .filter(i -> !itemsPerInterval.get(i).getValue().isEmpty())
                    .mapToObj(i -> {
                        Pair<Date, Set<TradeStatistics3>> pair = itemsPerInterval.get(i);
                        long tickStartDate = pair.getKey().getTime();
                        // If we don't have a price we take the previous one
                        if (usdAveragePriceMap.containsKey(tickStartDate)) {
                            averageUsdPrice.set(usdAveragePriceMap.get(tickStartDate));
                        }
                        return getCandleData(i, pair.getValue(), averageUsdPrice.get(), tickUnit, currencyCode, itemsPerInterval);
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
        private final List<Pair<Date, Set<TradeStatistics3>>> itemsPerInterval;
        private final List<XYChart.Data<Number, Number>> priceItems;
        private final List<XYChart.Data<Number, Number>> volumeItems;
        private final List<XYChart.Data<Number, Number>> volumeInUsdItems;

        public UpdateChartResult(List<Pair<Date, Set<TradeStatistics3>>> itemsPerInterval,
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

    static List<Pair<Date, Set<TradeStatistics3>>> getItemsPerInterval(List<TradeStatistics3> tradeStatisticsByCurrency,
                                                                       TickUnit tickUnit) {
        // Generate date range and create lists for all ticks
        List<Pair<Date, List<TradeStatistics3>>> itemsPerInterval = new ArrayList<>(Collections.nCopies(MAX_TICKS + 2, null));
        Date time = new Date();
        for (int i = MAX_TICKS + 1; i >= 0; --i) {
            Pair<Date, List<TradeStatistics3>> pair = new Pair<>((Date) time.clone(), new ArrayList<>());
            itemsPerInterval.set(i, pair);
            // We adjust the time for the next iteration
            time.setTime(time.getTime() - 1);
            time = roundToTick(time, tickUnit);
        }

        // Get all entries for the defined time interval
        int i = MAX_TICKS;
        for (TradeStatistics3 tradeStatistics : tradeStatisticsByCurrency) {
            // Start from the last used tick index - move forwards if necessary
            for (; i < MAX_TICKS; i++) {
                Pair<Date, List<TradeStatistics3>> pair = itemsPerInterval.get(i + 1);
                if (!tradeStatistics.getDate().after(pair.getKey())) {
                    break;
                }
            }
            // Scan backwards until the correct tick is reached
            for (; i > 0; --i) {
                Pair<Date, List<TradeStatistics3>> pair = itemsPerInterval.get(i);
                if (tradeStatistics.getDate().after(pair.getKey())) {
                    pair.getValue().add(tradeStatistics);
                    break;
                }
            }
        }
        // Convert the lists into sorted sets
        return itemsPerInterval.stream()
                .map(pair -> new Pair<>(pair.getKey(), (Set<TradeStatistics3>) ImmutableSortedSet.copyOf(pair.getValue())))
                .collect(Collectors.toList());
    }


    private static LocalDateTime roundToTickAsLocalDateTime(LocalDateTime localDateTime,
                                                            TickUnit tickUnit) {
        switch (tickUnit) {
            case YEAR:
                return localDateTime.withMonth(1).withDayOfYear(1).toLocalDate().atStartOfDay();
            case MONTH:
                return localDateTime.withDayOfMonth(1).toLocalDate().atStartOfDay();
            case WEEK:
                int dayOfWeek = localDateTime.getDayOfWeek().getValue();
                LocalDate firstDayOfWeek = localDateTime.toLocalDate().minusDays(dayOfWeek - 1);
                return firstDayOfWeek.atStartOfDay();
            case DAY:
                return localDateTime.toLocalDate().atStartOfDay();
            case HOUR:
                return localDateTime.withMinute(0).withSecond(0).withNano(0);
            case MINUTE_10:
                return localDateTime.withMinute(localDateTime.getMinute() - localDateTime.getMinute() % 10).withSecond(0).withNano(0);
            default:
                return localDateTime;
        }
    }

    // Use an array rather than an EnumMap here, since the latter is not thread safe - this gives benign races only:
    private static final Tuple2<?, ?>[] cachedLocalDateTimeToDateMappings = new Tuple2<?, ?>[TickUnit.values().length];

    private static Date roundToTick(LocalDateTime localDateTime, TickUnit tickUnit) {
        LocalDateTime rounded = roundToTickAsLocalDateTime(localDateTime, tickUnit);
        // Benefits from caching last result (per tick unit) since trade statistics are pre-sorted by date
        int i = tickUnit.ordinal();
        var tuple = cachedLocalDateTimeToDateMappings[i];
        if (tuple == null || !rounded.equals(tuple.first)) {
            cachedLocalDateTimeToDateMappings[i] = tuple = new Tuple2<>(rounded, Date.from(rounded.atZone(ZONE_ID).toInstant()));
        }
        return (Date) tuple.second;
    }

    @VisibleForTesting
    static Date roundToTick(Date time, TickUnit tickUnit) {
        return roundToTick(time.toInstant().atZone(ChartCalculations.ZONE_ID).toLocalDateTime(), tickUnit);
    }

    @VisibleForTesting
    static CandleData getCandleData(int tickIndex, Set<TradeStatistics3> set,
                                    long averageUsdPrice,
                                    TickUnit tickUnit,
                                    String currencyCode,
                                    List<Pair<Date, Set<TradeStatistics3>>> itemsPerInterval) {
        long open = 0;
        long close = 0;
        long high = 0;
        long low = 0;
        long accumulatedVolume = 0;
        long accumulatedAmount = 0;
        long numTrades = set.size();

        int arrayIndex = 0;
        long[] tradePrices = new long[set.size()];
        for (TradeStatistics3 item : set) {
            long tradePriceAsLong = item.getTradePrice().getValue();
            // Previously a check was done which inverted the low and high for cryptocurrencies.
            low = (low != 0) ? Math.min(low, tradePriceAsLong) : tradePriceAsLong;
            high = (high != 0) ? Math.max(high, tradePriceAsLong) : tradePriceAsLong;

            accumulatedVolume += item.getTradeVolume().getValue();
            accumulatedAmount += item.getTradeAmount().getValue();
            tradePrices[arrayIndex++] = tradePriceAsLong;
        }
        Arrays.sort(tradePrices);

        if (!set.isEmpty()) {
            NavigableSet<TradeStatistics3> sortedSet = ImmutableSortedSet.copyOf(set);
            open = sortedSet.first().getTradePrice().getValue();
            close = sortedSet.last().getTradePrice().getValue();
        }

        long averagePrice;
        long medianPrice = MathUtils.getMedian(tradePrices);
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

        Date dateFrom = new Date(getTimeFromTickIndex(tickIndex, itemsPerInterval));
        Date dateTo = new Date(getTimeFromTickIndex(tickIndex + 1, itemsPerInterval));
        String dateString = tickUnit.ordinal() > TickUnit.DAY.ordinal() ?
                DisplayUtils.formatDateTimeSpan(dateFrom, dateTo) :
                DisplayUtils.formatDate(dateFrom) + " - " + DisplayUtils.formatDate(dateTo);

        // We do not need precision, so we scale down before multiplication otherwise we could get an overflow.
        averageUsdPrice = (long) MathUtils.scaleDownByPowerOf10((double) averageUsdPrice, 4);
        long volumeInUsd = averageUsdPrice * (long) MathUtils.scaleDownByPowerOf10((double) accumulatedAmount, 4);
        // We store USD value without decimals as its only total volume, no precision is needed.
        volumeInUsd = (long) MathUtils.scaleDownByPowerOf10((double) volumeInUsd, 4);
        return new CandleData(tickIndex, open, close, high, low, averagePrice, medianPrice, accumulatedAmount, accumulatedVolume,
                numTrades, isBullish, dateString, volumeInUsd);
    }

    static long getTimeFromTickIndex(int tickIndex, List<Pair<Date, Set<TradeStatistics3>>> itemsPerInterval) {
        if (tickIndex < 0 || tickIndex >= itemsPerInterval.size()) {
            return 0;
        }
        return itemsPerInterval.get(tickIndex).getKey().getTime();
    }

    private static class PriceAccumulator {
        private long accumulatedAmount;
        private long accumulatedVolume;

        void add(TradeStatistics3 tradeStatistics) {
            accumulatedAmount += tradeStatistics.getAmount();
            accumulatedVolume += tradeStatistics.getTradeVolume().getValue();
        }

        long getAveragePrice() {
            double accumulatedVolumeAsDouble = MathUtils.scaleUpByPowerOf10((double) accumulatedVolume, Coin.SMALLEST_UNIT_EXPONENT);
            return MathUtils.roundDoubleToLong(accumulatedVolumeAsDouble / (double) accumulatedAmount);
        }
    }
}
