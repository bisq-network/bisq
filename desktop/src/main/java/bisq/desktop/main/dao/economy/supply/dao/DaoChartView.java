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

package bisq.desktop.main.dao.economy.supply.dao;

import bisq.desktop.components.chart.ChartView;

import bisq.core.locale.Res;

import javax.inject.Inject;

import javafx.scene.chart.XYChart;

import java.util.Collection;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DaoChartView extends ChartView<DaoChartViewModel> {
    private XYChart.Series<Number, Number> seriesBsqTradeFee, seriesProofOfBurn, seriesCompensation,
            seriesReimbursement, seriesTotalIssued, seriesTotalBurned;


    @Inject
    public DaoChartView(DaoChartViewModel model) {
        super(model);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API Total amounts
    ///////////////////////////////////////////////////////////////////////////////////////////

    public long getCompensationAmount() {
        return model.getCompensationAmount();
    }

    public long getReimbursementAmount() {
        return model.getReimbursementAmount();
    }

    public long getBsqTradeFeeAmount() {
        return model.getBsqTradeFeeAmount();
    }

    public long getProofOfBurnAmount() {
        return model.getProofOfBurnAmount();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Legend
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected Collection<XYChart.Series<Number, Number>> getSeriesForLegend1() {
        return List.of(seriesTotalIssued, seriesCompensation, seriesReimbursement);
    }

    @Override
    protected Collection<XYChart.Series<Number, Number>> getSeriesForLegend2() {
        return List.of(seriesTotalBurned, seriesBsqTradeFee, seriesProofOfBurn);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Timeline navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void initBoundsForTimelineNavigation() {
        setBoundsForTimelineNavigation(seriesTotalBurned.getData());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Series
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void createSeries() {
        seriesTotalIssued = new XYChart.Series<>();
        seriesTotalIssued.setName(Res.get("dao.factsAndFigures.supply.totalIssued"));
        seriesIndexMap.put(getSeriesId(seriesTotalIssued), 0);

        seriesTotalBurned = new XYChart.Series<>();
        seriesTotalBurned.setName(Res.get("dao.factsAndFigures.supply.totalBurned"));
        seriesIndexMap.put(getSeriesId(seriesTotalBurned), 1);

        seriesCompensation = new XYChart.Series<>();
        seriesCompensation.setName(Res.get("dao.factsAndFigures.supply.compReq"));
        seriesIndexMap.put(getSeriesId(seriesCompensation), 2);

        seriesReimbursement = new XYChart.Series<>();
        seriesReimbursement.setName(Res.get("dao.factsAndFigures.supply.reimbursement"));
        seriesIndexMap.put(getSeriesId(seriesReimbursement), 3);

        seriesBsqTradeFee = new XYChart.Series<>();
        seriesBsqTradeFee.setName(Res.get("dao.factsAndFigures.supply.bsqTradeFee"));
        seriesIndexMap.put(getSeriesId(seriesBsqTradeFee), 4);

        seriesProofOfBurn = new XYChart.Series<>();
        seriesProofOfBurn.setName(Res.get("dao.factsAndFigures.supply.proofOfBurn"));
        seriesIndexMap.put(getSeriesId(seriesProofOfBurn), 5);
    }

    @Override
    protected void defineAndAddActiveSeries() {
        activateSeries(seriesTotalIssued);
        activateSeries(seriesTotalBurned);
    }

    @Override
    protected void activateSeries(XYChart.Series<Number, Number> series) {
        super.activateSeries(series);

        if (getSeriesId(series).equals(getSeriesId(seriesBsqTradeFee))) {
            seriesBsqTradeFee.getData().setAll(model.getBsqTradeFeeChartData());
        } else if (getSeriesId(series).equals(getSeriesId(seriesCompensation))) {
            seriesCompensation.getData().setAll(model.getCompensationChartData());
        } else if (getSeriesId(series).equals(getSeriesId(seriesProofOfBurn))) {
            seriesProofOfBurn.getData().setAll(model.getProofOfBurnChartData());
        } else if (getSeriesId(series).equals(getSeriesId(seriesReimbursement))) {
            seriesReimbursement.getData().setAll(model.getReimbursementChartData());
        } else if (getSeriesId(series).equals(getSeriesId(seriesTotalIssued))) {
            seriesTotalIssued.getData().setAll(model.getTotalIssuedChartData());
        } else if (getSeriesId(series).equals(getSeriesId(seriesTotalBurned))) {
            seriesTotalBurned.getData().setAll(model.getTotalBurnedChartData());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Data
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void applyData() {
        if (activeSeries.contains(seriesTotalIssued)) {
            seriesTotalIssued.getData().setAll(model.getTotalIssuedChartData());
        }
        if (activeSeries.contains(seriesTotalBurned)) {
            seriesTotalBurned.getData().setAll(model.getTotalBurnedChartData());
        }
        if (activeSeries.contains(seriesCompensation)) {
            seriesCompensation.getData().setAll(model.getCompensationChartData());
        }
        if (activeSeries.contains(seriesReimbursement)) {
            seriesReimbursement.getData().setAll(model.getReimbursementChartData());
        }
        if (activeSeries.contains(seriesBsqTradeFee)) {
            seriesBsqTradeFee.getData().setAll(model.getBsqTradeFeeChartData());
        }
        if (activeSeries.contains(seriesProofOfBurn)) {
            seriesProofOfBurn.getData().setAll(model.getProofOfBurnChartData());
        }
    }
}
