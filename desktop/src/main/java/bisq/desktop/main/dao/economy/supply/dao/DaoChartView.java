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

import bisq.common.util.CompletableFutureUtils;

import javax.inject.Inject;

import javafx.scene.chart.XYChart;

import javafx.beans.property.LongProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.SimpleLongProperty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DaoChartView extends ChartView<DaoChartViewModel> {
    private final LongProperty compensationAmountProperty = new SimpleLongProperty();
    private final LongProperty reimbursementAmountProperty = new SimpleLongProperty();
    private final LongProperty bsqTradeFeeAmountProperty = new SimpleLongProperty();
    private final LongProperty proofOfBurnAmountProperty = new SimpleLongProperty();

    private XYChart.Series<Number, Number> seriesBsqTradeFee, seriesProofOfBurn, seriesCompensation,
            seriesReimbursement, seriesTotalSupply, seriesTotalIssued, seriesTotalBurned,
            seriesTotalTradeFees, seriesProofOfBurnFromBtcFees,
            seriesProofOfBurnFromArbitration, seriesProofOfBurnReimbursementDiff;


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
        return List.of(seriesTotalBurned, seriesBsqTradeFee, seriesProofOfBurnFromBtcFees, seriesProofOfBurnFromArbitration, seriesProofOfBurn);
    }

    @Override
    protected Collection<XYChart.Series<Number, Number>> getSeriesForLegend3() {
        return List.of(seriesTotalTradeFees, seriesTotalSupply, seriesProofOfBurnReimbursementDiff);
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

        seriesTotalSupply = new XYChart.Series<>();
        seriesTotalSupply.setName(Res.get("dao.factsAndFigures.supply.totalBsqSupply"));
        seriesIndexMap.put(getSeriesId(seriesTotalSupply), 6);

        seriesTotalTradeFees = new XYChart.Series<>();
        seriesTotalTradeFees.setName(Res.get("dao.factsAndFigures.supply.totalTradeFees"));
        seriesIndexMap.put(getSeriesId(seriesTotalTradeFees), 7);

        seriesProofOfBurnFromBtcFees = new XYChart.Series<>();
        seriesProofOfBurnFromBtcFees.setName(Res.get("dao.factsAndFigures.supply.btcFees"));
        seriesIndexMap.put(getSeriesId(seriesProofOfBurnFromBtcFees), 8);

        seriesProofOfBurnFromArbitration = new XYChart.Series<>();
        seriesProofOfBurnFromArbitration.setName(Res.get("dao.factsAndFigures.supply.arbitration"));
        seriesIndexMap.put(getSeriesId(seriesProofOfBurnFromArbitration), 9);

        seriesProofOfBurnReimbursementDiff = new XYChart.Series<>();
        seriesProofOfBurnReimbursementDiff.setName(Res.get("dao.factsAndFigures.supply.proofOfBurnReimbursementDiff"));
        seriesIndexMap.put(getSeriesId(seriesProofOfBurnReimbursementDiff), 10);
    }

    @Override
    protected void defineAndAddActiveSeries() {
        activateSeries(seriesTotalIssued);
        activateSeries(seriesTotalBurned);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Data
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected CompletableFuture<Boolean> applyData() {
        List<CompletableFuture<Boolean>> allFutures = new ArrayList<>();
        if (activeSeries.contains(seriesTotalIssued)) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            allFutures.add(future);
            applyTotalIssued(future);
        }
        if (activeSeries.contains(seriesCompensation)) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            allFutures.add(future);
            applyCompensation(future);
        }
        if (activeSeries.contains(seriesReimbursement)) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            allFutures.add(future);
            applyReimbursement(future);
        }
        if (activeSeries.contains(seriesTotalBurned)) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            allFutures.add(future);
            applyTotalBurned(future);
        }
        if (activeSeries.contains(seriesBsqTradeFee)) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            allFutures.add(future);
            applyBsqTradeFee(future);
        }
        if (activeSeries.contains(seriesProofOfBurn)) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            allFutures.add(future);
            applyProofOfBurn(future);
        }
        if (activeSeries.contains(seriesTotalSupply)) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            allFutures.add(future);
            applyTotalSupply(future);
        }
        if (activeSeries.contains(seriesTotalTradeFees)) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            allFutures.add(future);
            applyTotalTradeFees(future);
        }
        if (activeSeries.contains(seriesProofOfBurnFromBtcFees)) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            allFutures.add(future);
            applyProofOfBurnFromBtcFees(future);
        }
        if (activeSeries.contains(seriesProofOfBurnFromArbitration)) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            allFutures.add(future);
            applyProofOfBurnFromArbitration(future);
        }
        if (activeSeries.contains(seriesProofOfBurnReimbursementDiff)) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            allFutures.add(future);
            applyProofOfBurnReimbursementDiff(future);
        }

        CompletableFuture<Boolean> task7Done = new CompletableFuture<>();
        allFutures.add(task7Done);
        model.getCompensationAmount()
                .whenComplete((data, t) ->
                        mapToUserThread(() -> {
                            compensationAmountProperty.set(data);
                            task7Done.complete(true);
                        }));

        CompletableFuture<Boolean> task8Done = new CompletableFuture<>();
        allFutures.add(task8Done);
        model.getReimbursementAmount()
                .whenComplete((data, t) ->
                        mapToUserThread(() -> {
                            reimbursementAmountProperty.set(data);
                            task8Done.complete(true);
                        }));

        CompletableFuture<Boolean> task9Done = new CompletableFuture<>();
        allFutures.add(task9Done);
        model.getBsqTradeFeeAmount()
                .whenComplete((data, t) ->
                        mapToUserThread(() -> {
                            bsqTradeFeeAmountProperty.set(data);
                            task9Done.complete(true);
                        }));

        CompletableFuture<Boolean> task10Done = new CompletableFuture<>();
        allFutures.add(task10Done);
        model.getProofOfBurnAmount()
                .whenComplete((data, t) ->
                        mapToUserThread(() -> {
                            proofOfBurnAmountProperty.set(data);
                            task10Done.complete(true);
                        }));

        return CompletableFutureUtils.allOf(allFutures).thenApply(e -> true);
    }

    private void applyTotalSupply(CompletableFuture<Boolean> completeFuture) {
        model.getTotalSupplyChartData()
                .whenComplete((data, t) ->
                        mapToUserThread(() -> {
                            seriesTotalSupply.getData().setAll(data);
                            completeFuture.complete(true);
                        }));
    }

    private void applyTotalTradeFees(CompletableFuture<Boolean> completeFuture) {
        model.getTotalTradeFeesChartData()
                .whenComplete((data, t) ->
                        mapToUserThread(() -> {
                            seriesTotalTradeFees.getData().setAll(data);
                            completeFuture.complete(true);
                        }));
    }

    private void applyProofOfBurnFromBtcFees(CompletableFuture<Boolean> completeFuture) {
        model.getProofOfBurnFromBtcFeesChartData()
                .whenComplete((data, t) ->
                        mapToUserThread(() -> {
                            seriesProofOfBurnFromBtcFees.getData().setAll(data);
                            completeFuture.complete(true);
                        }));
    }

    private void applyProofOfBurnFromArbitration(CompletableFuture<Boolean> completeFuture) {
        model.getProofOfBurnFromArbitrationChartData()
                .whenComplete((data, t) ->
                        mapToUserThread(() -> {
                            seriesProofOfBurnFromArbitration.getData().setAll(data);
                            completeFuture.complete(true);
                        }));
    }

    private void applyProofOfBurnReimbursementDiff(CompletableFuture<Boolean> completeFuture) {
        model.getProofOfBurnReimbursementDiffChartData()
                .whenComplete((data, t) ->
                        mapToUserThread(() -> {
                            seriesProofOfBurnReimbursementDiff.getData().setAll(data);
                            completeFuture.complete(true);
                        }));
    }

    private void applyTotalIssued(CompletableFuture<Boolean> completeFuture) {
        model.getTotalIssuedChartData()
                .whenComplete((data, t) ->
                        mapToUserThread(() -> {
                            seriesTotalIssued.getData().setAll(data);
                            completeFuture.complete(true);
                        }));
    }

    private void applyCompensation(CompletableFuture<Boolean> completeFuture) {
        model.getCompensationChartData()
                .whenComplete((data, t) ->
                        mapToUserThread(() -> {
                            seriesCompensation.getData().setAll(data);
                            completeFuture.complete(true);
                        }));
    }

    private void applyReimbursement(CompletableFuture<Boolean> completeFuture) {
        model.getReimbursementChartData()
                .whenComplete((data, t) ->
                        mapToUserThread(() -> {
                            seriesReimbursement.getData().setAll(data);
                            completeFuture.complete(true);
                        }));
    }

    private void applyTotalBurned(CompletableFuture<Boolean> completeFuture) {
        model.getTotalBurnedChartData()
                .whenComplete((data, t) ->
                        mapToUserThread(() -> {
                            seriesTotalBurned.getData().setAll(data);
                            completeFuture.complete(true);
                        }));
    }

    private void applyBsqTradeFee(CompletableFuture<Boolean> completeFuture) {
        model.getBsqTradeFeeChartData()
                .whenComplete((data, t) ->
                        mapToUserThread(() -> {
                            seriesBsqTradeFee.getData().setAll(data);
                            completeFuture.complete(true);
                        }));
    }

    private void applyProofOfBurn(CompletableFuture<Boolean> completeFuture) {
        model.getProofOfBurnChartData()
                .whenComplete((data, t) ->
                        mapToUserThread(() -> {
                            seriesProofOfBurn.getData().setAll(data);
                            completeFuture.complete(true);
                        }));
    }
}
