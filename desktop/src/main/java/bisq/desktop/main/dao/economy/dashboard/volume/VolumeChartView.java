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

import javax.inject.Inject;

import javafx.scene.chart.XYChart;

import javafx.beans.property.LongProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.SimpleLongProperty;

import java.util.Collection;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VolumeChartView extends ChartView<VolumeChartViewModel> {
    private XYChart.Series<Number, Number> seriesUsdVolume, seriesBtcVolume;

    private LongProperty usdVolumeProperty = new SimpleLongProperty();
    private LongProperty btcVolumeProperty = new SimpleLongProperty();

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

    @Override
    protected void activateSeries(XYChart.Series<Number, Number> series) {
        super.activateSeries(series);

        if (getSeriesId(series).equals(getSeriesId(seriesUsdVolume))) {
            seriesUsdVolume.getData().setAll(model.getUsdVolumeChartData());
        } else if (getSeriesId(series).equals(getSeriesId(seriesBtcVolume))) {
            seriesBtcVolume.getData().setAll(model.getBtcVolumeChartData());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Data
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void applyData() {
        if (activeSeries.contains(seriesUsdVolume)) {
            seriesUsdVolume.getData().setAll(model.getUsdVolumeChartData());
        }
        if (activeSeries.contains(seriesBtcVolume)) {
            seriesBtcVolume.getData().setAll(model.getBtcVolumeChartData());
        }

        usdVolumeProperty.set(model.getUsdVolume());
        btcVolumeProperty.set(model.getBtcVolume());
    }
}
