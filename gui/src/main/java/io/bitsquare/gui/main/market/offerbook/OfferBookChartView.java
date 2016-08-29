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
import io.bitsquare.gui.util.CurrencyListItem;
import io.bitsquare.gui.util.GUIUtil;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.trade.offer.Offer;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
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
import java.util.Collections;

@FxmlView
public class OfferBookChartView extends ActivatableViewAndModel<VBox, OfferBookChartViewModel> {
    private static final Logger log = LoggerFactory.getLogger(OfferBookChartView.class);

    private NumberAxis xAxis, yAxis;
    private XYChart.Series seriesBuy, seriesSell;
    private final Navigation navigation;
    private final BSFormatter formatter;
    private TableView<OfferListItem> buyOfferTableView;
    private TableView<OfferListItem> sellOfferTableView;
    private AreaChart<Number, Number> areaChart;
    private ComboBox<CurrencyListItem> currencyComboBox;
    private Subscription tradeCurrencySubscriber;
    private final StringProperty volumeColumnLabel = new SimpleStringProperty();
    private final StringProperty priceColumnLabel = new SimpleStringProperty();
    private Button buyOfferButton;
    private Button sellOfferButton;
    private ChangeListener<Number> selectedTabIndexListener;
    private SingleSelectionModel<Tab> tabPaneSelectionModel;
    private Label buyOfferHeaderLabel, sellOfferHeaderLabel;
    private ChangeListener<OfferListItem> sellTableRowSelectionListener, buyTableRowSelectionListener;
    private HBox bottomHBox;
    private ListChangeListener<OfferBookListItem> changeListener;
    private ListChangeListener<CurrencyListItem> currencyListItemsListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OfferBookChartView(OfferBookChartViewModel model, Navigation navigation, BSFormatter formatter) {
        super(model);
        this.navigation = navigation;
        this.formatter = formatter;
    }

    @Override
    public void initialize() {
        changeListener = c -> updateChartData();

        currencyListItemsListener = c -> {
            if (model.getSelectedCurrencyListItem().isPresent())
                currencyComboBox.getSelectionModel().select(model.getSelectedCurrencyListItem().get());
        };

        currencyComboBox = new ComboBox<>();
        currencyComboBox.setPromptText("Select currency");
        currencyComboBox.setConverter(GUIUtil.getCurrencyListItemConverter("offers", model.preferences));

        Label currencyLabel = new Label("Currency:");
        HBox currencyHBox = new HBox();
        currencyHBox.setSpacing(5);
        currencyHBox.setPadding(new Insets(10, -20, -10, 20));
        currencyHBox.setAlignment(Pos.CENTER_LEFT);
        currencyHBox.getChildren().addAll(currencyLabel, currencyComboBox);

        createChart();

        Tuple4<TableView<OfferListItem>, VBox, Button, Label> tupleBuy = getOfferTable(Offer.Direction.BUY);
        Tuple4<TableView<OfferListItem>, VBox, Button, Label> tupleSell = getOfferTable(Offer.Direction.SELL);
        buyOfferTableView = tupleBuy.first;
        sellOfferTableView = tupleSell.first;

        buyOfferButton = tupleBuy.third;
        sellOfferButton = tupleSell.third;

        buyOfferHeaderLabel = tupleBuy.forth;
        sellOfferHeaderLabel = tupleSell.forth;

        bottomHBox = new HBox();
        bottomHBox.setSpacing(30);
        bottomHBox.setAlignment(Pos.CENTER);
        tupleBuy.second.setUserData("BUY");
        tupleSell.second.setUserData("SELL");
        bottomHBox.getChildren().addAll(tupleBuy.second, tupleSell.second);

        root.getChildren().addAll(currencyHBox, areaChart, bottomHBox);
    }

    @Override
    protected void activate() {
        // root.getParent() is null at initialize
        tabPaneSelectionModel = ((TabPane) root.getParent().getParent()).getSelectionModel();
        selectedTabIndexListener = (observable, oldValue, newValue) -> model.setSelectedTabIndex((int) newValue);

        model.setSelectedTabIndex(tabPaneSelectionModel.getSelectedIndex());
        tabPaneSelectionModel.selectedIndexProperty().addListener(selectedTabIndexListener);

        currencyComboBox.setItems(model.getCurrencyListItems());
        currencyComboBox.setVisibleRowCount(25);

        if (model.getSelectedCurrencyListItem().isPresent())
            currencyComboBox.getSelectionModel().select(model.getSelectedCurrencyListItem().get());

        currencyComboBox.setOnAction(e -> {
            CurrencyListItem selectedItem = currencyComboBox.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                model.onSetTradeCurrency(selectedItem.tradeCurrency);
                updateChartData();
            }
        });

