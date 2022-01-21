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

package bisq.desktop.main.funds.transactions;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AddressWithIconAndDirection;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.ExternalHyperlink;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.list.FilterBox;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.BsqTradeDetailsWindow;
import bisq.desktop.main.overlays.windows.OfferDetailsWindow;
import bisq.desktop.main.overlays.windows.TradeDetailsWindow;
import bisq.desktop.util.GUIUtil;

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.locale.Res;
import bisq.core.offer.OpenOffer;
import bisq.core.support.dispute.arbitration.ArbitrationManager;
import bisq.core.support.dispute.refund.RefundManager;
import bisq.core.trade.model.Tradable;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.user.Preferences;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.p2p.P2PService;

import bisq.common.crypto.PubKeyRing;
import bisq.common.util.Utilities;

import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.wallet.listeners.WalletChangeEventListener;

import com.googlecode.jcsv.writer.CSVEntryConverter;

import javax.inject.Inject;
import javax.inject.Named;

import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.fxml.FXML;

import javafx.stage.Stage;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;

import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.event.EventHandler;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

@FxmlView
public class TransactionsView extends ActivatableView<VBox, Void> {
    @FXML
    FilterBox filterBox;
    @FXML
    TableView<TransactionsListItem> tableView;
    @FXML
    TableColumn<TransactionsListItem, TransactionsListItem> dateColumn, detailsColumn, addressColumn, transactionColumn, amountColumn, memoColumn, confidenceColumn, revertTxColumn;
    @FXML
    Label numItems;
    @FXML
    Region spacer;
    @FXML
    AutoTooltipButton exportButton;

    private final ObservableList<TransactionsListItem> observableList = FXCollections.observableArrayList();
    private final FilteredList<TransactionsListItem> filteredList = new FilteredList<>(observableList);
    private final SortedList<TransactionsListItem> sortedList = new SortedList<>(filteredList);

    private final BtcWalletService btcWalletService;
    private final BsqWalletService bsqWalletService;
    private final CoinFormatter formatter;
    private final DaoFacade daoFacade;
    private final P2PService p2PService;
    private final WalletsSetup walletsSetup;
    private final Preferences preferences;
    private final TradableRepository tradableRepository;
    private final ArbitrationManager arbitrationManager;
    private final RefundManager refundManager;
    private final PubKeyRing pubKeyRing;
    private final TradeDetailsWindow tradeDetailsWindow;
    private final BsqTradeDetailsWindow bsqTradeDetailsWindow;
    private final OfferDetailsWindow offerDetailsWindow;

    private WalletChangeEventListener walletChangeEventListener;

    private EventHandler<KeyEvent> keyEventEventHandler;
    private Scene scene;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private TransactionsView(BtcWalletService btcWalletService,
                             BsqWalletService bsqWalletService,
                             DaoFacade daoFacade,
                             @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter formatter,
                             P2PService p2PService,
                             WalletsSetup walletsSetup,
                             Preferences preferences,
                             TradableRepository tradableRepository,
                             ArbitrationManager arbitrationManager,
                             RefundManager refundManager,
                             PubKeyRing pubKeyRing,
                             TradeDetailsWindow tradeDetailsWindow,
                             BsqTradeDetailsWindow bsqTradeDetailsWindow,
                             OfferDetailsWindow offerDetailsWindow) {
        this.btcWalletService = btcWalletService;
        this.bsqWalletService = bsqWalletService;
        this.daoFacade = daoFacade;
        this.formatter = formatter;
        this.p2PService = p2PService;
        this.walletsSetup = walletsSetup;
        this.preferences = preferences;
        this.tradableRepository = tradableRepository;
        this.arbitrationManager = arbitrationManager;
        this.refundManager = refundManager;
        this.pubKeyRing = pubKeyRing;
        this.tradeDetailsWindow = tradeDetailsWindow;
        this.bsqTradeDetailsWindow = bsqTradeDetailsWindow;
        this.offerDetailsWindow = offerDetailsWindow;
    }

