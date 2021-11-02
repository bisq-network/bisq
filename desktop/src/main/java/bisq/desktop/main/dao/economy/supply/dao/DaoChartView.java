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

import javafx.beans.property.LongProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.SimpleLongProperty;

import java.util.Collection;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DaoChartView extends ChartView<DaoChartViewModel> {
    private final LongProperty compensationAmountProperty = new SimpleLongProperty();
    private final LongProperty reimbursementAmountProperty = new SimpleLongProperty();
    private final LongProperty bsqTradeFeeAmountProperty = new SimpleLongProperty();
    private final LongProperty proofOfBurnAmountProperty = new SimpleLongProperty();

    private XYChart.Series<Number, Number> seriesBsqTradeFee, seriesProofOfBurn, seriesCompensation,
            seriesReimbursement, seriesTotalIssued, seriesTotalBurned;


    @Inject
    public DaoChartView(DaoChartViewModel model) {
        super(model);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API Total amounts
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ReadOnlyLongProperty compensationAmountProperty() {
        return compensationAmountProperty;
    }

    public ReadOnlyLongProperty reimbursementAmountProperty() {
        return reimbursementAmountProperty;
    }

    public ReadOnlyLongProperty bsqTradeFeeAmountProperty() {
        return bsqTradeFeeAmountProperty;
    }

    public ReadOnlyLongProperty proofOfBurnAmountProperty() {
        return proofOfBurnAmountProperty;
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

        if (getSeriesId(series).equals(getSeriesId(seriesTotalIssued))) {
            applyTotalIssued();
        } else if (getSeriesId(series).equals(getSeriesId(seriesCompensation))) {
            applyCompensation();
        } else if (getSeriesId(series).equals(getSeriesId(seriesReimbursement))) {
            applyReimbursement();
        } else if (getSeriesId(series).equals(getSeriesId(seriesTotalBurned))) {
            applyTotalBurned();
        } else if (getSeriesId(series).equals(getSeriesId(seriesBsqTradeFee))) {
            applyBsqTradeFee();
        } else if (getSeriesId(series).equals(getSeriesId(seriesProofOfBurn))) {
            applyProofOfBurn();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Data
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void applyData() {
        if (activeSeries.contains(seriesTotalIssued)) {
            applyTotalIssued();
        }
        if (activeSeries.contains(seriesCompensation)) {
            applyCompensation();
        }
        if (activeSeries.contains(seriesReimbursement)) {
            applyReimbursement();
        }
        if (activeSeries.contains(seriesTotalBurned)) {
            applyTotalBurned();
        }
        if (activeSeries.contains(seriesBsqTradeFee)) {
            applyBsqTradeFee();
        }
        if (activeSeries.contains(seriesProofOfBurn)) {
            applyProofOfBurn();
        }

        model.getCompensationAmount()
                .whenComplete((data, t) ->
                        mapToUserThread(() ->
                                compensationAmountProperty.set(data)));
        model.getReimbursementAmount()
                .whenComplete((data, t) ->
                        mapToUserThread(() ->
                                reimbursementAmountProperty.set(data)));
        model.getBsqTradeFeeAmount()
                .whenComplete((data, t) ->
                        mapToUserThread(() ->
                                bsqTradeFeeAmountProperty.set(data)));
        model.getProofOfBurnAmount()
                .whenComplete((data, t) ->
                        mapToUserThread(() ->
                                proofOfBurnAmountProperty.set(data)));
    }

    private void applyTotalIssued() {
        model.getTotalIssuedChartData()
                .whenComplete((data, t) ->
                        mapToUserThread(() ->
                                seriesTotalIssued.getData().setAll(data)));
    }

    private void applyCompensation() {
        model.getCompensationChartData()
                .whenComplete((data, t) ->
                        mapToUserThread(() ->
                                seriesCompensation.getData().setAll(data)));
    }

    private void applyReimbursement() {
        model.getReimbursementChartData()
                .whenComplete((data, t) ->
                        mapToUserThread(() ->
                                seriesReimbursement.getData().setAll(data)));
    }

    private void applyTotalBurned() {
        model.getTotalBurnedChartData()
                .whenComplete((data, t) ->
                        mapToUserThread(() ->
                                seriesTotalBurned.getData().setAll(data)));
    }

    private void applyBsqTradeFee() {
        model.getBsqTradeFeeChartData()
                .whenComplete((data, t) ->
                        mapToUserThread(() ->
                                seriesBsqTradeFee.getData().setAll(data)));
    }

    private void applyProofOfBurn() {
        model.getProofOfBurnChartData()
                .whenComplete((data, t) ->
                        mapToUserThread(() ->
                                seriesProofOfBurn.getData().setAll(data)));
    }
}
