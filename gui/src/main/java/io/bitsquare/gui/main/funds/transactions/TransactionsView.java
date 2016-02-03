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

package io.bitsquare.gui.main.funds.transactions;

import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bitsquare.arbitration.DisputeManager;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.AddressWithIconAndDirection;
import io.bitsquare.gui.components.HyperlinkWithIcon;
import io.bitsquare.gui.popups.OfferDetailsPopup;
import io.bitsquare.gui.popups.Popup;
import io.bitsquare.gui.popups.TradeDetailsPopup;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.trade.Tradable;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.closed.ClosedTradableManager;
import io.bitsquare.trade.failed.FailedTradesManager;
import io.bitsquare.trade.offer.OpenOffer;
import io.bitsquare.trade.offer.OpenOfferManager;
import io.bitsquare.user.Preferences;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@FxmlView
public class TransactionsView extends ActivatableView<VBox, Void> {

    @FXML
    TableView<TransactionsListItem> table;
    @FXML
    TableColumn<TransactionsListItem, TransactionsListItem> dateColumn, detailsColumn, addressColumn, transactionColumn, amountColumn, typeColumn,
            confidenceColumn;

    private final ObservableList<TransactionsListItem> transactionsListItems = FXCollections.observableArrayList();

    private final WalletService walletService;
    private final TradeManager tradeManager;
    private final OpenOfferManager openOfferManager;
    private final ClosedTradableManager closedTradableManager;
    private final FailedTradesManager failedTradesManager;
    private final BSFormatter formatter;
    private final Preferences preferences;
    private final TradeDetailsPopup tradeDetailsPopup;
    private DisputeManager disputeManager;
    private final OfferDetailsPopup offerDetailsPopup;
    private WalletEventListener walletEventListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private TransactionsView(WalletService walletService, TradeManager tradeManager, OpenOfferManager openOfferManager,
                             ClosedTradableManager closedTradableManager, FailedTradesManager failedTradesManager,
                             BSFormatter formatter, Preferences preferences, TradeDetailsPopup tradeDetailsPopup,
                             DisputeManager disputeManager,
                             OfferDetailsPopup offerDetailsPopup) {
        this.walletService = walletService;
        this.tradeManager = tradeManager;
        this.openOfferManager = openOfferManager;
        this.closedTradableManager = closedTradableManager;
        this.failedTradesManager = failedTradesManager;
        this.formatter = formatter;
        this.preferences = preferences;
        this.tradeDetailsPopup = tradeDetailsPopup;
        this.disputeManager = disputeManager;
        this.offerDetailsPopup = offerDetailsPopup;
    }

    @Override
    public void initialize() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No transactions available"));
        setDetailsColumnCellFactory();
        setAddressColumnCellFactory();
        setTransactionColumnCellFactory();
        setConfidenceColumnCellFactory();
        table.getSortOrder().add(dateColumn);
        
        walletEventListener = new WalletEventListener() {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                updateList();
            }

