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
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.util.BSFormatter;

import org.bitcoinj.core.Transaction;

import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import viewfx.ActivatableViewAndModel;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Callback;

class TransactionsView extends ActivatableViewAndModel {

    @FXML TableView<TransactionsListItem> table;
    @FXML TableColumn<TransactionsListItem, TransactionsListItem> dateColumn, addressColumn, amountColumn, typeColumn,
            confidenceColumn;

    private ObservableList<TransactionsListItem> transactionsListItems;

    private final WalletService walletService;
    private final BSFormatter formatter;

    @Inject
    private TransactionsView(WalletService walletService, BSFormatter formatter) {
        this.walletService = walletService;
        this.formatter = formatter;
    }

    @Override
    public void initialize() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No transactions available"));

        setAddressColumnCellFactory();
        setConfidenceColumnCellFactory();
    }

    @Override
    public void doActivate() {
        List<Transaction> transactions = walletService.getWallet().getRecentTransactions(10000, true);
        transactionsListItems = FXCollections.observableArrayList();
        transactionsListItems.addAll(transactions.stream().map(transaction ->
                new TransactionsListItem(transaction, walletService, formatter)).collect(Collectors.toList()));

        table.setItems(transactionsListItems);
    }

    @Override
    public void doDeactivate() {
        for (TransactionsListItem transactionsListItem : transactionsListItems)
            transactionsListItem.cleanup();
    }

    private void openTxDetails(TransactionsListItem item) {
        // TODO Open popup with details view
        log.debug("openTxDetails " + item);

        Popups.openWarningPopup("Under construction",
                "This will open a details popup but that is not implemented yet.");
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
                                    hyperlink.setId("id-link");
                                    hyperlink.setOnAction(event -> openTxDetails(item));
                                    setGraphic(hyperlink);
                                }
                                else {
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
                                }
                                else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }
}

