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

package bisq.desktop.main.market.trades.charts.volume;

import bisq.desktop.main.market.trades.charts.CandleData;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;

import javafx.scene.Node;
import javafx.scene.chart.Axis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;

import javafx.event.ActionEvent;

import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class VolumeChart extends XYChart<Number, Number> {

    private final StringConverter<Number> toolTipStringConverter;

    public VolumeChart(Axis<Number> xAxis, Axis<Number> yAxis, StringConverter<Number> toolTipStringConverter) {
        super(xAxis, yAxis);
        this.toolTipStringConverter = toolTipStringConverter;
    }

    @Override
    protected void layoutPlotChildren() {
        if (getData() == null) {
            return;
        }
        for (int seriesIndex = 0; seriesIndex < getData().size(); seriesIndex++) {
            XYChart.Series<Number, Number> series = getData().get(seriesIndex);
            Iterator<XYChart.Data<Number, Number>> iterator = getDisplayedDataIterator(series);
            while (iterator.hasNext()) {
                XYChart.Data<Number, Number> item = iterator.next();
                double x = getXAxis().getDisplayPosition(getCurrentDisplayedXValue(item));
                double y = getYAxis().getDisplayPosition(getCurrentDisplayedYValue(item));
                Node itemNode = item.getNode();
                CandleData candleData = (CandleData) item.getExtraValue();
                if (itemNode instanceof VolumeBar && candleData != null) {
                    VolumeBar volumeBar = (VolumeBar) itemNode;
                    double candleWidth = -1;
                    if (getXAxis() instanceof NumberAxis) {
                        NumberAxis xa = (NumberAxis) getXAxis();
                        candleWidth = xa.getDisplayPosition(1) * 0.60; // use 60% width between units
                    }

                    // 97 is visible chart data height if chart height is 140.
                    // So we subtract 43 form the height to get the height for the bar to the bottom.
                    // Did not find a way how to request the chart data height
                    final double height = getHeight() - 43;
                    double upperYPos = Math.min(height - 5, y); // We want min 5px height to allow tooltips
                    volumeBar.update(height - upperYPos, candleWidth, candleData);
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
        getPlotChildren().remove(volumeBar);

        if (shouldAnimate()) {
            volumeBar.setOpacity(0);
            getPlotChildren().add(volumeBar);
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
        for (int j = 0; j < series.getData().size(); j++) {
            XYChart.Data<Number, Number> item = series.getData().get(j);
            Node volumeBar = createCandle(seriesIndex, item, j);
            if (shouldAnimate()) {
                volumeBar.setOpacity(0);
                getPlotChildren().add(volumeBar);
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
        for (XYChart.Data<Number, Number> d : series.getData()) {
            final Node volumeBar = d.getNode();
            if (shouldAnimate()) {
                FadeTransition ft = new FadeTransition(Duration.millis(500), volumeBar);
                ft.setToValue(0);
                ft.setOnFinished((ActionEvent actionEvent) -> getPlotChildren().remove(volumeBar));
                ft.play();
            } else {
                getPlotChildren().remove(volumeBar);
            }
        }
        if (shouldAnimate()) {
            new Timeline(new KeyFrame(Duration.millis(500), event -> removeSeriesFromDisplay(series))).play();
        } else {
            removeSeriesFromDisplay(series);
        }
    }

    private Node createCandle(int seriesIndex, final XYChart.Data<Number, Number> item, int itemIndex) {
        Node volumeBar = item.getNode();
        if (volumeBar instanceof VolumeBar) {
            ((VolumeBar) volumeBar).setSeriesAndDataStyleClasses("series" + seriesIndex, "data" + itemIndex);
        } else {
            volumeBar = new VolumeBar("series" + seriesIndex, "data" + itemIndex, toolTipStringConverter);
            item.setNode(volumeBar);
        }
        return volumeBar;
    }

    @Override
    protected void updateAxisRange() {
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
