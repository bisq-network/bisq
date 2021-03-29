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

package bisq.desktop.main.dao.economy.dashboard.price;

import bisq.desktop.components.chart.ChartDataModel;

import bisq.core.trade.statistics.TradeStatistics3;
import bisq.core.trade.statistics.TradeStatisticsManager;

import bisq.common.util.MathUtils;

import javax.inject.Inject;

import java.time.Instant;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PriceChartDataModel extends ChartDataModel {
    private final TradeStatisticsManager tradeStatisticsManager;
    private Map<Long, Double> bsqUsdPriceByInterval, bsqBtcPriceByInterval, btcUsdPriceByInterval;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PriceChartDataModel(TradeStatisticsManager tradeStatisticsManager) {
        super();

        this.tradeStatisticsManager = tradeStatisticsManager;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Data
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void invalidateCache() {
        bsqUsdPriceByInterval = null;
        bsqBtcPriceByInterval = null;
        btcUsdPriceByInterval = null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Average price from timeline selection
    ///////////////////////////////////////////////////////////////////////////////////////////

    double averageBsqUsdPrice() {
        return getAveragePriceFromDateFilter(tradeStatistics -> tradeStatistics.getCurrency().equals("BSQ") ||
                        tradeStatistics.getCurrency().equals("USD"),
                PriceChartDataModel::getAverageBsqUsdPrice);
    }

    double averageBsqBtcPrice() {
        return getAveragePriceFromDateFilter(tradeStatistics -> tradeStatistics.getCurrency().equals("BSQ"),
                PriceChartDataModel::getAverageBsqBtcPrice);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Chart data
    ///////////////////////////////////////////////////////////////////////////////////////////

    Map<Long, Double> getBsqUsdPriceByInterval() {
        if (bsqUsdPriceByInterval != null) {
            return bsqUsdPriceByInterval;
        }
        bsqUsdPriceByInterval = getPriceByInterval(tradeStatistics -> tradeStatistics.getCurrency().equals("BSQ") ||
                        tradeStatistics.getCurrency().equals("USD"),
                PriceChartDataModel::getAverageBsqUsdPrice);
        return bsqUsdPriceByInterval;
    }

    Map<Long, Double> getBsqBtcPriceByInterval() {
        if (bsqBtcPriceByInterval != null) {
            return bsqBtcPriceByInterval;
        }

        bsqBtcPriceByInterval = getPriceByInterval(tradeStatistics -> tradeStatistics.getCurrency().equals("BSQ"),
                PriceChartDataModel::getAverageBsqBtcPrice);
        return bsqBtcPriceByInterval;
    }

    Map<Long, Double> getBtcUsdPriceByInterval() {
        if (btcUsdPriceByInterval != null) {
            return btcUsdPriceByInterval;
        }

        btcUsdPriceByInterval = getPriceByInterval(tradeStatistics -> tradeStatistics.getCurrency().equals("USD"),
                PriceChartDataModel::getAverageBtcUsdPrice);
        return btcUsdPriceByInterval;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Average price functions
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static double getAverageBsqUsdPrice(List<TradeStatistics3> list) {
        double sumBsq = 0;
        double sumBtcFromBsqTrades = 0;
        double sumBtcFromUsdTrades = 0;
        double sumUsd = 0;
        for (TradeStatistics3 tradeStatistics : list) {
            if (tradeStatistics.getCurrency().equals("BSQ")) {
                sumBsq += getBsqAmount(tradeStatistics);
                sumBtcFromBsqTrades += getBtcAmount(tradeStatistics);
            } else if (tradeStatistics.getCurrency().equals("USD")) {
                sumUsd += getUsdAmount(tradeStatistics);
                sumBtcFromUsdTrades += getBtcAmount(tradeStatistics);
            }
        }
        if (sumBsq == 0 || sumBtcFromBsqTrades == 0 || sumBtcFromUsdTrades == 0 || sumUsd == 0) {
            return 0d;
        }
        double averageUsdPrice = sumUsd / sumBtcFromUsdTrades;
        return sumBtcFromBsqTrades * averageUsdPrice / sumBsq;
    }

    private static double getAverageBsqBtcPrice(List<TradeStatistics3> list) {
        double sumBsq = 0;
        double sumBtc = 0;
        for (TradeStatistics3 tradeStatistics : list) {
            sumBsq += getBsqAmount(tradeStatistics);
            sumBtc += getBtcAmount(tradeStatistics);
        }
        if (sumBsq == 0 || sumBtc == 0) {
            return 0d;
        }
        return MathUtils.scaleUpByPowerOf10(sumBtc / sumBsq, 8);
    }

    private static double getAverageBtcUsdPrice(List<TradeStatistics3> list) {
        double sumUsd = 0;
        double sumBtc = 0;
        for (TradeStatistics3 tradeStatistics : list) {
            sumUsd += getUsdAmount(tradeStatistics);
            sumBtc += getBtcAmount(tradeStatistics);
        }
        if (sumUsd == 0 || sumBtc == 0) {
            return 0d;
        }
        return sumUsd / sumBtc;
    }

    private static long getBtcAmount(TradeStatistics3 tradeStatistics) {
        return tradeStatistics.getAmount();
    }

    private static double getUsdAmount(TradeStatistics3 tradeStatistics) {
        return MathUtils.scaleUpByPowerOf10(tradeStatistics.getTradeVolume().getValue(), 4);
    }

    private static long getBsqAmount(TradeStatistics3 tradeStatistics) {
        return tradeStatistics.getTradeVolume().getValue();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Aggregated collection data by interval
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Map<Long, Double> getPriceByInterval(Predicate<TradeStatistics3> collectionFilter,
                                                 Function<List<TradeStatistics3>, Double> getAveragePriceFunction) {
        return getPriceByInterval(tradeStatisticsManager.getObservableTradeStatisticsSet(),
                collectionFilter,
                tradeStatistics -> toTimeInterval(Instant.ofEpochMilli(tradeStatistics.getDateAsLong())),
                dateFilter,
                getAveragePriceFunction);
    }

    private Map<Long, Double> getPriceByInterval(Collection<TradeStatistics3> collection,
                                                 Predicate<TradeStatistics3> collectionFilter,
                                                 Function<TradeStatistics3, Long> groupByDateFunction,
                                                 Predicate<Long> dateFilter,
                                                 Function<List<TradeStatistics3>, Double> getAveragePriceFunction) {
        return collection.stream()
                .filter(collectionFilter)
                .collect(Collectors.groupingBy(groupByDateFunction))
                .entrySet()
                .stream()
                .filter(entry -> dateFilter.test(entry.getKey()))
                .map(entry -> new AbstractMap.SimpleEntry<>(
                        entry.getKey(),
                        getAveragePriceFunction.apply(entry.getValue())))
                .filter(e -> e.getValue() > 0d)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private double getAveragePriceFromDateFilter(Predicate<TradeStatistics3> collectionFilter,
                                                 Function<List<TradeStatistics3>, Double> getAveragePriceFunction) {
        return getAveragePriceFunction.apply(tradeStatisticsManager.getObservableTradeStatisticsSet().stream()
                .filter(collectionFilter)
                .filter(tradeStatistics -> dateFilter.test(tradeStatistics.getDateAsLong() / 1000))
                .collect(Collectors.toList()));
    }
}
