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

package io.bitsquare.gui.main.market.offerbook;

import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Tuple4;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.offer.BuyOfferView;
import io.bitsquare.gui.main.offer.SellOfferView;
import io.bitsquare.gui.main.offer.offerbook.OfferBookListItem;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.GUIUtil;
import io.bitsquare.locale.BSResources;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.trade.offer.Offer;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

@FxmlView
public class OfferBookChartView extends ActivatableViewAndModel<VBox, OfferBookChartViewModel> {
    private static final Logger log = LoggerFactory.getLogger(OfferBookChartView.class);

    private NumberAxis xAxis, yAxis;
    XYChart.Series seriesBuy, seriesSell;
    private final ListChangeListener<OfferBookListItem> changeListener;
    private final Navigation navigation;
    private final BSFormatter formatter;
    private TableView<Offer> buyOfferTableView;
    private TableView<Offer> sellOfferTableView;
    private AreaChart<Number, Number> areaChart;
    private ComboBox<TradeCurrency> currencyComboBox;
    private Subscription tradeCurrencySubscriber;
    private final StringProperty volumeColumnLabel = new SimpleStringProperty();
    private final StringProperty priceColumnLabel = new SimpleStringProperty();
    private Button buyOfferButton;
    private Button sellOfferButton;
    private ChangeListener<Number> selectedTabIndexListener;
    private SingleSelectionModel<Tab> tabPaneSelectionModel;
    private Label buyOfferHeaderLabel, sellOfferHeaderLabel;
    private ChangeListener<Offer> sellTableRowSelectionListener, buyTableRowSelectionListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OfferBookChartView(OfferBookChartViewModel model, Navigation navigation, BSFormatter formatter) {
        super(model);
        this.navigation = navigation;
        this.formatter = formatter;

        changeListener = c -> updateChartData();
    }

    @Override
    public void initialize() {
        currencyComboBox = new ComboBox<>();
        currencyComboBox.setPromptText("Select currency");
        currencyComboBox.setConverter(GUIUtil.getCurrencyListConverter());

        Label currencyLabel = new Label("Currency:");
        HBox currencyHBox = new HBox();
        currencyHBox.setSpacing(5);
        currencyHBox.setPadding(new Insets(10, -20, -20, 20));
        currencyHBox.setAlignment(Pos.CENTER_LEFT);
        currencyHBox.getChildren().addAll(currencyLabel, currencyComboBox);

        createChart();

        Tuple4<TableView<Offer>, VBox, Button, Label> tupleBuy = getOfferTable(Offer.Direction.BUY);
        Tuple4<TableView<Offer>, VBox, Button, Label> tupleSell = getOfferTable(Offer.Direction.SELL);
        buyOfferTableView = tupleBuy.first;
        sellOfferTableView = tupleSell.first;

        buyOfferButton = tupleBuy.third;
        sellOfferButton = tupleSell.third;

        buyOfferHeaderLabel = tupleBuy.forth;
        sellOfferHeaderLabel = tupleSell.forth;

        HBox hBox = new HBox();
        hBox.setSpacing(30);
        hBox.setAlignment(Pos.CENTER);
        hBox.getChildren().addAll(tupleBuy.second, tupleSell.second);

        root.getChildren().addAll(currencyHBox, areaChart, hBox);
    }

