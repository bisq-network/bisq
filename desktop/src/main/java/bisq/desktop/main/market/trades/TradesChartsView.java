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

package bisq.desktop.main.market.trades;

import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.AutoTooltipToggleButton;
import bisq.desktop.components.ColoredDecimalPlacesWithZerosText;
import bisq.desktop.main.market.trades.charts.price.CandleStickChart;
import bisq.desktop.main.market.trades.charts.volume.VolumeChart;
import bisq.desktop.util.CurrencyListItem;
import bisq.desktop.util.GUIUtil;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.OfferPayload;
import bisq.core.trade.statistics.TradeStatistics2;
import bisq.core.util.BSFormatter;

import bisq.common.UserThread;
import bisq.common.util.MathUtils;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import com.jfoenix.controls.JFXTabPane;

import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.Tab;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;
import javafx.geometry.Pos;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.fxmisc.easybind.monadic.MonadicBinding;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;
import javafx.util.StringConverter;

import java.util.Comparator;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.NotNull;

import static bisq.desktop.util.FormBuilder.addTopLabelComboBox;
import static bisq.desktop.util.FormBuilder.getTopLabelWithVBox;

@FxmlView
public class TradesChartsView extends ActivatableViewAndModel<VBox, TradesChartsViewModel> {

    private final BSFormatter formatter;

    private TableView<TradeStatistics2> tableView;
    private ComboBox<CurrencyListItem> currencyComboBox;
    private VolumeChart volumeChart;
    private CandleStickChart priceChart;
    private NumberAxis priceAxisX, priceAxisY, volumeAxisY, volumeAxisX;
    private XYChart.Series<Number, Number> priceSeries;
    private XYChart.Series<Number, Number> volumeSeries;
    private ChangeListener<Number> priceAxisYWidthListener;
    private ChangeListener<Number> volumeAxisYWidthListener;
    private double priceAxisYWidth;
    private double volumeAxisYWidth;
    private final StringProperty priceColumnLabel = new SimpleStringProperty();
    private ChangeListener<Toggle> timeUnitChangeListener;
    private ToggleGroup toggleGroup;
    private final ListChangeListener<XYChart.Data<Number, Number>> itemsChangeListener;
    private SortedList<TradeStatistics2> sortedList;
    private Label nrOfTradeStatisticsLabel;
    private ListChangeListener<TradeStatistics2> tradeStatisticsByCurrencyListener;
    private ChangeListener<Number> selectedTabIndexListener;
    private SingleSelectionModel<Tab> tabPaneSelectionModel;
    private TableColumn<TradeStatistics2, TradeStatistics2> priceColumn, volumeColumn, marketColumn;
    @SuppressWarnings("FieldCanBeLocal")
    private MonadicBinding<Void> currencySelectionBinding;
    private Subscription currencySelectionSubscriber;
    private HBox toolBox;
    private ChangeListener<Number> parentHeightListener;
    private Pane rootParent;
    private ChangeListener<String> priceColumnLabelListener;
    private AnchorPane priceChartPane, volumeChartPane;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public TradesChartsView(TradesChartsViewModel model, BSFormatter formatter) {
        super(model);
        this.formatter = formatter;

        // Need to render on next frame as otherwise there are issues in the chart rendering
        itemsChangeListener = c -> UserThread.execute(this::updateChartData);
    }

