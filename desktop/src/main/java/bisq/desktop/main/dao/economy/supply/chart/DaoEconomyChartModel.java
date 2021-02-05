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

package bisq.desktop.main.dao.economy.supply.chart;

import bisq.desktop.components.chart.ChartModel;
import bisq.desktop.main.dao.economy.supply.DaoEconomyDataProvider;

import bisq.core.dao.state.DaoStateService;

import bisq.common.util.Tuple2;

import javax.inject.Inject;

import javafx.scene.chart.XYChart;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DaoEconomyChartModel extends ChartModel {
    private final DaoStateService daoStateService;
    private final DaoEconomyDataProvider daoEconomyDataProvider;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DaoEconomyChartModel(DaoStateService daoStateService, DaoEconomyDataProvider daoEconomyDataProvider) {
        super();
        this.daoStateService = daoStateService;

        this.daoEconomyDataProvider = daoEconomyDataProvider;
    }

    List<XYChart.Data<Number, Number>> getBsqTradeFeeChartData(Predicate<Long> predicate) {
        return toChartData(daoEconomyDataProvider.getBurnedBsqByMonth(daoStateService.getTradeFeeTxs(), predicate));
    }

    // The resulting data are not very useful. It causes negative values if burn rate > issuance in selected timeframe
   /* List<XYChart.Data<Number, Number>> getBtcTradeFeeChartData(Predicate<Long> predicate) {
        return toChartData(daoEconomyDataProvider.getBurnedBtcByMonth(predicate));
    }*/

    List<XYChart.Data<Number, Number>> getCompensationChartData(Predicate<Long> predicate) {
        return toChartData(daoEconomyDataProvider.getMergedCompensationMap(predicate));
    }

    List<XYChart.Data<Number, Number>> getProofOfBurnChartData(Predicate<Long> predicate) {
        return toChartData(daoEconomyDataProvider.getBurnedBsqByMonth(daoStateService.getProofOfBurnTxs(), predicate));
    }

    List<XYChart.Data<Number, Number>> getReimbursementChartData(Predicate<Long> predicate) {
        return toChartData(daoEconomyDataProvider.getMergedReimbursementMap(predicate));
    }

    void initBounds(List<XYChart.Data<Number, Number>> tradeFeeChartData,
                    List<XYChart.Data<Number, Number>> compensationRequestsChartData) {
        Tuple2<Double, Double> xMinMaxTradeFee = getMinMax(tradeFeeChartData);
        Tuple2<Double, Double> xMinMaxCompensationRequest = getMinMax(compensationRequestsChartData);

        lowerBound = Math.min(xMinMaxTradeFee.first, xMinMaxCompensationRequest.first);
        upperBound = Math.max(xMinMaxTradeFee.second, xMinMaxCompensationRequest.second);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static List<XYChart.Data<Number, Number>> toChartData(Map<Long, Long> map) {
        return map.entrySet().stream()
                .map(entry -> new XYChart.Data<Number, Number>(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private static Tuple2<Double, Double> getMinMax(List<XYChart.Data<Number, Number>> chartData) {
        long min = Long.MAX_VALUE, max = 0;
        for (XYChart.Data<Number, ?> data : chartData) {
            min = Math.min(data.getXValue().longValue(), min);
            max = Math.max(data.getXValue().longValue(), max);
        }
        return new Tuple2<>((double) min, (double) max);
    }
}