    @Override
    protected void activate() {
        // root.getParent() is null at initialize
        tabPaneSelectionModel = ((TabPane) root.getParent().getParent()).getSelectionModel();
        selectedTabIndexListener = (observable, oldValue, newValue) -> model.setSelectedTabIndex((int) newValue);

        model.setSelectedTabIndex(tabPaneSelectionModel.getSelectedIndex());
        tabPaneSelectionModel.selectedIndexProperty().addListener(selectedTabIndexListener);

        currencyComboBox.setItems(model.getTradeCurrencies());
        currencyComboBox.getSelectionModel().select(model.getSelectedTradeCurrencyProperty());
        currencyComboBox.setVisibleRowCount(Math.min(currencyComboBox.getItems().size(), 25));
        currencyComboBox.setOnAction(e -> {
            TradeCurrency tradeCurrency = currencyComboBox.getSelectionModel().getSelectedItem();
            model.onSetTradeCurrency(tradeCurrency);
            updateChartData();
        });

        model.getOfferBookListItems().addListener(changeListener);
        tradeCurrencySubscriber = EasyBind.subscribe(model.selectedTradeCurrencyProperty,
                tradeCurrency -> {
                    String code = tradeCurrency.getCode();
                    String tradeCurrencyName = tradeCurrency.getName();
                    areaChart.setTitle("Offer book for " + formatter.getCurrencyNameAndCurrencyPair(code));
                    volumeColumnLabel.set("Amount in " + code);
                    xAxis.setLabel(formatter.getPriceWithCurrencyCode(code));
                    xAxis.setTickLabelFormatter(new StringConverter<Number>() {
                        @Override
                        public String toString(Number object) {
                            final double doubleValue = (double) object;
                            if (CurrencyUtil.isCryptoCurrency(model.getCurrencyCode())) {
                                final double value = doubleValue != 0 ? 1d / doubleValue : 0;
                                final String withPrecision4 = formatter.formatRoundedDoubleWithPrecision(value, 4);
                                if (withPrecision4.equals("0.0000"))
                                    return formatter.formatRoundedDoubleWithPrecision(value, 8);
                                else
                                    return withPrecision4;
                            } else {
                                return formatter.formatRoundedDoubleWithPrecision(doubleValue, 2);
                            }
                        }

                        @Override
                        public Number fromString(String string) {
                            return null;
                        }
                    });

                    if (CurrencyUtil.isCryptoCurrency(code)) {
                        buyOfferHeaderLabel.setText("Offers to sell " + code + " for BTC");
                        buyOfferButton.setText("I want to buy " + code + " (sell BTC)");

                        sellOfferHeaderLabel.setText("Offers to buy " + code + " for BTC");
                        sellOfferButton.setText("I want to sell " + code + " (buy BTC)");

                        priceColumnLabel.set("Price in BTC");
                    } else {
                        buyOfferHeaderLabel.setText("Offers to buy BTC for " + code);
                        buyOfferButton.setText("I want to sell BTC for " + code);

                        sellOfferHeaderLabel.setText("Offers to sell BTC for " + code);
                        sellOfferButton.setText("I want to buy BTC for " + code);

                        priceColumnLabel.set("Price in " + code);
                    }
                    seriesBuy.setName(buyOfferHeaderLabel.getText() + "   ");
                    seriesSell.setName(sellOfferHeaderLabel.getText());
                });

        buyOfferTableView.setItems(model.getTopBuyOfferList());
        sellOfferTableView.setItems(model.getTopSellOfferList());
        buyTableRowSelectionListener = (observable, oldValue, newValue) -> {
            model.preferences.setSellScreenCurrencyCode(model.getCurrencyCode());
            navigation.navigateTo(MainView.class, SellOfferView.class);
        };
        sellTableRowSelectionListener = (observable, oldValue, newValue) -> {
            model.preferences.setBuyScreenCurrencyCode(model.getCurrencyCode());
            navigation.navigateTo(MainView.class, BuyOfferView.class);
        };
        buyOfferTableView.getSelectionModel().selectedItemProperty().addListener(buyTableRowSelectionListener);
        sellOfferTableView.getSelectionModel().selectedItemProperty().addListener(sellTableRowSelectionListener);

        updateChartData();
    }

    @Override
    protected void deactivate() {
        model.getOfferBookListItems().removeListener(changeListener);
        tabPaneSelectionModel.selectedIndexProperty().removeListener(selectedTabIndexListener);
        tradeCurrencySubscriber.unsubscribe();
        currencyComboBox.setOnAction(null);
        buyOfferTableView.getSelectionModel().selectedItemProperty().removeListener(buyTableRowSelectionListener);
        sellOfferTableView.getSelectionModel().selectedItemProperty().removeListener(sellTableRowSelectionListener);
    }


