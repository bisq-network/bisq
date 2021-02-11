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

import javax.inject.Inject;

import javafx.scene.chart.XYChart;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

import java.util.Collection;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PriceChartView extends ChartView<PriceChartViewModel> {
    private XYChart.Series<Number, Number> seriesBsqUsdPrice, seriesBsqBtcPrice, seriesBtcUsdPrice;
    private DoubleProperty averageBsqUsdPriceProperty = new SimpleDoubleProperty();
    private DoubleProperty averageBsqBtcPriceProperty = new SimpleDoubleProperty();

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
        } else {
            model.setBtcUsdPriceFormatter();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Legend
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected Collection<XYChart.Series<Number, Number>> getSeriesForLegend1() {
        return List.of(seriesBsqUsdPrice, seriesBsqBtcPrice, seriesBtcUsdPrice);
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
        seriesBsqUsdPrice = new XYChart.Series<>();
        seriesBsqUsdPrice.setName(Res.get("dao.factsAndFigures.supply.bsqUsdPrice"));
        seriesIndexMap.put(getSeriesId(seriesBsqUsdPrice), 0);

        seriesBsqBtcPrice = new XYChart.Series<>();
        seriesBsqBtcPrice.setName(Res.get("dao.factsAndFigures.supply.bsqBtcPrice"));
        seriesIndexMap.put(getSeriesId(seriesBsqBtcPrice), 1);

        seriesBtcUsdPrice = new XYChart.Series<>();
        seriesBtcUsdPrice.setName(Res.get("dao.factsAndFigures.supply.btcUsdPrice"));
        seriesIndexMap.put(getSeriesId(seriesBtcUsdPrice), 2);
    }

    @Override
    protected void defineAndAddActiveSeries() {
        activateSeries(seriesBsqUsdPrice);
        onSetYAxisFormatter(seriesBsqUsdPrice);
    }

    @Override
    protected void activateSeries(XYChart.Series<Number, Number> series) {
        super.activateSeries(series);

        String seriesId = getSeriesId(series);
        if (seriesId.equals(getSeriesId(seriesBsqUsdPrice))) {
            seriesBsqUsdPrice.getData().setAll(model.getBsqUsdPriceChartData());
        } else if (seriesId.equals(getSeriesId(seriesBsqBtcPrice))) {
            seriesBsqBtcPrice.getData().setAll(model.getBsqBtcPriceChartData());
        } else if (seriesId.equals(getSeriesId(seriesBtcUsdPrice))) {
            seriesBtcUsdPrice.getData().setAll(model.getBtcUsdPriceChartData());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Data
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void applyData() {
        if (activeSeries.contains(seriesBsqUsdPrice)) {
            seriesBsqUsdPrice.getData().setAll(model.getBsqUsdPriceChartData());
        }
        if (activeSeries.contains(seriesBsqBtcPrice)) {
            seriesBsqBtcPrice.getData().setAll(model.getBsqBtcPriceChartData());
        }
        if (activeSeries.contains(seriesBtcUsdPrice)) {
            seriesBtcUsdPrice.getData().setAll(model.getBtcUsdPriceChartData());
        }

        averageBsqBtcPriceProperty.set(model.averageBsqBtcPrice());
        averageBsqUsdPriceProperty.set(model.averageBsqUsdPrice());
    }
}
