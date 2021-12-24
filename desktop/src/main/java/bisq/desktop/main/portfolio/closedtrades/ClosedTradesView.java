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

package bisq.desktop.main.portfolio.closedtrades;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.PeerInfoIconTrading;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.BsqTradeDetailsWindow;
import bisq.desktop.main.overlays.windows.ClosedTradesSummaryWindow;
import bisq.desktop.main.overlays.windows.OfferDetailsWindow;
import bisq.desktop.main.overlays.windows.TradeDetailsWindow;
import bisq.desktop.main.portfolio.presentation.PortfolioUtil;
import bisq.desktop.util.GUIUtil;

import bisq.core.alert.PrivateNotificationManager;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayloadBase;
import bisq.core.offer.OpenOffer;
import bisq.core.trade.model.Tradable;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.user.Preferences;

import bisq.network.p2p.NodeAddress;

import bisq.common.config.Config;
import bisq.common.crypto.KeyRing;

import com.google.protobuf.Message;

import com.googlecode.jcsv.writer.CSVEntryConverter;

import javax.inject.Inject;
import javax.inject.Named;

import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;

import javafx.fxml.FXML;

import javafx.stage.Stage;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;

import javafx.event.ActionEvent;

import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.Comparator;
import java.util.Date;
import java.util.function.Function;

import static bisq.desktop.util.FormBuilder.getRegularIconButton;

@FxmlView
public class ClosedTradesView extends ActivatableViewAndModel<VBox, ClosedTradesViewModel> {
    private final boolean useDevPrivilegeKeys;

    private enum ColumnNames {
        TRADE_ID(Res.get("shared.tradeId")),
        DATE(Res.get("shared.dateTime")),
        MARKET(Res.get("shared.market")),
        PRICE(Res.get("shared.price")),
        DEVIATION(Res.get("shared.deviation")),
        AMOUNT(Res.get("shared.amountWithCur", Res.getBaseCurrencyCode())),
        VOLUME(Res.get("shared.amount")),
        VOLUME_CURRENCY(Res.get("shared.currency")),
        TX_FEE(Res.get("shared.txFee")),
        TRADE_FEE_BTC(Res.get("shared.tradeFee") + " BTC"),
        TRADE_FEE_BSQ(Res.get("shared.tradeFee") + " BSQ"),
        BUYER_SEC(Res.get("shared.buyerSecurityDeposit")),
        SELLER_SEC(Res.get("shared.sellerSecurityDeposit")),
        OFFER_TYPE(Res.get("shared.offerType")),
        STATUS(Res.get("shared.state"));

        private final String text;

