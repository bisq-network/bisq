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

package io.bitsquare.gui.main.markets.charts;

import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.main.markets.statistics.MarketStatisticItem;
import io.bitsquare.gui.main.offer.offerbook.OfferBookListItem;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.trade.offer.Offer;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javax.inject.Inject;

@FxmlView
public class MarketsChartsView extends ActivatableViewAndModel<VBox, MarketsChartsViewModel> {

    private NumberAxis xAxis, yAxis;
    XYChart.Series seriesBuy, seriesSell;
    private ListChangeListener<OfferBookListItem> changeListener;
    private BSFormatter formatter;
    private TableView<Offer> buyOfferTableView;
    private TableView<Offer> sellOfferTableView;
    private AreaChart<Number, Number> areaChart;
    private ComboBox<TradeCurrency> currencyComboBox;
    private Subscription tradeCurrencySubscriber;
    private StringProperty priceColumnLabel = new SimpleStringProperty("Price (EUR/BTC)");
    private StringProperty amountColumnLabel = new SimpleStringProperty("Amount (BTC)");
    private StringProperty volumeColumnLabel = new SimpleStringProperty("Volume (EUR)");


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MarketsChartsView(MarketsChartsViewModel model, BSFormatter formatter) {
        super(model);
        this.formatter = formatter;

        changeListener = c -> updateChartData();
    }