    @Override
    public void initialize() {
        root.setAlignment(Pos.CENTER_LEFT);
        toolBox = getToolBox();
        createCharts();
        createTable();

        nrOfTradeStatisticsLabel = new AutoTooltipLabel(" "); // set empty string for layout
        nrOfTradeStatisticsLabel.setId("num-offers");
        nrOfTradeStatisticsLabel.setPadding(new Insets(-5, 0, -10, 5));
        root.getChildren().addAll(toolBox, priceChartPane, volumeChartPane, tableView, nrOfTradeStatisticsLabel);

        timeUnitChangeListener = (observable, oldValue, newValue) -> {
            if (newValue != null) {
                model.setTickUnit((TradesChartsViewModel.TickUnit) newValue.getUserData());
                priceAxisX.setTickLabelFormatter(getTimeAxisStringConverter());
                volumeAxisX.setTickLabelFormatter(getTimeAxisStringConverter());
            }
        };
        priceAxisYWidthListener = (observable, oldValue, newValue) -> {
            priceAxisYWidth = (double) newValue;
            layoutChart();
        };
        volumeAxisYWidthListener = (observable, oldValue, newValue) -> {
            volumeAxisYWidth = (double) newValue;
            layoutChart();
        };
        tradeStatisticsByCurrencyListener = c -> nrOfTradeStatisticsLabel.setText(Res.get("market.trades.nrOfTrades",
                model.tradeStatisticsByCurrency.size()));
        parentHeightListener = (observable, oldValue, newValue) -> layout();

        priceColumnLabelListener = (o, oldVal, newVal) -> priceColumn.setGraphic(new AutoTooltipLabel(newVal));
    }

    @Override
    protected void activate() {
        // root.getParent() is null at initialize
        tabPaneSelectionModel = Objects.requireNonNull(GUIUtil.getParentOfType(root, JFXTabPane.class)).getSelectionModel();
        selectedTabIndexListener = (observable, oldValue, newValue) -> model.setSelectedTabIndex((int) newValue);
        model.setSelectedTabIndex(tabPaneSelectionModel.getSelectedIndex());
        tabPaneSelectionModel.selectedIndexProperty().addListener(selectedTabIndexListener);

        currencyComboBox.setItems(model.getCurrencyListItems());
        currencyComboBox.setVisibleRowCount(12);

        if (model.showAllTradeCurrenciesProperty.get())
            currencyComboBox.getSelectionModel().select(0);
        else if (model.getSelectedCurrencyListItem().isPresent())
            currencyComboBox.getSelectionModel().select(model.getSelectedCurrencyListItem().get());

        currencyComboBox.setOnAction(e -> {
            CurrencyListItem selectedItem = currencyComboBox.getSelectionModel().getSelectedItem();
            if (selectedItem != null)
                model.onSetTradeCurrency(selectedItem.tradeCurrency);
        });


        toggleGroup.getToggles().get(model.tickUnit.ordinal()).setSelected(true);

        model.priceItems.addListener(itemsChangeListener);
        toggleGroup.selectedToggleProperty().addListener(timeUnitChangeListener);
        priceAxisY.widthProperty().addListener(priceAxisYWidthListener);
        volumeAxisY.widthProperty().addListener(volumeAxisYWidthListener);
        model.tradeStatisticsByCurrency.addListener(tradeStatisticsByCurrencyListener);

        priceAxisY.labelProperty().bind(priceColumnLabel);
        priceColumnLabel.addListener(priceColumnLabelListener);

        currencySelectionBinding = EasyBind.combine(
                model.showAllTradeCurrenciesProperty, model.selectedTradeCurrencyProperty,
                (showAll, selectedTradeCurrency) -> {
                    priceChart.setVisible(!showAll);
                    priceChart.setManaged(!showAll);
                    priceColumn.setSortable(!showAll);

                    if (showAll) {
                        volumeColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.amount")));
                        priceColumnLabel.set(Res.get("shared.price"));
                        if (!tableView.getColumns().contains(marketColumn))
                            tableView.getColumns().add(1, marketColumn);

                        volumeChart.setPrefHeight(volumeChart.getMaxHeight());
                    } else {
                        volumeChart.setPrefHeight(volumeChart.getMinHeight());
                        priceSeries.setName(selectedTradeCurrency.getName());
                        String code = selectedTradeCurrency.getCode();
                        volumeColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.amountWithCur", code)));

                        priceColumnLabel.set(formatter.getPriceWithCurrencyCode(code));

                        tableView.getColumns().remove(marketColumn);
                    }
                    layout();
                    return null;
                });

        currencySelectionSubscriber = currencySelectionBinding.subscribe((observable, oldValue, newValue) -> {
        });

        sortedList = new SortedList<>(model.tradeStatisticsByCurrency);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);

        priceChart.setAnimated(model.preferences.isUseAnimations());
        volumeChart.setAnimated(model.preferences.isUseAnimations());
        priceAxisX.setTickLabelFormatter(getTimeAxisStringConverter());
        volumeAxisX.setTickLabelFormatter(getTimeAxisStringConverter());

