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
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.candlestick.CandleStickChart;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.CryptoCurrency;
import io.bitsquare.locale.FiatCurrency;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.trade.TradeStatistics;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
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

    private NumberAxis xAxis, yAxis;
    XYChart.Series<Number, Number> series;
    private final ListChangeListener<XYChart.Data<Number, Number>> itemsChangeListener;
    private final Navigation navigation;
    private final BSFormatter formatter;
    private TableView<TradeStatistics> tableView;
    private ComboBox<TradeCurrency> currencyComboBox;
    private Subscription tradeCurrencySubscriber;
    private final StringProperty priceColumnLabel = new SimpleStringProperty();
    private final StringProperty volumeColumnLabel = new SimpleStringProperty();
    private CandleStickChart candleStickChart;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TradesChartsView(TradesChartsViewModel model, Navigation navigation, BSFormatter formatter) {
        super(model);
        this.navigation = navigation;
        this.formatter = formatter;

        itemsChangeListener = c -> updateChartData();
    }

    @Override
    public void initialize() {
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

        createChart();

        final VBox vBox = getTableBox();
        root.getChildren().addAll(currencyHBox, candleStickChart, vBox);
    }

    @Override
    protected void activate() {
        currencyComboBox.setItems(model.getTradeCurrencies());
        currencyComboBox.getSelectionModel().select(model.getTradeCurrency());
        currencyComboBox.setVisibleRowCount(Math.min(currencyComboBox.getItems().size(), 25));
        currencyComboBox.setOnAction(e -> {
            TradeCurrency tradeCurrency = currencyComboBox.getSelectionModel().getSelectedItem();
            model.onSetTradeCurrency(tradeCurrency);
            updateChartData();
        });

        model.items.addListener(itemsChangeListener);
        tradeCurrencySubscriber = EasyBind.subscribe(model.tradeCurrency,
                tradeCurrency -> {
                    String code = tradeCurrency.getCode();
                    String tradeCurrencyName = tradeCurrency.getName();
                    //lineChart.setTitle("Trade history for " + tradeCurrencyName);
                    series.setName(tradeCurrencyName);

                    priceColumnLabel.set("Price (" + formatter.getCurrencyPair(code) + ")");
                    volumeColumnLabel.set("Volume (" + code + ")");
                    yAxis.setLabel(priceColumnLabel.get());
                    // xAxis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(xAxis, "", ""));
                });

        tableView.setItems(model.tradeStatistics);
        updateChartData();
    }

    @Override
    protected void deactivate() {
        model.items.removeListener(itemsChangeListener);
        tradeCurrencySubscriber.unsubscribe();
        currencyComboBox.setOnAction(null);
    }


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


    private void createChart() {
        xAxis = new NumberAxis(0, model.upperBound + 1, 1);
        xAxis.setTickUnit(1);
        //  final double lowerBound = (double) model.getTimeInterval(0, model.tickUnit);
        //xAxis.setLowerBound(lowerBound);
        //final long minWith = (long) root.getWidth() / 20;
        //final double upperBound = (double) minWith;
        // xAxis.setUpperBound(upperBound);
        xAxis.setMinorTickCount(0);
        xAxis.setForceZeroInRange(false);
        //xAxis.setAutoRanging(true);
        xAxis.setLabel("Date/Time");
        xAxis.setTickLabelFormatter(new StringConverter<Number>() {
            @Override
            public String toString(Number object) {
                // comes as double
                long index = new Double((double) object).longValue();
                // int pos = model.upperBound - index;
                // model.getTickFromTime(new Date().getTime(), model.tickUnit);

                //final long time1 = model.getTickFromTime(e.tradeDateAsTime, model.tickUnit);
                final long now = model.getTickFromTime(new Date().getTime(), model.tickUnit);
                // long index11 = model.upperBound - (now - time);

                final long tick = now - (model.upperBound - index);
                final long time = model.getTimeFromTick(tick, model.tickUnit);
                if (model.tickUnit.ordinal() <= TradesChartsViewModel.TickUnit.DAY.ordinal())
                    return index % 7 == 0 ? formatter.formatDate(new Date(time)) : "";
                else
                    return index % 4 == 0 ? formatter.formatTime(new Date(time)) : "";
            }

            @Override
            public Number fromString(String string) {
                return null;
            }
        });

        yAxis = new NumberAxis();
        yAxis.setForceZeroInRange(false);
        yAxis.setAutoRanging(true);
        yAxis.setLabel(priceColumnLabel.get());
        yAxis.setTickLabelFormatter(getStringConverter());
        series = new XYChart.Series<>();

        candleStickChart = new CandleStickChart(xAxis, yAxis);
        candleStickChart.setData(FXCollections.observableArrayList(series));
        //candleStickChart.setAnimated(false);
        candleStickChart.setId("charts");
        candleStickChart.setMinHeight(300);
        candleStickChart.setPadding(new Insets(0, 30, 10, 0));
        candleStickChart.setToolTipStringConverter(getStringConverter());
    }

    @NotNull
    private StringConverter<Number> getStringConverter() {
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

    private void updateChartData() {
        series.getData().clear();
        series.getData().addAll(model.getItems());

        // xAxis.setLowerBound((double) model.getTimeInterval(0, model.tickUnit));
        //xAxis.setUpperBound((double) model.getTimeInterval((long) root.getWidth() / 20, model.tickUnit));

    }
}
