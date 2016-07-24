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
import io.bitsquare.gui.main.markets.trades.candlestick.CandleStickChart;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.CryptoCurrency;
import io.bitsquare.locale.FiatCurrency;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.trade.TradeStatistics;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import javafx.util.StringConverter;
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
    private Subscription tradeCurrencySubscriber;
    private final StringProperty priceColumnLabel = new SimpleStringProperty();
    private final StringProperty volumeColumnLabel = new SimpleStringProperty();
    private ChangeListener<Toggle> toggleChangeListener;
    private ToggleGroup toggleGroup;

    private NumberAxis timeAxisX, priceAxisY, volumeAxisY;
    private CategoryAxis volumeAxisX;
    private XYChart.Series<Number, Number> priceSeries;
    private XYChart.Series<String, Number> volumeSeries;
    private BarChart<String, Number> volumeChart;
    private CandleStickChart priceChart;
    // private LineChart<Number, Number> priceChart;

    private final ListChangeListener<XYChart.Data<Number, Number>> itemsChangeListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TradesChartsView(TradesChartsViewModel model, BSFormatter formatter) {
        super(model);
        this.formatter = formatter;

        itemsChangeListener = c -> updateChartData();
    }

    @Override
    public void initialize() {
        HBox currencyHBox = createCurrencyComboBox();
        HBox toggleBarHBox = createToggleBar();
        createChart();
        final VBox tableVBox = getTableBox();

        StackPane stackPane = new StackPane();
        stackPane.getChildren().addAll(volumeChart, priceChart);

        root.getChildren().addAll(currencyHBox, toggleBarHBox, stackPane, tableVBox);

        toggleChangeListener = (observable, oldValue, newValue) -> {
            if (newValue != null)
                model.setTickUnit((TradesChartsViewModel.TickUnit) newValue.getUserData());
        };
    }


    @Override
    protected void activate() {
        currencyComboBox.setItems(model.getTradeCurrencies());
        currencyComboBox.getSelectionModel().select(model.getTradeCurrency());
        currencyComboBox.setVisibleRowCount(Math.min(currencyComboBox.getItems().size(), 25));
        currencyComboBox.setOnAction(e -> model.onSetTradeCurrency(currencyComboBox.getSelectionModel().getSelectedItem()));

        model.items.addListener(itemsChangeListener);
        tradeCurrencySubscriber = EasyBind.subscribe(model.tradeCurrency,
                tradeCurrency -> {
                    String code = tradeCurrency.getCode();
                    String tradeCurrencyName = tradeCurrency.getName();
                    priceSeries.setName(tradeCurrencyName);
                    priceColumnLabel.set("Price (" + formatter.getCurrencyPair(code) + ")");
                    volumeColumnLabel.set("Volume (" + code + ")");
                    priceAxisY.setLabel(priceColumnLabel.get());
                });

        tableView.setItems(model.tradeStatistics);
        toggleGroup.selectedToggleProperty().addListener(toggleChangeListener);
        updateChartData();
    }

    @Override
    protected void deactivate() {
        model.items.removeListener(itemsChangeListener);
        tradeCurrencySubscriber.unsubscribe();
        currencyComboBox.setOnAction(null);
        toggleGroup.selectedToggleProperty().removeListener(toggleChangeListener);
        priceSeries.getData().clear();
        priceChart.getData().clear();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Chart
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createChart() {
        volumeSeries = new XYChart.Series<>();

        volumeAxisX = new CategoryAxis();
        volumeAxisX.setLabel("");
        volumeAxisX.setCategories(FXCollections.observableArrayList());

        volumeAxisY = new NumberAxis();
        volumeAxisY.setForceZeroInRange(false);
        volumeAxisY.setAutoRanging(true);
        volumeAxisY.setLabel("Volume");
        volumeAxisY.setTickLabelFormatter(getVolumeStringConverter());
        volumeAxisY.setSide(Side.RIGHT);

        volumeChart = new BarChart<>(volumeAxisX, volumeAxisY);
        volumeChart.setData(FXCollections.observableArrayList(volumeSeries));
        volumeChart.setAnimated(false);
        volumeChart.setMinHeight(300);
        volumeChart.setPadding(new Insets(0, 0, -10, 75));
        volumeChart.setLegendVisible(false);


        priceSeries = new XYChart.Series<>();

        timeAxisX = new NumberAxis(0, model.upperBound + 1, 1);
        timeAxisX.setTickUnit(1);
        timeAxisX.setMinorTickCount(0);
        timeAxisX.setForceZeroInRange(false);
        timeAxisX.setLabel("Date/Time");
        timeAxisX.setTickLabelFormatter(getXAxisStringConverter());

        priceAxisY = new NumberAxis();
        priceAxisY.setForceZeroInRange(false);
        priceAxisY.setAutoRanging(true);
        priceAxisY.setLabel(priceColumnLabel.get());
        priceAxisY.setTickLabelFormatter(getPriceStringConverter());

        priceChart = new CandleStickChart(timeAxisX, priceAxisY);
        priceChart.setData(FXCollections.observableArrayList(priceSeries));
        priceChart.setToolTipStringConverter(getPriceStringConverter());
        priceChart.setLegendVisible(false);
        priceChart.setAnimated(true);
        priceChart.setMinHeight(300);
        priceChart.setPadding(new Insets(0, 75, -10, 0));
        priceChart.setAlternativeRowFillVisible(false);
        priceChart.setAlternativeColumnFillVisible(false);
        priceChart.setHorizontalGridLinesVisible(false);
        priceChart.setVerticalGridLinesVisible(false);
        priceChart.getXAxis().setVisible(false);
        priceChart.getYAxis().setVisible(false);
    }

    private void updateChartData() {
        /*priceSeries.getData().clear();
        priceChart.getData().clear();
        priceSeries = new XYChart.Series<>();
        priceSeries.getData().addAll(model.items);
        priceChart.setData(FXCollections.observableArrayList(priceSeries));*/

        volumeSeries.getData().clear();
        volumeChart.getData().clear();
        volumeSeries = new XYChart.Series<>();
        volumeSeries.getData().addAll(model.volumeItems);
        volumeChart.setData(FXCollections.observableArrayList(volumeSeries));

        timeAxisX.setTickLabelFormatter(getXAxisStringConverter());
    }

    @NotNull
    private StringConverter<Number> getXAxisStringConverter() {
        return new StringConverter<Number>() {
            @Override
            public String toString(Number object) {
                // comes as double
                long index = new Double((double) object).longValue();
                final long now = model.getTickFromTime(new Date().getTime(), model.tickUnit);
                final long tick = now - (model.upperBound - index);
                final long time = model.getTimeFromTick(tick, model.tickUnit);
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

    @NotNull
    private StringConverter<Number> getPriceStringConverter() {
        return new StringConverter<Number>() {
            @Override
            public String toString(Number object) {
                // comes as double
                return formatter.formatFiat(Fiat.valueOf(model.getCurrencyCode(), new Double((double) object).longValue()));
            }

            @Override
            public Number fromString(String string) {
                return null;
            }
        };
    }

    @NotNull
    private StringConverter<Number> getVolumeStringConverter() {
        return new StringConverter<Number>() {
            @Override
            public String toString(Number object) {
                // comes as double
                return formatter.formatFiat(Fiat.valueOf(model.getCurrencyCode(), new Double((double) object).longValue()));
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

    private HBox createCurrencyComboBox() {
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


        Label currencyLabel = new Label("Currency:");
        HBox currencyHBox = new HBox();
        currencyHBox.setSpacing(5);
        currencyHBox.setPadding(new Insets(10, -20, 0, 20));
        currencyHBox.setAlignment(Pos.CENTER_LEFT);
        currencyHBox.getChildren().addAll(currencyLabel, currencyComboBox);
        return currencyHBox;

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ToggleBar
    ///////////////////////////////////////////////////////////////////////////////////////////

    private HBox createToggleBar() {
        HBox hBox = new HBox();
        hBox.setSpacing(0);
        hBox.setPadding(new Insets(0, 0, -26, 85));

        Label label = new Label("Interval:");
        label.setPadding(new Insets(5, 5, 0, 0));

        toggleGroup = new ToggleGroup();
        ToggleButton month = getToggleButton("Month", TradesChartsViewModel.TickUnit.MONTH, toggleGroup, "toggle-left");
        ToggleButton week = getToggleButton("Week", TradesChartsViewModel.TickUnit.WEEK, toggleGroup, "toggle-center");
        ToggleButton day = getToggleButton("Day", TradesChartsViewModel.TickUnit.DAY, toggleGroup, "toggle-center");
        ToggleButton hour = getToggleButton("Hour", TradesChartsViewModel.TickUnit.HOUR, toggleGroup, "toggle-center");
        ToggleButton minute10 = getToggleButton("10 Minute", TradesChartsViewModel.TickUnit.MINUTE_10, toggleGroup, "toggle-center");
        ToggleButton minute = getToggleButton("Minute", TradesChartsViewModel.TickUnit.MINUTE, toggleGroup, "toggle-right");
        minute10.setSelected(true);
        hBox.getChildren().addAll(label, month, week, day, hour, minute10, minute);
        return hBox;
    }

    private ToggleButton getToggleButton(String label, TradesChartsViewModel.TickUnit tickUnit, ToggleGroup toggleGroup, String style) {
        ToggleButton toggleButton = new ToggleButton(label);
        toggleButton.setPadding(new Insets(0, 3, 0, 3));
        toggleButton.setUserData(tickUnit);
        toggleButton.setToggleGroup(toggleGroup);
        toggleButton.setId(style);
        return toggleButton;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table
    ///////////////////////////////////////////////////////////////////////////////////////////

    private VBox getTableBox() {
        tableView = new TableView<>();

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
        tableView.getColumns().add(priceColumn);


        // volume
        TableColumn<TradeStatistics, TradeStatistics> volumeColumn = new TableColumn<>();
        volumeColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        volumeColumn.textProperty().bind(volumeColumnLabel);
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
                                    setText(formatter.getDirection(item.offer.getDirection()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        tableView.getColumns().add(directionColumn);

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Label placeholder = new Label("Currently there is no data available");
        placeholder.setWrapText(true);
        tableView.setPlaceholder(placeholder);

        Label titleLabel = new Label("Trades");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16; -fx-alignment: center");
        UserThread.execute(() -> titleLabel.prefWidthProperty().bind(tableView.widthProperty()));

        VBox vBox = new VBox();
        vBox.setSpacing(10);
        vBox.setFillWidth(true);
        vBox.setMinHeight(190);
        vBox.getChildren().addAll(titleLabel, tableView);

        return vBox;
    }

}
