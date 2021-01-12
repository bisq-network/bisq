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

/*
 * Copyright (c) 2008, 2014, Oracle and/or its affiliates.
 * All rights reserved. Use is subject to license terms.
 *
 * This file is available and licensed under the following license:
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  - Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the distribution.
 *  - Neither the name of Oracle Corporation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package bisq.desktop.main.market.trades.charts.price;

import bisq.desktop.main.market.trades.charts.CandleData;

import javafx.animation.FadeTransition;

import javafx.scene.Node;
import javafx.scene.chart.Axis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;

import javafx.event.ActionEvent;

import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A candlestick chart is a style of bar-chart used primarily to describe price movements of a security, derivative,
 * or currency over time.
 * <p/>
 * The Data Y value is used for the opening price and then the close, high and low values are stored in the Data's
 * extra value property using a CandleStickExtraValues object.
 */
public class CandleStickChart extends XYChart<Number, Number> {

    private final StringConverter<Number> priceStringConverter;

    // -------------- CONSTRUCTORS ----------------------------------------------

    /**
     * Construct a new CandleStickChart with the given axis.
     *
     * @param xAxis The x axis to use
     * @param yAxis The y axis to use
     */
    public CandleStickChart(Axis<Number> xAxis, Axis<Number> yAxis, StringConverter<Number> priceStringConverter) {
        super(xAxis, yAxis);
        this.priceStringConverter = priceStringConverter;
    }

    // -------------- METHODS ------------------------------------------------------------------------------------------

    /**
     * Called to update and layout the content for the plot
     */
    @Override
    protected void layoutPlotChildren() {
        // we have nothing to layout if no data is present
        if (getData() == null) {
            return;
        }
        // update candle positions
        for (int seriesIndex = 0; seriesIndex < getData().size(); seriesIndex++) {
            XYChart.Series<Number, Number> series = getData().get(seriesIndex);
            Iterator<XYChart.Data<Number, Number>> iterator = getDisplayedDataIterator(series);
            Path seriesPath = null;
            if (series.getNode() instanceof Path) {
                seriesPath = (Path) series.getNode();
                seriesPath.getElements().clear();
            }
            while (iterator.hasNext()) {
                XYChart.Data<Number, Number> item = iterator.next();
                double x = getXAxis().getDisplayPosition(getCurrentDisplayedXValue(item));
                double y = getYAxis().getDisplayPosition(getCurrentDisplayedYValue(item));
                Node itemNode = item.getNode();
                CandleData candleData = (CandleData) item.getExtraValue();
                if (itemNode instanceof Candle && candleData != null) {
                    Candle candle = (Candle) itemNode;

                    double close = getYAxis().getDisplayPosition(candleData.close);
                    double high = getYAxis().getDisplayPosition(candleData.high);
                    double low = getYAxis().getDisplayPosition(candleData.low);
                    // calculate candle width
                    double candleWidth = -1;
                    if (getXAxis() instanceof NumberAxis) {
                        NumberAxis xa = (NumberAxis) getXAxis();
                        candleWidth = xa.getDisplayPosition(1) * 0.60; // use 60% width between units
                    }
                    // update candle
                    candle.update(close - y, high - y, low - y, candleWidth);
                    candle.updateTooltip(candleData);

                    // position the candle
                    candle.setLayoutX(x);
                    candle.setLayoutY(y);
                }
                if (seriesPath != null && candleData != null) {
                    final double displayPosition = getYAxis().getDisplayPosition(candleData.average);
                    if (seriesPath.getElements().isEmpty())
                        seriesPath.getElements().add(new MoveTo(x, displayPosition));
                    else
                        seriesPath.getElements().add(new LineTo(x, displayPosition));
                }
            }
        }
    }

    @Override
    protected void dataItemChanged(XYChart.Data<Number, Number> item) {
    }

    @Override
    protected void dataItemAdded(XYChart.Series<Number, Number> series, int itemIndex, XYChart.Data<Number, Number> item) {
        Node candle = createCandle(getData().indexOf(series), item, itemIndex);
        getPlotChildren().remove(candle);

        if (shouldAnimate()) {
            candle.setOpacity(0);
            getPlotChildren().add(candle);
            // fade in new candle
            FadeTransition ft = new FadeTransition(Duration.millis(500), candle);
            ft.setToValue(1);
            ft.play();
        } else {
            getPlotChildren().add(candle);
        }
        // always draw average line on top

        if (series.getNode() instanceof Path) {
            Path seriesPath = (Path) series.getNode();
            seriesPath.toFront();
        }
    }

    @Override
    protected void dataItemRemoved(XYChart.Data<Number, Number> item, XYChart.Series<Number, Number> series) {
        if (series.getNode() instanceof Path) {
            Path seriesPath = (Path) series.getNode();
            seriesPath.getElements().clear();
        }

        final Node node = item.getNode();
        if (shouldAnimate()) {
            // fade out old candle
            FadeTransition ft = new FadeTransition(Duration.millis(500), node);
            ft.setToValue(0);
            ft.setOnFinished((ActionEvent actionEvent) -> {
                getPlotChildren().remove(node);
                removeDataItemFromDisplay(series, item);
            });
            ft.play();
        } else {
            getPlotChildren().remove(node);
            removeDataItemFromDisplay(series, item);
        }
    }

