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

import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.CachedViewController;

import com.google.bitcoin.core.Transaction;

import java.net.URL;

import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import javax.inject.Inject;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionsController extends CachedViewController {
    private static final Logger log = LoggerFactory.getLogger(TransactionsController.class);

    private final WalletFacade walletFacade;
    private ObservableList<TransactionsListItem> transactionsListItems;

    @FXML TableView<TransactionsListItem> tableView;
    @FXML TableColumn<String, TransactionsListItem> dateColumn, addressColumn, amountColumn, typeColumn,
            confidenceColumn;
    @FXML Button addNewAddressButton;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private TransactionsController(WalletFacade walletFacade) {
        this.walletFacade = walletFacade;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        setAddressColumnCellFactory();
        setConfidenceColumnCellFactory();
    }

    @Override
    public void deactivate() {
        super.deactivate();

        for (TransactionsListItem transactionsListItem : transactionsListItems)
            transactionsListItem.cleanup();
    }

    @Override
    public void activate() {
        super.activate();

        List<Transaction> transactions = walletFacade.getWallet().getRecentTransactions(10000, true);
        transactionsListItems = FXCollections.observableArrayList();
        transactionsListItems.addAll(transactions.stream().map(transaction ->
                new TransactionsListItem(transaction, walletFacade)).collect(Collectors.toList()));

        tableView.setItems(transactionsListItems);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Cell factories
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setAddressColumnCellFactory() {
        addressColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper(addressListItem.getValue()));
        addressColumn.setCellFactory(
                new Callback<TableColumn<String, TransactionsListItem>, TableCell<String, TransactionsListItem>>() {

                    @Override
                    public TableCell<String, TransactionsListItem> call(TableColumn<String,
                            TransactionsListItem> column) {
                        return new TableCell<String, TransactionsListItem>() {
                            Hyperlink hyperlink;

                            @Override
                            public void updateItem(final TransactionsListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    hyperlink = new Hyperlink(item.getAddressString());
                                    hyperlink.setId("id-link");
                                    hyperlink.setOnAction(event -> log.info("Show trade details " + item
                                            .getAddressString()));
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
                new ReadOnlyObjectWrapper(addressListItem.getValue()));
        confidenceColumn.setCellFactory(
                new Callback<TableColumn<String, TransactionsListItem>, TableCell<String, TransactionsListItem>>() {

                    @Override
                    public TableCell<String, TransactionsListItem> call(TableColumn<String,
                            TransactionsListItem> column) {
                        return new TableCell<String, TransactionsListItem>() {

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