        ColumnNames(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    @FXML
    TableView<Tradable> tableView;
    @FXML
    TableColumn<Tradable, Tradable> priceColumn, deviationColumn, amountColumn, volumeColumn,
            txFeeColumn, tradeFeeColumn, buyerSecurityDepositColumn, sellerSecurityDepositColumn,
            marketColumn, directionColumn, dateColumn, tradeIdColumn, stateColumn,
            duplicateColumn, avatarColumn;
    @FXML
    HBox searchBox;
    @FXML
    AutoTooltipLabel filterLabel;
    @FXML
    InputTextField filterTextField;
    @FXML
    Pane searchBoxSpacer;
    @FXML
    AutoTooltipButton exportButton, summaryButton;
    @FXML
    Label numItems;
    @FXML
    Region footerSpacer;

    private final OfferDetailsWindow offerDetailsWindow;
    private final BsqTradeDetailsWindow bsqTradeDetailsWindow;
    private final Navigation navigation;
    private final KeyRing keyRing;
    private final Preferences preferences;
    private final TradeDetailsWindow tradeDetailsWindow;
    private final PrivateNotificationManager privateNotificationManager;
    private SortedList<Tradable> sortedList;
    private FilteredList<Tradable> filteredList;
    private ChangeListener<String> filterTextFieldListener;
    private ChangeListener<Number> widthListener;

    @Inject
    public ClosedTradesView(ClosedTradesViewModel model,
                            OfferDetailsWindow offerDetailsWindow,
                            BsqTradeDetailsWindow bsqTradeDetailsWindow,
                            Navigation navigation,
                            KeyRing keyRing,
                            Preferences preferences,
                            TradeDetailsWindow tradeDetailsWindow,
                            PrivateNotificationManager privateNotificationManager,
                            @Named(Config.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys) {
        super(model);
        this.offerDetailsWindow = offerDetailsWindow;
        this.bsqTradeDetailsWindow = bsqTradeDetailsWindow;
        this.navigation = navigation;
        this.keyRing = keyRing;
        this.preferences = preferences;
        this.tradeDetailsWindow = tradeDetailsWindow;
        this.privateNotificationManager = privateNotificationManager;
        this.useDevPrivilegeKeys = useDevPrivilegeKeys;
    }

    @Override
    public void initialize() {
        widthListener = (observable, oldValue, newValue) -> onWidthChange((double) newValue);
        txFeeColumn.setGraphic(new AutoTooltipLabel(ColumnNames.TX_FEE.toString()));
        tradeFeeColumn.setGraphic(new AutoTooltipLabel(ColumnNames.TRADE_FEE_BTC.toString().replace(" BTC", "")));
        buyerSecurityDepositColumn.setGraphic(new AutoTooltipLabel(ColumnNames.BUYER_SEC.toString()));
        sellerSecurityDepositColumn.setGraphic(new AutoTooltipLabel(ColumnNames.SELLER_SEC.toString()));
        priceColumn.setGraphic(new AutoTooltipLabel(ColumnNames.PRICE.toString()));
        deviationColumn.setGraphic(new AutoTooltipTableColumn<>(ColumnNames.DEVIATION.toString(),
                Res.get("portfolio.closedTrades.deviation.help")).getGraphic());
        amountColumn.setGraphic(new AutoTooltipLabel(ColumnNames.AMOUNT.toString()));
        volumeColumn.setGraphic(new AutoTooltipLabel(ColumnNames.VOLUME.toString()));
        marketColumn.setGraphic(new AutoTooltipLabel(ColumnNames.MARKET.toString()));
        directionColumn.setGraphic(new AutoTooltipLabel(ColumnNames.OFFER_TYPE.toString()));
        dateColumn.setGraphic(new AutoTooltipLabel(ColumnNames.DATE.toString()));
        tradeIdColumn.setGraphic(new AutoTooltipLabel(ColumnNames.TRADE_ID.toString()));
        stateColumn.setGraphic(new AutoTooltipLabel(ColumnNames.STATUS.toString()));
        duplicateColumn.setGraphic(new AutoTooltipLabel(""));
        avatarColumn.setText("");

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noItems", Res.get("shared.trades"))));

        setTradeIdColumnCellFactory();
        setDirectionColumnCellFactory();
        setAmountColumnCellFactory();
        setTxFeeColumnCellFactory();
        setTradeFeeColumnCellFactory();
        setBuyerSecurityDepositColumnCellFactory();
        setSellerSecurityDepositColumnCellFactory();
        setPriceColumnCellFactory();
        setDeviationColumnCellFactory();
        setVolumeColumnCellFactory();
        setDateColumnCellFactory();
        setMarketColumnCellFactory();
        setStateColumnCellFactory();
        setDuplicateColumnCellFactory();
        setAvatarColumnCellFactory();

        tradeIdColumn.setComparator(Comparator.comparing(Tradable::getId));
        dateColumn.setComparator(Comparator.comparing(Tradable::getDate));
        directionColumn.setComparator(Comparator.comparing(o -> o.getOffer().getDirection()));
        marketColumn.setComparator(Comparator.comparing(model::getMarketLabel));
        priceColumn.setComparator(Comparator.comparing(model::getPrice, Comparator.nullsFirst(Comparator.naturalOrder())));
        deviationColumn.setComparator(Comparator.comparing(o ->
                        o.getOffer().isUseMarketBasedPrice() ? o.getOffer().getMarketPriceMargin() : 1,
                Comparator.nullsFirst(Comparator.naturalOrder())));
        volumeColumn.setComparator(nullsFirstComparingAsTrade(TradeModel::getVolume));
        amountColumn.setComparator(Comparator.comparing(model::getAmount, Comparator.nullsFirst(Comparator.naturalOrder())));
        avatarColumn.setComparator(Comparator.comparing(
                model.dataModel::getNumPastTrades,
                Comparator.nullsFirst(Comparator.naturalOrder())
        ));
        txFeeColumn.setComparator(nullsFirstComparing(o ->
                o instanceof TradeModel ? ((TradeModel) o).getTxFee() : o.getOffer().getTxFee()
        ));
        txFeeColumn.setComparator(Comparator.comparing(model::getTxFee, Comparator.nullsFirst(Comparator.naturalOrder())));

        //
        tradeFeeColumn.setComparator(Comparator.comparing(item -> {
            String tradeFee = model.getTradeFee(item, true);
            // We want to separate BSQ and BTC fees so we use a prefix
            if (item.getOffer().isCurrencyForMakerFeeBtc()) {
                return "BTC" + tradeFee;
            } else {
                return "BSQ" + tradeFee;
            }
        }, Comparator.nullsFirst(Comparator.naturalOrder())));
        buyerSecurityDepositColumn.setComparator(nullsFirstComparing(o ->
                o.getOffer() != null ? o.getOffer().getBuyerSecurityDeposit() : null
        ));
        sellerSecurityDepositColumn.setComparator(nullsFirstComparing(o ->
                o.getOffer() != null ? o.getOffer().getSellerSecurityDeposit() : null
        ));
        stateColumn.setComparator(Comparator.comparing(model::getState));

        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(dateColumn);

        tableView.setRowFactory(
                tableView -> {
                    TableRow<Tradable> row = new TableRow<>();
                    ContextMenu rowMenu = new ContextMenu();
                    MenuItem duplicateItem = new MenuItem(Res.get("portfolio.context.offerLikeThis"));
                    duplicateItem.setOnAction((ActionEvent event) -> onDuplicateOffer(row.getItem().getOffer()));
                    rowMenu.getItems().add(duplicateItem);
                    row.contextMenuProperty().bind(
                            Bindings.when(Bindings.isNotNull(row.itemProperty()))
                                    .then(rowMenu)
                                    .otherwise((ContextMenu) null));
                    return row;
                });

        filterLabel.setText(Res.get("shared.filter"));
        HBox.setMargin(filterLabel, new Insets(5, 0, 0, 10));
        filterTextFieldListener = (observable, oldValue, newValue) -> applyFilteredListPredicate(filterTextField.getText());
        searchBox.setSpacing(5);
        HBox.setHgrow(searchBoxSpacer, Priority.ALWAYS);

        numItems.setId("num-offers");
        numItems.setPadding(new Insets(-5, 0, 0, 10));
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        HBox.setMargin(exportButton, new Insets(0, 10, 0, 0));
        exportButton.updateText(Res.get("shared.exportCSV"));
        summaryButton.updateText(Res.get("shared.summary"));
    }

    @Override
    protected void activate() {
        filteredList = new FilteredList<>(model.dataModel.getList());

        sortedList = new SortedList<>(filteredList);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());

        tableView.setItems(sortedList);

        numItems.setText(Res.get("shared.numItemsLabel", sortedList.size()));
        exportButton.setOnAction(event -> {
            CSVEntryConverter<Tradable> headerConverter = item -> {
                String[] columns = new String[ColumnNames.values().length];
                for (ColumnNames m : ColumnNames.values()) {
                    columns[m.ordinal()] = m.toString();
                }
                return columns;
            };
            CSVEntryConverter<Tradable> contentConverter = item -> {
                String[] columns = new String[ColumnNames.values().length];
                columns[ColumnNames.TRADE_ID.ordinal()] = model.getTradeId(item);
                columns[ColumnNames.DATE.ordinal()] = model.getDate(item);
                columns[ColumnNames.MARKET.ordinal()] = model.getMarketLabel(item);
                columns[ColumnNames.PRICE.ordinal()] = model.getPrice(item);
                columns[ColumnNames.DEVIATION.ordinal()] = model.getPriceDeviation(item);
                columns[ColumnNames.AMOUNT.ordinal()] = model.getAmount(item);
                columns[ColumnNames.VOLUME.ordinal()] = model.getVolume(item, false);
                columns[ColumnNames.VOLUME_CURRENCY.ordinal()] = model.getVolumeCurrency(item);
                columns[ColumnNames.TX_FEE.ordinal()] = model.getTxFee(item);
                if (model.dataModel.isCurrencyForTradeFeeBtc(item)) {
                    columns[ColumnNames.TRADE_FEE_BTC.ordinal()] = model.getTradeFee(item, false);
                    columns[ColumnNames.TRADE_FEE_BSQ.ordinal()] = "";
                } else {
                    columns[ColumnNames.TRADE_FEE_BTC.ordinal()] = "";
                    columns[ColumnNames.TRADE_FEE_BSQ.ordinal()] = model.getTradeFee(item, false);
                }
                columns[ColumnNames.BUYER_SEC.ordinal()] = model.getBuyerSecurityDeposit(item);
                columns[ColumnNames.SELLER_SEC.ordinal()] = model.getSellerSecurityDeposit(item);
                columns[ColumnNames.OFFER_TYPE.ordinal()] = model.getDirectionLabel(item);
                columns[ColumnNames.STATUS.ordinal()] = model.getState(item);
                return columns;
            };

            GUIUtil.exportCSV("tradeHistory.csv", headerConverter, contentConverter,
                    getDummyTradable(), sortedList, (Stage) root.getScene().getWindow());
        });

        summaryButton.setOnAction(event -> new ClosedTradesSummaryWindow(model).show());

        filterTextField.textProperty().addListener(filterTextFieldListener);
        applyFilteredListPredicate(filterTextField.getText());
        root.widthProperty().addListener(widthListener);
        onWidthChange(root.getWidth());
    }

    @Override
    protected void deactivate() {
        sortedList.comparatorProperty().unbind();
        exportButton.setOnAction(null);
        summaryButton.setOnAction(null);

        filterTextField.textProperty().removeListener(filterTextFieldListener);
        root.widthProperty().removeListener(widthListener);
    }

    private static <T extends Comparable<T>> Comparator<Tradable> nullsFirstComparing(Function<Tradable, T> keyExtractor) {
        return Comparator.comparing(
                o -> o != null ? keyExtractor.apply(o) : null,
                Comparator.nullsFirst(Comparator.naturalOrder())
        );
    }

    private static <T extends Comparable<T>> Comparator<Tradable> nullsFirstComparingAsTrade(Function<TradeModel, T> keyExtractor) {
        return Comparator.comparing(
                o -> o instanceof TradeModel ? keyExtractor.apply((TradeModel) o) : null,
                Comparator.nullsFirst(Comparator.naturalOrder())
        );
    }

    private void onWidthChange(double width) {
        txFeeColumn.setVisible(width > 1200);
        tradeFeeColumn.setVisible(width > 1300);
        buyerSecurityDepositColumn.setVisible(width > 1400);
        sellerSecurityDepositColumn.setVisible(width > 1500);
    }

    private void applyFilteredListPredicate(String filterString) {
        filteredList.setPredicate(tradable -> {
            if (filterString.isEmpty())
                return true;

            Offer offer = tradable.getOffer();
            if (offer.getId().contains(filterString)) {
                return true;
            }
            if (model.getDate(tradable).contains(filterString)) {
                return true;
            }
            if (model.getMarketLabel(tradable).contains(filterString)) {
                return true;
            }
            if (model.getPrice(tradable).contains(filterString)) {
                return true;
            }
            if (model.getPriceDeviation(tradable).contains(filterString)) {
                return true;
            }

            if (model.getVolume(tradable, true).contains(filterString)) {
                return true;
            }
            if (model.getAmount(tradable).contains(filterString)) {
                return true;
            }
            if (model.getTradeFee(tradable, true).contains(filterString)) {
                return true;
            }
            if (model.getTxFee(tradable).contains(filterString)) {
                return true;
            }
            if (model.getBuyerSecurityDeposit(tradable).contains(filterString)) {
                return true;
            }
            if (model.getSellerSecurityDeposit(tradable).contains(filterString)) {
                return true;
            }
            if (model.getState(tradable).contains(filterString)) {
                return true;
            }
            if (model.getDirectionLabel(tradable).contains(filterString)) {
                return true;
            }
            if (offer.getPaymentMethod().getDisplayString().contains(filterString)) {
                return true;
            }
            if (offer.getOfferFeePaymentTxId() != null &&
                    offer.getOfferFeePaymentTxId().contains(filterString)) {
                return true;
            }

            if (tradable instanceof BsqSwapTrade) {
                BsqSwapTrade bsqSwapTrade = (BsqSwapTrade) tradable;
                if (bsqSwapTrade.getTxId() != null && bsqSwapTrade.getTxId().contains(filterString)) {
                    return true;
                }
                if (bsqSwapTrade.getTradingPeerNodeAddress().getFullAddress().contains(filterString)) {
                    return true;
                }
            }

            if (tradable instanceof Trade) {
                Trade trade = (Trade) tradable;
                if (trade.getTakerFeeTxId() != null && trade.getTakerFeeTxId().contains(filterString)) {
                    return true;
                }
                if (trade.getDepositTxId() != null && trade.getDepositTxId().contains(filterString)) {
                    return true;
                }
                if (trade.getPayoutTxId() != null && trade.getPayoutTxId().contains(filterString)) {
                    return true;
                }

                Contract contract = trade.getContract();
                boolean isBuyerOnion = false;
                boolean isSellerOnion = false;
                boolean matchesBuyersPaymentAccountData = false;
                boolean matchesSellersPaymentAccountData = false;
                if (contract != null) {
                    isBuyerOnion = contract.getBuyerNodeAddress().getFullAddress().contains(filterString);
                    isSellerOnion = contract.getSellerNodeAddress().getFullAddress().contains(filterString);
                    matchesBuyersPaymentAccountData = contract.getBuyerPaymentAccountPayload() != null &&
                            contract.getBuyerPaymentAccountPayload().getPaymentDetails().contains(filterString);
                    matchesSellersPaymentAccountData = contract.getSellerPaymentAccountPayload() != null &&
                            contract.getSellerPaymentAccountPayload().getPaymentDetails().contains(filterString);
                }
                return isBuyerOnion || isSellerOnion ||
                        matchesBuyersPaymentAccountData || matchesSellersPaymentAccountData;
            } else {
                return false;
            }
        });
    }

    private void setTradeIdColumnCellFactory() {
        tradeIdColumn.getStyleClass().add("first-column");
        tradeIdColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper<>(offerListItem.getValue()));
        tradeIdColumn.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<Tradable, Tradable> call(TableColumn<Tradable,
                            Tradable> column) {
                        return new TableCell<>() {
                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final Tradable tradable, boolean empty) {
                                super.updateItem(tradable, empty);
                                if (tradable != null && !empty) {
                                    field = new HyperlinkWithIcon(model.getTradeId(tradable));
                                    field.setOnAction(event -> {
                                        if (tradable instanceof Trade) {
                                            tradeDetailsWindow.show((Trade) tradable);
                                        } else if (tradable instanceof BsqSwapTrade) {
                                            bsqTradeDetailsWindow.show((BsqSwapTrade) tradable);
                                        } else if (tradable instanceof OpenOffer) {
                                            offerDetailsWindow.show(tradable.getOffer());
                                        }
                                    });
                                    field.setTooltip(new Tooltip(Res.get("tooltip.openPopupForDetails")));
                                    setGraphic(field);
                                } else {
                                    setGraphic(null);
                                    if (field != null)
                                        field.setOnAction(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setDateColumnCellFactory() {
        dateColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        dateColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<Tradable, Tradable> call(
                            TableColumn<Tradable, Tradable> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final Tradable item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setGraphic(new AutoTooltipLabel(model.getDate(item)));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
    }

    private void setMarketColumnCellFactory() {
        marketColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        marketColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<Tradable, Tradable> call(
                            TableColumn<Tradable, Tradable> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final Tradable item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(new AutoTooltipLabel(model.getMarketLabel(item)));
                            }
                        };
                    }
                });
    }

    private void setStateColumnCellFactory() {
        stateColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        stateColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<Tradable, Tradable> call(
                            TableColumn<Tradable, Tradable> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final Tradable item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setGraphic(new AutoTooltipLabel(model.getState(item)));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
    }

    private void setDuplicateColumnCellFactory() {
        duplicateColumn.getStyleClass().add("avatar-column");
        duplicateColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper<>(offerListItem.getValue()));
        duplicateColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<Tradable, Tradable> call(TableColumn<Tradable, Tradable> column) {
                        return new TableCell<>() {
                            Button button;

                            @Override
                            public void updateItem(final Tradable item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty && isMyOfferAsMaker(item.getOffer().getOfferPayloadBase())) {
                                    if (button == null) {
                                        button = getRegularIconButton(MaterialDesignIcon.CONTENT_COPY);
                                        button.setTooltip(new Tooltip(Res.get("shared.duplicateOffer")));
                                        setGraphic(button);
                                    }
                                    button.setOnAction(event -> onDuplicateOffer(item.getOffer()));
                                } else {
                                    setGraphic(null);
                                    if (button != null) {
                                        button.setOnAction(null);
                                        button = null;
                                    }
                                }
                            }
                        };
                    }
                });
    }

