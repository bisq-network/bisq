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
import bisq.desktop.components.AutocompleteComboBox;
import bisq.desktop.components.ColoredDecimalPlacesWithZerosText;
import bisq.desktop.components.PeerInfoIconSmall;
import bisq.desktop.main.MainView;
import bisq.desktop.main.offer.BuyOfferView;
import bisq.desktop.main.offer.SellOfferView;
import bisq.desktop.main.offer.offerbook.OfferBookListItem;
import bisq.desktop.util.CurrencyListItem;
import bisq.desktop.util.GUIUtil;

import bisq.core.app.AppOptionKeys;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.p2p.NodeAddress;

import bisq.common.util.Tuple3;
import bisq.common.util.Tuple4;

import javax.inject.Inject;
import javax.inject.Named;

import com.jfoenix.controls.JFXTabPane;

import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.Tab;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;

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
import java.util.List;
import java.util.function.Function;

import static bisq.desktop.util.FormBuilder.addTopLabelAutocompleteComboBox;
import static bisq.desktop.util.Layout.INITIAL_WINDOW_HEIGHT;

@FxmlView
public class OfferBookChartView extends ActivatableViewAndModel<VBox, OfferBookChartViewModel> {
    private final boolean useDevPrivilegeKeys;

    private NumberAxis xAxis;
    private XYChart.Series<Number, Number> seriesBuy, seriesSell;
    private final Navigation navigation;
    private final CoinFormatter formatter;
    private TableView<OfferListItem> buyOfferTableView;
    private TableView<OfferListItem> sellOfferTableView;
    private AreaChart<Number, Number> areaChart;
    private AnchorPane chartPane;
    private AutocompleteComboBox<CurrencyListItem> currencyComboBox;
    private Subscription tradeCurrencySubscriber;
    private final StringProperty volumeColumnLabel = new SimpleStringProperty();
    private final StringProperty priceColumnLabel = new SimpleStringProperty();
    private AutoTooltipButton leftButton;
    private AutoTooltipButton rightButton;
    private ChangeListener<Number> selectedTabIndexListener;
    private SingleSelectionModel<Tab> tabPaneSelectionModel;
    private Label leftHeaderLabel, rightHeaderLabel;
    private ChangeListener<OfferListItem> sellTableRowSelectionListener, buyTableRowSelectionListener;
    private HBox bottomHBox;
    private ListChangeListener<OfferBookListItem> changeListener;
    private ListChangeListener<CurrencyListItem> currencyListItemsListener;
    private final double initialOfferTableViewHeight = 121;
    private final double pixelsPerOfferTableRow = (initialOfferTableViewHeight - 30) / 5.0; // initial visible row count=5, header height=30
    private final Function<Double, Double> offerTableViewHeight = (screenSize) -> {
        int extraRows = screenSize <= INITIAL_WINDOW_HEIGHT ? 0 : (int) ((screenSize - INITIAL_WINDOW_HEIGHT) / pixelsPerOfferTableRow);
        return extraRows == 0 ? initialOfferTableViewHeight : Math.ceil(initialOfferTableViewHeight + ((extraRows + 1) * pixelsPerOfferTableRow));
    };
    private ChangeListener<Number> bisqWindowVerticalSizeListener;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OfferBookChartView(OfferBookChartViewModel model, Navigation navigation, @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter formatter,
                              @Named(AppOptionKeys.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys) {
        super(model);
        this.navigation = navigation;
        this.formatter = formatter;
        this.useDevPrivilegeKeys = useDevPrivilegeKeys;
    }

    @Override
    public void initialize() {
        createListener();

        final Tuple3<VBox, Label, AutocompleteComboBox<CurrencyListItem>> currencyComboBoxTuple = addTopLabelAutocompleteComboBox(Res.get("shared.currency"), 0);
        this.currencyComboBox = currencyComboBoxTuple.third;
        this.currencyComboBox.setCellFactory(GUIUtil.getCurrencyListItemCellFactory(Res.get("shared.oneOffer"),
                Res.get("shared.multipleOffers"), model.preferences));

        createChart();

        VBox.setMargin(chartPane, new Insets(0, 0, 5, 0));

        Tuple4<TableView<OfferListItem>, VBox, Button, Label> tupleBuy = getOfferTable(OfferPayload.Direction.BUY);
        Tuple4<TableView<OfferListItem>, VBox, Button, Label> tupleSell = getOfferTable(OfferPayload.Direction.SELL);
        buyOfferTableView = tupleBuy.first;
        sellOfferTableView = tupleSell.first;

        leftButton = (AutoTooltipButton) tupleBuy.third;
        rightButton = (AutoTooltipButton) tupleSell.third;

        leftHeaderLabel = tupleBuy.fourth;
        rightHeaderLabel = tupleSell.fourth;

        bottomHBox = new HBox();
        bottomHBox.setSpacing(20); //30
        bottomHBox.setAlignment(Pos.CENTER);
        VBox.setMargin(bottomHBox, new Insets(-5, 0, 0, 0));
        HBox.setHgrow(tupleBuy.second, Priority.ALWAYS);
        HBox.setHgrow(tupleSell.second, Priority.ALWAYS);
        tupleBuy.second.setUserData(OfferPayload.Direction.BUY.name());
        tupleSell.second.setUserData(OfferPayload.Direction.SELL.name());
        bottomHBox.getChildren().addAll(tupleBuy.second, tupleSell.second);

        root.getChildren().addAll(currencyComboBoxTuple.first, chartPane, bottomHBox);
    }

    @Override
    protected void activate() {
        // root.getParent() is null at initialize
        tabPaneSelectionModel = GUIUtil.getParentOfType(root, JFXTabPane.class).getSelectionModel();
        selectedTabIndexListener = (observable, oldValue, newValue) -> model.setSelectedTabIndex((int) newValue);

        model.setSelectedTabIndex(tabPaneSelectionModel.getSelectedIndex());
        tabPaneSelectionModel.selectedIndexProperty().addListener(selectedTabIndexListener);

        currencyComboBox.setConverter(new CurrencyListItemStringConverter(currencyComboBox));
        currencyComboBox.getEditor().getStyleClass().add("combo-box-editor-bold");

        currencyComboBox.setAutocompleteItems(model.getCurrencyListItems());
        currencyComboBox.setVisibleRowCount(10);

        if (model.getSelectedCurrencyListItem().isPresent()) {
            CurrencyListItem selectedItem = model.getSelectedCurrencyListItem().get();
            currencyComboBox.getSelectionModel().select(model.getSelectedCurrencyListItem().get());
            currencyComboBox.getEditor().setText(new CurrencyListItemStringConverter(currencyComboBox).toString(selectedItem));
        }

        currencyComboBox.setOnChangeConfirmed(e -> {
            CurrencyListItem selectedItem = currencyComboBox.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                model.onSetTradeCurrency(selectedItem.tradeCurrency);
                updateChartData();
            }
        });

        model.currencyListItems.getObservableList().addListener(currencyListItemsListener);

        model.getOfferBookListItems().addListener(changeListener);
        tradeCurrencySubscriber = EasyBind.subscribe(model.selectedTradeCurrencyProperty,
                tradeCurrency -> {
                    String code = tradeCurrency.getCode();
                    volumeColumnLabel.set(Res.get("shared.amountWithCur", code));
                    xAxis.setTickLabelFormatter(new StringConverter<>() {
                        int cryptoPrecision = 3;

                        @Override
                        public String toString(Number object) {
                            final double doubleValue = (double) object;
                            if (CurrencyUtil.isCryptoCurrency(model.getCurrencyCode())) {
                                final String withCryptoPrecision = FormattingUtils.formatRoundedDoubleWithPrecision(doubleValue, cryptoPrecision);
                                if (withCryptoPrecision.equals("0.000")) {
                                    cryptoPrecision = 8;
                                    return FormattingUtils.formatRoundedDoubleWithPrecision(doubleValue, cryptoPrecision);
                                } else {
                                    return withCryptoPrecision;
                                }
                            } else {
                                return FormattingUtils.formatRoundedDoubleWithPrecision(doubleValue, 2);
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
                        leftButton.updateText(Res.get("market.offerBook.buyAltcoin", code, Res.getBaseCurrencyCode()));

                        rightHeaderLabel.setText(Res.get("market.offerBook.sellOffersHeaderLabel", code));
                        rightButton.updateText(Res.get("market.offerBook.sellAltcoin", code, Res.getBaseCurrencyCode()));

                        priceColumnLabel.set(Res.get("shared.priceWithCur", Res.getBaseCurrencyCode()));
                    } else {
                        if (bottomHBox.getChildren().size() == 2 && bottomHBox.getChildren().get(0).getUserData().equals(OfferPayload.Direction.SELL.name())) {
                            bottomHBox.getChildren().get(0).toFront();
                            reverseTableColumns();
                        }

                        leftHeaderLabel.setText(Res.get("market.offerBook.sellOffersHeaderLabel", Res.getBaseCurrencyCode()));
                        leftButton.updateText(Res.get("market.offerBook.sellWithFiat", Res.getBaseCurrencyCode(), code));

                        rightHeaderLabel.setText(Res.get("market.offerBook.buyOffersHeaderLabel", Res.getBaseCurrencyCode()));
                        rightButton.updateText(Res.get("market.offerBook.buyWithFiat", Res.getBaseCurrencyCode(), code));

                        priceColumnLabel.set(Res.get("shared.priceWithCur", code));
                    }
                    xAxis.setLabel(CurrencyUtil.getPriceWithCurrencyCode(code));

                    seriesBuy.setName(leftHeaderLabel.getText() + "   ");
                    seriesSell.setName(rightHeaderLabel.getText());
                });

        buyOfferTableView.setItems(model.getTopBuyOfferList());
        sellOfferTableView.setItems(model.getTopSellOfferList());

        buyOfferTableView.getSelectionModel().selectedItemProperty().addListener(buyTableRowSelectionListener);
        sellOfferTableView.getSelectionModel().selectedItemProperty().addListener(sellTableRowSelectionListener);

        root.getScene().heightProperty().addListener(bisqWindowVerticalSizeListener);

        updateChartData();
    }

    static class CurrencyListItemStringConverter extends StringConverter<CurrencyListItem> {
        private ComboBox<CurrencyListItem> comboBox;

        CurrencyListItemStringConverter(ComboBox<CurrencyListItem> comboBox) {
            this.comboBox = comboBox;
        }

        @Override
        public String toString(CurrencyListItem currencyItem) {
            return currencyItem != null ? currencyItem.codeDashNameString() : "";
        }

        @Override
        public CurrencyListItem fromString(String s) {
            return comboBox.getItems().stream().
                    filter(currencyItem -> currencyItem.codeDashNameString().equals(s)).
                    findAny().orElse(null);
        }
    }

    private void createListener() {
        changeListener = c -> updateChartData();

        currencyListItemsListener = c -> {
            if (model.getSelectedCurrencyListItem().isPresent())
                currencyComboBox.getSelectionModel().select(model.getSelectedCurrencyListItem().get());
        };

        buyTableRowSelectionListener = (observable, oldValue, newValue) -> {
            model.preferences.setSellScreenCurrencyCode(model.getCurrencyCode());
            navigation.navigateTo(MainView.class, SellOfferView.class);
        };
        sellTableRowSelectionListener = (observable, oldValue, newValue) -> {
            model.preferences.setBuyScreenCurrencyCode(model.getCurrencyCode());
            navigation.navigateTo(MainView.class, BuyOfferView.class);
        };

        bisqWindowVerticalSizeListener = (observable, oldValue, newValue) -> {
            double newTableViewHeight = offerTableViewHeight.apply(newValue.doubleValue());
            if (buyOfferTableView.getHeight() != newTableViewHeight) {
                buyOfferTableView.setMinHeight(newTableViewHeight);
                sellOfferTableView.setMinHeight(newTableViewHeight);
            }
        };
    }

    @Override
    protected void deactivate() {
        model.getOfferBookListItems().removeListener(changeListener);
        tabPaneSelectionModel.selectedIndexProperty().removeListener(selectedTabIndexListener);
        model.currencyListItems.getObservableList().removeListener(currencyListItemsListener);
        tradeCurrencySubscriber.unsubscribe();
        buyOfferTableView.getSelectionModel().selectedItemProperty().removeListener(buyTableRowSelectionListener);
        sellOfferTableView.getSelectionModel().selectedItemProperty().removeListener(sellTableRowSelectionListener);
    }

    private void createChart() {
        xAxis = new NumberAxis();
        xAxis.setForceZeroInRange(false);
        xAxis.setAutoRanging(false);
        xAxis.setTickLabelGap(6);
        xAxis.setTickMarkVisible(false);
        xAxis.setMinorTickVisible(false);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setForceZeroInRange(false);
        yAxis.setSide(Side.RIGHT);
        yAxis.setAutoRanging(true);
        yAxis.setTickMarkVisible(false);
        yAxis.setMinorTickVisible(false);
        yAxis.setTickLabelGap(5);
        yAxis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(yAxis, "", " " + Res.getBaseCurrencyCode()));

        seriesBuy = new XYChart.Series<>();
        seriesSell = new XYChart.Series<>();

        areaChart = new AreaChart<>(xAxis, yAxis);
        areaChart.setLegendVisible(false);
        areaChart.setAnimated(false);
        areaChart.setId("charts");
        areaChart.setMinHeight(270);
        areaChart.setPrefHeight(270);
        areaChart.setCreateSymbols(true);
        areaChart.setPadding(new Insets(0, 10, 0, 10));
        areaChart.getData().addAll(List.of(seriesBuy, seriesSell));

        chartPane = new AnchorPane();
        chartPane.getStyleClass().add("chart-pane");

        AnchorPane.setTopAnchor(areaChart, 15d);
        AnchorPane.setBottomAnchor(areaChart, 10d);
        AnchorPane.setLeftAnchor(areaChart, 10d);
        AnchorPane.setRightAnchor(areaChart, 0d);

        chartPane.getChildren().add(areaChart);
    }

    private void updateChartData() {
        seriesBuy.getData().clear();
        seriesSell.getData().clear();
        areaChart.getData().clear();

        double buyMinValue = model.getBuyData().stream()
                .mapToDouble(o -> o.getXValue().doubleValue())
                .min()
                .orElse(Double.MAX_VALUE);

        // Hide buy offers that are more than a factor 3 higher than the lowest buy offer
        double buyMaxValue = model.getBuyData().stream()
                .mapToDouble(o -> o.getXValue().doubleValue())
                .filter(o -> o < buyMinValue * 3)
                .max()
                .orElse(Double.MIN_VALUE);

        double sellMaxValue = model.getSellData().stream()
                .mapToDouble(o -> o.getXValue().doubleValue())
                .max()
                .orElse(Double.MIN_VALUE);

        // Hide sell offers that are less than a factor 3 lower than the highest sell offer
        double sellMinValue = model.getSellData().stream()
                .mapToDouble(o -> o.getXValue().doubleValue())
                .filter(o -> o > sellMaxValue / 3)
                .min()
                .orElse(Double.MAX_VALUE);

        double minValue = Double.min(buyMinValue, sellMinValue);
        double maxValue = Double.max(buyMaxValue, sellMaxValue);

        if (minValue == Double.MAX_VALUE || maxValue == Double.MIN_VALUE) {
            xAxis.setAutoRanging(true);
        } else {
            xAxis.setAutoRanging(false);
            xAxis.setLowerBound(minValue);
            xAxis.setUpperBound(maxValue);
            xAxis.setTickUnit((maxValue - minValue) / 13);
        }

        seriesBuy.getData().addAll(model.getBuyData());
        seriesSell.getData().addAll(model.getSellData());
        areaChart.getData().addAll(List.of(seriesBuy, seriesSell));
    }

    private Tuple4<TableView<OfferListItem>, VBox, Button, Label> getOfferTable(OfferPayload.Direction direction) {
        TableView<OfferListItem> tableView = new TableView<>();
        tableView.setMinHeight(initialOfferTableViewHeight);
        tableView.setPrefHeight(initialOfferTableViewHeight);
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
                new Callback<>() {
                    @Override
                    public TableCell<OfferListItem, OfferListItem> call(TableColumn<OfferListItem, OfferListItem> column) {
                        return new TableCell<>() {
                            private Offer offer;
                            final ChangeListener<Number> listener = new ChangeListener<>() {
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
        volumeColumn.getStyleClass().addAll("number-column", "first-column");
        volumeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        volumeColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OfferListItem, OfferListItem> call(TableColumn<OfferListItem, OfferListItem> column) {
                        return new TableCell<>() {
                            private Offer offer;
                            final ChangeListener<Number> listener = new ChangeListener<>() {
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
                new Callback<>() {
                    @Override
                    public TableCell<OfferListItem, OfferListItem> call(TableColumn<OfferListItem, OfferListItem> column) {
                        return new TableCell<>() {
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

        boolean isSellOffer = direction == OfferPayload.Direction.SELL;

        // trader avatar
        TableColumn<OfferListItem, OfferListItem> avatarColumn = new AutoTooltipTableColumn<>(isSellOffer ?
                Res.get("shared.sellerUpperCase") : Res.get("shared.buyerUpperCase")) {
            {
                setMinWidth(80);
                setMaxWidth(80);
                setSortable(true);
            }
        };

        avatarColumn.getStyleClass().addAll("last-column", "avatar-column");
        avatarColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        avatarColumn.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<OfferListItem, OfferListItem> call(TableColumn<OfferListItem, OfferListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final OfferListItem newItem, boolean empty) {
                                super.updateItem(newItem, empty);
                                if (newItem != null && !empty) {
                                    final Offer offer = newItem.offer;
                                    final NodeAddress makersNodeAddress = offer.getOwnerNodeAddress();
                                    String role = Res.get("peerInfoIcon.tooltip.maker");
                                    PeerInfoIconSmall peerInfoIcon = new PeerInfoIconSmall(makersNodeAddress,
                                            role,
                                            offer,
                                            model.preferences,
                                            model.accountAgeWitnessService,
                                            useDevPrivilegeKeys);
//                                    setAlignment(Pos.CENTER);
                                    setGraphic(peerInfoIcon);
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
        tableView.getColumns().add(avatarColumn);

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Label placeholder = new AutoTooltipLabel(Res.get("table.placeholder.noItems", Res.get("shared.multipleOffers")));
        placeholder.setWrapText(true);
        tableView.setPlaceholder(placeholder);

        HBox titleButtonBox = new HBox();
        titleButtonBox.setAlignment(Pos.CENTER);

        Label titleLabel = new AutoTooltipLabel();
        titleLabel.getStyleClass().add("table-title");

        AutoTooltipButton button = new AutoTooltipButton();
        ImageView iconView = new ImageView();
        iconView.setId(isSellOffer ? "image-buy-white" : "image-sell-white");
        button.setGraphic(iconView);
        button.setGraphicTextGap(10);
        button.updateText(isSellOffer ? Res.get("market.offerBook.buy") : Res.get("market.offerBook.sell"));
        button.setMinHeight(32);
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

        Region spacer = new Region();

        HBox.setHgrow(spacer, Priority.ALWAYS);

        titleButtonBox.getChildren().addAll(titleLabel, spacer, button);

        VBox vBox = new VBox();
        VBox.setVgrow(tableView, Priority.ALWAYS);
        vBox.setPadding(new Insets(0, 0, 0, 0));
        vBox.setSpacing(10);
        vBox.setFillWidth(true);
        vBox.setMinHeight(190);
        vBox.getChildren().addAll(titleButtonBox, tableView);

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
