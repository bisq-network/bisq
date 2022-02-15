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
import bisq.desktop.components.PeerInfoIconTrading;
import bisq.desktop.components.list.FilterBox;
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
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.user.Preferences;

import bisq.network.p2p.NodeAddress;

import bisq.common.config.Config;
import bisq.common.crypto.KeyRing;

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
    TableView<ClosedTradesListItem> tableView;
    @FXML
    TableColumn<ClosedTradesListItem, ClosedTradesListItem> priceColumn, deviationColumn, amountColumn, volumeColumn,
            txFeeColumn, tradeFeeColumn, buyerSecurityDepositColumn, sellerSecurityDepositColumn,
            marketColumn, directionColumn, dateColumn, tradeIdColumn, stateColumn,
            duplicateColumn, avatarColumn;
    @FXML
    FilterBox filterBox;
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
    private SortedList<ClosedTradesListItem> sortedList;
    private FilteredList<ClosedTradesListItem> filteredList;
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

        tradeIdColumn.setComparator(Comparator.comparing(ClosedTradesListItem::getTradeId));
        dateColumn.setComparator(Comparator.comparing(ClosedTradesListItem::getDate));
        directionColumn.setComparator(Comparator.comparing(o -> o.getTradable().getOffer().getDirection()));
        marketColumn.setComparator(Comparator.comparing(ClosedTradesListItem::getMarketLabel));
        priceColumn.setComparator(Comparator.comparing(ClosedTradesListItem::getPrice, Comparator.nullsFirst(Comparator.naturalOrder())));
        deviationColumn.setComparator(Comparator.comparing(o ->
                        o.getTradable().getOffer().isUseMarketBasedPrice() ? o.getTradable().getOffer().getMarketPriceMargin() : 1,
                Comparator.nullsFirst(Comparator.naturalOrder())));
        volumeColumn.setComparator(nullsFirstComparingAsTrade(TradeModel::getVolume));
        amountColumn.setComparator(Comparator.comparing(ClosedTradesListItem::getAmount, Comparator.nullsFirst(Comparator.naturalOrder())));
        avatarColumn.setComparator(Comparator.comparing(ClosedTradesListItem::getNumPastTrades, Comparator.nullsFirst(Comparator.naturalOrder())));
        txFeeColumn.setComparator(nullsFirstComparing(o ->
                o.getTradable() instanceof TradeModel ? ((TradeModel) o.getTradable()).getTxFee() : o.getTradable().getOffer().getTxFee()
        ));
        txFeeColumn.setComparator(Comparator.comparing(ClosedTradesListItem::getTxFeeAsString, Comparator.nullsFirst(Comparator.naturalOrder())));

        //
        tradeFeeColumn.setComparator(Comparator.comparing(item -> {
            String tradeFee = item.getTradeFeeAsString(true);
            // We want to separate BSQ and BTC fees so we use a prefix
            if (item.getTradable().getOffer().isCurrencyForMakerFeeBtc()) {
                return "BTC" + tradeFee;
            } else {
                return "BSQ" + tradeFee;
            }
        }, Comparator.nullsFirst(Comparator.naturalOrder())));
        buyerSecurityDepositColumn.setComparator(nullsFirstComparing(o ->
                o.getTradable().getOffer() != null ? o.getTradable().getOffer().getBuyerSecurityDeposit() : null
        ));
        sellerSecurityDepositColumn.setComparator(nullsFirstComparing(o ->
                o.getTradable().getOffer() != null ? o.getTradable().getOffer().getSellerSecurityDeposit() : null
        ));
        stateColumn.setComparator(Comparator.comparing(ClosedTradesListItem::getState));

        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(dateColumn);

        tableView.setRowFactory(
                tableView -> {
                    TableRow<ClosedTradesListItem> row = new TableRow<>();
                    ContextMenu rowMenu = new ContextMenu();
                    MenuItem duplicateItem = new MenuItem(Res.get("portfolio.context.offerLikeThis"));
                    duplicateItem.setOnAction((ActionEvent event) -> onDuplicateOffer(row.getItem().getTradable().getOffer()));
                    rowMenu.getItems().add(duplicateItem);
                    row.contextMenuProperty().bind(
                            Bindings.when(Bindings.isNotNull(row.itemProperty()))
                                    .then(rowMenu)
                                    .otherwise((ContextMenu) null));
                    return row;
                });

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

        filterBox.initialize(filteredList, tableView); // here because filteredList is instantiated here
        filterBox.activate();

        numItems.setText(Res.get("shared.numItemsLabel", sortedList.size()));
        exportButton.setOnAction(event -> {
            CSVEntryConverter<ClosedTradesListItem> headerConverter = item -> {
                String[] columns = new String[ColumnNames.values().length];
                for (ColumnNames m : ColumnNames.values()) {
                    columns[m.ordinal()] = m.toString();
                }
                return columns;
            };
            CSVEntryConverter<ClosedTradesListItem> contentConverter = item -> {
                String[] columns = new String[ColumnNames.values().length];
                columns[ColumnNames.TRADE_ID.ordinal()] = item.getTradeId();
                columns[ColumnNames.DATE.ordinal()] = item.getDateAsString();
                columns[ColumnNames.MARKET.ordinal()] = item.getMarketLabel();
                columns[ColumnNames.PRICE.ordinal()] = item.getPriceAsString();
                columns[ColumnNames.DEVIATION.ordinal()] = item.getPriceDeviationAsString();
                columns[ColumnNames.AMOUNT.ordinal()] = item.getAmountAsString();
                columns[ColumnNames.VOLUME.ordinal()] = item.getVolumeAsString(false);
                columns[ColumnNames.VOLUME_CURRENCY.ordinal()] = item.getVolumeCurrencyAsString();
                columns[ColumnNames.TX_FEE.ordinal()] = item.getTxFeeAsString();
                if (model.dataModel.isCurrencyForTradeFeeBtc(item.getTradable())) {
                    columns[ColumnNames.TRADE_FEE_BTC.ordinal()] = item.getTradeFeeAsString(false);
                    columns[ColumnNames.TRADE_FEE_BSQ.ordinal()] = "";
                } else {
                    columns[ColumnNames.TRADE_FEE_BTC.ordinal()] = "";
                    columns[ColumnNames.TRADE_FEE_BSQ.ordinal()] = item.getTradeFeeAsString(false);
                }
                columns[ColumnNames.BUYER_SEC.ordinal()] = item.getBuyerSecurityDepositAsString();
                columns[ColumnNames.SELLER_SEC.ordinal()] = item.getSellerSecurityDepositAsString();
                columns[ColumnNames.OFFER_TYPE.ordinal()] = item.getDirectionLabel();
                columns[ColumnNames.STATUS.ordinal()] = item.getState();
                return columns;
            };

            GUIUtil.exportCSV("tradeHistory.csv", headerConverter, contentConverter,
                    null, sortedList, (Stage) root.getScene().getWindow());
        });

        summaryButton.setOnAction(event -> new ClosedTradesSummaryWindow(model).show());

        root.widthProperty().addListener(widthListener);
        onWidthChange(root.getWidth());
    }

    @Override
    protected void deactivate() {
        sortedList.comparatorProperty().unbind();
        exportButton.setOnAction(null);
        summaryButton.setOnAction(null);

        filterBox.deactivate();
        root.widthProperty().removeListener(widthListener);
    }

    private static <T extends Comparable<T>> Comparator<ClosedTradesListItem> nullsFirstComparing(Function<ClosedTradesListItem, T> keyExtractor) {
        return Comparator.comparing(
                o -> o != null ? keyExtractor.apply(o) : null,
                Comparator.nullsFirst(Comparator.naturalOrder())
        );
    }

    private static <T extends Comparable<T>> Comparator<ClosedTradesListItem> nullsFirstComparingAsTrade(Function<TradeModel, T> keyExtractor) {
        return Comparator.comparing(
                o -> o.getTradable() instanceof TradeModel ? keyExtractor.apply((TradeModel) o.getTradable()) : null,
                Comparator.nullsFirst(Comparator.naturalOrder())
        );
    }

    private void onWidthChange(double width) {
        txFeeColumn.setVisible(width > 1200);
        tradeFeeColumn.setVisible(width > 1300);
        buyerSecurityDepositColumn.setVisible(width > 1400);
        sellerSecurityDepositColumn.setVisible(width > 1500);
    }

    private void setTradeIdColumnCellFactory() {
        tradeIdColumn.getStyleClass().add("first-column");
        tradeIdColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper<>(offerListItem.getValue()));
        tradeIdColumn.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<ClosedTradesListItem, ClosedTradesListItem> call(TableColumn<ClosedTradesListItem,
                            ClosedTradesListItem> column) {
                        return new TableCell<>() {
                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final ClosedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    field = new HyperlinkWithIcon(item.getTradeId());
                                    field.setOnAction(event -> {
                                        Tradable tradable = item.getTradable();
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
        dateColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        dateColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradesListItem, ClosedTradesListItem> call(
                            TableColumn<ClosedTradesListItem, ClosedTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ClosedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setGraphic(new AutoTooltipLabel(item.getDateAsString()));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
    }

    private void setMarketColumnCellFactory() {
        marketColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        marketColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradesListItem, ClosedTradesListItem> call(
                            TableColumn<ClosedTradesListItem, ClosedTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ClosedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setGraphic(new AutoTooltipLabel(item.getMarketLabel()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setStateColumnCellFactory() {
        stateColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        stateColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradesListItem, ClosedTradesListItem> call(
                            TableColumn<ClosedTradesListItem, ClosedTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ClosedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setGraphic(new AutoTooltipLabel(item.getState()));
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
                    public TableCell<ClosedTradesListItem, ClosedTradesListItem> call(TableColumn<ClosedTradesListItem, ClosedTradesListItem> column) {
                        return new TableCell<>() {
                            Button button;

                            @Override
                            public void updateItem(final ClosedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty && isMyOfferAsMaker(item.getTradable().getOffer().getOfferPayloadBase())) {
                                    if (button == null) {
                                        button = getRegularIconButton(MaterialDesignIcon.CONTENT_COPY);
                                        button.setTooltip(new Tooltip(Res.get("shared.duplicateOffer")));
                                        setGraphic(button);
                                    }
                                    button.setOnAction(event -> onDuplicateOffer(item.getTradable().getOffer()));
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
    private TableColumn<ClosedTradesListItem, ClosedTradesListItem> setAvatarColumnCellFactory() {
        avatarColumn.getStyleClass().addAll("last-column", "avatar-column");
        avatarColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        avatarColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradesListItem, ClosedTradesListItem> call(TableColumn<ClosedTradesListItem, ClosedTradesListItem> column) {
                        return new TableCell<>() {

                            @Override
                            public void updateItem(final ClosedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (!empty && item != null && item.getTradable() instanceof TradeModel) {
                                    TradeModel tradeModel = (TradeModel) item.getTradable();
                                    int numPastTrades = item.getNumPastTrades();
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
        amountColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        amountColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradesListItem, ClosedTradesListItem> call(
                            TableColumn<ClosedTradesListItem, ClosedTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ClosedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setGraphic(new AutoTooltipLabel(item.getAmountAsString()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setPriceColumnCellFactory() {
        priceColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        priceColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradesListItem, ClosedTradesListItem> call(
                            TableColumn<ClosedTradesListItem, ClosedTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ClosedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setGraphic(new AutoTooltipLabel(item.getPriceAsString()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setDeviationColumnCellFactory() {
        deviationColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        deviationColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradesListItem, ClosedTradesListItem> call(
                            TableColumn<ClosedTradesListItem, ClosedTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ClosedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setGraphic(new AutoTooltipLabel(item.getPriceDeviationAsString()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setVolumeColumnCellFactory() {
        volumeColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        volumeColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradesListItem, ClosedTradesListItem> call(
                            TableColumn<ClosedTradesListItem, ClosedTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ClosedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setGraphic(new AutoTooltipLabel(item.getVolumeAsString(true)));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
    }

    private void setDirectionColumnCellFactory() {
        directionColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        directionColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradesListItem, ClosedTradesListItem> call(
                            TableColumn<ClosedTradesListItem, ClosedTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ClosedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setGraphic(new AutoTooltipLabel(item.getDirectionLabel()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setTxFeeColumnCellFactory() {
        txFeeColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        txFeeColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradesListItem, ClosedTradesListItem> call(
                            TableColumn<ClosedTradesListItem, ClosedTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ClosedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setGraphic(new AutoTooltipLabel(item.getTxFeeAsString()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setTradeFeeColumnCellFactory() {
        tradeFeeColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        tradeFeeColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradesListItem, ClosedTradesListItem> call(
                            TableColumn<ClosedTradesListItem, ClosedTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ClosedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setGraphic(new AutoTooltipLabel(item.getTradeFeeAsString(true)));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setBuyerSecurityDepositColumnCellFactory() {
        buyerSecurityDepositColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        buyerSecurityDepositColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradesListItem, ClosedTradesListItem> call(
                            TableColumn<ClosedTradesListItem, ClosedTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ClosedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setGraphic(new AutoTooltipLabel(item.getBuyerSecurityDepositAsString()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setSellerSecurityDepositColumnCellFactory() {
        sellerSecurityDepositColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        sellerSecurityDepositColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradesListItem, ClosedTradesListItem> call(
                            TableColumn<ClosedTradesListItem, ClosedTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ClosedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setGraphic(new AutoTooltipLabel(item.getSellerSecurityDepositAsString()));
                                } else {
                                    setGraphic(null);
                                }
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
}
