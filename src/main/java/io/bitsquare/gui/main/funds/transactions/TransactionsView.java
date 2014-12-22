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
import io.bitsquare.util.Utilities;

import org.bitcoinj.core.Transaction;

import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import viewfx.view.FxmlView;
import viewfx.view.support.ActivatableViewAndModel;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Callback;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

@FxmlView
public class TransactionsView extends ActivatableViewAndModel {

    @FXML TableView<TransactionsListItem> table;
    @FXML TableColumn<TransactionsListItem, TransactionsListItem> dateColumn, addressColumn, amountColumn, typeColumn,
            confidenceColumn, copyColumn;

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
        setCopyColumnCellFactory();
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

        try {
            // TODO get the url form the app preferences
            Utilities.openWebPage("https://www.biteasy.com/testnet/addresses/" + item.getAddressString());
        } catch (Exception e) {
            log.error(e.getMessage());
            Popups.openWarningPopup("Warning", "Opening browser failed. Please check your internet " +
                    "connection.");
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

    private void setCopyColumnCellFactory() {
        copyColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        copyColumn.setCellFactory(
                new Callback<TableColumn<TransactionsListItem, TransactionsListItem>, TableCell<TransactionsListItem,
                        TransactionsListItem>>() {

                    @Override
                    public TableCell<TransactionsListItem, TransactionsListItem> call(TableColumn<TransactionsListItem,
                            TransactionsListItem> column) {
                        return new TableCell<TransactionsListItem, TransactionsListItem>() {
                            final Label copyIcon = new Label();

                            {
                                copyIcon.getStyleClass().add("copy-icon");
                                AwesomeDude.setIcon(copyIcon, AwesomeIcon.COPY);
                                Tooltip.install(copyIcon, new Tooltip("Copy address to clipboard"));
                            }

                            @Override
                            public void updateItem(final TransactionsListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    setGraphic(copyIcon);
                                    copyIcon.setOnMouseClicked(e -> Utilities.copyToClipboard(item
                                            .getAddressString()));

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

