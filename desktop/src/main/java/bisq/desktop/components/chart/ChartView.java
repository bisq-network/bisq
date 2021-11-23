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

import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.components.AutoTooltipSlideToggleButton;
import bisq.desktop.components.AutoTooltipToggleButton;

import bisq.core.locale.Res;

import bisq.common.UserThread;

import javafx.stage.PopupWindow;
import javafx.stage.Stage;

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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;

import javafx.event.EventHandler;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import javafx.util.Duration;

import java.time.temporal.TemporalAdjuster;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public abstract class ChartView<T extends ChartViewModel<? extends ChartDataModel>> extends ActivatableViewAndModel<VBox, T> {
    private Pane center;
    private SplitPane timelineNavigation;
    protected NumberAxis xAxis, yAxis;
    protected LineChart<Number, Number> chart;
    private HBox timelineLabels, legendBox2;
    private final ToggleGroup timeIntervalToggleGroup = new ToggleGroup();

    protected final Set<XYChart.Series<Number, Number>> activeSeries = new HashSet<>();
    protected final Map<String, Integer> seriesIndexMap = new HashMap<>();
    protected final Map<String, AutoTooltipSlideToggleButton> legendToggleBySeriesName = new HashMap<>();
    private final List<Node> dividerNodes = new ArrayList<>();
    private final List<Tooltip> dividerNodesTooltips = new ArrayList<>();
    private ChangeListener<Number> widthListener;
    private ChangeListener<Toggle> timeIntervalChangeListener;
    private ListChangeListener<Node> nodeListChangeListener;
    private int maxSeriesSize;
    private boolean centerPanePressed;
    private double x;

    @Setter
    protected boolean isRadioButtonBehaviour;
    @Setter
    private int maxDataPointsForShowingSymbols = 100;
    private ChangeListener<Number> yAxisWidthListener;
    private EventHandler<MouseEvent> dividerMouseDraggedEventHandler;
    private final StringProperty fromProperty = new SimpleStringProperty();
    private final StringProperty toProperty = new SimpleStringProperty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ChartView(T model) {
        super(model);

        root = new VBox();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize() {
        // We need to call prepareInitialize as we are not using FXMLLoader
        prepareInitialize();

        maxSeriesSize = 0;
        centerPanePressed = false;
        x = 0;

        // Series
        createSeries();

        // Time interval
        HBox timeIntervalBox = getTimeIntervalBox();

        // chart
        xAxis = getXAxis();
        yAxis = getYAxis();
        chart = getChart();

        // Timeline navigation
        addTimelineNavigation();

        // Legend
        HBox legendBox1 = initLegendsAndGetLegendBox(getSeriesForLegend1());

        Collection<XYChart.Series<Number, Number>> seriesForLegend2 = getSeriesForLegend2();
        if (seriesForLegend2 != null && !seriesForLegend2.isEmpty()) {
            legendBox2 = initLegendsAndGetLegendBox(seriesForLegend2);
        }

        // Set active series/legends
        defineAndAddActiveSeries();

        // Put all together
        VBox timelineNavigationBox = new VBox();
        double paddingLeft = 15;
        double paddingRight = 89;
        // Y-axis width depends on data so we register a listener to get correct value
        yAxisWidthListener = (observable, oldValue, newValue) -> {
            double width = newValue.doubleValue();
            if (width > 0) {
                double rightPadding = width + 14;
                VBox.setMargin(timeIntervalBox, new Insets(0, rightPadding, 0, paddingLeft));
                VBox.setMargin(timelineNavigation, new Insets(0, rightPadding, 0, paddingLeft));
                VBox.setMargin(timelineLabels, new Insets(0, rightPadding, 0, paddingLeft));
                VBox.setMargin(legendBox1, new Insets(10, rightPadding, 0, paddingLeft));
                if (legendBox2 != null) {
                    VBox.setMargin(legendBox2, new Insets(-20, rightPadding, 0, paddingLeft));
                }

                if (model.getDividerPositions()[0] == 0 && model.getDividerPositions()[1] == 1) {
                    resetTimeNavigation();
                }
            }
        };

        VBox.setMargin(timeIntervalBox, new Insets(0, paddingRight, 0, paddingLeft));
        VBox.setMargin(timelineNavigation, new Insets(0, paddingRight, 0, paddingLeft));
        VBox.setMargin(timelineLabels, new Insets(0, paddingRight, 0, paddingLeft));
        VBox.setMargin(legendBox1, new Insets(0, paddingRight, 0, paddingLeft));
        timelineNavigationBox.getChildren().addAll(timelineNavigation, timelineLabels, legendBox1);
        if (legendBox2 != null) {
            VBox.setMargin(legendBox2, new Insets(-20, paddingRight, 0, paddingLeft));
            timelineNavigationBox.getChildren().add(legendBox2);
        }
        root.getChildren().addAll(timeIntervalBox, chart, timelineNavigationBox);

        // Listeners
        widthListener = (observable, oldValue, newValue) -> {
            timelineNavigation.setDividerPosition(0, model.getDividerPositions()[0]);
            timelineNavigation.setDividerPosition(1, model.getDividerPositions()[1]);
        };

        timeIntervalChangeListener = (observable, oldValue, newValue) -> {
            if (newValue != null) {
                onTimeIntervalChanged(newValue);
            }
        };

        nodeListChangeListener = c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    c.getAddedSubList().stream()
                            .filter(node -> node instanceof Text)
                            .forEach(node -> node.getStyleClass().add("axis-tick-mark-text-node"));
                }
            }
        };
    }

    @Override
    public void activate() {
        timelineNavigation.setDividerPositions(model.getDividerPositions()[0], model.getDividerPositions()[1]);

        TemporalAdjuster temporalAdjuster = model.getTemporalAdjuster();
        applyTemporalAdjuster(temporalAdjuster);
        findTimeIntervalToggleByTemporalAdjuster(temporalAdjuster).ifPresent(timeIntervalToggleGroup::selectToggle);

        defineAndAddActiveSeries();
        initBoundsForTimelineNavigation();

        // Apply listeners and handlers
        root.widthProperty().addListener(widthListener);
        xAxis.getChildrenUnmodifiable().addListener(nodeListChangeListener);
        yAxis.widthProperty().addListener(yAxisWidthListener);
        timeIntervalToggleGroup.selectedToggleProperty().addListener(timeIntervalChangeListener);

        timelineNavigation.setOnMousePressed(this::onMousePressedSplitPane);
        timelineNavigation.setOnMouseDragged(this::onMouseDragged);
        center.setOnMousePressed(this::onMousePressedCenter);
        center.setOnMouseReleased(this::onMouseReleasedCenter);

        addLegendToggleActionHandlers(getSeriesForLegend1());
        addLegendToggleActionHandlers(getSeriesForLegend2());
        addActionHandlersToDividers();
    }

    @Override
    public void deactivate() {
        root.widthProperty().removeListener(widthListener);
        xAxis.getChildrenUnmodifiable().removeListener(nodeListChangeListener);
        yAxis.widthProperty().removeListener(yAxisWidthListener);
        timeIntervalToggleGroup.selectedToggleProperty().removeListener(timeIntervalChangeListener);

        timelineNavigation.setOnMousePressed(null);
        timelineNavigation.setOnMouseDragged(null);
        center.setOnMousePressed(null);
        center.setOnMouseReleased(null);

        removeLegendToggleActionHandlers(getSeriesForLegend1());
        removeLegendToggleActionHandlers(getSeriesForLegend2());
        removeActionHandlersToDividers();

        // clear data, reset states. We keep timeInterval state though
        activeSeries.clear();
        chart.getData().clear();
        legendToggleBySeriesName.values().forEach(e -> e.setSelected(false));
        dividerNodes.clear();
        dividerNodesTooltips.clear();
        model.invalidateCache();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TimeInterval/TemporalAdjuster
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected HBox getTimeIntervalBox() {
        ToggleButton year = getTimeIntervalToggleButton(Res.get("time.year"), TemporalAdjusterModel.Interval.YEAR,
                timeIntervalToggleGroup, "toggle-left");
        ToggleButton month = getTimeIntervalToggleButton(Res.get("time.month"), TemporalAdjusterModel.Interval.MONTH,
                timeIntervalToggleGroup, "toggle-center");
        ToggleButton week = getTimeIntervalToggleButton(Res.get("time.week"), TemporalAdjusterModel.Interval.WEEK,
                timeIntervalToggleGroup, "toggle-center");
        ToggleButton day = getTimeIntervalToggleButton(Res.get("time.day"), TemporalAdjusterModel.Interval.DAY,
                timeIntervalToggleGroup, "toggle-center");
        HBox toggleBox = new HBox();
        toggleBox.setSpacing(0);
        toggleBox.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        toggleBox.getChildren().addAll(spacer, year, month, week, day);
        return toggleBox;
    }

    private ToggleButton getTimeIntervalToggleButton(String label,
                                                     TemporalAdjusterModel.Interval interval,
                                                     ToggleGroup toggleGroup,
                                                     String style) {
        ToggleButton toggleButton = new AutoTooltipToggleButton(label);
        toggleButton.setUserData(interval);
        toggleButton.setToggleGroup(toggleGroup);
        toggleButton.setId(style);
        return toggleButton;
    }

    protected void applyTemporalAdjuster(TemporalAdjuster temporalAdjuster) {
        model.applyTemporalAdjuster(temporalAdjuster);
        findTimeIntervalToggleByTemporalAdjuster(temporalAdjuster)
                .map(e -> (TemporalAdjusterModel.Interval) e.getUserData())
                .ifPresent(model::setDateFormatPattern);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Chart
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected NumberAxis getXAxis() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setForceZeroInRange(false);
        xAxis.setAutoRanging(true);
        xAxis.setTickLabelFormatter(model.getTimeAxisStringConverter());
        return xAxis;
    }

    protected NumberAxis getYAxis() {
        NumberAxis yAxis = new NumberAxis();
        yAxis.setForceZeroInRange(true);
        yAxis.setSide(Side.RIGHT);
        yAxis.setTickLabelFormatter(model.getYAxisStringConverter());
        return yAxis;
    }

    // Add implementation if update of the y axis is required at series change
    protected void onSetYAxisFormatter(XYChart.Series<Number, Number> series) {
    }

    protected LineChart<Number, Number> getChart() {
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setMinHeight(200);
        chart.setId("charts-dao");
        return chart;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Legend
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected HBox initLegendsAndGetLegendBox(Collection<XYChart.Series<Number, Number>> collection) {
        HBox hBox = new HBox();
        hBox.setSpacing(10);
        collection.forEach(series -> {
            AutoTooltipSlideToggleButton toggle = new AutoTooltipSlideToggleButton();
            toggle.setMinWidth(200);
            toggle.setAlignment(Pos.TOP_LEFT);
            String seriesId = getSeriesId(series);
            legendToggleBySeriesName.put(seriesId, toggle);
            toggle.setText(seriesId);
            toggle.setId("charts-legend-toggle" + seriesIndexMap.get(seriesId));
            toggle.setSelected(false);
            hBox.getChildren().add(toggle);
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        hBox.getChildren().add(spacer);
        return hBox;
    }

    private void addLegendToggleActionHandlers(@Nullable Collection<XYChart.Series<Number, Number>> collection) {
        if (collection != null) {
            collection.forEach(series ->
                    legendToggleBySeriesName.get(getSeriesId(series)).setOnAction(e -> onSelectLegendToggle(series)));
        }
    }

    private void removeLegendToggleActionHandlers(@Nullable Collection<XYChart.Series<Number, Number>> collection) {
        if (collection != null) {
            collection.forEach(series ->
                    legendToggleBySeriesName.get(getSeriesId(series)).setOnAction(null));
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Timeline navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addTimelineNavigation() {
        Pane left = new Pane();
        center = new Pane();
        center.setId("chart-navigation-center-pane");
        Pane right = new Pane();
        timelineNavigation = new SplitPane(left, center, right);
        timelineNavigation.setDividerPositions(model.getDividerPositions()[0], model.getDividerPositions()[1]);
        timelineNavigation.setMinHeight(25);
        timelineLabels = new HBox();
    }

    // After initial chart data are created we apply the text from the x-axis ticks to our timeline navigation.
    protected void applyTimeLineNavigationLabels() {
        timelineLabels.getChildren().clear();
        ObservableList<Axis.TickMark<Number>> tickMarks = xAxis.getTickMarks();
        int size = tickMarks.size();
        for (int i = 0; i < size; i++) {
            Axis.TickMark<Number> tickMark = tickMarks.get(i);
            Number xValue = tickMark.getValue();
            String xValueString;
            if (xAxis.getTickLabelFormatter() != null) {
                xValueString = xAxis.getTickLabelFormatter().toString(xValue);
            } else {
                xValueString = String.valueOf(xValue);
            }
            Label label = new Label(xValueString);
            label.setMinHeight(30);
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

    private void onMousePressedSplitPane(MouseEvent e) {
        x = e.getX();
        applyFromToDates();
        showDividerTooltips();
    }

    private void onMousePressedCenter(MouseEvent e) {
        centerPanePressed = true;
        applyFromToDates();
        showDividerTooltips();
    }

    private void onMouseReleasedCenter(MouseEvent e) {
        centerPanePressed = false;
        onTimelineChanged();
        hideDividerTooltips();
    }

    private void onMouseDragged(MouseEvent e) {
        if (centerPanePressed) {
            double newX = e.getX();
            double width = timelineNavigation.getWidth();
            double relativeDelta = (x - newX) / width;
            double leftPos = timelineNavigation.getDividerPositions()[0] - relativeDelta;
            double rightPos = timelineNavigation.getDividerPositions()[1] - relativeDelta;

            // Model might limit application of new values if we hit a boundary
            model.onTimelineMouseDrag(leftPos, rightPos);
            timelineNavigation.setDividerPositions(model.getDividerPositions()[0], model.getDividerPositions()[1]);
            x = newX;

            applyFromToDates();
            showDividerTooltips();
        }
    }

    private void addActionHandlersToDividers() {
        // No API access to dividers ;-( only via css lookup hack (https://stackoverflow.com/questions/40707295/how-to-add-listener-to-divider-position?rq=1)
        // Need to be done after added to scene and call requestLayout and applyCss. We keep it in a list atm
        // and set action handler in activate.
        timelineNavigation.requestLayout();
        timelineNavigation.applyCss();
        dividerMouseDraggedEventHandler = event -> {
            applyFromToDates();
            showDividerTooltips();
        };

        for (Node node : timelineNavigation.lookupAll(".split-pane-divider")) {
            dividerNodes.add(node);
            node.setOnMouseReleased(e -> {
                hideDividerTooltips();
                onTimelineChanged();
            });
            node.addEventHandler(MouseEvent.MOUSE_DRAGGED, dividerMouseDraggedEventHandler);

            Tooltip tooltip = new Tooltip("");
            dividerNodesTooltips.add(tooltip);
            tooltip.setShowDelay(Duration.millis(300));
            tooltip.setShowDuration(Duration.seconds(3));
            tooltip.textProperty().bind(dividerNodes.size() == 1 ? fromProperty : toProperty);
            Tooltip.install(node, tooltip);
        }
    }

    private void removeActionHandlersToDividers() {
        dividerNodes.forEach(node -> {
            node.setOnMouseReleased(null);
            node.removeEventHandler(MouseEvent.MOUSE_DRAGGED, dividerMouseDraggedEventHandler);
        });
        for (int i = 0; i < dividerNodesTooltips.size(); i++) {
            Tooltip tooltip = dividerNodesTooltips.get(i);
            tooltip.textProperty().unbind();
            Tooltip.uninstall(dividerNodes.get(i), tooltip);
        }
    }

    private void resetTimeNavigation() {
        timelineNavigation.setDividerPositions(0d, 1d);
        model.onTimelineNavigationChanged(0, 1);
    }

    private void showDividerTooltips() {
        showDividerTooltip(0);
        showDividerTooltip(1);
    }

    private void hideDividerTooltips() {
        dividerNodesTooltips.forEach(PopupWindow::hide);
    }

    private void showDividerTooltip(int index) {
        Node divider = dividerNodes.get(index);
        Bounds bounds = divider.localToScene(divider.getBoundsInLocal());
        Tooltip tooltip = dividerNodesTooltips.get(index);
        double xOffset = index == 0 ? -90 : 10;
        Stage stage = (Stage) root.getScene().getWindow();
        tooltip.show(stage, stage.getX() + bounds.getMaxX() + xOffset,
                stage.getY() + bounds.getMaxY() - 40);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Series
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected abstract void createSeries();

    protected abstract Collection<XYChart.Series<Number, Number>> getSeriesForLegend1();

    // If a second legend is used this has to be overridden
    protected Collection<XYChart.Series<Number, Number>> getSeriesForLegend2() {
        return null;
    }

    protected abstract void defineAndAddActiveSeries();

    protected void activateSeries(XYChart.Series<Number, Number> series) {
        if (activeSeries.contains(series)) {
            return;
        }

        chart.getData().add(series);
        activeSeries.add(series);
        legendToggleBySeriesName.get(getSeriesId(series)).setSelected(true);
        applyDataAndUpdate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Data
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected abstract CompletableFuture<Boolean> applyData();

    private void applyDataAndUpdate() {
        long ts = System.currentTimeMillis();
        applyData().whenComplete((r, t) -> {
            log.debug("applyData took {}", System.currentTimeMillis() - ts);
            long ts2 = System.currentTimeMillis();
            updateChartAfterDataChange();
            log.debug("updateChartAfterDataChange took {}", System.currentTimeMillis() - ts2);

            onDataApplied();
        });
    }

    /**
     * Implementations define which series will be used for setBoundsForTimelineNavigation
     */
    protected abstract void initBoundsForTimelineNavigation();

    /**
     * @param   data The series data which determines the min/max x values for the time line navigation.
     *               If not applicable initBoundsForTimelineNavigation requires custom implementation.
     */
    protected void setBoundsForTimelineNavigation(ObservableList<XYChart.Data<Number, Number>> data) {
        model.initBounds(data);
        xAxis.setLowerBound(model.getLowerBound().doubleValue());
        xAxis.setUpperBound(model.getUpperBound().doubleValue());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handlers triggering a data/chart update
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onTimeIntervalChanged(Toggle newValue) {
        TemporalAdjusterModel.Interval interval = (TemporalAdjusterModel.Interval) newValue.getUserData();
        applyTemporalAdjuster(interval.getAdjuster());
        model.invalidateCache();
        applyDataAndUpdate();
    }

    private void onTimelineChanged() {
        updateTimeLinePositions();

        model.invalidateCache();
        applyDataAndUpdate();
    }

    private void updateTimeLinePositions() {
        double leftPos = timelineNavigation.getDividerPositions()[0];
        double rightPos = timelineNavigation.getDividerPositions()[1];
        model.onTimelineNavigationChanged(leftPos, rightPos);
        // We need to update as model might have adjusted the values
        timelineNavigation.setDividerPositions(model.getDividerPositions()[0], model.getDividerPositions()[1]);
        fromProperty.set(model.getTimeAxisStringConverter().toString(model.getFromDate()).replace("\n", " "));
        toProperty.set(model.getTimeAxisStringConverter().toString(model.getToDate()).replace("\n", " "));
    }

    private void applyFromToDates() {
        double leftPos = timelineNavigation.getDividerPositions()[0];
        double rightPos = timelineNavigation.getDividerPositions()[1];
        model.applyFromToDates(leftPos, rightPos);
        fromProperty.set(model.getTimeAxisStringConverter().toString(model.getFromDate()).replace("\n", " "));
        toProperty.set(model.getTimeAxisStringConverter().toString(model.getToDate()).replace("\n", " "));
    }

    private void onSelectLegendToggle(XYChart.Series<Number, Number> series) {
        boolean isSelected = legendToggleBySeriesName.get(getSeriesId(series)).isSelected();
        // If we have set that flag we deselect all other toggles
        if (isRadioButtonBehaviour) {
            new ArrayList<>(chart.getData()).stream() // We need to copy to a new list to avoid ConcurrentModificationException
                    .filter(activeSeries::contains)
                    .forEach(seriesToRemove -> {
                        chart.getData().remove(seriesToRemove);
                        String seriesId = getSeriesId(seriesToRemove);
                        activeSeries.remove(seriesToRemove);
                        legendToggleBySeriesName.get(seriesId).setSelected(false);
                    });
        }

        if (isSelected) {
            chart.getData().add(series);
            activeSeries.add(series);
            applyDataAndUpdate();

            if (isRadioButtonBehaviour) {
                // We support different y-axis formats only if isRadioButtonBehaviour is set, otherwise we would get
                // mixed data on y-axis
                onSetYAxisFormatter(series);
            }
        } else if (!isRadioButtonBehaviour) { // if isRadioButtonBehaviour we have removed it already via the code above
            chart.getData().remove(series);
            activeSeries.remove(series);
            updateChartAfterDataChange();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Chart update after data change
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Update of the chart data can be triggered by:
    // 1. activate()
    // 2. TimeInterval toggle change
    // 3. Timeline navigation change
    // 4. Legend/series toggle change

    // Timeline navigation and legend/series toggles get reset at activate.
    // Time interval toggle keeps its state at screen changes.
    protected void updateChartAfterDataChange() {
        // If a series got no data points after update we need to clear it from the chart
        cleanupDanglingSeries();

        // Hides symbols if too many data points are created
        updateSymbolsVisibility();

        // When series gets added/removed the JavaFx charts framework would try to apply styles by the index of
        // addition, but we want to use a static color assignment which is synced with the legend color.
        applySeriesStyles();

        // Set tooltip on symbols
        applyTooltip();
    }

    private void cleanupDanglingSeries() {
        List<XYChart.Series<Number, Number>> activeSeriesList = new ArrayList<>(activeSeries);
        activeSeriesList.forEach(series -> {
            ObservableList<XYChart.Series<Number, Number>> seriesOnChart = chart.getData();
            if (series.getData().isEmpty()) {
                seriesOnChart.remove(series);
            } else if (!seriesOnChart.contains(series)) {
                seriesOnChart.add(series);
            }
        });
    }

    private void updateSymbolsVisibility() {
        maxDataPointsForShowingSymbols = 100;
        long numDataPoints = chart.getData().stream()
                .map(XYChart.Series::getData)
                .mapToLong(List::size)
                .max()
                .orElse(0);
        boolean prevValue = chart.getCreateSymbols();
        boolean newValue = numDataPoints < maxDataPointsForShowingSymbols;
        if (prevValue != newValue) {
            chart.setCreateSymbols(newValue);
        }
    }

    // The chart framework assigns the colored depending on the order it got added, but want to keep colors
    // the same so they match with the legend toggle.
    private void applySeriesStyles() {
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

    private void applyTooltip() {
        chart.getData().forEach(series -> {
            series.getData().forEach(data -> {
                Node node = data.getNode();
                if (node == null) {
                    return;
                }
                String xValue = model.getTooltipDateConverter(data.getXValue());
                String yValue = model.getYAxisStringConverter().toString(data.getYValue());
                Tooltip.install(node, new Tooltip(Res.get("dao.factsAndFigures.supply.chart.tradeFee.toolTip", yValue, xValue)));
            });
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void removeStyles(Node node) {
        for (int i = 0; i < getMaxSeriesSize(); i++) {
            node.getStyleClass().remove("default-color" + i);
        }
    }

    private Set<Node> getNodesForStyle(Node node, String style) {
        Set<Node> result = new HashSet<>();
        if (node != null) {
            for (int i = 0; i < getMaxSeriesSize(); i++) {
                result.addAll(node.lookupAll(String.format(style, i)));
            }
        }
        return result;
    }

    private int getMaxSeriesSize() {
        maxSeriesSize = Math.max(maxSeriesSize, chart.getData().size());
        return maxSeriesSize;
    }

    private Optional<Toggle> findTimeIntervalToggleByTemporalAdjuster(TemporalAdjuster adjuster) {
        return timeIntervalToggleGroup.getToggles().stream()
                .filter(toggle -> ((TemporalAdjusterModel.Interval) toggle.getUserData()).getAdjuster().equals(adjuster))
                .findAny();
    }

    // We use the name as id as there is no other suitable data inside series
    protected String getSeriesId(XYChart.Series<Number, Number> series) {
        return series.getName();
    }

    protected void mapToUserThread(Runnable command) {
        UserThread.execute(command);
    }

    protected void onDataApplied() {
        // Once we have data applied we need to call initBoundsForTimelineNavigation again
        if (model.upperBound.longValue() == 0) {
            initBoundsForTimelineNavigation();
        }
    }
}