        model.currencyListItems.addListener(currencyListItemsListener);

        model.getOfferBookListItems().addListener(changeListener);
        tradeCurrencySubscriber = EasyBind.subscribe(model.selectedTradeCurrencyProperty,
                tradeCurrency -> {
                    String code = tradeCurrency.getCode();
                    areaChart.setTitle("Offer book for " + formatter.getCurrencyNameAndCurrencyPair(code));
                    volumeColumnLabel.set("Amount in " + code);
                    xAxis.setTickLabelFormatter(new StringConverter<Number>() {
                        @Override
                        public String toString(Number object) {
                            final double doubleValue = (double) object;
                            if (CurrencyUtil.isCryptoCurrency(model.getCurrencyCode())) {
                                final String withPrecision4 = formatter.formatRoundedDoubleWithPrecision(doubleValue, 4);
                                if (withPrecision4.equals("0.0000"))
                                    return formatter.formatRoundedDoubleWithPrecision(doubleValue, 8);
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
                        if (bottomHBox.getChildren().size() == 2 && bottomHBox.getChildren().get(0).getUserData().equals("BUY")) {
                            bottomHBox.getChildren().get(0).toFront();
                            reverseTableColumns();
                        }

                        buyOfferHeaderLabel.setText("Offers to sell " + code + " for BTC");
                        buyOfferButton.setText("I want to buy " + code + " (sell BTC)");

                        sellOfferHeaderLabel.setText("Offers to buy " + code + " with BTC");
                        sellOfferButton.setText("I want to sell " + code + " (buy BTC)");

                        priceColumnLabel.set("Price in BTC");
                    } else {
                        if (bottomHBox.getChildren().size() == 2 && bottomHBox.getChildren().get(0).getUserData().equals("SELL")) {
                            bottomHBox.getChildren().get(0).toFront();
                            reverseTableColumns();
                        }
                        buyOfferHeaderLabel.setText("Offers to buy BTC with " + code);
                        buyOfferButton.setText("I want to sell BTC for " + code);

                        sellOfferHeaderLabel.setText("Offers to sell BTC for " + code);
                        sellOfferButton.setText("I want to buy BTC with " + code);

                        priceColumnLabel.set("Price in " + code);
                    }
                    xAxis.setLabel(formatter.getPriceWithCurrencyCode(code));

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
        model.currencyListItems.removeListener(currencyListItemsListener);
        tradeCurrencySubscriber.unsubscribe();
        currencyComboBox.setOnAction(null);
        buyOfferTableView.getSelectionModel().selectedItemProperty().removeListener(buyTableRowSelectionListener);
        sellOfferTableView.getSelectionModel().selectedItemProperty().removeListener(sellTableRowSelectionListener);
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

        seriesBuy = new XYChart.Series<>();
        seriesSell = new XYChart.Series<>();

        areaChart = new AreaChart<>(xAxis, yAxis);
        areaChart.setLegendVisible(false);
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

    private Tuple4<TableView<OfferListItem>, VBox, Button, Label> getOfferTable(Offer.Direction direction) {
        TableView<OfferListItem> tableView = new TableView<>();
        tableView.setMinHeight(109);
        tableView.setMinWidth(530);

        // price
        TableColumn<OfferListItem, OfferListItem> priceColumn = new TableColumn<>();
        priceColumn.textProperty().bind(priceColumnLabel);
        priceColumn.setMinWidth(130);
        priceColumn.setMaxWidth(130);
        priceColumn.setSortable(false);
        priceColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        priceColumn.setCellFactory(
                new Callback<TableColumn<OfferListItem, OfferListItem>, TableCell<OfferListItem, OfferListItem>>() {
                    @Override
                    public TableCell<OfferListItem, OfferListItem> call(TableColumn<OfferListItem, OfferListItem> column) {
                        return new TableCell<OfferListItem, OfferListItem>() {
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
                            public void updateItem(final OfferListItem offerListItem, boolean empty) {
                                super.updateItem(offerListItem, empty);
                                if (offerListItem != null && !empty) {
                                    if (offerListItem.offer.getPrice() == null) {
                                        this.offer = offerListItem.offer;
                                        model.priceFeedService.currenciesUpdateFlagProperty().addListener(listener);
                                        setText("N/A");
                                    } else {
                                        setText(formatter.formatPrice(offerListItem.offer.getPrice()));
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
        TableColumn<OfferListItem, OfferListItem> volumeColumn = new TableColumn<>();
        volumeColumn.setMinWidth(125);
        volumeColumn.setMaxWidth(125);
        volumeColumn.setSortable(false);
        volumeColumn.textProperty().bind(volumeColumnLabel);
        volumeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        volumeColumn.setCellFactory(
                new Callback<TableColumn<OfferListItem, OfferListItem>, TableCell<OfferListItem, OfferListItem>>() {
                    @Override
                    public TableCell<OfferListItem, OfferListItem> call(TableColumn<OfferListItem, OfferListItem> column) {
                        return new TableCell<OfferListItem, OfferListItem>() {
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
                            public void updateItem(final OfferListItem offerListItem, boolean empty) {
                                super.updateItem(offerListItem, empty);
                                if (offerListItem != null && !empty) {
                                    this.offer = offerListItem.offer;
                                    if (offer.getPrice() == null) {
                                        this.offer = offerListItem.offer;
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
        TableColumn<OfferListItem, OfferListItem> amountColumn = new TableColumn<>("Amount in BTC");
        amountColumn.setMinWidth(125);
        amountColumn.setMaxWidth(125);
        amountColumn.setSortable(false);
        amountColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        amountColumn.setCellFactory(
                new Callback<TableColumn<OfferListItem, OfferListItem>, TableCell<OfferListItem, OfferListItem>>() {
                    @Override
                    public TableCell<OfferListItem, OfferListItem> call(TableColumn<OfferListItem, OfferListItem> column) {
                        return new TableCell<OfferListItem, OfferListItem>() {
                            @Override
                            public void updateItem(final OfferListItem offerListItem, boolean empty) {
                                super.updateItem(offerListItem, empty);
                                if (offerListItem != null && !empty)
                                    setText(formatter.formatCoin(offerListItem.offer.getAmount()));
                                else
                                    setText("");
                            }
                        };
                    }
                });

        // accumulated
        TableColumn<OfferListItem, OfferListItem> accumulatedColumn = new TableColumn<>("Sum in BTC");
        accumulatedColumn.setMinWidth(130);
        accumulatedColumn.setSortable(false);
        accumulatedColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        accumulatedColumn.setCellFactory(
                new Callback<TableColumn<OfferListItem, OfferListItem>, TableCell<OfferListItem, OfferListItem>>() {
                    @Override
                    public TableCell<OfferListItem, OfferListItem> call(TableColumn<OfferListItem, OfferListItem> column) {
                        return new TableCell<OfferListItem, OfferListItem>() {
                            @Override
                            public void updateItem(final OfferListItem offerListItem, boolean empty) {
                                super.updateItem(offerListItem, empty);
                                if (offerListItem != null && !empty)
                                    setText(formatter.formatRoundedDoubleWithPrecision(offerListItem.accumulated, 4));
                                else
                                    setText("");
                            }
                        };
                    }
                });

        if (direction == Offer.Direction.BUY) {
            tableView.getColumns().add(accumulatedColumn);
            tableView.getColumns().add(volumeColumn);
            tableView.getColumns().add(amountColumn);
            tableView.getColumns().add(priceColumn);
        } else {
            tableView.getColumns().add(priceColumn);
            tableView.getColumns().add(amountColumn);
            tableView.getColumns().add(volumeColumn);
            tableView.getColumns().add(accumulatedColumn);
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

    private void reverseTableColumns() {
        ObservableList<TableColumn<OfferListItem, ?>> columns = FXCollections.observableArrayList(buyOfferTableView.getColumns());
        buyOfferTableView.getColumns().clear();
        Collections.reverse(columns);
        buyOfferTableView.getColumns().addAll(columns);

        columns = FXCollections.observableArrayList(sellOfferTableView.getColumns());
        sellOfferTableView.getColumns().clear();
        Collections.reverse(columns);
        sellOfferTableView.getColumns().addAll(columns);
    }
}
