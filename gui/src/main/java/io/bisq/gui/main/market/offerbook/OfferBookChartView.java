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

package io.bisq.gui.main.market.offerbook;

import io.bisq.common.UserThread;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.locale.Res;
import io.bisq.common.util.Tuple4;
import io.bisq.core.offer.Offer;
import io.bisq.core.offer.OfferPayload;
import io.bisq.gui.Navigation;
import io.bisq.gui.common.view.ActivatableViewAndModel;
import io.bisq.gui.common.view.FxmlView;
import io.bisq.gui.main.MainView;
import io.bisq.gui.main.offer.BuyOfferView;
import io.bisq.gui.main.offer.SellOfferView;
import io.bisq.gui.main.offer.offerbook.OfferBookListItem;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.CurrencyListItem;
import io.bisq.gui.util.GUIUtil;
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
import javafx.scene.layout.Priority;
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

    private NumberAxis xAxis;
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
    private Button leftButton;
    private Button rightButton;
    private ChangeListener<Number> selectedTabIndexListener;
    private SingleSelectionModel<Tab> tabPaneSelectionModel;
    private Label leftHeaderLabel, rightHeaderLabel;
    private ChangeListener<OfferListItem> sellTableRowSelectionListener, buyTableRowSelectionListener;
    private HBox bottomHBox;
    private ListChangeListener<OfferBookListItem> changeListener;
    private ListChangeListener<CurrencyListItem> currencyListItemsListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
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
        currencyComboBox.setPromptText(Res.get("list.currency.select"));
        currencyComboBox.setConverter(GUIUtil.getCurrencyListItemConverter(Res.get("shared.offer"),
                Res.get("shared.offers"),
                model.preferences));

        Label currencyLabel = new Label(Res.getWithCol("shared.currency"));
        HBox currencyHBox = new HBox();
        currencyHBox.setSpacing(5);
        currencyHBox.setPadding(new Insets(5, -20, -5, 20));
        currencyHBox.setAlignment(Pos.CENTER_LEFT);
        currencyHBox.getChildren().addAll(currencyLabel, currencyComboBox);

        createChart();

        Tuple4<TableView<OfferListItem>, VBox, Button, Label> tupleBuy = getOfferTable(OfferPayload.Direction.BUY);
        Tuple4<TableView<OfferListItem>, VBox, Button, Label> tupleSell = getOfferTable(OfferPayload.Direction.SELL);
        buyOfferTableView = tupleBuy.first;
        sellOfferTableView = tupleSell.first;

        leftButton = tupleBuy.third;
        rightButton = tupleSell.third;

        leftHeaderLabel = tupleBuy.forth;
        rightHeaderLabel = tupleSell.forth;

        bottomHBox = new HBox();
        bottomHBox.setSpacing(20); //30
        bottomHBox.setAlignment(Pos.CENTER);
        HBox.setHgrow(tupleBuy.second, Priority.ALWAYS);
        HBox.setHgrow(tupleSell.second, Priority.ALWAYS);
        tupleBuy.second.setUserData(OfferPayload.Direction.BUY.name());
        tupleSell.second.setUserData(OfferPayload.Direction.SELL.name());
        bottomHBox.getChildren().addAll(tupleBuy.second, tupleSell.second);

        root.getChildren().addAll(currencyHBox, areaChart, bottomHBox);
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
                    areaChart.setTitle(Res.get("market.offerBook.chart.title", formatter.getCurrencyNameAndCurrencyPair(code)));
                    volumeColumnLabel.set(Res.get("shared.amountWithCur", code));
                    xAxis.setTickLabelFormatter(new StringConverter<Number>() {
                        @Override
                        public String toString(Number object) {
                            final double doubleValue = (double) object;
                            if (CurrencyUtil.isCryptoCurrency(model.getCurrencyCode())) {
                                final String withPrecision3 = formatter.formatRoundedDoubleWithPrecision(doubleValue, 3);
                                if (withPrecision3.equals("0.000"))
                                    return formatter.formatRoundedDoubleWithPrecision(doubleValue, 8);
                                else
                                    return withPrecision3;
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
                        if (bottomHBox.getChildren().size() == 2 && bottomHBox.getChildren().get(0).getUserData().equals(OfferPayload.Direction.BUY.name())) {
                            bottomHBox.getChildren().get(0).toFront();
                            reverseTableColumns();
                        }

                        leftHeaderLabel.setText(Res.get("market.offerBook.leftHeaderLabel", code, Res.getBaseCurrencyCode()));
                        leftButton.setText(Res.get("market.offerBook.leftButtonAltcoin", code, Res.getBaseCurrencyCode()));

                        rightHeaderLabel.setText(Res.get("market.offerBook.rightHeaderLabel", code, Res.getBaseCurrencyCode()));
                        rightButton.setText(Res.get("market.offerBook.rightButtonAltcoin", code, Res.getBaseCurrencyCode()));

                        priceColumnLabel.set(Res.get("shared.priceWithCur", Res.getBaseCurrencyCode()));
                    } else {
                        if (bottomHBox.getChildren().size() == 2 && bottomHBox.getChildren().get(0).getUserData().equals(OfferPayload.Direction.SELL.name())) {
                            bottomHBox.getChildren().get(0).toFront();
                            reverseTableColumns();
                        }

                        leftHeaderLabel.setText(Res.get("market.offerBook.rightHeaderLabel", Res.getBaseCurrencyCode(), code));
                        leftButton.setText(Res.get("market.offerBook.rightButtonFiat", Res.getBaseCurrencyCode(), code));

                        rightHeaderLabel.setText(Res.get("market.offerBook.leftHeaderLabel", Res.getBaseCurrencyCode(), code));
                        rightButton.setText(Res.get("market.offerBook.leftButtonFiat", Res.getBaseCurrencyCode(), code));

                        priceColumnLabel.set(Res.get("shared.priceWithCur", code));
                    }
                    xAxis.setLabel(formatter.getPriceWithCurrencyCode(code));

                    seriesBuy.setName(leftHeaderLabel.getText() + "   ");
                    seriesSell.setName(rightHeaderLabel.getText());
                });

        buyOfferTableView.setItems(model.getTopBuyOfferList());
        sellOfferTableView.setItems(model.getTopSellOfferList());
        buyTableRowSelectionListener = (observable, oldValue, newValue) -> {
            model.preferences.setSellScreenCurrencyCode(model.getCurrencyCode());
            //noinspection unchecked
            navigation.navigateTo(MainView.class, SellOfferView.class);
        };
        sellTableRowSelectionListener = (observable, oldValue, newValue) -> {
            model.preferences.setBuyScreenCurrencyCode(model.getCurrencyCode());
            //noinspection unchecked
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

        NumberAxis yAxis = new NumberAxis();
        yAxis.setForceZeroInRange(false);
        yAxis.setAutoRanging(true);
        yAxis.setLabel(Res.get("shared.amountWithCur", Res.getBaseCurrencyCode()));
        yAxis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(yAxis, "", ""));

        seriesBuy = new XYChart.Series<>();
        seriesSell = new XYChart.Series<>();

        areaChart = new AreaChart<>(xAxis, yAxis);
        areaChart.setLegendVisible(false);
        areaChart.setAnimated(false);
        areaChart.setId("charts");
        areaChart.setMinHeight(300);
        areaChart.setPrefHeight(300);
        areaChart.setPadding(new Insets(0, 30, 0, 0));
        areaChart.getData().addAll(seriesBuy, seriesSell);
    }

    private void updateChartData() {
        seriesBuy.getData().clear();
        seriesSell.getData().clear();

        //noinspection unchecked
        seriesBuy.getData().addAll(model.getBuyData());
        //noinspection unchecked
        seriesSell.getData().addAll(model.getSellData());
    }

    private Tuple4<TableView<OfferListItem>, VBox, Button, Label> getOfferTable(OfferPayload.Direction direction) {
        TableView<OfferListItem> tableView = new TableView<>();
        tableView.setMinHeight(109);
        tableView.setPrefHeight(121);
        tableView.setMinWidth(480);

        // price
        TableColumn<OfferListItem, OfferListItem> priceColumn = new TableColumn<>();
        priceColumn.textProperty().bind(priceColumnLabel);
        priceColumn.setMinWidth(115);
        priceColumn.setMaxWidth(115);
        priceColumn.setSortable(false);
        priceColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        priceColumn.setCellFactory(
                new Callback<TableColumn<OfferListItem, OfferListItem>, TableCell<OfferListItem, OfferListItem>>() {
                    @Override
                    public TableCell<OfferListItem, OfferListItem> call(TableColumn<OfferListItem, OfferListItem> column) {
                        return new TableCell<OfferListItem, OfferListItem>() {
                            private Offer offer;
                            final ChangeListener<Number> listener = new ChangeListener<Number>() {
                                @Override
                                public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                                    if (offer != null && offer.getPrice() != null) {
                                        setText(formatter.formatPrice(offer.getPrice()));
                                        model.priceFeedService.updateCounterProperty().removeListener(listener);
                                    }
                                }
                            };

                            @Override
                            public void updateItem(final OfferListItem offerListItem, boolean empty) {
                                super.updateItem(offerListItem, empty);
                                if (offerListItem != null && !empty) {
                                    if (offerListItem.offer.getPrice() == null) {
                                        this.offer = offerListItem.offer;
                                        model.priceFeedService.updateCounterProperty().addListener(listener);
                                        setText(Res.get("shared.na"));
                                    } else {
                                        setText(formatter.formatPrice(offerListItem.offer.getPrice()));
                                    }
                                } else {
                                    if (listener != null)
                                        model.priceFeedService.updateCounterProperty().removeListener(listener);
                                    this.offer = null;
                                    setText("");
                                }
                            }
                        };
                    }
                });

        // volume
        TableColumn<OfferListItem, OfferListItem> volumeColumn = new TableColumn<>();
        volumeColumn.setMinWidth(115);
        volumeColumn.setSortable(false);
        volumeColumn.textProperty().bind(volumeColumnLabel);
        volumeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        volumeColumn.setCellFactory(
                new Callback<TableColumn<OfferListItem, OfferListItem>, TableCell<OfferListItem, OfferListItem>>() {
                    @Override
                    public TableCell<OfferListItem, OfferListItem> call(TableColumn<OfferListItem, OfferListItem> column) {
                        return new TableCell<OfferListItem, OfferListItem>() {
                            private Offer offer;
                            final ChangeListener<Number> listener = new ChangeListener<Number>() {
                                @Override
                                public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                                    if (offer != null && offer.getPrice() != null) {
                                        setText(formatter.formatVolume(offer.getVolume()));
                                        model.priceFeedService.updateCounterProperty().removeListener(listener);
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
                                        model.priceFeedService.updateCounterProperty().addListener(listener);
                                        setText(Res.get("shared.na"));
                                    } else {
                                        setText(formatter.formatVolume(offer.getVolume()));
                                    }
                                } else {
                                    if (listener != null)
                                        model.priceFeedService.updateCounterProperty().removeListener(listener);
                                    this.offer = null;
                                    setText("");
                                }
                            }
                        };
                    }
                });

        // amount
        TableColumn<OfferListItem, OfferListItem> amountColumn = new TableColumn<>(Res.get("shared.amountWithCur", Res.getBaseCurrencyCode()));
        amountColumn.setMinWidth(115);
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

        // Lets remove that as it is not really relevant and seems to be confusing to some users
        // accumulated
       /* TableColumn<OfferListItem, OfferListItem> accumulatedColumn = new TableColumn<>(Res.get("shared.sumWithCur", Res.getBaseCurrencyCode()));
        accumulatedColumn.setMinWidth(100);
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
*/
        if (direction == OfferPayload.Direction.BUY) {
            // tableView.getColumns().add(accumulatedColumn);
            tableView.getColumns().add(volumeColumn);
            tableView.getColumns().add(amountColumn);
            tableView.getColumns().add(priceColumn);
        } else {
            tableView.getColumns().add(priceColumn);
            tableView.getColumns().add(amountColumn);
            tableView.getColumns().add(volumeColumn);
            //tableView.getColumns().add(accumulatedColumn);
        }

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Label placeholder = new Label(Res.get("table.placeholder.noItems", Res.get("shared.offers")));
        placeholder.setWrapText(true);
        tableView.setPlaceholder(placeholder);

        Label titleLabel = new Label();
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16; -fx-alignment: center");
        UserThread.execute(() -> titleLabel.prefWidthProperty().bind(tableView.widthProperty()));

        boolean isSellOffer = direction == OfferPayload.Direction.SELL;
        Button button = new Button();
        ImageView iconView = new ImageView();
        iconView.setId(isSellOffer ? "image-buy-white" : "image-sell-white");
        button.setGraphic(iconView);
        button.setGraphicTextGap(10);
        button.setText(isSellOffer ? Res.get("market.offerBook.buy") : Res.get("market.offerBook.sell"));
        button.setMinHeight(40);
        button.setId(isSellOffer ? "buy-button-big" : "sell-button-big");
        button.setOnAction(e -> {
            if (isSellOffer) {
                model.preferences.setBuyScreenCurrencyCode(model.getCurrencyCode());
                //noinspection unchecked
                navigation.navigateTo(MainView.class, BuyOfferView.class);
            } else {
                model.preferences.setSellScreenCurrencyCode(model.getCurrencyCode());
                //noinspection unchecked
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
