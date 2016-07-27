/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main.markets.trades;

import io.bitsquare.common.UserThread;
import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.main.markets.trades.charts.price.CandleStickChart;
import io.bitsquare.gui.main.markets.trades.charts.volume.VolumeChart;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.CryptoCurrency;
import io.bitsquare.locale.FiatCurrency;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.trade.statistics.TradeStatistics;
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
import org.bitcoinj.utils.Fiat;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Date;

@FxmlView
public class TradesChartsView extends ActivatableViewAndModel<VBox, TradesChartsViewModel> {
    private static final Logger log = LoggerFactory.getLogger(TradesChartsView.class);

    private final BSFormatter formatter;

    private TableView<TradeStatistics> tableView;
    private ComboBox<TradeCurrency> currencyComboBox;
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
    private ChangeListener<Toggle> toggleChangeListener;
    private ToggleGroup toggleGroup;
    private final ListChangeListener<XYChart.Data<Number, Number>> itemsChangeListener;
    private Subscription tradeCurrencySubscriber;
    private SortedList<TradeStatistics> sortedList;
    private Label nrOfTradeStatisticsLabel;
    private ListChangeListener<TradeStatistics> tradeStatisticsByCurrencyListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TradesChartsView(TradesChartsViewModel model, BSFormatter formatter) {
        super(model);
        this.formatter = formatter;

        // Need to render on next frame as otherwise there are issues in the chart rendering
        itemsChangeListener = c -> UserThread.execute(this::updateChartData);
    }

    @Override
    public void initialize() {
        HBox toolBox = getToolBox();
        createCharts();
        createTable();
        nrOfTradeStatisticsLabel = new Label("");
        nrOfTradeStatisticsLabel.setId("num-offers");
        nrOfTradeStatisticsLabel.setPadding(new Insets(-5, 0, -10, 5));
        root.getChildren().addAll(toolBox, priceChart, volumeChart, tableView, nrOfTradeStatisticsLabel);

        toggleChangeListener = (observable, oldValue, newValue) -> {
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
        tradeStatisticsByCurrencyListener = c -> nrOfTradeStatisticsLabel.setText("Nr. of trades: " + model.tradeStatisticsByCurrency.size());
    }

    @Override
    protected void activate() {
        currencyComboBox.setItems(model.getTradeCurrencies());
        currencyComboBox.getSelectionModel().select(model.getTradeCurrency());
        currencyComboBox.setVisibleRowCount(Math.min(currencyComboBox.getItems().size(), 25));
        currencyComboBox.setOnAction(e -> model.onSetTradeCurrency(currencyComboBox.getSelectionModel().getSelectedItem()));

        toggleGroup.getToggles().get(model.tickUnit.ordinal()).setSelected(true);

        model.priceItems.addListener(itemsChangeListener);
        toggleGroup.selectedToggleProperty().addListener(toggleChangeListener);
        priceAxisY.widthProperty().addListener(priceAxisYWidthListener);
        volumeAxisY.widthProperty().addListener(volumeAxisYWidthListener);

        tradeCurrencySubscriber = EasyBind.subscribe(model.tradeCurrencyProperty,
                tradeCurrency -> {
                    String code = tradeCurrency.getCode();
                    String tradeCurrencyName = tradeCurrency.getName();

                    priceSeries.setName(tradeCurrencyName);
                    final String currencyPair = formatter.getCurrencyPair(code);
                    priceColumnLabel.set("Price (" + currencyPair + ")");
                });

        sortedList = new SortedList<>(model.tradeStatisticsByCurrency);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);

        priceChart.setAnimated(model.preferences.getUseAnimations());
        volumeChart.setAnimated(model.preferences.getUseAnimations());
        updateChartData();
        priceAxisX.setTickLabelFormatter(getTimeAxisStringConverter());
        volumeAxisX.setTickLabelFormatter(getTimeAxisStringConverter());

        model.tradeStatisticsByCurrency.addListener(tradeStatisticsByCurrencyListener);
        nrOfTradeStatisticsLabel.setText("Nr. of trades: " + model.tradeStatisticsByCurrency.size());
    }