    @SuppressWarnings("UnusedReturnValue")
    private TableColumn<Tradable, Tradable> setAvatarColumnCellFactory() {
        avatarColumn.getStyleClass().addAll("last-column", "avatar-column");
        avatarColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        avatarColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<Tradable, Tradable> call(TableColumn<Tradable, Tradable> column) {
                        return new TableCell<>() {

                            @Override
                            public void updateItem(final Tradable item, boolean empty) {
                                super.updateItem(item, empty);

                                if (!empty && item instanceof TradeModel) {
                                    TradeModel tradeModel = (TradeModel) item;
                                    int numPastTrades = model.dataModel.getNumPastTrades(tradeModel);
                                    NodeAddress tradingPeerNodeAddress = tradeModel.getTradingPeerNodeAddress();
                                    String role = Res.get("peerInfoIcon.tooltip.tradePeer");
                                    Node peerInfoIcon = new PeerInfoIconTrading(tradingPeerNodeAddress,
                                            role,
                                            numPastTrades,
                                            privateNotificationManager,
                                            tradeModel,
                                            preferences,
                                            model.dataModel.accountAgeWitnessService,
                                            useDevPrivilegeKeys);
                                    setPadding(new Insets(1, 15, 0, 0));
                                    setGraphic(peerInfoIcon);
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
        return avatarColumn;
    }

    private void setAmountColumnCellFactory() {
        amountColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        amountColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<Tradable, Tradable> call(
                            TableColumn<Tradable, Tradable> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final Tradable item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(new AutoTooltipLabel(model.getAmount(item)));
                            }
                        };
                    }
                });
    }