    @Override
    protected void seriesAdded(XYChart.Series<Number, Number> series, int seriesIndex) {
        // handle any data already in series
        for (int j = 0; j < series.getData().size(); j++) {
            XYChart.Data<Number, Number> item = series.getData().get(j);
            Node candle = createCandle(seriesIndex, item, j);

            if (!getPlotChildren().contains(candle)) {
                getPlotChildren().add(candle);
                if (shouldAnimate()) {
                    candle.setOpacity(0);
                    FadeTransition ft = new FadeTransition(Duration.millis(500), candle);
                    ft.setToValue(1);
                    ft.play();
                }
            }
        }
        Path seriesPath = new Path();
        seriesPath.getStyleClass().setAll("candlestick-average-line", "series" + seriesIndex);
        series.setNode(seriesPath);

        if (!getPlotChildren().contains(seriesPath)) {
            getPlotChildren().add(seriesPath);
            if (shouldAnimate()) {
                seriesPath.setOpacity(0);
                FadeTransition ft = new FadeTransition(Duration.millis(500), seriesPath);
                ft.setToValue(1);
                ft.play();
            }
        }
    }

    @Override
    protected void seriesRemoved(XYChart.Series<Number, Number> series) {
        // remove all candle nodes
        for (XYChart.Data<Number, Number> d : series.getData()) {
            final Node candle = d.getNode();
            if (shouldAnimate()) {
                FadeTransition ft = new FadeTransition(Duration.millis(500), candle);
                ft.setToValue(0);
                ft.setOnFinished((ActionEvent actionEvent) -> getPlotChildren().remove(candle));
                ft.play();
            } else {
                getPlotChildren().remove(candle);
            }
        }
        if (series.getNode() instanceof Path) {
            Path seriesPath = (Path) series.getNode();
            if (shouldAnimate()) {
                FadeTransition ft = new FadeTransition(Duration.millis(500), seriesPath);
                ft.setToValue(0);
                ft.setOnFinished((ActionEvent actionEvent) -> {
                    getPlotChildren().remove(seriesPath);
                    seriesPath.getElements().clear();
                    removeSeriesFromDisplay(series);
                });
                ft.play();
            } else {
                getPlotChildren().remove(seriesPath);
                seriesPath.getElements().clear();
                removeSeriesFromDisplay(series);
            }
        } else {
            removeSeriesFromDisplay(series);
        }
    }

    /**
     * Create a new Candle node to represent a single data item
     *
     * @param seriesIndex The index of the series the data item is in
     * @param item        The data item to create node for
     * @param itemIndex   The index of the data item in the series
     * @return New candle node to represent the give data item
     */
    private Node createCandle(int seriesIndex, final XYChart.Data<Number, Number> item, int itemIndex) {
        Node candle = item.getNode();
        // check if candle has already been created
        if (candle instanceof Candle) {
            ((Candle) candle).setSeriesAndDataStyleClasses("series" + seriesIndex, "data" + itemIndex);
        } else {
            candle = new Candle("series" + seriesIndex, "data" + itemIndex, priceStringConverter);
            item.setNode(candle);
        }
        return candle;
    }

    /**
     * This is called when the range has been invalidated and we need to update it. If the axis are auto
     * ranging then we compile a list of all data that the given axis has to plot and call invalidateRange() on the
     * axis passing it that data.
     */
    @Override
    protected void updateAxisRange() {
        // For candle stick chart we need to override this method as we need to let the axis know that they need to be able
        // to cover the whole area occupied by the high to low range not just its center data value
        final Axis<Number> xa = getXAxis();
        final Axis<Number> ya = getYAxis();
        List<Number> xData = null;
        List<Number> yData = null;
        if (xa.isAutoRanging()) {
            xData = new ArrayList<>();
        }
        if (ya.isAutoRanging()) {
            yData = new ArrayList<>();
        }
        if (xData != null || yData != null) {
            for (XYChart.Series<Number, Number> series : getData()) {
                for (XYChart.Data<Number, Number> data : series.getData()) {
                    if (xData != null) {
                        xData.add(data.getXValue());
                    }
                    if (yData != null) {
                        if (data.getExtraValue() instanceof CandleData) {
                            CandleData candleData = (CandleData) data.getExtraValue();
                            yData.add(candleData.high);
                            yData.add(candleData.low);
                        } else {
                            yData.add(data.getYValue());
                        }
                    }
                }
            }
            if (xData != null) {
                xa.invalidateRange(xData);
            }
            if (yData != null) {
                ya.invalidateRange(yData);
            }
        }
    }
}