    private Tuple4<TableView<Offer>, VBox, Button, Label> getOfferTable(Offer.Direction direction) {
        TableView<Offer> tableView = new TableView<>();
        tableView.setMinHeight(109);
        tableView.setMinWidth(530);

        // price
        TableColumn<Offer, Offer> priceColumn = new TableColumn<>();
        priceColumn.textProperty().bind(priceColumnLabel);
        priceColumn.setMinWidth(130);
        priceColumn.setMaxWidth(130);
        priceColumn.setSortable(false);
        priceColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        priceColumn.setCellFactory(
                new Callback<TableColumn<Offer, Offer>, TableCell<Offer, Offer>>() {
                    @Override
                    public TableCell<Offer, Offer> call(TableColumn<Offer, Offer> column) {
                        return new TableCell<Offer, Offer>() {
                            private Offer offer;
                            ChangeListener<Number> listener = new ChangeListener<Number>() {
                                @Override
                                public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                                    if (offer != null && offer.getPrice() != null) {
                                        setText(formatter.formatPrice(offer.getPrice()));
                                        model.priceFeedService.currenciesUpdateFlagProperty().removeListener(listener);
                                    }
                                }
                            };

                            @Override
                            public void updateItem(final Offer offer, boolean empty) {
                                super.updateItem(offer, empty);
                                if (offer != null && !empty) {
                                    if (offer.getPrice() == null) {
                                        this.offer = offer;
                                        model.priceFeedService.currenciesUpdateFlagProperty().addListener(listener);
                                        setText("N/A");
                                    } else {
                                        setText(formatter.formatPrice(offer.getPrice()));
                                    }
                                } else {
                                    if (listener != null)
                                        model.priceFeedService.currenciesUpdateFlagProperty().removeListener(listener);
                                    this.offer = null;
                                    setText("");
                                }
                            }
                        };
                    }
                });