        nrOfTradeStatisticsLabel.setText(Res.get("market.trades.nrOfTrades", model.tradeStatisticsByCurrency.size()));

        UserThread.runAfter(this::updateChartData, 100, TimeUnit.MILLISECONDS);

        if (root.getParent() instanceof Pane) {
            rootParent = (Pane) root.getParent();
            rootParent.heightProperty().addListener(parentHeightListener);
        }

        layout();
    }

    @Override
    protected void deactivate() {
        currencyComboBox.setOnAction(null);

        tabPaneSelectionModel.selectedIndexProperty().removeListener(selectedTabIndexListener);
        model.priceItems.removeListener(itemsChangeListener);
        toggleGroup.selectedToggleProperty().removeListener(timeUnitChangeListener);
        priceAxisY.widthProperty().removeListener(priceAxisYWidthListener);
        volumeAxisY.widthProperty().removeListener(volumeAxisYWidthListener);
        model.tradeStatisticsByCurrency.removeListener(tradeStatisticsByCurrencyListener);

        priceAxisY.labelProperty().unbind();
        priceColumn.textProperty().removeListener(priceColumnLabelListener);

        currencySelectionSubscriber.unsubscribe();

        sortedList.comparatorProperty().unbind();

        priceSeries.getData().clear();
        priceChart.getData().clear();

        if (rootParent != null)
            rootParent.heightProperty().removeListener(parentHeightListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Chart
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createCharts() {
        priceSeries = new XYChart.Series<>();

        priceAxisX = new NumberAxis(0, model.maxTicks + 1, 1);
        priceAxisX.setTickUnit(1);
        priceAxisX.setMinorTickCount(0);
        priceAxisX.setForceZeroInRange(false);
        priceAxisX.setTickLabelFormatter(getTimeAxisStringConverter());

        priceAxisY = new NumberAxis();
        priceAxisY.setForceZeroInRange(false);
        priceAxisY.setAutoRanging(true);
        priceAxisY.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number object) {
                String currencyCode = model.getCurrencyCode();
                double doubleValue = (double) object;
                if (CurrencyUtil.isCryptoCurrency(currencyCode)) {
                    final double value = MathUtils.scaleDownByPowerOf10(doubleValue, 8);
                    return formatter.formatRoundedDoubleWithPrecision(value, 8);
                } else {
                    return formatter.formatPrice(Price.valueOf(currencyCode, MathUtils.doubleToLong(doubleValue)));
                }
            }

            @Override
            public Number fromString(String string) {
                return null;
            }
        });

        priceChart = new CandleStickChart(priceAxisX, priceAxisY, new StringConverter<>() {
            @Override
            public String toString(Number object) {
                if (CurrencyUtil.isCryptoCurrency(model.getCurrencyCode())) {
                    final double value = MathUtils.scaleDownByPowerOf10((long) object, 8);
                    return formatter.formatRoundedDoubleWithPrecision(value, 8);
                } else {
                    return formatter.formatPrice(Price.valueOf(model.getCurrencyCode(), (long) object));
                }
            }

            @Override
            public Number fromString(String string) {
                return null;
            }
        });
        priceChart.setId("price-chart");
        priceChart.setMinHeight(198);
        priceChart.setPrefHeight(198);
        priceChart.setMaxHeight(300);
        priceChart.setLegendVisible(false);
        priceChart.setPadding(new Insets(0));
        //noinspection unchecked
        priceChart.setData(FXCollections.observableArrayList(priceSeries));

        priceChartPane = new AnchorPane();
        priceChartPane.getStyleClass().add("chart-pane");

        AnchorPane.setTopAnchor(priceChart, 15d);
        AnchorPane.setBottomAnchor(priceChart, 10d);
        AnchorPane.setLeftAnchor(priceChart, 0d);
        AnchorPane.setRightAnchor(priceChart, 10d);

        priceChartPane.getChildren().add(priceChart);

        volumeSeries = new XYChart.Series<>();

        volumeAxisX = new NumberAxis(0, model.maxTicks + 1, 1);
        volumeAxisX.setTickUnit(1);
        volumeAxisX.setMinorTickCount(0);
        volumeAxisX.setForceZeroInRange(false);
        volumeAxisX.setTickLabelFormatter(getTimeAxisStringConverter());

        volumeAxisY = new NumberAxis();
        volumeAxisY.setForceZeroInRange(true);
        volumeAxisY.setAutoRanging(true);
        volumeAxisY.setLabel(Res.get("shared.volumeWithCur", Res.getBaseCurrencyCode()));
        volumeAxisY.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number object) {
                return formatter.formatCoin(Coin.valueOf(MathUtils.doubleToLong((double) object)));
            }

            @Override
            public Number fromString(String string) {
                return null;
            }
        });

        volumeChart = new VolumeChart(volumeAxisX, volumeAxisY, new StringConverter<>() {
            @Override
            public String toString(Number object) {
                return formatter.formatCoinWithCode(Coin.valueOf((long) object));
            }

            @Override
            public Number fromString(String string) {
                return null;
            }
        });
        //noinspection unchecked
        volumeChart.setId("volume-chart");
        volumeChart.setData(FXCollections.observableArrayList(volumeSeries));
        volumeChart.setMinHeight(148);
        volumeChart.setPrefHeight(148);
        volumeChart.setMaxHeight(200);
        volumeChart.setLegendVisible(false);
        volumeChart.setPadding(new Insets(0));

        volumeChartPane = new AnchorPane();
        volumeChartPane.getStyleClass().add("chart-pane");

        AnchorPane.setTopAnchor(volumeChart, 15d);
        AnchorPane.setBottomAnchor(volumeChart, 10d);
        AnchorPane.setLeftAnchor(volumeChart, 0d);
        AnchorPane.setRightAnchor(volumeChart, 10d);

        volumeChartPane.getChildren().add(volumeChart);
    }

    private void updateChartData() {
        volumeSeries.getData().setAll(model.volumeItems);

        // At price chart we need to set the priceSeries new otherwise the lines are not rendered correctly
        // TODO should be fixed in candle chart
        priceSeries.getData().clear();
        priceSeries = new XYChart.Series<>();
        priceSeries.getData().setAll(model.priceItems);
        priceChart.getData().clear();
        //noinspection unchecked
        priceChart.setData(FXCollections.observableArrayList(priceSeries));
    }

    private void layoutChart() {
        UserThread.execute(() -> {
            if (volumeAxisYWidth > priceAxisYWidth) {
                priceChart.setPadding(new Insets(0, 0, 0, volumeAxisYWidth - priceAxisYWidth));
                volumeChart.setPadding(new Insets(0, 0, 0, 0));
            } else if (volumeAxisYWidth < priceAxisYWidth) {
                priceChart.setPadding(new Insets(0, 0, 0, 0));
                volumeChart.setPadding(new Insets(0, 0, 0, priceAxisYWidth - volumeAxisYWidth));
            }
        });
    }

    @NotNull
    private StringConverter<Number> getTimeAxisStringConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Number object) {
                long index = MathUtils.doubleToLong((double) object);
                long time = model.getTimeFromTickIndex(index);
                if (model.tickUnit.ordinal() <= TradesChartsViewModel.TickUnit.DAY.ordinal())
                    return index % 4 == 0 ? formatter.formatDate(new Date(time)) : "";
                else
                    return index % 3 == 0 ? formatter.formatTime(new Date(time)) : "";
            }

            @Override
            public Number fromString(String string) {
                return null;
            }
        };
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // CurrencyComboBox
    ///////////////////////////////////////////////////////////////////////////////////////////

    private HBox getToolBox() {

        final Tuple3<VBox, Label, ComboBox<CurrencyListItem>> currencyComboBoxTuple = addTopLabelComboBox(Res.get("shared.currency"),
                Res.get("list.currency.select"));
        currencyComboBox = currencyComboBoxTuple.third;
        currencyComboBox.setButtonCell(GUIUtil.getCurrencyListItemButtonCell(Res.get("shared.trade"),
                Res.get("shared.trades"), model.preferences));
        currencyComboBox.setCellFactory(GUIUtil.getCurrencyListItemCellFactory(Res.get("shared.trade"),
                Res.get("shared.trades"), model.preferences));

        currencyComboBox.setPromptText(Res.get("list.currency.select"));

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toggleGroup = new ToggleGroup();
        ToggleButton year = getToggleButton(Res.get("time.year"), TradesChartsViewModel.TickUnit.YEAR, toggleGroup, "toggle-left");
        ToggleButton month = getToggleButton(Res.get("time.month"), TradesChartsViewModel.TickUnit.MONTH, toggleGroup, "toggle-center");
        ToggleButton week = getToggleButton(Res.get("time.week"), TradesChartsViewModel.TickUnit.WEEK, toggleGroup, "toggle-center");
        ToggleButton day = getToggleButton(Res.get("time.day"), TradesChartsViewModel.TickUnit.DAY, toggleGroup, "toggle-center");
        ToggleButton hour = getToggleButton(Res.get("time.hour"), TradesChartsViewModel.TickUnit.HOUR, toggleGroup, "toggle-center");
        ToggleButton minute10 = getToggleButton(Res.get("time.minute10"), TradesChartsViewModel.TickUnit.MINUTE_10, toggleGroup, "toggle-right");

        HBox toggleBox = new HBox();
        toggleBox.setSpacing(0);
        toggleBox.setAlignment(Pos.CENTER_LEFT);
        toggleBox.getChildren().addAll(year, month, week, day, hour, minute10);

        final Tuple2<Label, VBox> topLabelWithVBox = getTopLabelWithVBox(Res.get("shared.interval"), toggleBox);

        HBox hBox = new HBox();
        hBox.setSpacing(0);
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.getChildren().addAll(currencyComboBoxTuple.first, spacer, topLabelWithVBox.second);
        return hBox;
    }

    private ToggleButton getToggleButton(String label, TradesChartsViewModel.TickUnit tickUnit, ToggleGroup toggleGroup, String style) {
        ToggleButton toggleButton = new AutoTooltipToggleButton(label);
        toggleButton.setUserData(tickUnit);
        toggleButton.setToggleGroup(toggleGroup);
        toggleButton.setId(style);
        return toggleButton;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createTable() {
        tableView = new TableView<>();
        tableView.setMinHeight(130);
        tableView.setPrefHeight(130);
        VBox.setVgrow(tableView, Priority.ALWAYS);

        // date
        TableColumn<TradeStatistics2, TradeStatistics2> dateColumn = new AutoTooltipTableColumn<>(Res.get("shared.dateTime")) {
            {
                setMinWidth(240);
                setMaxWidth(240);
            }
        };
        dateColumn.getStyleClass().addAll("number-column", "first-column");
        dateColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        dateColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<TradeStatistics2, TradeStatistics2> call(
                            TableColumn<TradeStatistics2, TradeStatistics2> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final TradeStatistics2 item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(formatter.formatDateTime(item.getTradeDate()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        dateColumn.setComparator(Comparator.comparing(TradeStatistics2::getTradeDate));
        tableView.getColumns().add(dateColumn);

        // market
        marketColumn = new AutoTooltipTableColumn<>(Res.get("shared.market")) {
            {
                setMinWidth(130);
                setMaxWidth(130);
            }
        };
        marketColumn.getStyleClass().add("number-column");
        marketColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        marketColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<TradeStatistics2, TradeStatistics2> call(
                            TableColumn<TradeStatistics2, TradeStatistics2> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final TradeStatistics2 item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(formatter.getCurrencyPair(item.getCurrencyCode()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        marketColumn.setComparator(Comparator.comparing(TradeStatistics2::getTradeDate));
        tableView.getColumns().add(marketColumn);

        // price
        priceColumn = new TableColumn<>();
        priceColumn.getStyleClass().add("number-column");
        priceColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        priceColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<TradeStatistics2, TradeStatistics2> call(
                            TableColumn<TradeStatistics2, TradeStatistics2> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final TradeStatistics2 item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(formatter.formatPrice(item.getTradePrice()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        priceColumn.setComparator(Comparator.comparing(TradeStatistics2::getTradePrice));
        tableView.getColumns().add(priceColumn);

        // amount
        TableColumn<TradeStatistics2, TradeStatistics2> amountColumn = new AutoTooltipTableColumn<>(Res.get("shared.amountWithCur", Res.getBaseCurrencyCode()));
        amountColumn.getStyleClass().add("number-column");
        amountColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        amountColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<TradeStatistics2, TradeStatistics2> call(
                            TableColumn<TradeStatistics2, TradeStatistics2> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final TradeStatistics2 item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setGraphic(new ColoredDecimalPlacesWithZerosText(formatter.formatCoin(item.getTradeAmount(),
                                            4), GUIUtil.AMOUNT_DECIMALS_WITH_ZEROS));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
        amountColumn.setComparator(Comparator.comparing(TradeStatistics2::getTradeAmount));
        tableView.getColumns().add(amountColumn);

        // volume
        volumeColumn = new TableColumn<>();
        volumeColumn.getStyleClass().add("number-column");
        volumeColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        volumeColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<TradeStatistics2, TradeStatistics2> call(
                            TableColumn<TradeStatistics2, TradeStatistics2> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final TradeStatistics2 item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(model.showAllTradeCurrenciesProperty.get() ?
                                            formatter.formatVolumeWithCode(item.getTradeVolume()) :
                                            formatter.formatVolume(item.getTradeVolume()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        volumeColumn.setComparator((o1, o2) -> {
            final Volume tradeVolume1 = o1.getTradeVolume();
            final Volume tradeVolume2 = o2.getTradeVolume();
            return tradeVolume1.compareTo(tradeVolume2);
        });
        tableView.getColumns().add(volumeColumn);

        // paymentMethod
        TableColumn<TradeStatistics2, TradeStatistics2> paymentMethodColumn = new AutoTooltipTableColumn<>(Res.get("shared.paymentMethod"));
        paymentMethodColumn.getStyleClass().add("number-column");
        paymentMethodColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        paymentMethodColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<TradeStatistics2, TradeStatistics2> call(
                            TableColumn<TradeStatistics2, TradeStatistics2> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final TradeStatistics2 item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(getPaymentMethodLabel(item));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        paymentMethodColumn.setComparator(Comparator.comparing(this::getPaymentMethodLabel));
        tableView.getColumns().add(paymentMethodColumn);

        // direction
        TableColumn<TradeStatistics2, TradeStatistics2> directionColumn = new AutoTooltipTableColumn<>(Res.get("shared.offerType"));
        directionColumn.getStyleClass().addAll("number-column", "last-column");
        directionColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        directionColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<TradeStatistics2, TradeStatistics2> call(
                            TableColumn<TradeStatistics2, TradeStatistics2> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final TradeStatistics2 item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(getDirectionLabel(item));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        directionColumn.setComparator(Comparator.comparing(this::getDirectionLabel));
        tableView.getColumns().add(directionColumn);

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Label placeholder = new AutoTooltipLabel(Res.get("table.placeholder.noData"));
        placeholder.setWrapText(true);
        tableView.setPlaceholder(placeholder);
        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(dateColumn);
    }

    @NotNull
    private String getDirectionLabel(TradeStatistics2 item) {
        return formatter.getDirectionWithCode(OfferPayload.Direction.valueOf(item.getDirection().name()), item.getCurrencyCode());
    }

    @NotNull
    private String getPaymentMethodLabel(TradeStatistics2 item) {
        return Res.get(item.getOfferPaymentMethod());
    }

    private void layout() {
        UserThread.runAfter(() -> {
            double available;
            if (root.getParent() instanceof Pane)
                available = ((Pane) root.getParent()).getHeight();
            else
                available = root.getHeight();

            available = available - volumeChart.getHeight() - toolBox.getHeight() - nrOfTradeStatisticsLabel.getHeight() - 65;
            if (priceChart.isManaged())
                available = available - priceChart.getHeight();
            else
                available = available + 10;
            tableView.setPrefHeight(available);
        }, 100, TimeUnit.MILLISECONDS);
    }
}