    private void setPriceColumnCellFactory() {
        priceColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        priceColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<Tradable, Tradable> call(
                            TableColumn<Tradable, Tradable> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final Tradable item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(new AutoTooltipLabel(model.getPrice(item)));
                            }
                        };
                    }
                });
    }

    private void setDeviationColumnCellFactory() {
        deviationColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        deviationColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<Tradable, Tradable> call(
                            TableColumn<Tradable, Tradable> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final Tradable item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(new AutoTooltipLabel(model.getPriceDeviation(item)));
                            }
                        };
                    }
                });
    }

    private void setVolumeColumnCellFactory() {
        volumeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        volumeColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<Tradable, Tradable> call(
                            TableColumn<Tradable, Tradable> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final Tradable item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setGraphic(new AutoTooltipLabel(model.getVolume(item, true)));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
    }

    private void setDirectionColumnCellFactory() {
        directionColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        directionColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<Tradable, Tradable> call(
                            TableColumn<Tradable, Tradable> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final Tradable item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(new AutoTooltipLabel(model.getDirectionLabel(item)));
                            }
                        };
                    }
                });
    }

    private void setTxFeeColumnCellFactory() {
        txFeeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        txFeeColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<Tradable, Tradable> call(
                            TableColumn<Tradable, Tradable> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final Tradable item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(new AutoTooltipLabel(model.getTxFee(item)));
                            }
                        };
                    }
                });
    }

    private void setTradeFeeColumnCellFactory() {
        tradeFeeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        tradeFeeColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<Tradable, Tradable> call(
                            TableColumn<Tradable, Tradable> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final Tradable item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(new AutoTooltipLabel(model.getTradeFee(item, true)));
                            }
                        };
                    }
                });
    }

    private void setBuyerSecurityDepositColumnCellFactory() {
        buyerSecurityDepositColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        buyerSecurityDepositColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<Tradable, Tradable> call(
                            TableColumn<Tradable, Tradable> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final Tradable item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(new AutoTooltipLabel(model.getBuyerSecurityDeposit(item)));
                            }
                        };
                    }
                });
    }

    private void setSellerSecurityDepositColumnCellFactory() {
        sellerSecurityDepositColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        sellerSecurityDepositColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<Tradable, Tradable> call(
                            TableColumn<Tradable, Tradable> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final Tradable item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(new AutoTooltipLabel(model.getSellerSecurityDeposit(item)));
                            }
                        };
                    }
                });
    }

    private void onDuplicateOffer(Offer offer) {
        try {
            OfferPayloadBase offerPayloadBase = offer.getOfferPayloadBase();
            if (isMyOfferAsMaker(offerPayloadBase)) {
                PortfolioUtil.duplicateOffer(navigation, offerPayloadBase);
            } else {
                new Popup().warning(Res.get("portfolio.context.notYourOffer")).show();
            }
        } catch (NullPointerException e) {
            log.warn("Unable to get offerPayload - {}", e.toString());
        }
    }

    private boolean isMyOfferAsMaker(OfferPayloadBase offerPayloadBase) {
        return offerPayloadBase.getPubKeyRing().equals(keyRing.getPubKeyRing());
    }

    private Tradable getDummyTradable() {
        return new Tradable() {
            @Override
            public Offer getOffer() {
                return null;
            }

            @Override
            public Date getDate() {
                return null;
            }

            @Override
            public String getId() {
                return null;
            }

            @Override
            public String getShortId() {
                return null;
            }

            @Override
            public Message toProtoMessage() {
                return null;
            }
        };
    }
}