    @Override
    public void initialize() {
        filterBox.initialize(filteredList, tableView);
        dateColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.dateTime")));
        detailsColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.details")));
        addressColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.address")));
        transactionColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.txId", Res.getBaseCurrencyCode())));
        amountColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.amountWithCur", Res.getBaseCurrencyCode())));
        memoColumn.setGraphic(new AutoTooltipLabel(Res.get("funds.tx.memo")));
        confidenceColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.confirmations", Res.getBaseCurrencyCode())));
        revertTxColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.revert", Res.getBaseCurrencyCode())));

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("funds.tx.noTxAvailable")));

        setDateColumnCellFactory();
        setDetailsColumnCellFactory();
        setAddressColumnCellFactory();
        setTransactionColumnCellFactory();
        setAmountColumnCellFactory();
        setMemoColumnCellFactory();
        setConfidenceColumnCellFactory();
        setRevertTxColumnCellFactory();

        dateColumn.setComparator(Comparator.comparing(TransactionsListItem::getDate));
        detailsColumn.setComparator((o1, o2) -> {
            String id1 = !o1.getDetails().isEmpty() ? o1.getDetails() :
                    o1.getTradable() != null ? o1.getTradable().getId() : o1.getTxId();
            String id2 = !o2.getDetails().isEmpty() ? o2.getDetails() :
                    o2.getTradable() != null ? o2.getTradable().getId() : o2.getTxId();
            return id1.compareTo(id2);
        });
        addressColumn.setComparator(Comparator.comparing(item -> item.getDirection() + item.getAddressString()));
        transactionColumn.setComparator(Comparator.comparing(TransactionsListItem::getTxId));
        amountColumn.setComparator(Comparator.comparing(TransactionsListItem::getAmountAsCoin));
        confidenceColumn.setComparator(Comparator.comparingDouble(item -> item.getTxConfidenceIndicator().getProgress()));
        memoColumn.setComparator(Comparator.comparing(TransactionsListItem::getMemo));

        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(dateColumn);

        walletChangeEventListener = wallet -> {
            updateList();
        };

        keyEventEventHandler = event -> {
            // Not intended to be public to users as the feature is not well tested
            if (Utilities.isAltOrCtrlPressed(KeyCode.R, event)) {
                if (revertTxColumn.isVisible()) {
                    confidenceColumn.getStyleClass().remove("last-column");
                } else {
                    confidenceColumn.getStyleClass().add("last-column");
                }
                revertTxColumn.setVisible(!revertTxColumn.isVisible());
            }
        };

        HBox.setHgrow(spacer, Priority.ALWAYS);
        numItems.setId("num-offers");
        numItems.setPadding(new Insets(-5, 0, 0, 10));
        exportButton.updateText(Res.get("shared.exportCSV"));
    }

    @Override
    protected void activate() {
        filterBox.activate();
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);
        updateList();

        btcWalletService.addChangeEventListener(walletChangeEventListener);

        scene = root.getScene();
        if (scene != null)
            scene.addEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);

        numItems.setText(Res.get("shared.numItemsLabel", sortedList.size()));
        exportButton.setOnAction(event -> {
            final ObservableList<TableColumn<TransactionsListItem, ?>> tableColumns = tableView.getColumns();
            final int reportColumns = tableColumns.size() - 1;    // CSV report excludes the last column (an icon)
            CSVEntryConverter<TransactionsListItem> headerConverter = item -> {
                String[] columns = new String[reportColumns];
                for (int i = 0; i < columns.length; i++)
                    columns[i] = ((AutoTooltipLabel) tableColumns.get(i).getGraphic()).getText();
                return columns;
            };
            CSVEntryConverter<TransactionsListItem> contentConverter = item -> {
                String[] columns = new String[reportColumns];
                columns[0] = item.getDateString();
                columns[1] = item.getDetails();
                columns[2] = item.getDirection() + " " + item.getAddressString();
                columns[3] = item.getTxId();
                columns[4] = item.getAmount();
                columns[5] = item.getMemo() == null ? "" : item.getMemo();
                columns[6] = item.getNumConfirmations();
                return columns;
            };

            GUIUtil.exportCSV("transactions.csv", headerConverter, contentConverter,
                    new TransactionsListItem(), sortedList, (Stage) root.getScene().getWindow());
        });
    }

    @Override
    protected void deactivate() {
        filterBox.deactivate();
        sortedList.comparatorProperty().unbind();
        observableList.forEach(TransactionsListItem::cleanup);
        btcWalletService.removeChangeEventListener(walletChangeEventListener);

        if (scene != null)
            scene.removeEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);

        exportButton.setOnAction(null);
    }

    private void updateList() {
        List<TransactionsListItem> transactionsListItems = btcWalletService.getTransactions(false)
                .stream()
                .map(transaction -> {
                    Set<Tradable> tradables = tradableRepository.getAll();

                    TransactionAwareTradable maybeTradable = tradables.stream()
                            .map(tradable -> {
                                if (tradable instanceof OpenOffer) {
                                    return new TransactionAwareOpenOffer((OpenOffer) tradable);
                                } else if (tradable instanceof TradeModel) {
                                    return new TransactionAwareTrade(
                                            (TradeModel) tradable,
                                            arbitrationManager,
                                            refundManager,
                                            btcWalletService,
                                            pubKeyRing
                                    );
                                } else {
                                    return null;
                                }
                            })
                            .filter(tradable -> tradable != null && tradable.isRelatedToTransaction(transaction))
                            .findAny()
                            .orElse(null);

                    return new TransactionsListItem(
                            transaction,
                            btcWalletService,
                            bsqWalletService,
                            maybeTradable,
                            daoFacade,
                            formatter,
                            preferences.getIgnoreDustThreshold()
                    );
                })
                .collect(Collectors.toList());
        // are sorted by getRecentTransactions
        transactionsListItems.forEach(TransactionsListItem::cleanup);
        observableList.setAll(transactionsListItems);
    }

    private void openTxInBlockExplorer(TransactionsListItem item) {
        if (item.getTxId() != null)
            GUIUtil.openWebPage(preferences.getBlockChainExplorer().txUrl + item.getTxId(), false);
    }

    private void openAddressInBlockExplorer(TransactionsListItem item) {
        if (item.getAddressString() != null) {
            GUIUtil.openWebPage(preferences.getBlockChainExplorer().addressUrl + item.getAddressString(), false);
        }
    }

    private void openDetailPopup(TransactionsListItem item) {
        if (item.getTradable() instanceof OpenOffer) {
            offerDetailsWindow.show(item.getTradable().getOffer());
        } else if ((item.getTradable()) instanceof Trade) {
            tradeDetailsWindow.show((Trade) item.getTradable());
        } else if ((item.getTradable()) instanceof BsqSwapTrade) {
            bsqTradeDetailsWindow.show((BsqSwapTrade) item.getTradable());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ColumnCellFactories
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setDateColumnCellFactory() {
        dateColumn.getStyleClass().add("first-column");
        dateColumn.setCellValueFactory((addressListItem) ->
                new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        dateColumn.setMaxWidth(200);
        dateColumn.setMinWidth(dateColumn.getMaxWidth());
        dateColumn.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<TransactionsListItem, TransactionsListItem> call(TableColumn<TransactionsListItem,
                            TransactionsListItem> column) {
                        return new TableCell<>() {

                            @Override
                            public void updateItem(final TransactionsListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    setGraphic(new AutoTooltipLabel(item.getDateString()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setDetailsColumnCellFactory() {
        detailsColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        detailsColumn.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<TransactionsListItem, TransactionsListItem> call(TableColumn<TransactionsListItem,
                            TransactionsListItem> column) {
                        return new TableCell<>() {

                            private HyperlinkWithIcon hyperlinkWithIcon;

                            @Override
                            public void updateItem(final TransactionsListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    if (item.getDetailsAvailable()) {
                                        hyperlinkWithIcon = new HyperlinkWithIcon(item.getDetails(), AwesomeIcon.INFO_SIGN);
                                        hyperlinkWithIcon.setOnAction(event -> openDetailPopup(item));
                                        hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("tooltip.openPopupForDetails")));
                                        setGraphic(hyperlinkWithIcon);
                                        // If details are available its a trade tx and we don't expect any dust attack tx
                                    } else {
                                        if (item.isDustAttackTx()) {
                                            hyperlinkWithIcon = new HyperlinkWithIcon(item.getDetails(), AwesomeIcon.WARNING_SIGN);
                                            hyperlinkWithIcon.setOnAction(event -> new Popup().warning(Res.get("funds.tx.dustAttackTx.popup")).show());
                                            setGraphic(hyperlinkWithIcon);
                                        } else {
                                            setGraphic(new AutoTooltipLabel(item.getDetails()));
                                        }
                                    }
                                } else {
                                    setGraphic(null);
                                    if (hyperlinkWithIcon != null)
                                        hyperlinkWithIcon.setOnAction(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setAddressColumnCellFactory() {
        addressColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper<>(addressListItem.getValue()));

        addressColumn.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<TransactionsListItem, TransactionsListItem> call(TableColumn<TransactionsListItem,
                            TransactionsListItem> column) {
                        return new TableCell<>() {

                            private AddressWithIconAndDirection field;

                            @Override
                            public void updateItem(final TransactionsListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    String addressString = item.getAddressString();
                                    field = new AddressWithIconAndDirection(item.getDirection(), addressString,
                                            item.getReceived());
                                    field.setOnAction(event -> openAddressInBlockExplorer(item));
                                    field.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForAddress", addressString)));
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

    private void setTransactionColumnCellFactory() {
        transactionColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        transactionColumn.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<TransactionsListItem, TransactionsListItem> call(TableColumn<TransactionsListItem,
                            TransactionsListItem> column) {
                        return new TableCell<>() {
                            private HyperlinkWithIcon hyperlinkWithIcon;

                            @Override
                            public void updateItem(final TransactionsListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                //noinspection Duplicates
                                if (item != null && !empty) {
                                    String transactionId = item.getTxId();
                                    hyperlinkWithIcon = new ExternalHyperlink(transactionId);
                                    hyperlinkWithIcon.setOnAction(event -> openTxInBlockExplorer(item));
                                    hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForTx", transactionId)));
                                    setGraphic(hyperlinkWithIcon);
                                } else {
                                    setGraphic(null);
                                    if (hyperlinkWithIcon != null)
                                        hyperlinkWithIcon.setOnAction(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setAmountColumnCellFactory() {
        amountColumn.setCellValueFactory((addressListItem) ->
                new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        amountColumn.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<TransactionsListItem, TransactionsListItem> call(TableColumn<TransactionsListItem,
                            TransactionsListItem> column) {
                        return new TableCell<>() {

                            @Override
                            public void updateItem(final TransactionsListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    setGraphic(new AutoTooltipLabel(item.getAmount()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setMemoColumnCellFactory() {
        memoColumn.setCellValueFactory((addressListItem) ->
                new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        memoColumn.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<TransactionsListItem, TransactionsListItem> call(TableColumn<TransactionsListItem,
                            TransactionsListItem> column) {
                        return new TableCell<>() {

                            @Override
                            public void updateItem(final TransactionsListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    setGraphic(new AutoTooltipLabel(item.getMemo()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setConfidenceColumnCellFactory() {
        confidenceColumn.getStyleClass().add("last-column");
        confidenceColumn.setCellValueFactory((addressListItem) ->
                new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        confidenceColumn.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<TransactionsListItem, TransactionsListItem> call(TableColumn<TransactionsListItem,
                            TransactionsListItem> column) {
                        return new TableCell<>() {

                            @Override
                            public void updateItem(final TransactionsListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    setGraphic(item.getTxConfidenceIndicator());
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setRevertTxColumnCellFactory() {
        revertTxColumn.getStyleClass().add("last-column");
        revertTxColumn.setCellValueFactory((addressListItem) ->
                new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        revertTxColumn.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<TransactionsListItem, TransactionsListItem> call(TableColumn<TransactionsListItem,
                            TransactionsListItem> column) {
                        return new TableCell<>() {
                            Button button;

                            @Override
                            public void updateItem(final TransactionsListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    TransactionConfidence confidence = btcWalletService.getConfidenceForTxId(item.getTxId());
                                    if (confidence != null) {
                                        if (confidence.getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING) {
                                            if (button == null) {
                                                button = new AutoTooltipButton(Res.get("funds.tx.revert"));
                                                setGraphic(button);
                                            }
                                            button.setOnAction(e -> revertTransaction(item.getTxId(), item.getTradable()));
                                        } else {
                                            setGraphic(null);
                                            if (button != null) {
                                                button.setOnAction(null);
                                                button = null;
                                            }
                                        }
                                    }
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

    private void revertTransaction(String txId, @Nullable Tradable tradable) {
        if (GUIUtil.isReadyForTxBroadcastOrShowPopup(p2PService, walletsSetup)) {
            try {
                btcWalletService.doubleSpendTransaction(txId, () -> {
                    if (tradable != null)
                        btcWalletService.swapAnyTradeEntryContextToAvailableEntry(tradable.getId());

                    new Popup().information(Res.get("funds.tx.txSent")).show();
                }, errorMessage -> new Popup().warning(errorMessage).show());
            } catch (Throwable e) {
                new Popup().warning(e.getMessage()).show();
            }
        }
    }
}

