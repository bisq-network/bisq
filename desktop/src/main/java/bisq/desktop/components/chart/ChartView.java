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
import bisq.desktop.components.AutoTooltipToggleButton;

import bisq.core.locale.Res;

import bisq.common.util.Tuple2;

import javafx.scene.Node;
import javafx.scene.chart.Axis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;
import javafx.geometry.Pos;

import javafx.beans.value.ChangeListener;

import javafx.util.StringConverter;

import java.time.temporal.TemporalAdjuster;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.getTopLabelWithVBox;

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
    private final ToggleGroup timeUnitToggleGroup = new ToggleGroup();
    protected final Set<String> activeSeries = new HashSet<>();
    private boolean pressed;
    private double x;
    private ChangeListener<Number> widthListener;
    private int maxSeriesSize;
    private ChangeListener<Toggle> timeUnitChangeListener;
    protected String dateFormatPatters = "dd MMM\nyyyy";

    public ChartView(T model) {
        super(model);

        root = new VBox();

        // time units
        Pane timeIntervalBox = getTimeIntervalBox();

        // chart
        xAxis = getXAxis();
        yAxis = getYAxis();
        chart = getChart();
        initSeries();
        addActiveSeries();
        HBox legendBox1 = getLegendBox(getSeriesForLegend1());
        Collection<XYChart.Series<Number, Number>> seriesForLegend2 = getSeriesForLegend2();
        HBox legendBox2 = null;
        if (seriesForLegend2 != null && !seriesForLegend2.isEmpty()) {
            legendBox2 = getLegendBox(seriesForLegend2);
        }

        // Time navigation
        Pane left = new Pane();
        center = new Pane();
        Pane right = new Pane();
        splitPane = new SplitPane();
        splitPane.getItems().addAll(left, center, right);
        timelineLabels = new HBox();

        // Container
        VBox box = new VBox();
        int paddingRight = 89;
        int paddingLeft = 15;
        VBox.setMargin(splitPane, new Insets(0, paddingRight, 0, paddingLeft));
        VBox.setMargin(timelineLabels, new Insets(0, paddingRight, 0, paddingLeft));
        VBox.setMargin(legendBox1, new Insets(10, paddingRight, 0, paddingLeft));
        box.getChildren().addAll(splitPane, timelineLabels, legendBox1);
        if (legendBox2 != null) {
            VBox.setMargin(legendBox2, new Insets(-20, paddingRight, 0, paddingLeft));
            box.getChildren().add(legendBox2);
        }
        root.getChildren().addAll(timeIntervalBox, chart, box);
    }

    protected abstract void addActiveSeries();

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize() {
        center.setId("chart-navigation-center-pane");

        splitPane.setMinHeight(30);
        splitPane.setDividerPosition(0, dividerPositions[0]);
        splitPane.setDividerPosition(1, dividerPositions[1]);

        widthListener = (observable, oldValue, newValue) -> {
            splitPane.setDividerPosition(0, dividerPositions[0]);
            splitPane.setDividerPosition(1, dividerPositions[1]);
        };

        timeUnitChangeListener = (observable, oldValue, newValue) -> {
            TemporalAdjusterUtil.Interval interval = (TemporalAdjusterUtil.Interval) newValue.getUserData();
            applyTemporalAdjuster(interval.getAdjuster());
        };
    }

    @Override
    public void activate() {
        root.widthProperty().addListener(widthListener);
        timeUnitToggleGroup.selectedToggleProperty().addListener(timeUnitChangeListener);

        splitPane.setOnMousePressed(this::onMousePressedSplitPane);
        splitPane.setOnMouseDragged(this::onMouseDragged);
        center.setOnMousePressed(this::onMousePressedCenter);
        center.setOnMouseReleased(this::onMouseReleasedCenter);

        initData();
        initDividerMouseHandlers();
        // Need to get called again here as otherwise styles are not applied correctly
        applySeriesStyles();

        TemporalAdjuster temporalAdjuster = model.getTemporalAdjuster();
        applyTemporalAdjuster(temporalAdjuster);
        findToggleByTemporalAdjuster(temporalAdjuster).ifPresent(timeUnitToggleGroup::selectToggle);
    }

    @Override
    public void deactivate() {
        root.widthProperty().removeListener(widthListener);
        timeUnitToggleGroup.selectedToggleProperty().removeListener(timeUnitChangeListener);
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

    protected Pane getTimeIntervalBox() {
        ToggleButton year = getToggleButton(Res.get("time.year"), TemporalAdjusterUtil.Interval.YEAR,
                timeUnitToggleGroup, "toggle-left");
        ToggleButton month = getToggleButton(Res.get("time.month"), TemporalAdjusterUtil.Interval.MONTH,
                timeUnitToggleGroup, "toggle-center");
        ToggleButton week = getToggleButton(Res.get("time.week"), TemporalAdjusterUtil.Interval.WEEK,
                timeUnitToggleGroup, "toggle-center");
        ToggleButton day = getToggleButton(Res.get("time.day"), TemporalAdjusterUtil.Interval.DAY,
                timeUnitToggleGroup, "toggle-center");

        HBox toggleBox = new HBox();
        toggleBox.setSpacing(0);
        toggleBox.setAlignment(Pos.CENTER_LEFT);
        toggleBox.getChildren().addAll(year, month, week, day);

        Tuple2<Label, VBox> topLabelWithVBox = getTopLabelWithVBox(Res.get("shared.interval"), toggleBox);
        AnchorPane pane = new AnchorPane();
        VBox vBox = topLabelWithVBox.second;
        pane.getChildren().add(vBox);
        AnchorPane.setRightAnchor(vBox, 90d);
        return pane;
    }

    protected ToggleButton getToggleButton(String label,
                                           TemporalAdjusterUtil.Interval interval,
                                           ToggleGroup toggleGroup,
                                           String style) {
        ToggleButton toggleButton = new AutoTooltipToggleButton(label);
        toggleButton.setUserData(interval);
        toggleButton.setToggleGroup(toggleGroup);
        toggleButton.setId(style);
        return toggleButton;
    }

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

    protected abstract void initSeries();

    protected HBox getLegendBox(Collection<XYChart.Series<Number, Number>> data) {
        HBox hBox = new HBox();
        hBox.setSpacing(10);
        data.forEach(series -> {
            AutoTooltipSlideToggleButton toggle = new AutoTooltipSlideToggleButton();
            toggle.setMinWidth(200);
            toggle.setAlignment(Pos.TOP_LEFT);
            String seriesName = getSeriesId(series);
            toggleBySeriesName.put(seriesName, toggle);
            toggle.setText(seriesName);
            toggle.setId("charts-legend-toggle" + seriesIndexMap.get(seriesName));
            toggle.setSelected(true);
            toggle.setOnAction(e -> onSelectLegendToggle(series, toggle.isSelected()));
            hBox.getChildren().add(toggle);
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        hBox.getChildren().add(spacer);
        return hBox;
    }

    protected abstract Collection<XYChart.Series<Number, Number>> getSeriesForLegend1();

    protected abstract Collection<XYChart.Series<Number, Number>> getSeriesForLegend2();

    private void onSelectLegendToggle(XYChart.Series<Number, Number> series, boolean isSelected) {
        if (isSelected) {
            activateSeries(series);
        } else {
            chart.getData().remove(series);
            activeSeries.remove(getSeriesId(series));
        }
        applySeriesStyles();
        applyTooltip();
    }

    protected void activateSeries(XYChart.Series<Number, Number> series) {
        chart.getData().add(series);
        activeSeries.add(getSeriesId(series));
    }

    protected void hideSeries(XYChart.Series<Number, Number> series) {
        toggleBySeriesName.get(getSeriesId(series)).setSelected(false);
        onSelectLegendToggle(series, false);
    }

    protected StringConverter<Number> getTimeAxisStringConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Number value) {
                DateFormat format = new SimpleDateFormat(dateFormatPatters);
                Date date = new Date(Math.round(value.doubleValue()) * 1000);
                return format.format(date);
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

    protected void applyTemporalAdjuster(TemporalAdjuster temporalAdjuster) {
        model.applyTemporalAdjuster(temporalAdjuster);
        findToggleByTemporalAdjuster(temporalAdjuster)
                .map(e -> (TemporalAdjusterUtil.Interval) e.getUserData())
                .ifPresent(this::setDateFormatPatters);

        updateData(model.getPredicate());
    }

    private void setDateFormatPatters(TemporalAdjusterUtil.Interval interval) {
        switch (interval) {
            case YEAR:
                dateFormatPatters = "yyyy";
                break;
            case MONTH:
                dateFormatPatters = "MMM\nyyyy";
                break;
            default:
                dateFormatPatters = "MMM dd\nyyyy";
                break;
        }

    }

    protected abstract void initData();

    protected abstract void updateData(Predicate<Long> predicate);

    protected void applyTooltip() {
        chart.getData().forEach(series -> {
            series.getData().forEach(data -> {
                Node node = data.getNode();
                if (node == null) {
                    return;
                }
                String xValue = getTooltipDateConverter(data.getXValue());
                String yValue = getYAxisStringConverter().toString(data.getYValue());
                Tooltip.install(node, new Tooltip(Res.get("dao.factsAndFigures.supply.chart.tradeFee.toolTip", yValue, xValue)));
            });
        });
    }

    protected String getTooltipDateConverter(Number date) {
        return getTimeAxisStringConverter().toString(date).replace("\n", " ");
    }

    protected String getTooltipValueConverter(Number value) {
        return getYAxisStringConverter().toString(value);
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
    protected void applySeriesStyles() {
        for (int index = 0; index < chart.getData().size(); index++) {
            XYChart.Series<Number, Number> series = chart.getData().get(index);
            int staticIndex = seriesIndexMap.get(getSeriesId(series));
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

    private Optional<Toggle> findToggleByTemporalAdjuster(TemporalAdjuster adjuster) {
        return timeUnitToggleGroup.getToggles().stream()
                .filter(toggle -> ((TemporalAdjusterUtil.Interval) toggle.getUserData()).getAdjuster().equals(adjuster))
                .findAny();
    }

    // We use the name as id as there is no other suitable data inside series
    protected String getSeriesId(XYChart.Series<Number, Number> series) {
        return series.getName();
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
        updateData(model.setAndGetPredicate(fromToTuple));
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