            @Override
            public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                updateList();
            }

            @Override
            public void onReorganize(Wallet wallet) {
                updateList();
            }

            @Override
            public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
            }

            @Override
            public void onWalletChanged(Wallet wallet) {
                updateList();
            }

            @Override
            public void onScriptsChanged(Wallet wallet, List<Script> scripts, boolean isAddingScripts) {
                updateList();
            }

            @Override
            public void onKeysAdded(List<ECKey> keys) {
                updateList();
            }
        };
    }

    @Override
    protected void activate() {
        updateList();
        table.setItems(transactionsListItems);
        walletService.getWallet().addEventListener(walletEventListener);
    }

    @Override
    protected void deactivate() {
        transactionsListItems.forEach(TransactionsListItem::cleanup);
        walletService.getWallet().removeEventListener(walletEventListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateList() {
        Stream<Tradable> concat1 = Stream.concat(openOfferManager.getOpenOffers().stream(), tradeManager.getTrades().stream());
        Stream<Tradable> concat2 = Stream.concat(concat1, closedTradableManager.getClosedTrades().stream());
        Stream<Tradable> concat3 = Stream.concat(concat2, failedTradesManager.getFailedTrades().stream());
        Set<Tradable> allTradables = concat3.collect(Collectors.toSet());

        List<TransactionsListItem> listItems = walletService.getWallet().getRecentTransactions(1000, true).stream()
                .map(transaction -> {
                    Optional<Tradable> tradableOptional = allTradables.stream()
                            .filter(e -> {
                                String txId = transaction.getHashAsString();
                                if (e instanceof OpenOffer)
                                    return e.getOffer().getOfferFeePaymentTxID().equals(txId);
                                else if (e instanceof Trade) {
                                    Trade trade = (Trade) e;
                                    return (trade.getTakeOfferFeeTx() != null &&
                                            trade.getTakeOfferFeeTx().getHashAsString().equals(txId)) ||
                                            (trade.getOffer() != null &&
                                                    trade.getOffer().getOfferFeePaymentTxID() != null &&
                                                    trade.getOffer().getOfferFeePaymentTxID().equals(txId)) ||
                                            (trade.getDepositTx() != null &&
                                                    trade.getDepositTx().getHashAsString().equals(txId)) ||
                                            (trade.getPayoutTx() != null &&
                                                    trade.getPayoutTx().getHashAsString().equals(txId)) ||
                                            (disputeManager.getDisputesAsObservableList().stream()
                                                    .filter(dispute -> dispute.getDisputePayoutTx() != null &&
                                                            dispute.getDisputePayoutTx().getHashAsString().equals(txId))
                                                    .findAny()
                                                    .isPresent());
                                } else
                                    return false;
                            })
                            .findAny();
                    return new TransactionsListItem(transaction, walletService, tradableOptional, formatter);
                })
                .collect(Collectors.toList());

        // are sorted by getRecentTransactions
        transactionsListItems.forEach(TransactionsListItem::cleanup);
        transactionsListItems.setAll(listItems);
    }

    private void openBlockExplorer(TransactionsListItem item) {
        if (item.getAddressString() != null) {
            try {
                Utilities.openWebPage(preferences.getBlockChainExplorer().addressUrl + item.getAddressString());
            } catch (Exception e) {
                log.error(e.getMessage());
                new Popup().warning("Opening browser failed. Please check your internet " +
                        "connection.").show();
            }
        }
    }

    private void openDetailPopup(TransactionsListItem item) {
        if (item.getTradable() instanceof OpenOffer)
            offerDetailsPopup.show(item.getTradable().getOffer());
        else if (item.getTradable() instanceof Trade)
            tradeDetailsPopup.show((Trade) item.getTradable());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ColumnCellFactories
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setDetailsColumnCellFactory() {
        detailsColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        detailsColumn.setCellFactory(
                new Callback<TableColumn<TransactionsListItem, TransactionsListItem>, TableCell<TransactionsListItem,
                        TransactionsListItem>>() {

                    @Override
                    public TableCell<TransactionsListItem, TransactionsListItem> call(TableColumn<TransactionsListItem,
                            TransactionsListItem> column) {
                        return new TableCell<TransactionsListItem, TransactionsListItem>() {

                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final TransactionsListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    if (item.getDetailsAvailable()) {
                                        field = new HyperlinkWithIcon(item.getDetails(), AwesomeIcon.INFO_SIGN);
                                        field.setOnAction(event -> openDetailPopup(item));
                                        field.setTooltip(new Tooltip("Open popup for details"));
                                        setGraphic(field);
                                    } else {
                                        setGraphic(new Label(item.getDetails()));
                                    }
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

    private void setAddressColumnCellFactory() {
        addressColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        addressColumn.setCellFactory(
                new Callback<TableColumn<TransactionsListItem, TransactionsListItem>, TableCell<TransactionsListItem,
                        TransactionsListItem>>() {

                    @Override
                    public TableCell<TransactionsListItem, TransactionsListItem> call(TableColumn<TransactionsListItem,
                            TransactionsListItem> column) {
                        return new TableCell<TransactionsListItem, TransactionsListItem>() {

                            private AddressWithIconAndDirection field;

                            @Override
                            public void updateItem(final TransactionsListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    String addressString = item.getAddressString();
                                    field = new AddressWithIconAndDirection(item.getDirection(), addressString,
                                            AwesomeIcon.EXTERNAL_LINK, item.getReceived());
                                    field.setOnAction(event -> openBlockExplorer(item));
                                    field.setTooltip(new Tooltip("Open external blockchain explorer for " +
                                            "address: " + addressString));
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
                new Callback<TableColumn<TransactionsListItem, TransactionsListItem>, TableCell<TransactionsListItem,
                        TransactionsListItem>>() {

                    @Override
                    public TableCell<TransactionsListItem, TransactionsListItem> call(TableColumn<TransactionsListItem,
                            TransactionsListItem> column) {
                        return new TableCell<TransactionsListItem, TransactionsListItem>() {
                            private HyperlinkWithIcon hyperlinkWithIcon;

                            @Override
                            public void updateItem(final TransactionsListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    String transactionId = item.getTxId();
                                    hyperlinkWithIcon = new HyperlinkWithIcon(transactionId, AwesomeIcon.EXTERNAL_LINK);
                                    hyperlinkWithIcon.setOnAction(event -> openBlockExplorer(item));
                                    hyperlinkWithIcon.setTooltip(new Tooltip("Open external blockchain explorer for " +
                                            "transaction: " + transactionId));
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

    private void setConfidenceColumnCellFactory() {
        confidenceColumn.setCellValueFactory((addressListItem) ->
                new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        confidenceColumn.setCellFactory(
                new Callback<TableColumn<TransactionsListItem, TransactionsListItem>, TableCell<TransactionsListItem,
                        TransactionsListItem>>() {

                    @Override
                    public TableCell<TransactionsListItem, TransactionsListItem> call(TableColumn<TransactionsListItem,
                            TransactionsListItem> column) {
                        return new TableCell<TransactionsListItem, TransactionsListItem>() {

                            @Override
                            public void updateItem(final TransactionsListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    setGraphic(item.getProgressIndicator());
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }
}

