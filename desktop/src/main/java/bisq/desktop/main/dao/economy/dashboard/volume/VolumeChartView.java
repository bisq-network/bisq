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
public class VolumeChartView extends ChartView<VolumeChartViewModel> {
    private XYChart.Series<Number, Number> seriesUsdVolume, seriesBtcVolume;

    private final LongProperty usdVolumeProperty = new SimpleLongProperty();
    private final LongProperty btcVolumeProperty = new SimpleLongProperty();

    @Inject
    public VolumeChartView(VolumeChartViewModel model) {
        super(model);

        setRadioButtonBehaviour(true);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ReadOnlyLongProperty usdVolumeProperty() {
        return usdVolumeProperty;
    }

    public ReadOnlyLongProperty btcVolumeProperty() {
        return btcVolumeProperty;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Chart
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onSetYAxisFormatter(XYChart.Series<Number, Number> series) {
        if (series == seriesUsdVolume) {
            model.setUsdVolumeFormatter();
        } else {
            model.setBtcVolumeFormatter();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Legend
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Override
    protected Collection<XYChart.Series<Number, Number>> getSeriesForLegend1() {
        return List.of(seriesUsdVolume, seriesBtcVolume);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Timeline navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void initBoundsForTimelineNavigation() {
        setBoundsForTimelineNavigation(seriesUsdVolume.getData());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Series
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void createSeries() {
        seriesUsdVolume = new XYChart.Series<>();
        seriesUsdVolume.setName(Res.get("dao.factsAndFigures.supply.tradeVolumeInUsd"));
        seriesIndexMap.put(getSeriesId(seriesUsdVolume), 0);

        seriesBtcVolume = new XYChart.Series<>();
        seriesBtcVolume.setName(Res.get("dao.factsAndFigures.supply.tradeVolumeInBtc"));
        seriesIndexMap.put(getSeriesId(seriesBtcVolume), 1);
    }

    @Override
    protected void defineAndAddActiveSeries() {
        activateSeries(seriesUsdVolume);
        onSetYAxisFormatter(seriesUsdVolume);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Data
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected CompletableFuture<Boolean> applyData() {
        List<CompletableFuture<Boolean>> allFutures = new ArrayList<>();
        if (activeSeries.contains(seriesUsdVolume)) {
            CompletableFuture<Boolean> task1Done = new CompletableFuture<>();
            allFutures.add(task1Done);
            applyUsdVolumeChartData(task1Done);
        }
        if (activeSeries.contains(seriesBtcVolume)) {
            CompletableFuture<Boolean> task2Done = new CompletableFuture<>();
            allFutures.add(task2Done);
            applyBtcVolumeChartData(task2Done);
        }

        CompletableFuture<Boolean> task3Done = new CompletableFuture<>();
        allFutures.add(task3Done);
        model.getUsdVolume()
                .whenComplete((data, t) ->
                        mapToUserThread(() -> {
                            usdVolumeProperty.set(data);
                            task3Done.complete(true);
                        }));

        CompletableFuture<Boolean> task4Done = new CompletableFuture<>();
        allFutures.add(task4Done);
        model.getBtcVolume()
                .whenComplete((data, t) ->
                        mapToUserThread(() -> {
                            btcVolumeProperty.set(data);
                            task4Done.complete(true);
                        }));

        return CompletableFutureUtils.allOf(allFutures).thenApply(e -> true);
    }

    private void applyBtcVolumeChartData(CompletableFuture<Boolean> completeFuture) {
        model.getBtcVolumeChartData()
                .whenComplete((data, t) ->
                        mapToUserThread(() -> {
                            seriesBtcVolume.getData().setAll(data);
                            completeFuture.complete(true);
                        }));
    }

    private void applyUsdVolumeChartData(CompletableFuture<Boolean> completeFuture) {
        model.getUsdVolumeChartData()
                .whenComplete((data, t) ->
                        mapToUserThread(() -> {
                            seriesUsdVolume.getData().setAll(data);
                            completeFuture.complete(true);
                        }));
    }
}
