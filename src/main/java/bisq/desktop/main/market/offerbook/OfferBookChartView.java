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

package bisq.desktop.main.market.offerbook;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.ColoredDecimalPlacesWithZerosText;
import bisq.desktop.main.MainView;
import bisq.desktop.main.offer.BuyOfferView;
import bisq.desktop.main.offer.SellOfferView;
import bisq.desktop.main.offer.offerbook.OfferBookListItem;
import bisq.desktop.util.BSFormatter;
import bisq.desktop.util.CurrencyListItem;
import bisq.desktop.util.GUIUtil;

import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;

import bisq.common.UserThread;
import bisq.common.locale.CurrencyUtil;
import bisq.common.locale.Res;
import bisq.common.util.Tuple4;

import javax.inject.Inject;

import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;
import javafx.geometry.Pos;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import javafx.util.Callback;
import javafx.util.StringConverter;

import java.util.Collections;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bisq.desktop.util.Layout.INITIAL_SCENE_HEIGHT;

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
    private ChangeListener<Number> bisqWindowVerticalSizeListener;
    private final double initialOfferTableViewHeight = 109;
    private final double pixelsPerOfferTableRow = (initialOfferTableViewHeight / 4.0) + 10.0; // initial visible row count=4
    private final Function<Double, Double> offerTableViewHeight = (screenSize) -> {
        int extraRows = screenSize <= INITIAL_SCENE_HEIGHT ? 0 : (int) ((screenSize - INITIAL_SCENE_HEIGHT) / pixelsPerOfferTableRow);
        return extraRows == 0 ? initialOfferTableViewHeight : Math.ceil(initialOfferTableViewHeight + (extraRows * pixelsPerOfferTableRow));
    };

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
        currencyComboBox.setConverter(GUIUtil.getCurrencyListItemConverter(Res.get("shared.oneOffer"),
                Res.get("shared.multipleOffers"),
                model.preferences));

        Label currencyLabel = new AutoTooltipLabel(Res.getWithCol("shared.currency"));
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

                        leftHeaderLabel.setText(Res.get("market.offerBook.buyOffersHeaderLabel", code));
                        leftButton.setText(Res.get("market.offerBook.buyAltcoin", code, Res.getBaseCurrencyCode()));

                        rightHeaderLabel.setText(Res.get("market.offerBook.sellOffersHeaderLabel", code));
                        rightButton.setText(Res.get("market.offerBook.sellAltcoin", code, Res.getBaseCurrencyCode()));

                        priceColumnLabel.set(Res.get("shared.priceWithCur", Res.getBaseCurrencyCode()));
                    } else {
                        if (bottomHBox.getChildren().size() == 2 && bottomHBox.getChildren().get(0).getUserData().equals(OfferPayload.Direction.SELL.name())) {
                            bottomHBox.getChildren().get(0).toFront();
                            reverseTableColumns();
                        }

                        leftHeaderLabel.setText(Res.get("market.offerBook.sellOffersHeaderLabel", Res.getBaseCurrencyCode()));
                        leftButton.setText(Res.get("market.offerBook.sellWithFiat", Res.getBaseCurrencyCode(), code));

                        rightHeaderLabel.setText(Res.get("market.offerBook.buyOffersHeaderLabel", Res.getBaseCurrencyCode()));
                        rightButton.setText(Res.get("market.offerBook.buyWithFiat", Res.getBaseCurrencyCode(), code));

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

        bisqWindowVerticalSizeListener = (observable, oldValue, newValue) -> {
            double newTableViewHeight = offerTableViewHeight.apply(newValue.doubleValue());
            if (buyOfferTableView.getHeight() != newTableViewHeight) {
                buyOfferTableView.setMinHeight(newTableViewHeight);
                sellOfferTableView.setMinHeight(newTableViewHeight);
            }
        };
        root.getScene().heightProperty().addListener(bisqWindowVerticalSizeListener);

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
        tableView.setMinHeight(initialOfferTableViewHeight);
        tableView.setPrefHeight(121);
        tableView.setMinWidth(480);
        tableView.getStyleClass().add("offer-table");

        // price
        TableColumn<OfferListItem, OfferListItem> priceColumn = new TableColumn<>();
        priceColumn.textProperty().bind(priceColumnLabel);
        priceColumn.setMinWidth(115);
        priceColumn.setMaxWidth(115);
        priceColumn.setSortable(false);
        priceColumn.getStyleClass().add("number-column");
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
                                        setText("");
                                        setGraphic(new ColoredDecimalPlacesWithZerosText(model.getPrice(offer),
                                                model.getZeroDecimalsForPrice(offer)));
                                        model.priceFeedService.updateCounterProperty().removeListener(listener);
                                    }
                                }
                            };

                            @Override
                            public void updateItem(final OfferListItem offerListItem, boolean empty) {
                                super.updateItem(offerListItem, empty);
                                if (offerListItem != null && !empty) {

                                    final Offer offer = offerListItem.offer;
                                    if (offer.getPrice() == null) {
                                        this.offer = offer;
                                        model.priceFeedService.updateCounterProperty().addListener(listener);
                                        setText(Res.get("shared.na"));
                                    } else {
                                        setGraphic(new ColoredDecimalPlacesWithZerosText(model.getPrice(offer),
                                                model.getZeroDecimalsForPrice(offer)));
                                    }
                                } else {
                                    if (listener != null)
                                        model.priceFeedService.updateCounterProperty().removeListener(listener);
                                    this.offer = null;
                                    setText("");
                                    setGraphic(null);
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
        volumeColumn.getStyleClass().add("number-column");
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
                                        setText("");
                                        setGraphic(new ColoredDecimalPlacesWithZerosText(model.getVolume(offer),
                                                model.getMaxNumberOfPriceZeroDecimalsToColorize(offer)));
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
                                        setText("");
                                        setGraphic(new ColoredDecimalPlacesWithZerosText(model.getVolume(offer),
                                                model.getMaxNumberOfPriceZeroDecimalsToColorize(offer)));
                                    }
                                } else {
                                    if (listener != null)
                                        model.priceFeedService.updateCounterProperty().removeListener(listener);
                                    this.offer = null;
                                    setText("");
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });

        // amount
        TableColumn<OfferListItem, OfferListItem> amountColumn = new AutoTooltipTableColumn<>(Res.get("shared.amountWithCur", Res.getBaseCurrencyCode()));
        amountColumn.setMinWidth(115);
        amountColumn.setSortable(false);
        amountColumn.getStyleClass().add("number-column");
        amountColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        amountColumn.setCellFactory(
                new Callback<TableColumn<OfferListItem, OfferListItem>, TableCell<OfferListItem, OfferListItem>>() {
                    @Override
                    public TableCell<OfferListItem, OfferListItem> call(TableColumn<OfferListItem, OfferListItem> column) {
                        return new TableCell<OfferListItem, OfferListItem>() {
                            @Override
                            public void updateItem(final OfferListItem offerListItem, boolean empty) {
                                super.updateItem(offerListItem, empty);
                                if (offerListItem != null && !empty) {
                                    setGraphic(new ColoredDecimalPlacesWithZerosText(formatter.formatCoin(offerListItem.offer.getAmount(),
                                            4), GUIUtil.AMOUNT_DECIMALS_WITH_ZEROS));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });

        tableView.getColumns().add(volumeColumn);
        tableView.getColumns().add(amountColumn);
        tableView.getColumns().add(priceColumn);

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Label placeholder = new AutoTooltipLabel(Res.get("table.placeholder.noItems", Res.get("shared.multipleOffers")));
        placeholder.setWrapText(true);
        tableView.setPlaceholder(placeholder);

        Label titleLabel = new AutoTooltipLabel();
        titleLabel.getStyleClass().add("table-title");
        UserThread.execute(() -> titleLabel.prefWidthProperty().bind(tableView.widthProperty()));

        boolean isSellOffer = direction == OfferPayload.Direction.SELL;
        Button button = new AutoTooltipButton();
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
        vBox.setVgrow(tableView, Priority.ALWAYS);
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