        // volume
        TableColumn<Offer, Offer> volumeColumn = new TableColumn<>();
        volumeColumn.setMinWidth(125);
        volumeColumn.setMaxWidth(125);
        volumeColumn.setSortable(false);
        volumeColumn.textProperty().bind(volumeColumnLabel);
        volumeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        volumeColumn.setCellFactory(
                new Callback<TableColumn<Offer, Offer>, TableCell<Offer, Offer>>() {
                    @Override
                    public TableCell<Offer, Offer> call(TableColumn<Offer, Offer> column) {
                        return new TableCell<Offer, Offer>() {
                            private Offer offer;
                            ChangeListener<Number> listener = new ChangeListener<Number>() {
                                @Override
                                public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                                    if (offer != null && offer.getPrice() != null) {
                                        setText(formatter.formatVolume(offer.getOfferVolume()));
                                        model.priceFeedService.currenciesUpdateFlagProperty().removeListener(listener);
                                    }
                                }
                            };

                            @Override
                            public void updateItem(final Offer offer, boolean empty) {
                                super.updateItem(offer, empty);
                                if (offer != null && !empty) {
                                    if (offer.getPrice() == null) {
                                        this.offer = offer;
                                        model.priceFeedService.currenciesUpdateFlagProperty().addListener(listener);
                                        setText("N/A");
                                    } else {
                                        setText(formatter.formatVolume(offer.getOfferVolume()));
                                    }
                                } else {
                                    if (listener != null)
                                        model.priceFeedService.currenciesUpdateFlagProperty().removeListener(listener);
                                    this.offer = null;
                                    setText("");
                                }
                            }
                        };
                    }
                });

        // amount
        TableColumn<Offer, Offer> amountColumn = new TableColumn<>("Amount in BTC");
        amountColumn.setMinWidth(125);
        amountColumn.setMaxWidth(125);
        amountColumn.setSortable(false);
        amountColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        amountColumn.setCellFactory(
                new Callback<TableColumn<Offer, Offer>, TableCell<Offer, Offer>>() {
                    @Override
                    public TableCell<Offer, Offer> call(TableColumn<Offer, Offer> column) {
                        return new TableCell<Offer, Offer>() {
                            @Override
                            public void updateItem(final Offer offer, boolean empty) {
                                super.updateItem(offer, empty);
                                if (offer != null && !empty)
                                    setText(formatter.formatCoin(offer.getAmount()));
                                else
                                    setText("");
                            }
                        };
                    }
                });

        // payment method
        TableColumn<Offer, Offer> paymentMethodColumn = new TableColumn<>("Payment method");
        paymentMethodColumn.setMinWidth(130);
        paymentMethodColumn.setSortable(false);
        paymentMethodColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        paymentMethodColumn.setCellFactory(
                new Callback<TableColumn<Offer, Offer>, TableCell<Offer, Offer>>() {
                    @Override
                    public TableCell<Offer, Offer> call(TableColumn<Offer, Offer> column) {
                        return new TableCell<Offer, Offer>() {
                            @Override
                            public void updateItem(final Offer offer, boolean empty) {
                                super.updateItem(offer, empty);
                                if (offer != null && !empty)
                                    setText(BSResources.get(offer.getPaymentMethod().getId() + "_SHORT"));
                                else
                                    setText("");
                            }
                        };
                    }
                });

        if (direction == Offer.Direction.BUY) {
            tableView.getColumns().add(paymentMethodColumn);
            tableView.getColumns().add(volumeColumn);
            tableView.getColumns().add(amountColumn);
            tableView.getColumns().add(priceColumn);
        } else {
            tableView.getColumns().add(priceColumn);
            tableView.getColumns().add(amountColumn);
            tableView.getColumns().add(volumeColumn);
            tableView.getColumns().add(paymentMethodColumn);
        }

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Label placeholder = new Label("Currently there are no offers available");
        placeholder.setWrapText(true);
        tableView.setPlaceholder(placeholder);

        Label titleLabel = new Label();
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16; -fx-alignment: center");
        UserThread.execute(() -> titleLabel.prefWidthProperty().bind(tableView.widthProperty()));

        boolean isSellOffer = direction == Offer.Direction.SELL;
        Button button = new Button();
        ImageView iconView = new ImageView();
        iconView.setId(isSellOffer ? "image-buy-white" : "image-sell-white");
        button.setGraphic(iconView);
        button.setGraphicTextGap(10);
        button.setText(isSellOffer ? "I want to buy bitcoin" : "I want to sell bitcoin");
        button.setMinHeight(40);
        button.setId(isSellOffer ? "buy-button-big" : "sell-button-big");
        button.setOnAction(e -> {
            if (isSellOffer) {
                model.preferences.setBuyScreenCurrencyCode(model.getCurrencyCode());
                navigation.navigateTo(MainView.class, BuyOfferView.class);
            } else {
                model.preferences.setSellScreenCurrencyCode(model.getCurrencyCode());
                navigation.navigateTo(MainView.class, SellOfferView.class);
            }
        });

        VBox vBox = new VBox();
        vBox.setSpacing(10);
        vBox.setFillWidth(true);
        vBox.setMinHeight(190);
        vBox.getChildren().addAll(titleLabel, tableView, button);

        button.prefWidthProperty().bind(vBox.widthProperty());
        return new Tuple4<>(tableView, vBox, button, titleLabel);
    }

    private void createChart() {
        xAxis = new NumberAxis();
        xAxis.setForceZeroInRange(false);
        xAxis.setAutoRanging(true);

        yAxis = new NumberAxis();
        yAxis.setForceZeroInRange(false);
        yAxis.setAutoRanging(true);
        yAxis.setLabel("Amount in BTC");
        yAxis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(yAxis, "", ""));

        seriesBuy = new XYChart.Series();
        seriesSell = new XYChart.Series();

        areaChart = new AreaChart<>(xAxis, yAxis);
        areaChart.setAnimated(false);
        areaChart.setId("charts");
        areaChart.setMinHeight(300);
        areaChart.setPadding(new Insets(0, 30, 0, 0));
        areaChart.getData().addAll(seriesBuy, seriesSell);
    }

    private void updateChartData() {
        seriesBuy.getData().clear();
        seriesSell.getData().clear();

        seriesBuy.getData().addAll(model.getBuyData());
        seriesSell.getData().addAll(model.getSellData());
    }
}
