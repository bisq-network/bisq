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

package bisq.desktop.main.dao.economy.dashboard.volume;

import bisq.desktop.components.chart.ChartDataModel;

import bisq.core.trade.statistics.TradeStatistics3;
import bisq.core.trade.statistics.TradeStatisticsManager;

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
public class VolumeChartDataModel extends ChartDataModel {
    private final TradeStatisticsManager tradeStatisticsManager;
    private Map<Long, Long> usdVolumeByInterval, btcVolumeByInterval;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public VolumeChartDataModel(TradeStatisticsManager tradeStatisticsManager) {
        super();

        this.tradeStatisticsManager = tradeStatisticsManager;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Total amounts
    ///////////////////////////////////////////////////////////////////////////////////////////

    long getUsdVolume() {
        return getUsdVolumeByInterval().values().stream()
                .mapToLong(e -> e)
                .sum();
    }

    long getBtcVolume() {
        return getBtcVolumeByInterval().values().stream()
                .mapToLong(e -> e)
                .sum();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Data
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void invalidateCache() {
        usdVolumeByInterval = null;
        btcVolumeByInterval = null;
    }

    public Map<Long, Long> getUsdVolumeByInterval() {
        if (usdVolumeByInterval != null) {
            return usdVolumeByInterval;
        }

        usdVolumeByInterval = getVolumeByInterval(VolumeChartDataModel::getVolumeInUsd);
        return usdVolumeByInterval;
    }

    public Map<Long, Long> getBtcVolumeByInterval() {
        if (btcVolumeByInterval != null) {
            return btcVolumeByInterval;
        }

        btcVolumeByInterval = getVolumeByInterval(VolumeChartDataModel::getVolumeInBtc);
        return btcVolumeByInterval;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Get volume functions
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static long getVolumeInUsd(List<TradeStatistics3> list) {
        double sumBtcFromAllTrades = 0;
        double sumBtcFromUsdTrades = 0;
        double sumUsd = 0;
        for (TradeStatistics3 tradeStatistics : list) {
            long amount = tradeStatistics.getAmount();
            if (tradeStatistics.getCurrency().equals("USD")) {
                sumUsd += tradeStatistics.getTradeVolume().getValue();
                sumBtcFromUsdTrades += amount;
            }
            sumBtcFromAllTrades += amount;
        }
        if (sumBtcFromAllTrades == 0 || sumBtcFromUsdTrades == 0 || sumUsd == 0) {
            return 0L;
        }
        double averageUsdPrice = sumUsd / sumBtcFromUsdTrades;
        // We truncate to 4 decimals
        return (long) (sumBtcFromAllTrades * averageUsdPrice);
    }

    private static long getVolumeInBtc(List<TradeStatistics3> list) {
        return list.stream().mapToLong(TradeStatistics3::getAmount).sum();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Aggregated collection data by interval
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Map<Long, Long> getVolumeByInterval(Function<List<TradeStatistics3>, Long> getVolumeFunction) {
        return getVolumeByInterval(tradeStatisticsManager.getObservableTradeStatisticsSet(),
                tradeStatistics -> toTimeInterval(Instant.ofEpochMilli(tradeStatistics.getDateAsLong())),
                dateFilter,
                getVolumeFunction);
    }

    private Map<Long, Long> getVolumeByInterval(Collection<TradeStatistics3> collection,
                                                Function<TradeStatistics3, Long> groupByDateFunction,
                                                Predicate<Long> dateFilter,
                                                Function<List<TradeStatistics3>, Long> getVolumeFunction) {
        return collection.stream()
                .collect(Collectors.groupingBy(groupByDateFunction))
                .entrySet()
                .stream()
                .filter(entry -> dateFilter.test(entry.getKey()))
                .map(entry -> new AbstractMap.SimpleEntry<>(
                        entry.getKey(),
                        getVolumeFunction.apply(entry.getValue())))
                .filter(e -> e.getValue() > 0L)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
