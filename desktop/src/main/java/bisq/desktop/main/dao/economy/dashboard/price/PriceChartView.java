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

import bisq.desktop.components.chart.ChartView;

import bisq.core.locale.Res;

import bisq.common.util.CompletableFutureUtils;

import javax.inject.Inject;

import javafx.scene.chart.XYChart;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PriceChartView extends ChartView<PriceChartViewModel> {
    private XYChart.Series<Number, Number> seriesBsqUsdPrice, seriesBsqBtcPrice, seriesBtcUsdPrice,
            seriesBsqUsdMarketCap, seriesBsqBtcMarketCap;
    private final DoubleProperty averageBsqUsdPriceProperty = new SimpleDoubleProperty();
    private final DoubleProperty averageBsqBtcPriceProperty = new SimpleDoubleProperty();

    @Inject
    public PriceChartView(PriceChartViewModel model) {
        super(model);

        setRadioButtonBehaviour(true);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ReadOnlyDoubleProperty averageBsqUsdPriceProperty() {
        return averageBsqUsdPriceProperty;
    }

    public ReadOnlyDoubleProperty averageBsqBtcPriceProperty() {
        return averageBsqBtcPriceProperty;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Chart
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onSetYAxisFormatter(XYChart.Series<Number, Number> series) {
        if (series == seriesBsqUsdPrice) {
            model.setBsqUsdPriceFormatter();
        } else if (series == seriesBsqBtcPrice) {
            model.setBsqBtcPriceFormatter();
        } else if (series == seriesBtcUsdPrice){
            model.setBtcUsdPriceFormatter();
        } else if (series == seriesBsqUsdMarketCap) {
            model.setBsqUsdMarketCapPriceFormatter();
        } else {
            model.setBsqBtcMarketCapPriceFormatter();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Legend
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected Collection<XYChart.Series<Number, Number>> getSeriesForLegend1() {
        return List.of(seriesBsqUsdPrice, seriesBsqBtcPrice, seriesBtcUsdPrice, seriesBsqUsdMarketCap, seriesBsqBtcMarketCap);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Timeline navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void initBoundsForTimelineNavigation() {
        setBoundsForTimelineNavigation(seriesBsqUsdPrice.getData());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Series
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void createSeries() {
        int index = -1;
        seriesBsqUsdPrice = new XYChart.Series<>();
        seriesBsqUsdPrice.setName(Res.get("dao.factsAndFigures.supply.bsqUsdPrice"));
        seriesIndexMap.put(getSeriesId(seriesBsqUsdPrice), ++index);

        seriesBsqBtcPrice = new XYChart.Series<>();
        seriesBsqBtcPrice.setName(Res.get("dao.factsAndFigures.supply.bsqBtcPrice"));
        seriesIndexMap.put(getSeriesId(seriesBsqBtcPrice), ++index);

        seriesBtcUsdPrice = new XYChart.Series<>();
        seriesBtcUsdPrice.setName(Res.get("dao.factsAndFigures.supply.btcUsdPrice"));
        seriesIndexMap.put(getSeriesId(seriesBtcUsdPrice), ++index);

        seriesBsqUsdMarketCap = new XYChart.Series<>();
        seriesBsqUsdMarketCap.setName(Res.get("dao.factsAndFigures.supply.bsqUsdMarketCap"));
        seriesIndexMap.put(getSeriesId(seriesBsqUsdMarketCap), ++index);

        seriesBsqBtcMarketCap = new XYChart.Series<>();
        seriesBsqBtcMarketCap.setName(Res.get("dao.factsAndFigures.supply.bsqBtcMarketCap"));
        seriesIndexMap.put(getSeriesId(seriesBsqBtcMarketCap), ++index);
    }

    @Override
    protected void defineAndAddActiveSeries() {
        activateSeries(seriesBsqUsdPrice);
        onSetYAxisFormatter(seriesBsqUsdPrice);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Data
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected CompletableFuture<Boolean> applyData() {
        List<CompletableFuture<Boolean>> allFutures = new ArrayList<>();

        if (activeSeries.contains(seriesBsqUsdPrice)) {
            CompletableFuture<Boolean> task1Done = new CompletableFuture<>();
            allFutures.add(task1Done);
            applyBsqUsdPriceChartDataAsync(task1Done);
        }
        if (activeSeries.contains(seriesBsqBtcPrice)) {
            CompletableFuture<Boolean> task2Done = new CompletableFuture<>();
            allFutures.add(task2Done);
            applyBsqBtcPriceChartData(task2Done);
        }
        if (activeSeries.contains(seriesBtcUsdPrice)) {
            CompletableFuture<Boolean> task3Done = new CompletableFuture<>();
            allFutures.add(task3Done);
            applyBtcUsdPriceChartData(task3Done);
        }
        if (activeSeries.contains(seriesBsqUsdMarketCap)) {
            CompletableFuture<Boolean> taskDone = new CompletableFuture<>();
            allFutures.add(taskDone);
            applyBsqUsdMarketCapChartData(taskDone);
        }
        if (activeSeries.contains(seriesBsqBtcMarketCap)) {
            CompletableFuture<Boolean> taskDone = new CompletableFuture<>();
            allFutures.add(taskDone);
            applyBsqBtcMarketCapChartData(taskDone);
        }

        CompletableFuture<Boolean> task4Done = new CompletableFuture<>();
        allFutures.add(task4Done);
        model.averageBsqBtcPrice()
                .whenComplete((data, t) ->
                        mapToUserThread(() -> {
                            averageBsqBtcPriceProperty.set(data);
                            task4Done.complete(true);
                        }));

        CompletableFuture<Boolean> task5Done = new CompletableFuture<>();
        allFutures.add(task5Done);
        model.averageBsqUsdPrice()
                .whenComplete((data, t) ->
                        mapToUserThread(() -> {
                            averageBsqUsdPriceProperty.set(data);
                            task5Done.complete(true);
                        }));

        return CompletableFutureUtils.allOf(allFutures).thenApply(e -> {
            return true;
        });
    }

    private void applyBsqUsdPriceChartDataAsync(CompletableFuture<Boolean> completeFuture) {
        model.getBsqUsdPriceChartData()
                .whenComplete((data, t) ->
                        mapToUserThread(() -> {
                            ObservableList<XYChart.Data<Number, Number>> data1 = seriesBsqUsdPrice.getData();
                            data1.setAll(data);
                            completeFuture.complete(true);
                        })
                );
    }

    private void applyBtcUsdPriceChartData(CompletableFuture<Boolean> completeFuture) {
        model.getBtcUsdPriceChartData()
                .whenComplete((data, t) ->
                        mapToUserThread(() -> {
                            seriesBtcUsdPrice.getData().setAll(data);
                            completeFuture.complete(true);
                        }));
    }

    private void applyBsqBtcPriceChartData(CompletableFuture<Boolean> completeFuture) {
        model.getBsqBtcPriceChartData()
                .whenComplete((data, t) ->
                        mapToUserThread(() -> {
                            seriesBsqBtcPrice.getData().setAll(data);
                            completeFuture.complete(true);
                        }));
    }

    private void applyBsqUsdMarketCapChartData(CompletableFuture<Boolean> completeFuture) {
        model.getBsqUsdMarketCapChartData()
                .whenComplete((data, t) ->
                        mapToUserThread(() -> {
                            seriesBsqUsdMarketCap.getData().setAll(data);
                            completeFuture.complete(true);
                        }));
    }

    private void applyBsqBtcMarketCapChartData(CompletableFuture<Boolean> completeFuture) {
        model.getBsqBtcMarketCapChartData()
                .whenComplete((data, t) ->
                        mapToUserThread(() -> {
                            seriesBsqBtcMarketCap.getData().setAll(data);
                            completeFuture.complete(true);
                        }));
    }
}
