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

package io.bisq.gui.main.market.trades;

import io.bisq.common.UserThread;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.locale.Res;
import io.bisq.common.monetary.Price;
import io.bisq.common.monetary.Volume;
import io.bisq.common.util.MathUtils;
import io.bisq.core.offer.OfferPayload;
import io.bisq.core.trade.statistics.TradeStatistics2;
import io.bisq.gui.common.view.ActivatableViewAndModel;
import io.bisq.gui.common.view.FxmlView;
import io.bisq.gui.main.market.trades.charts.price.CandleStickChart;
import io.bisq.gui.main.market.trades.charts.volume.VolumeChart;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.CurrencyListItem;
import io.bisq.gui.util.GUIUtil;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.bitcoinj.core.Coin;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.fxmisc.easybind.monadic.MonadicBinding;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@FxmlView
public class TradesChartsView extends ActivatableViewAndModel<VBox, TradesChartsViewModel> {
    private static final Logger log = LoggerFactory.getLogger(TradesChartsView.class);

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
        toolBox = getToolBox();
        createCharts();
        createTable();

        nrOfTradeStatisticsLabel = new Label(" "); // set empty string for layout
        nrOfTradeStatisticsLabel.setId("num-offers");
        nrOfTradeStatisticsLabel.setPadding(new Insets(-5, 0, -10, 5));
        root.getChildren().addAll(toolBox, priceChart, volumeChart, tableView, nrOfTradeStatisticsLabel);

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
    }

    @Override
    protected void activate() {
        // root.getParent() is null at initialize
        tabPaneSelectionModel = GUIUtil.getParentOfType(root, TabPane.class).getSelectionModel();
        selectedTabIndexListener = (observable, oldValue, newValue) -> model.setSelectedTabIndex((int) newValue);
        model.setSelectedTabIndex(tabPaneSelectionModel.getSelectedIndex());
        tabPaneSelectionModel.selectedIndexProperty().addListener(selectedTabIndexListener);

        currencyComboBox.setItems(model.getCurrencyListItems());
        currencyComboBox.setVisibleRowCount(25);

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
        priceColumn.textProperty().bind(priceColumnLabel);

        currencySelectionBinding = EasyBind.combine(
                model.showAllTradeCurrenciesProperty, model.selectedTradeCurrencyProperty,
                (showAll, selectedTradeCurrency) -> {
                    priceChart.setVisible(!showAll);
                    priceChart.setManaged(!showAll);
                    priceColumn.setSortable(!showAll);

                    if (showAll) {
                        volumeColumn.setText(Res.get("shared.amount"));
                        priceColumnLabel.set(Res.get("shared.price"));
                        if (!tableView.getColumns().contains(marketColumn))
                            tableView.getColumns().add(1, marketColumn);

                        volumeChart.setPrefHeight(volumeChart.getMaxHeight());
                    } else {
                        volumeChart.setPrefHeight(volumeChart.getMinHeight());
                        priceSeries.setName(selectedTradeCurrency.getName());
                        String code = selectedTradeCurrency.getCode();
                        volumeColumn.setText(Res.get("shared.amountWithCur", code));

                        priceColumnLabel.set(formatter.getPriceWithCurrencyCode(code));

                        if (tableView.getColumns().contains(marketColumn))
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
        priceColumn.textProperty().unbind();

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
        priceAxisY.setTickLabelFormatter(new StringConverter<Number>() {
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

        priceChart = new CandleStickChart(priceAxisX, priceAxisY, new StringConverter<Number>() {
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
        priceChart.setMinHeight(198);
        priceChart.setPrefHeight(198);
        priceChart.setMaxHeight(300);
        priceChart.setLegendVisible(false);
        //noinspection unchecked
        priceChart.setData(FXCollections.observableArrayList(priceSeries));


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
        volumeAxisY.setTickLabelFormatter(new StringConverter<Number>() {
            @Override
            public String toString(Number object) {
                return formatter.formatCoin(Coin.valueOf(MathUtils.doubleToLong((double) object)));
            }

            @Override
            public Number fromString(String string) {
                return null;
            }
        });

        volumeChart = new VolumeChart(volumeAxisX, volumeAxisY, new StringConverter<Number>() {
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
        volumeChart.setData(FXCollections.observableArrayList(volumeSeries));
        volumeChart.setMinHeight(148);
        volumeChart.setPrefHeight(148);
        volumeChart.setMaxHeight(200);
        volumeChart.setLegendVisible(false);
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
        return new StringConverter<Number>() {
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
        Label currencyLabel = new Label(Res.getWithCol("shared.currency"));
        currencyLabel.setPadding(new Insets(0, 4, 0, 0));

        currencyComboBox = new ComboBox<>();
        currencyComboBox.setPromptText(Res.get("list.currency.select"));
        currencyComboBox.setConverter(GUIUtil.getCurrencyListItemConverter(Res.get("shared.trade"),
                Res.get("shared.trades"),
                model.preferences));

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label label = new Label("Interval:");
        label.setPadding(new Insets(0, 4, 0, 0));

        toggleGroup = new ToggleGroup();
        ToggleButton year = getToggleButton(Res.get("time.year"), TradesChartsViewModel.TickUnit.YEAR, toggleGroup, "toggle-left");
        ToggleButton month = getToggleButton(Res.get("time.month"), TradesChartsViewModel.TickUnit.MONTH, toggleGroup, "toggle-left");
        ToggleButton week = getToggleButton(Res.get("time.week"), TradesChartsViewModel.TickUnit.WEEK, toggleGroup, "toggle-center");
        ToggleButton day = getToggleButton(Res.get("time.day"), TradesChartsViewModel.TickUnit.DAY, toggleGroup, "toggle-center");
        ToggleButton hour = getToggleButton(Res.get("time.hour"), TradesChartsViewModel.TickUnit.HOUR, toggleGroup, "toggle-center");
        ToggleButton minute10 = getToggleButton(Res.get("time.minute10"), TradesChartsViewModel.TickUnit.MINUTE_10, toggleGroup, "toggle-center");

        HBox hBox = new HBox();
        hBox.setSpacing(0);
        hBox.setPadding(new Insets(5, 9, -10, 10));
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.getChildren().addAll(currencyLabel, currencyComboBox, spacer, label, year, month, week, day, hour, minute10);
        return hBox;
    }

    private ToggleButton getToggleButton(String label, TradesChartsViewModel.TickUnit tickUnit, ToggleGroup toggleGroup, String style) {
        ToggleButton toggleButton = new ToggleButton(label);
        toggleButton.setPadding(new Insets(0, 5, 0, 5));
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
        tableView.setMinHeight(140);
        tableView.setPrefHeight(140);
        VBox.setVgrow(tableView, Priority.ALWAYS);

        // date
        TableColumn<TradeStatistics2, TradeStatistics2> dateColumn = new TableColumn<TradeStatistics2, TradeStatistics2>(Res.get("shared.dateTime")) {
            {
                setMinWidth(190);
                setMaxWidth(190);
            }
        };
        dateColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        dateColumn.setCellFactory(
                new Callback<TableColumn<TradeStatistics2, TradeStatistics2>, TableCell<TradeStatistics2,
                        TradeStatistics2>>() {
                    @Override
                    public TableCell<TradeStatistics2, TradeStatistics2> call(
                            TableColumn<TradeStatistics2, TradeStatistics2> column) {
                        return new TableCell<TradeStatistics2, TradeStatistics2>() {
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
        dateColumn.setComparator((o1, o2) -> o1.getTradeDate().compareTo(o2.getTradeDate()));
        tableView.getColumns().add(dateColumn);

        // market
        marketColumn = new TableColumn<TradeStatistics2, TradeStatistics2>(Res.get("shared.market")) {
            {
                setMinWidth(130);
                setMaxWidth(130);
            }
        };
        marketColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        marketColumn.setCellFactory(
                new Callback<TableColumn<TradeStatistics2, TradeStatistics2>, TableCell<TradeStatistics2,
                        TradeStatistics2>>() {
                    @Override
                    public TableCell<TradeStatistics2, TradeStatistics2> call(
                            TableColumn<TradeStatistics2, TradeStatistics2> column) {
                        return new TableCell<TradeStatistics2, TradeStatistics2>() {
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
        marketColumn.setComparator((o1, o2) -> o1.getTradeDate().compareTo(o2.getTradeDate()));
        tableView.getColumns().add(marketColumn);

        // price
        priceColumn = new TableColumn<>();
        priceColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        priceColumn.setCellFactory(
                new Callback<TableColumn<TradeStatistics2, TradeStatistics2>, TableCell<TradeStatistics2,
                        TradeStatistics2>>() {
                    @Override
                    public TableCell<TradeStatistics2, TradeStatistics2> call(
                            TableColumn<TradeStatistics2, TradeStatistics2> column) {
                        return new TableCell<TradeStatistics2, TradeStatistics2>() {
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
        priceColumn.setComparator((o1, o2) -> o1.getTradePrice().compareTo(o2.getTradePrice()));
        tableView.getColumns().add(priceColumn);

        // amount
        TableColumn<TradeStatistics2, TradeStatistics2> amountColumn = new TableColumn<>(Res.get("shared.amountWithCur", Res.getBaseCurrencyCode()));
        amountColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        amountColumn.setCellFactory(
                new Callback<TableColumn<TradeStatistics2, TradeStatistics2>, TableCell<TradeStatistics2,
                        TradeStatistics2>>() {
                    @Override
                    public TableCell<TradeStatistics2, TradeStatistics2> call(
                            TableColumn<TradeStatistics2, TradeStatistics2> column) {
                        return new TableCell<TradeStatistics2, TradeStatistics2>() {
                            @Override
                            public void updateItem(final TradeStatistics2 item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(formatter.formatCoinWithCode(item.getTradeAmount()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        amountColumn.setComparator((o1, o2) -> o1.getTradeAmount().compareTo(o2.getTradeAmount()));
        tableView.getColumns().add(amountColumn);

        // volume
        volumeColumn = new TableColumn<>();
        volumeColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        volumeColumn.setCellFactory(
                new Callback<TableColumn<TradeStatistics2, TradeStatistics2>, TableCell<TradeStatistics2,
                        TradeStatistics2>>() {
                    @Override
                    public TableCell<TradeStatistics2, TradeStatistics2> call(
                            TableColumn<TradeStatistics2, TradeStatistics2> column) {
                        return new TableCell<TradeStatistics2, TradeStatistics2>() {
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
            return tradeVolume1 != null && tradeVolume2 != null ? tradeVolume1.compareTo(tradeVolume2) : 0;
        });
        tableView.getColumns().add(volumeColumn);

        // paymentMethod
        TableColumn<TradeStatistics2, TradeStatistics2> paymentMethodColumn = new TableColumn<>(Res.get("shared.paymentMethod"));
        paymentMethodColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        paymentMethodColumn.setCellFactory(
                new Callback<TableColumn<TradeStatistics2, TradeStatistics2>, TableCell<TradeStatistics2,
                        TradeStatistics2>>() {
                    @Override
                    public TableCell<TradeStatistics2, TradeStatistics2> call(
                            TableColumn<TradeStatistics2, TradeStatistics2> column) {
                        return new TableCell<TradeStatistics2, TradeStatistics2>() {
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
        paymentMethodColumn.setComparator((o1, o2) -> getPaymentMethodLabel(o1).compareTo(getPaymentMethodLabel(o2)));
        tableView.getColumns().add(paymentMethodColumn);

        // direction
        TableColumn<TradeStatistics2, TradeStatistics2> directionColumn = new TableColumn<>(Res.get("shared.offerType"));
        directionColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        directionColumn.setCellFactory(
                new Callback<TableColumn<TradeStatistics2, TradeStatistics2>, TableCell<TradeStatistics2,
                        TradeStatistics2>>() {
                    @Override
                    public TableCell<TradeStatistics2, TradeStatistics2> call(
                            TableColumn<TradeStatistics2, TradeStatistics2> column) {
                        return new TableCell<TradeStatistics2, TradeStatistics2>() {
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
        directionColumn.setComparator((o1, o2) -> getDirectionLabel(o1).compareTo(getDirectionLabel(o2)));
        tableView.getColumns().add(directionColumn);

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Label placeholder = new Label(Res.get("table.placeholder.noData"));
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