    @Override
    protected void deactivate() {
        model.priceItems.removeListener(itemsChangeListener);
        toggleGroup.selectedToggleProperty().removeListener(toggleChangeListener);
        priceAxisY.widthProperty().removeListener(priceAxisYWidthListener);
        volumeAxisY.widthProperty().removeListener(volumeAxisYWidthListener);
        model.tradeStatisticsByCurrency.removeListener(tradeStatisticsByCurrencyListener);
        tradeCurrencySubscriber.unsubscribe();
        currencyComboBox.setOnAction(null);
        priceAxisY.labelProperty().unbind();
        priceSeries.getData().clear();
        priceChart.getData().clear();
        sortedList.comparatorProperty().unbind();
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
        priceAxisY.labelProperty().bind(priceColumnLabel);
        priceAxisY.setTickLabelFormatter(new StringConverter<Number>() {
            @Override
            public String toString(Number object) {
                return formatter.formatFiat(Fiat.valueOf(model.getCurrencyCode(), new Double((double) object).longValue()));
            }

            @Override
            public Number fromString(String string) {
                return null;
            }
        });

        priceChart = new CandleStickChart(priceAxisX, priceAxisY);
        priceChart.setMinHeight(250);
        priceChart.setLegendVisible(false);
        priceChart.setData(FXCollections.observableArrayList(priceSeries));
        priceChart.setToolTipStringConverter(new StringConverter<Number>() {
            @Override
            public String toString(Number object) {
                return formatter.formatFiatWithCode(Fiat.valueOf(model.getCurrencyCode(), (long) object));
            }

            @Override
            public Number fromString(String string) {
                return null;
            }
        });


        volumeSeries = new XYChart.Series<>();

        volumeAxisX = new NumberAxis(0, model.maxTicks + 1, 1);
        volumeAxisX.setTickUnit(1);
        volumeAxisX.setMinorTickCount(0);
        volumeAxisX.setForceZeroInRange(false);
        volumeAxisX.setTickLabelFormatter(getTimeAxisStringConverter());

        volumeAxisY = new NumberAxis();
        volumeAxisY.setForceZeroInRange(true);
        volumeAxisY.setAutoRanging(true);
        volumeAxisY.setLabel("Volume (BTC)");
        volumeAxisY.setTickLabelFormatter(new StringConverter<Number>() {
            @Override
            public String toString(Number object) {
                return formatter.formatCoin(Coin.valueOf(new Double((double) object).longValue()));
            }

            @Override
            public Number fromString(String string) {
                return null;
            }
        });

        volumeChart = new VolumeChart(volumeAxisX, volumeAxisY);
        volumeChart.setData(FXCollections.observableArrayList(volumeSeries));
        volumeChart.setMinHeight(140);
        volumeChart.setLegendVisible(false);
        volumeChart.setToolTipStringConverter(new StringConverter<Number>() {
            @Override
            public String toString(Number object) {
                return formatter.formatCoinWithCode(Coin.valueOf(new Double((double) object).longValue()));
            }

            @Override
            public Number fromString(String string) {
                return null;
            }
        });
    }

