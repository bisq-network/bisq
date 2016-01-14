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

import io.bitsquare.btc.WalletService;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.popups.Popup;
import io.bitsquare.gui.util.BSFormatter;
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
import java.util.stream.Collectors;

@FxmlView
public class TransactionsView extends ActivatableView<VBox, Void> {

    @FXML
    TableView<TransactionsListItem> table;
    @FXML
    TableColumn<TransactionsListItem, TransactionsListItem> dateColumn, addressColumn, amountColumn, typeColumn,
            confidenceColumn;

    private final ObservableList<TransactionsListItem> transactionsListItems = FXCollections.observableArrayList();

    private final WalletService walletService;
    private final BSFormatter formatter;
    private final Preferences preferences;
    private WalletEventListener walletEventListener;

    @Inject
    private TransactionsView(WalletService walletService, BSFormatter formatter, Preferences preferences) {
        this.walletService = walletService;
        this.formatter = formatter;
        this.preferences = preferences;
    }

    @Override
    public void initialize() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No transactions available"));

        setAddressColumnCellFactory();
        setConfidenceColumnCellFactory();

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

    private void updateList() {
        List<Transaction> transactions = walletService.getWallet().getRecentTransactions(10000, true);
        transactionsListItems.clear();
        transactionsListItems.addAll(transactions.stream().map(transaction ->
                new TransactionsListItem(transaction, walletService, formatter)).collect(Collectors.toList()));

        table.setItems(transactionsListItems);
    }

    @Override
    protected void activate() {
        updateList();
        walletService.getWallet().addEventListener(walletEventListener);
    }

    @Override
    protected void deactivate() {
        transactionsListItems.forEach(TransactionsListItem::cleanup);
        walletService.getWallet().removeEventListener(walletEventListener);
    }

    private void openTxDetails(TransactionsListItem item) {
        // TODO Open popup with details view
        log.debug("openTxDetails " + item);

        if (!item.isNotAnAddress()) {
            try {
                Utilities.openWebPage(preferences.getBlockChainExplorer().addressUrl + item.getAddressString());
            } catch (Exception e) {
                log.error(e.getMessage());
                new Popup().warning("Opening browser failed. Please check your internet " +
                        "connection.").show();
            }
        }
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
                            private Hyperlink hyperlink;

                            @Override
                            public void updateItem(final TransactionsListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    hyperlink = new Hyperlink(item.getAddressString());
                                    hyperlink.setOnAction(event -> openTxDetails(item));
                                    setGraphic(hyperlink);
                                } else {
                                    setGraphic(null);
                                    setId(null);
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

