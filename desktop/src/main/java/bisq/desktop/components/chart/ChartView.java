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

package bisq.desktop.components.chart;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.components.AutoTooltipSlideToggleButton;

import bisq.common.util.Tuple2;

import javafx.scene.Node;
import javafx.scene.chart.Axis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;

import javafx.beans.value.ChangeListener;

import javafx.util.StringConverter;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ChartView<T extends ChartModel> extends ActivatableView<VBox, T> {
    private final Pane center;
    private final SplitPane splitPane;
    protected final NumberAxis xAxis;
    protected final NumberAxis yAxis;
    protected final XYChart<Number, Number> chart;
    protected final Map<String, Integer> seriesIndexMap = new HashMap<>();
    protected final Map<String, AutoTooltipSlideToggleButton> toggleBySeriesName = new HashMap<>();
    private final HBox timelineLabels;
    private final List<Node> dividerNodes = new ArrayList<>();
    private final Double[] dividerPositions = new Double[]{0d, 1d};
    private HBox legendBox;
    private boolean pressed;
    private double x;
    private ChangeListener<Number> widthListener;
    private int maxSeriesSize;

    public ChartView(T model) {
        super(model);

        root = new VBox();
        Pane left = new Pane();
        center = new Pane();
        Pane right = new Pane();
        splitPane = new SplitPane();
        splitPane.getItems().addAll(left, center, right);

        xAxis = getXAxis();
        yAxis = getYAxis();

        chart = getChart();


        addSeries();
        addLegend();
        timelineLabels = new HBox();

        VBox box = new VBox();
        int paddingRight = 60;
        VBox.setMargin(splitPane, new Insets(20, paddingRight, 0, 0));
        VBox.setMargin(timelineLabels, new Insets(0, paddingRight, 0, 0));
        VBox.setMargin(legendBox, new Insets(10, paddingRight, 0, 0));
        box.getChildren().addAll(splitPane, timelineLabels, legendBox);

        VBox vBox = new VBox();
        vBox.setSpacing(10);
        vBox.getChildren().addAll(chart, box);

        root.getChildren().addAll(chart, box);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize() {
        center.setStyle("-fx-background-color: #dddddd");

        splitPane.setMinHeight(30);
        splitPane.setDividerPosition(0, dividerPositions[0]);
        splitPane.setDividerPosition(1, dividerPositions[1]);

        widthListener = (observable, oldValue, newValue) -> {
            splitPane.setDividerPosition(0, dividerPositions[0]);
            splitPane.setDividerPosition(1, dividerPositions[1]);
        };
    }

    @Override
    public void activate() {
        root.widthProperty().addListener(widthListener);
        splitPane.setOnMousePressed(this::onMousePressedSplitPane);
        splitPane.setOnMouseDragged(this::onMouseDragged);
        center.setOnMousePressed(this::onMousePressedCenter);
        center.setOnMouseReleased(this::onMouseReleasedCenter);

        initData();

        initDividerMouseHandlers();
    }

    @Override
    public void deactivate() {
        root.widthProperty().removeListener(widthListener);
        splitPane.setOnMousePressed(null);
        splitPane.setOnMouseDragged(null);
        center.setOnMousePressed(null);
        center.setOnMouseReleased(null);

        dividerNodes.forEach(node -> node.setOnMouseReleased(null));
    }

    public void addListener(ChartModel.Listener listener) {
        model.addListener(listener);
    }

    public void removeListener(ChartModel.Listener listener) {
        model.removeListener(listener);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Customisations
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected NumberAxis getXAxis() {
        NumberAxis xAxis;
        xAxis = new NumberAxis();
        xAxis.setForceZeroInRange(false);
        xAxis.setTickLabelFormatter(getTimeAxisStringConverter());
        xAxis.setAutoRanging(true);
        return xAxis;
    }

    protected NumberAxis getYAxis() {
        NumberAxis yAxis;
        yAxis = new NumberAxis();
        yAxis.setForceZeroInRange(false);
        yAxis.setTickLabelFormatter(getYAxisStringConverter());
        return yAxis;
    }

    protected XYChart<Number, Number> getChart() {
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        return chart;
    }

    protected abstract void addSeries();

    protected void addLegend() {
        legendBox = new HBox();
        legendBox.setSpacing(10);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        legendBox.getChildren().add(spacer);

        chart.getData().forEach(series -> {
            AutoTooltipSlideToggleButton toggle = new AutoTooltipSlideToggleButton();
            String seriesName = series.getName();
            toggleBySeriesName.put(seriesName, toggle);
            toggle.setText(seriesName);
            toggle.setId("charts-legend-toggle" + seriesIndexMap.get(seriesName));
            toggle.setSelected(true);
            toggle.setOnAction(e -> onSelectToggle(series, toggle.isSelected()));
            legendBox.getChildren().add(toggle);
        });
        spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        legendBox.getChildren().add(spacer);
    }

    private void onSelectToggle(XYChart.Series<Number, Number> series, boolean isSelected) {
        if (isSelected) {
            chart.getData().add(series);
        } else {
            chart.getData().remove(series);
        }
        applySeriesStyles();
    }

    protected void hideSeries(XYChart.Series<Number, Number> series) {
        toggleBySeriesName.get(series.getName()).setSelected(false);
        onSelectToggle(series, false);
    }

    protected StringConverter<Number> getTimeAxisStringConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Number value) {
                DateFormat f = new SimpleDateFormat("YYYY-MM");
                Date date = new Date(Math.round(value.doubleValue()) * 1000);
                return f.format(date);
            }

            @Override
            public Number fromString(String string) {
                return null;
            }
        };
    }

    protected StringConverter<Number> getYAxisStringConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Number value) {
                return String.valueOf(value);
            }

            @Override
            public Number fromString(String string) {
                return null;
            }
        };
    }

    protected abstract void initData();

    protected abstract void updateData(Predicate<Long> predicate);

    protected void applyTooltip() {
        chart.getData().forEach(series -> {
            series.getData().forEach(data -> {
                String xValue = getXAxis().getTickLabelFormatter().toString(data.getXValue());
                String yValue = getYAxis().getTickLabelFormatter().toString(data.getYValue());
                Node node = data.getNode();
                Tooltip.install(node, new Tooltip(yValue + "\n" + xValue));

                //Adding class on hover
                node.setOnMouseEntered(event -> node.getStyleClass().add("onHover"));

                //Removing class on exit
                node.setOnMouseExited(event -> node.getStyleClass().remove("onHover"));
            });
        });
    }

    // Only called once when initial data are applied. We want the min. and max. values so we have the max. scale for
    // navigation.
    protected void setTimeLineLabels() {
        timelineLabels.getChildren().clear();
        int size = xAxis.getTickMarks().size();
        for (int i = 0; i < size; i++) {
            Axis.TickMark<Number> tickMark = xAxis.getTickMarks().get(i);
            Number xValue = tickMark.getValue();
            String xValueString;
            if (xAxis.getTickLabelFormatter() != null) {
                xValueString = xAxis.getTickLabelFormatter().toString(xValue);
            } else {
                xValueString = String.valueOf(xValue);
            }
            Label label = new Label(xValueString);
            label.setId("chart-navigation-label");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            if (i < size - 1) {
                timelineLabels.getChildren().addAll(label, spacer);
            } else {
                // After last label we don't add a spacer
                timelineLabels.getChildren().add(label);
            }
        }
    }

    // The chart framework assigns the colored depending on the order it got added, but want to keep colors
    // the same so they match with the legend toggle.
    private void applySeriesStyles() {
        for (int index = 0; index < chart.getData().size(); index++) {
            XYChart.Series<Number, Number> series = chart.getData().get(index);
            int staticIndex = seriesIndexMap.get(series.getName());
            Set<Node> lines = getNodesForStyle(series.getNode(), ".default-color%d.chart-series-line");
            Stream<Node> symbols = series.getData().stream().map(XYChart.Data::getNode)
                    .flatMap(node -> getNodesForStyle(node, ".default-color%d.chart-line-symbol").stream());
            Stream.concat(lines.stream(), symbols).forEach(node -> {
                removeStyles(node);
                node.getStyleClass().add("default-color" + staticIndex);
            });
        }
    }

    private void removeStyles(Node node) {
        for (int i = 0; i < getMaxSeriesSize(); i++) {
            node.getStyleClass().remove("default-color" + i);
        }
    }

    private Set<Node> getNodesForStyle(Node node, String style) {
        Set<Node> result = new HashSet<>();
        for (int i = 0; i < getMaxSeriesSize(); i++) {
            result.addAll(node.lookupAll(String.format(style, i)));
        }
        return result;
    }

    private int getMaxSeriesSize() {
        maxSeriesSize = Math.max(maxSeriesSize, chart.getData().size());
        return maxSeriesSize;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Timeline navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onTimelineChanged() {
        double leftPos = splitPane.getDividerPositions()[0];
        double rightPos = splitPane.getDividerPositions()[1];
        // We need to snap into the 0 and 1 values once we are close as otherwise once navigation has been used we
        // would not get back to exact 0 or 1. Not clear why but might be rounding issues from values at x positions of
        // drag operations.
        if (leftPos < 0.01) {
            leftPos = 0;
        }
        if (rightPos > 0.99) {
            rightPos = 1;
        }
        dividerPositions[0] = leftPos;
        dividerPositions[1] = rightPos;
        splitPane.setDividerPositions(leftPos, rightPos);
        Tuple2<Double, Double> fromToTuple = model.timelinePositionToEpochSeconds(leftPos, rightPos);
        updateData(model.getPredicate(fromToTuple));
        applySeriesStyles();
        model.notifyListeners(fromToTuple);
    }

    private void initDividerMouseHandlers() {
        // No API access to dividers ;-( only via css lookup hack (https://stackoverflow.com/questions/40707295/how-to-add-listener-to-divider-position?rq=1)
        // Need to be done after added to scene and call requestLayout and applyCss. We keep it in a list atm
        // and set action handler in activate.
        splitPane.requestLayout();
        splitPane.applyCss();
        for (Node node : splitPane.lookupAll(".split-pane-divider")) {
            dividerNodes.add(node);
            node.setOnMouseReleased(e -> onTimelineChanged());
        }
    }

    private void onMouseDragged(MouseEvent e) {
        if (pressed) {
            double newX = e.getX();
            double width = splitPane.getWidth();
            double relativeDelta = (x - newX) / width;
            double leftPos = splitPane.getDividerPositions()[0] - relativeDelta;
            double rightPos = splitPane.getDividerPositions()[1] - relativeDelta;
            dividerPositions[0] = leftPos;
            dividerPositions[1] = rightPos;
            splitPane.setDividerPositions(leftPos, rightPos);
            x = newX;
        }
    }

    private void onMouseReleasedCenter(MouseEvent e) {
        pressed = false;
        onTimelineChanged();
    }

    private void onMousePressedSplitPane(MouseEvent e) {
        x = e.getX();
    }

    private void onMousePressedCenter(MouseEvent e) {
        pressed = true;
    }
}