    private void updateChartData() {
        volumeSeries.getData().setAll(model.volumeItems);

        // At price chart we need to set the priceSeries new otherwise the lines are not rendered correctly 
        // TODO should be fixed in candle chart
        priceSeries.getData().clear();
        priceSeries = new XYChart.Series<>();
        priceSeries.getData().setAll(model.priceItems);
        priceChart.getData().clear();
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
                long index = new Double((double) object).longValue();
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
        Label currencyLabel = new Label("Currency:");
        currencyLabel.setPadding(new Insets(0, 4, 0, 0));

        currencyComboBox = new ComboBox<>();
        currencyComboBox.setPromptText("Select currency");
        currencyComboBox.setConverter(new StringConverter<TradeCurrency>() {
            @Override
            public String toString(TradeCurrency tradeCurrency) {
                // http://boschista.deviantart.com/journal/Cool-ASCII-Symbols-214218618
                if (tradeCurrency instanceof FiatCurrency)
                    return "★ " + tradeCurrency.getNameAndCode();
                else if (tradeCurrency instanceof CryptoCurrency)
                    return "✦ " + tradeCurrency.getNameAndCode();
                else
                    return "-";
            }

            @Override
            public TradeCurrency fromString(String s) {
                return null;
            }
        });

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label label = new Label("Interval:");
        label.setPadding(new Insets(0, 4, 0, 0));

        toggleGroup = new ToggleGroup();
        ToggleButton month = getToggleButton("Month", TradesChartsViewModel.TickUnit.MONTH, toggleGroup, "toggle-left");
        ToggleButton week = getToggleButton("Week", TradesChartsViewModel.TickUnit.WEEK, toggleGroup, "toggle-center");
        ToggleButton day = getToggleButton("Day", TradesChartsViewModel.TickUnit.DAY, toggleGroup, "toggle-center");
        ToggleButton hour = getToggleButton("Hour", TradesChartsViewModel.TickUnit.HOUR, toggleGroup, "toggle-center");
        ToggleButton minute10 = getToggleButton("10 Minutes", TradesChartsViewModel.TickUnit.MINUTE_10, toggleGroup, "toggle-center");
        ToggleButton minute = getToggleButton("Minute", TradesChartsViewModel.TickUnit.MINUTE, toggleGroup, "toggle-right");

        HBox hBox = new HBox();
        hBox.setSpacing(0);
        hBox.setPadding(new Insets(5, 9, -10, 10));
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.getChildren().addAll(currencyLabel, currencyComboBox, spacer, label, month, week, day, hour, minute10, minute);
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
        tableView.setMinHeight(120);

        // date
        TableColumn<TradeStatistics, TradeStatistics> dateColumn = new TableColumn<>("Date/Time");
        dateColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        dateColumn.setCellFactory(
                new Callback<TableColumn<TradeStatistics, TradeStatistics>, TableCell<TradeStatistics,
                        TradeStatistics>>() {
                    @Override
                    public TableCell<TradeStatistics, TradeStatistics> call(
                            TableColumn<TradeStatistics, TradeStatistics> column) {
                        return new TableCell<TradeStatistics, TradeStatistics>() {
                            @Override
                            public void updateItem(final TradeStatistics item, boolean empty) {
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

        // amount
        TableColumn<TradeStatistics, TradeStatistics> amountColumn = new TableColumn<>("Amount in BTC");
        amountColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        amountColumn.setCellFactory(
                new Callback<TableColumn<TradeStatistics, TradeStatistics>, TableCell<TradeStatistics,
                        TradeStatistics>>() {
                    @Override
                    public TableCell<TradeStatistics, TradeStatistics> call(
                            TableColumn<TradeStatistics, TradeStatistics> column) {
                        return new TableCell<TradeStatistics, TradeStatistics>() {
                            @Override
                            public void updateItem(final TradeStatistics item, boolean empty) {
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

        // price
        TableColumn<TradeStatistics, TradeStatistics> priceColumn = new TableColumn<>();
        priceColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        priceColumn.textProperty().bind(priceColumnLabel);
        priceColumn.setCellFactory(
                new Callback<TableColumn<TradeStatistics, TradeStatistics>, TableCell<TradeStatistics,
                        TradeStatistics>>() {
                    @Override
                    public TableCell<TradeStatistics, TradeStatistics> call(
                            TableColumn<TradeStatistics, TradeStatistics> column) {
                        return new TableCell<TradeStatistics, TradeStatistics>() {
                            @Override
                            public void updateItem(final TradeStatistics item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(formatter.formatFiat(item.getTradePrice()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        priceColumn.setComparator((o1, o2) -> o1.getTradePrice().compareTo(o2.getTradePrice()));
        tableView.getColumns().add(priceColumn);

        // volume
        TableColumn<TradeStatistics, TradeStatistics> volumeColumn = new TableColumn<>();
        volumeColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        volumeColumn.setText("Volume (BTC)");
        volumeColumn.setCellFactory(
                new Callback<TableColumn<TradeStatistics, TradeStatistics>, TableCell<TradeStatistics,
                        TradeStatistics>>() {
                    @Override
                    public TableCell<TradeStatistics, TradeStatistics> call(
                            TableColumn<TradeStatistics, TradeStatistics> column) {
                        return new TableCell<TradeStatistics, TradeStatistics>() {
                            @Override
                            public void updateItem(final TradeStatistics item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(formatter.formatFiatWithCode(item.getTradeVolume()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        volumeColumn.setComparator((o1, o2) -> {
            final Fiat tradeVolume1 = o1.getTradeVolume();
            final Fiat tradeVolume2 = o2.getTradeVolume();
            return tradeVolume1 != null && tradeVolume2 != null ? tradeVolume1.compareTo(tradeVolume2) : 0;
        });
        tableView.getColumns().add(volumeColumn);

        // direction
        TableColumn<TradeStatistics, TradeStatistics> directionColumn = new TableColumn<>("Trade type");
        directionColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        directionColumn.setCellFactory(
                new Callback<TableColumn<TradeStatistics, TradeStatistics>, TableCell<TradeStatistics,
                        TradeStatistics>>() {
                    @Override
                    public TableCell<TradeStatistics, TradeStatistics> call(
                            TableColumn<TradeStatistics, TradeStatistics> column) {
                        return new TableCell<TradeStatistics, TradeStatistics>() {
                            @Override
                            public void updateItem(final TradeStatistics item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(formatter.getDirection(item.direction));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        directionColumn.setComparator((o1, o2) -> o1.direction.compareTo(o2.direction));
        tableView.getColumns().add(directionColumn);

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Label placeholder = new Label("There is no data available");
        placeholder.setWrapText(true);
        tableView.setPlaceholder(placeholder);
        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(dateColumn);
    }
}
