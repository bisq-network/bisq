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
package io.bitsquare.gui.main.markets.trades.candlestick;

import javafx.animation.FadeTransition;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.chart.Axis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A candlestick chart is a style of bar-chart used primarily to describe price movements of a security, derivative,
 * or currency over time.
 * <p>
 * The Data Y value is used for the opening price and then the close, high and low values are stored in the Data's
 * extra value property using a CandleStickExtraValues object.
 */
public class VolumeChart extends XYChart<Number, Number> {
    private static final Logger log = LoggerFactory.getLogger(CandleStickChart.class);

    private StringConverter<Number> toolTipStringConverter;

    // -------------- CONSTRUCTORS ----------------------------------------------

    /**
     * Construct a new CandleStickChart with the given axis.
     *
     * @param xAxis The x axis to use
     * @param yAxis The y axis to use
     */
    public VolumeChart(Axis<Number> xAxis, Axis<Number> yAxis) {
        super(xAxis, yAxis);
    }

    // -------------- METHODS ------------------------------------------------------------------------------------------

    public final void setToolTipStringConverter(StringConverter<Number> toolTipStringConverter) {
        this.toolTipStringConverter = toolTipStringConverter;
    }

    /**
     * Called to update and layout the content for the plot
     */
    @Override
    protected void layoutPlotChildren() {
        // we have nothing to layout if no data is present
        if (getData() == null) {
            return;
        }
        // update volumeBar positions
        for (int seriesIndex = 0; seriesIndex < getData().size(); seriesIndex++) {
            XYChart.Series<Number, Number> series = getData().get(seriesIndex);
            Iterator<XYChart.Data<Number, Number>> iter = getDisplayedDataIterator(series);
            while (iter.hasNext()) {
                XYChart.Data<Number, Number> item = iter.next();
                double x = getXAxis().getDisplayPosition(getCurrentDisplayedXValue(item));
                double y = getYAxis().getDisplayPosition(getCurrentDisplayedYValue(item));
                Node itemNode = item.getNode();
                CandleStickExtraValues extra = (CandleStickExtraValues) item.getExtraValue();
                if (itemNode instanceof VolumeBar && extra != null) {
                    VolumeBar volumeBar = (VolumeBar) itemNode;
                    double candleWidth = -1;
                    if (getXAxis() instanceof NumberAxis) {
                        NumberAxis xa = (NumberAxis) getXAxis();
                        candleWidth = xa.getDisplayPosition(xa.getTickUnit()) * 0.90; // use 90% width between ticks
                    }

                    // 97 is visible chart data height if chart height is 140. 
                    // So we subtract 43 form the height to get the height for the bar to the bottom.
                    // Did not find a way how to request the chart data height
                    final double height = getHeight() - 43;
                    double upperYPos = Math.min(height - 5, y); // We want min 5px height to allow tooltips
                    volumeBar.update(height - upperYPos, candleWidth, extra.getVolume());
                    volumeBar.setLayoutX(x);
                    volumeBar.setLayoutY(upperYPos);
                }
            }
        }
    }

    @Override
    protected void dataItemChanged(XYChart.Data<Number, Number> item) {
    }

    @Override
    protected void dataItemAdded(XYChart.Series<Number, Number> series, int itemIndex, XYChart.Data<Number, Number> item) {
        Node volumeBar = createCandle(getData().indexOf(series), item, itemIndex);
        if (getPlotChildren().contains(volumeBar))
            getPlotChildren().remove(volumeBar);

        if (shouldAnimate()) {
            volumeBar.setOpacity(0);
            getPlotChildren().add(volumeBar);
            // fade in new volumeBar
            FadeTransition ft = new FadeTransition(Duration.millis(500), volumeBar);
            ft.setToValue(1);
            ft.play();
        } else {
            getPlotChildren().add(volumeBar);
        }
    }

    @Override
    protected void dataItemRemoved(XYChart.Data<Number, Number> item, XYChart.Series<Number, Number> series) {
        final Node node = item.getNode();
        if (shouldAnimate()) {
            // fade out old volumeBar
            FadeTransition ft = new FadeTransition(Duration.millis(500), node);
            ft.setToValue(0);
            ft.setOnFinished((ActionEvent actionEvent) -> {
                getPlotChildren().remove(node);
            });
            ft.play();
        } else {
            getPlotChildren().remove(node);
        }
    }

    @Override
    protected void seriesAdded(XYChart.Series<Number, Number> series, int seriesIndex) {
        // handle any data already in series
        for (int j = 0; j < series.getData().size(); j++) {
            XYChart.Data item = series.getData().get(j);
            Node volumeBar = createCandle(seriesIndex, item, j);
            if (shouldAnimate()) {
                volumeBar.setOpacity(0);
                getPlotChildren().add(volumeBar);
                // fade in new volumeBar
                FadeTransition ft = new FadeTransition(Duration.millis(500), volumeBar);
                ft.setToValue(1);
                ft.play();
            } else {
                getPlotChildren().add(volumeBar);
            }
        }
    }

    @Override
    protected void seriesRemoved(XYChart.Series<Number, Number> series) {
        // remove all volumeBar nodes
        for (XYChart.Data<Number, Number> d : series.getData()) {
            final Node volumeBar = d.getNode();
            if (shouldAnimate()) {
                // fade out old volumeBar
                FadeTransition ft = new FadeTransition(Duration.millis(500), volumeBar);
                ft.setToValue(0);
                ft.setOnFinished((ActionEvent actionEvent) -> {
                    getPlotChildren().remove(volumeBar);
                });
                ft.play();
            } else {
                getPlotChildren().remove(volumeBar);
            }
        }
    }

    /**
     * Create a new VolumeBar node to represent a single data item
     *
     * @param seriesIndex The index of the series the data item is in
     * @param item        The data item to create node for
     * @param itemIndex   The index of the data item in the series
     * @return New volumeBar node to represent the give data item
     */
    private Node createCandle(int seriesIndex, final XYChart.Data item, int itemIndex) {
        Node volumeBar = item.getNode();
        // check if volumeBar has already been created
        if (volumeBar instanceof VolumeBar) {
            ((VolumeBar) volumeBar).setSeriesAndDataStyleClasses("series" + seriesIndex, "data" + itemIndex);
        } else {
            volumeBar = new VolumeBar("series" + seriesIndex, "data" + itemIndex, toolTipStringConverter);
            item.setNode(volumeBar);
        }
        return volumeBar;
    }

    /**
     * This is called when the range has been invalidated and we need to update it. If the axis are auto
     * ranging then we compile a list of all data that the given axis has to plot and call invalidateRange() on the
     * axis passing it that data.
     */
    @Override
    protected void updateAxisRange() {
        // For volumeBar stick chart we need to override this method as we need to let the axis know that they need to be able
        // to cover the whole area occupied by the high to low range not just its center data value
        final Axis<Number> xa = getXAxis();
        final Axis<Number> ya = getYAxis();
        List<Number> xData = null;
        List<Number> yData = null;
        if (xa.isAutoRanging()) {
            xData = new ArrayList<>();
        }
        if (ya.isAutoRanging())
            yData = new ArrayList<>();
        if (xData != null || yData != null) {
            for (XYChart.Series<Number, Number> series : getData()) {
                for (XYChart.Data<Number, Number> data : series.getData()) {
                    if (xData != null) {
                        xData.add(data.getXValue());
                    }
                    if (yData != null)
                        yData.add(data.getYValue());
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