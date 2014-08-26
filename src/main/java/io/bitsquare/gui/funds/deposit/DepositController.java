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

package io.bitsquare.gui.funds.deposit;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.CachedViewController;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.util.Callback;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DepositController extends CachedViewController {
    private static final Logger log = LoggerFactory.getLogger(DepositController.class);

    private final WalletFacade walletFacade;
    private ObservableList<DepositListItem> addressList;

    @FXML private TableView<DepositListItem> tableView;
    @FXML private TableColumn<String, DepositListItem> labelColumn, addressColumn, balanceColumn, copyColumn,
            confidenceColumn;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private DepositController(WalletFacade walletFacade) {
        this.walletFacade = walletFacade;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        setLabelColumnCellFactory();
        setBalanceColumnCellFactory();
        setCopyColumnCellFactory();
        setConfidenceColumnCellFactory();
    }

    @Override
    public void deactivate() {
        super.deactivate();

        for (DepositListItem anAddressList : addressList)
            anAddressList.cleanup();
    }

    @Override
    public void activate() {
        super.activate();

        List<AddressEntry> addressEntryList = walletFacade.getAddressEntryList();
        addressList = FXCollections.observableArrayList();
        addressList.addAll(addressEntryList.stream().map(anAddressEntryList ->
                new DepositListItem(anAddressEntryList, walletFacade)).collect(Collectors.toList()));

        tableView.setItems(addressList);
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

    private void setLabelColumnCellFactory() {
        labelColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper(addressListItem.getValue()));
        labelColumn.setCellFactory(new Callback<TableColumn<String, DepositListItem>, TableCell<String,
                DepositListItem>>() {

            @Override
            public TableCell<String, DepositListItem> call(TableColumn<String, DepositListItem> column) {
                return new TableCell<String, DepositListItem>() {

                    Hyperlink hyperlink;

                    @Override
                    public void updateItem(final DepositListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            hyperlink = new Hyperlink(item.getLabel());
                            hyperlink.setId("id-link");
                            if (item.getAddressEntry().getOfferId() != null) {
                                Tooltip tooltip = new Tooltip(item.getAddressEntry().getOfferId());
                                Tooltip.install(hyperlink, tooltip);

                                hyperlink.setOnAction(event ->
                                        log.info("Show trade details " + item.getAddressEntry().getOfferId()));
                            }
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

    private void setBalanceColumnCellFactory() {
        balanceColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper(addressListItem.getValue()));
        balanceColumn.setCellFactory(
                new Callback<TableColumn<String, DepositListItem>, TableCell<String, DepositListItem>>() {

                    @Override
                    public TableCell<String, DepositListItem> call(TableColumn<String, DepositListItem> column) {
                        return new TableCell<String, DepositListItem>() {
                            @Override
                            public void updateItem(final DepositListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    setGraphic(item.getBalanceLabel());
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setCopyColumnCellFactory() {
        copyColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper(addressListItem.getValue()));
        copyColumn.setCellFactory(
                new Callback<TableColumn<String, DepositListItem>, TableCell<String, DepositListItem>>() {

                    @Override
                    public TableCell<String, DepositListItem> call(TableColumn<String, DepositListItem> column) {
                        return new TableCell<String, DepositListItem>() {
                            final Label copyIcon = new Label();

                            {
                                copyIcon.getStyleClass().add("copy-icon");
                                AwesomeDude.setIcon(copyIcon, AwesomeIcon.COPY);
                                Tooltip.install(copyIcon, new Tooltip("Copy address to clipboard"));
                            }

                            @Override
                            public void updateItem(final DepositListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    setGraphic(copyIcon);
                                    copyIcon.setOnMouseClicked(e -> {
                                        Clipboard clipboard = Clipboard.getSystemClipboard();
                                        ClipboardContent content = new ClipboardContent();
                                        content.putString(item.addressStringProperty().get());
                                        clipboard.setContent(content);
                                    });

                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setConfidenceColumnCellFactory() {
        confidenceColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper(addressListItem.getValue
                ()));
        confidenceColumn.setCellFactory(new Callback<TableColumn<String, DepositListItem>, TableCell<String,
                DepositListItem>>() {

            @Override
            public TableCell<String, DepositListItem> call(TableColumn<String, DepositListItem> column) {
                return new TableCell<String, DepositListItem>() {

                    @Override
                    public void updateItem(final DepositListItem item, boolean empty) {
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