    @Override
    public void initialize() {
        currencyComboBox = new ComboBox<>();
        currencyComboBox.setPromptText("Select currency");
        currencyComboBox.setConverter(new StringConverter<TradeCurrency>() {
            @Override
            public String toString(TradeCurrency tradeCurrency) {
                return tradeCurrency.getCodeAndName();
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

        Tuple2<TableView<Offer>, VBox> tupleBuy = getOfferTable(Offer.Direction.BUY);
        Tuple2<TableView<Offer>, VBox> tupleSell = getOfferTable(Offer.Direction.SELL);
        buyOfferTableView = tupleBuy.first;
        sellOfferTableView = tupleSell.first;

        HBox hBox = new HBox();
        hBox.setSpacing(30);
        hBox.setAlignment(Pos.CENTER);
        hBox.getChildren().addAll(tupleBuy.second, tupleSell.second);

        root.getChildren().addAll(currencyHBox, areaChart, hBox);
    }

    @Override
    protected void activate() {
        currencyComboBox.setItems(model.getTradeCurrencies());
        currencyComboBox.getSelectionModel().select(model.getTradeCurrency());
        currencyComboBox.setVisibleRowCount(Math.min(currencyComboBox.getItems().size(), 25));
        currencyComboBox.setOnAction(e -> {
            model.onSetTradeCurrency(currencyComboBox.getSelectionModel().getSelectedItem());
            updateChartData();
        });

        model.getOfferBookListItems().addListener(changeListener);
        tradeCurrencySubscriber = EasyBind.subscribe(model.tradeCurrency,
                newValue -> {
                    String code = newValue.getCode();
                    areaChart.setTitle("Offer book for " + newValue.getName());
                    xAxis.setLabel(priceColumnLabel.get());
                    xAxis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(xAxis, "", ""));
                    priceColumnLabel.set("Price (" + code + "/BTC)");
                    volumeColumnLabel.set("Volume (" + code + ")");
                });

        buyOfferTableView.setItems(model.getBuyOfferList());
        sellOfferTableView.setItems(model.getSellOfferList());

        updateChartData();
    }

    @Override
    protected void deactivate() {
        model.getOfferBookListItems().removeListener(changeListener);
        tradeCurrencySubscriber.unsubscribe();
    }


    private Tuple2<TableView<Offer>, VBox> getOfferTable(Offer.Direction direction) {
        TableView<Offer> tableView = new TableView();

        // price
        TableColumn<Offer, Offer> priceColumn = new TableColumn<>();
        priceColumn.textProperty().bind(priceColumnLabel);
        priceColumn.setMinWidth(120);
        priceColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        priceColumn.setCellFactory(
                new Callback<TableColumn<Offer, Offer>, TableCell<Offer,
                        Offer>>() {
                    @Override
                    public TableCell<Offer, Offer> call(
                            TableColumn<Offer, Offer> column) {
                        return new TableCell<Offer, Offer>() {
                            @Override
                            public void updateItem(final Offer item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(formatter.formatFiat(item.getPrice()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        tableView.getColumns().add(priceColumn);

        // amount
        TableColumn<Offer, Offer> amountColumn = new TableColumn<>("Amount (BTC)");
        amountColumn.textProperty().bind(amountColumnLabel);
        amountColumn.setMinWidth(120);
        amountColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        amountColumn.setCellFactory(
                new Callback<TableColumn<Offer, Offer>, TableCell<Offer,
                        Offer>>() {
                    @Override
                    public TableCell<Offer, Offer> call(
                            TableColumn<Offer, Offer> column) {
                        return new TableCell<Offer, Offer>() {
                            @Override
                            public void updateItem(final Offer item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(formatter.formatCoin(item.getAmount()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        tableView.getColumns().add(amountColumn);

        // volume
        TableColumn<Offer, Offer> volumeColumn = new TableColumn<>("Amount (EUR)");
        volumeColumn.setMinWidth(120);
        volumeColumn.textProperty().bind(volumeColumnLabel);
        volumeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        volumeColumn.setCellFactory(
                new Callback<TableColumn<Offer, Offer>, TableCell<Offer,
                        Offer>>() {
                    @Override
                    public TableCell<Offer, Offer> call(
                            TableColumn<Offer, Offer> column) {
                        return new TableCell<Offer, Offer>() {
                            @Override
                            public void updateItem(final Offer item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(formatter.formatFiat(item.getOfferVolume()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        tableView.getColumns().add(volumeColumn);

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Label placeholder = new Label("Currently there are no offers available");
        placeholder.setWrapText(true);
        tableView.setPlaceholder(placeholder);

        Label titleLabel = new Label(direction.equals(Offer.Direction.BUY) ? "Offers for buy bitcoin (bid)" : "Offers for sell bitcoin (ask)");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16; -fx-alignment: center");
        UserThread.execute(() -> titleLabel.prefWidthProperty().bind(tableView.widthProperty()));

        VBox vBox = new VBox();
        vBox.setSpacing(10);
        vBox.setFillWidth(true);
        vBox.setMinHeight(150);
        vBox.getChildren().addAll(titleLabel, tableView);
        return new Tuple2<>(tableView, vBox);
    }


    private void createChart() {
        xAxis = new NumberAxis();
        xAxis.setForceZeroInRange(false);
        xAxis.setAutoRanging(true);
        xAxis.setLabel(priceColumnLabel.get());

        yAxis = new NumberAxis();
        yAxis.setForceZeroInRange(false);
        yAxis.setAutoRanging(true);
        yAxis.setLabel("Amount in BTC");
        yAxis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(yAxis, "", ""));

        seriesBuy = new XYChart.Series();
        seriesBuy.setName("Offers for  buy bitcoin  ");

        seriesSell = new XYChart.Series();
        seriesSell.setName("Offers for sell bitcoin");

        areaChart = new AreaChart<>(xAxis, yAxis);
        areaChart.setAnimated(false);
        areaChart.setId("charts");
        areaChart.setMinHeight(300);
        areaChart.setPadding(new Insets(0, 30, 10, 0));
        areaChart.getData().addAll(seriesBuy, seriesSell);
    }


    private void updateChartData() {
        seriesBuy.getData().clear();
        seriesSell.getData().clear();

        seriesBuy.getData().addAll(model.getBuyData());
        seriesSell.getData().addAll(model.getSellData());
    }


    private TableColumn<MarketStatisticItem, MarketStatisticItem> getCurrencyColumn() {
        TableColumn<MarketStatisticItem, MarketStatisticItem> column = new TableColumn<MarketStatisticItem, MarketStatisticItem>("Currency") {
            {
                setMinWidth(100);
            }
        };
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<TableColumn<MarketStatisticItem, MarketStatisticItem>, TableCell<MarketStatisticItem,
                        MarketStatisticItem>>() {
                    @Override
                    public TableCell<MarketStatisticItem, MarketStatisticItem> call(
                            TableColumn<MarketStatisticItem, MarketStatisticItem> column) {
                        return new TableCell<MarketStatisticItem, MarketStatisticItem>() {
                            @Override
                            public void updateItem(final MarketStatisticItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(CurrencyUtil.getNameByCode(item.currencyCode));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<MarketStatisticItem, MarketStatisticItem> getNumberOfOffersColumn() {
        TableColumn<MarketStatisticItem, MarketStatisticItem> column = new TableColumn<MarketStatisticItem, MarketStatisticItem>("Total offers") {
            {
                setMinWidth(100);
            }
        };
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<TableColumn<MarketStatisticItem, MarketStatisticItem>, TableCell<MarketStatisticItem,
                        MarketStatisticItem>>() {
                    @Override
                    public TableCell<MarketStatisticItem, MarketStatisticItem> call(
                            TableColumn<MarketStatisticItem, MarketStatisticItem> column) {
                        return new TableCell<MarketStatisticItem, MarketStatisticItem>() {
                            @Override
                            public void updateItem(final MarketStatisticItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(String.valueOf(item.numberOfOffers));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<MarketStatisticItem, MarketStatisticItem> getTotalAmountColumn() {
        TableColumn<MarketStatisticItem, MarketStatisticItem> column = new TableColumn<MarketStatisticItem, MarketStatisticItem>("Total amount (BTC)") {
            {
                setMinWidth(130);
            }
        };
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<TableColumn<MarketStatisticItem, MarketStatisticItem>, TableCell<MarketStatisticItem,
                        MarketStatisticItem>>() {
                    @Override
                    public TableCell<MarketStatisticItem, MarketStatisticItem> call(
                            TableColumn<MarketStatisticItem, MarketStatisticItem> column) {
                        return new TableCell<MarketStatisticItem, MarketStatisticItem>() {
                            @Override
                            public void updateItem(final MarketStatisticItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(formatter.formatCoin(item.totalAmount));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<MarketStatisticItem, MarketStatisticItem> getSpreadColumn() {
        TableColumn<MarketStatisticItem, MarketStatisticItem> column = new TableColumn<MarketStatisticItem, MarketStatisticItem>("Spread") {
            {
                setMinWidth(130);
            }
        };
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<TableColumn<MarketStatisticItem, MarketStatisticItem>, TableCell<MarketStatisticItem,
                        MarketStatisticItem>>() {
                    @Override
                    public TableCell<MarketStatisticItem, MarketStatisticItem> call(
                            TableColumn<MarketStatisticItem, MarketStatisticItem> column) {
                        return new TableCell<MarketStatisticItem, MarketStatisticItem>() {
                            @Override
                            public void updateItem(final MarketStatisticItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty && item.spread != null)
                                    setText(formatter.formatFiatWithCode(item.spread));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }


}
