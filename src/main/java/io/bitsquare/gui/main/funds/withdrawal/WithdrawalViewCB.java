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

package io.bitsquare.gui.main.funds.withdrawal;

import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.Restrictions;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.gui.CachedViewCB;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.util.BSFormatter;

import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.Transaction;

import com.google.common.util.concurrent.FutureCallback;

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
import javafx.scene.input.*;
import javafx.util.Callback;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WithdrawalViewCB extends CachedViewCB {
    private static final Logger log = LoggerFactory.getLogger(WithdrawalViewCB.class);


    private final WalletFacade walletFacade;
    private BSFormatter formatter;
    private final ObservableList<WithdrawalListItem> addressList = FXCollections.observableArrayList();

    @FXML TableView<WithdrawalListItem> table;
    @FXML TableColumn<WithdrawalListItem, WithdrawalListItem> labelColumn, addressColumn, balanceColumn, copyColumn,
            confidenceColumn;
    @FXML Button addNewAddressButton;
    @FXML TextField withdrawFromTextField, withdrawToTextField, amountTextField;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private WithdrawalViewCB(WalletFacade walletFacade, BSFormatter formatter) {
        this.walletFacade = walletFacade;
        this.formatter = formatter;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No funded wallets for withdrawal available"));

        setLabelColumnCellFactory();
        setBalanceColumnCellFactory();
        setCopyColumnCellFactory();
        setConfidenceColumnCellFactory();

        super.initialize(url, rb);
    }

    @Override
    public void activate() {
        super.activate();

        table.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
            if (newValue != null) {

                if (Coin.ZERO.compareTo(newValue.getBalance()) <= 0) {
                    amountTextField.setText(newValue.getBalance().toPlainString());
                    withdrawFromTextField.setText(newValue.getAddressEntry().getAddressString());
                }
                else {
                    withdrawFromTextField.setText("");
                    withdrawFromTextField.setPromptText("No fund to withdrawal on that address.");
                    amountTextField.setText("");
                    amountTextField.setPromptText("Invalid amount");
                }
            }
        });

        fillList();
        table.setItems(addressList);

        walletFacade.addBalanceListener(new BalanceListener() {
            @Override
            public void onBalanceChanged(Coin balance) {
                fillList();
            }
        });
    }

    @Override
    public void deactivate() {
        super.deactivate();

        for (WithdrawalListItem item : addressList)
            item.cleanup();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    public void onWithdraw() {
        Coin amount = formatter.parseToCoin(amountTextField.getText());
        if (Restrictions.isMinSpendableAmount(amount)) {
            FutureCallback<Transaction> callback = new FutureCallback<Transaction>() {
                @Override
                public void onSuccess(@javax.annotation.Nullable Transaction transaction) {
                    if (transaction != null) {
                        log.info("onWithdraw onSuccess tx ID:" + transaction.getHashAsString());
                    }
                }

                @Override
                public void onFailure(@NotNull Throwable t) {
                    log.debug("onWithdraw onFailure");
                }
            };

            Action response = Popups.openConfirmPopup(
                    "Withdrawal request", "Confirm your request",
                    "Your withdrawal request:\n\n" + "Amount: " + amountTextField.getText() + " BTC\n" + "Sending" +
                            " address: " + withdrawFromTextField.getText() + "\n" + "Receiving address: " +
                            withdrawToTextField.getText() + "\n" + "Transaction fee: " +
                            formatter.formatCoinWithCode(FeePolicy.TX_FEE) + "\n" +
                            "You receive in total: " +
                            formatter.formatCoinWithCode(amount.subtract(FeePolicy.TX_FEE)) + " BTC\n\n" +
                            "Are you sure you withdraw that amount?");
            if (response == Dialog.Actions.OK) {
                try {
                    walletFacade.sendFunds(
                            withdrawFromTextField.getText(), withdrawToTextField.getText(),
                            amount, callback);

                    fillList();
                } catch (AddressFormatException e) {
                    Popups.openErrorPopup("Address invalid",
                            "The address is not correct. Please check the address format.");

                } catch (InsufficientMoneyException e) {
                    Popups.openInsufficientMoneyPopup();
                } catch (IllegalArgumentException e) {
                    Popups.openErrorPopup("Wrong inputs", "Please check the inputs.");
                }
            }

        }
        else {
            Popups.openErrorPopup("Insufficient amount",
                    "The amount to transfer is lower the the transaction fee and the min. possible tx value.");
        }

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void fillList() {
        addressList.clear();
        List<AddressEntry> addressEntryList = walletFacade.getAddressEntryList();
        addressList.addAll(addressEntryList.stream()
                .filter(e -> walletFacade.getBalanceForAddress(e.getAddress()).isPositive())
                .map(anAddressEntryList -> new WithdrawalListItem(anAddressEntryList, walletFacade, formatter))
                .collect(Collectors.toList()));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Cell factories
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setLabelColumnCellFactory() {
        labelColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        labelColumn.setCellFactory(new Callback<TableColumn<WithdrawalListItem, WithdrawalListItem>,
                TableCell<WithdrawalListItem,
                        WithdrawalListItem>>() {

            @Override
            public TableCell<WithdrawalListItem, WithdrawalListItem> call(TableColumn<WithdrawalListItem,
                    WithdrawalListItem> column) {
                return new TableCell<WithdrawalListItem, WithdrawalListItem>() {

                    private Hyperlink hyperlink;

                    @Override
                    public void updateItem(final WithdrawalListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            hyperlink = new Hyperlink(item.getLabel());
                            hyperlink.setId("id-link");
                            if (item.getAddressEntry().getOfferId() != null) {
                                Tooltip tooltip = new Tooltip(item.getAddressEntry().getOfferId());
                                Tooltip.install(hyperlink, tooltip);

                                hyperlink.setOnAction(event -> log.info("Show trade details " + item.getAddressEntry
                                        ().getOfferId()));
                            }
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

    private void setBalanceColumnCellFactory() {
        balanceColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        balanceColumn.setCellFactory(
                new Callback<TableColumn<WithdrawalListItem, WithdrawalListItem>, TableCell<WithdrawalListItem,
                        WithdrawalListItem>>() {

                    @Override
                    public TableCell<WithdrawalListItem, WithdrawalListItem> call(TableColumn<WithdrawalListItem,
                            WithdrawalListItem> column) {
                        return new TableCell<WithdrawalListItem, WithdrawalListItem>() {
                            @Override
                            public void updateItem(final WithdrawalListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic((item != null && !empty) ? item.getBalanceLabel() : null);
                            }
                        };
                    }
                });
    }

    private void setCopyColumnCellFactory() {
        copyColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        copyColumn.setCellFactory(
                new Callback<TableColumn<WithdrawalListItem, WithdrawalListItem>, TableCell<WithdrawalListItem,
                        WithdrawalListItem>>() {

                    @Override
                    public TableCell<WithdrawalListItem, WithdrawalListItem> call(TableColumn<WithdrawalListItem,
                            WithdrawalListItem> column) {
                        return new TableCell<WithdrawalListItem, WithdrawalListItem>() {
                            final Label copyIcon = new Label();

                            {
                                copyIcon.getStyleClass().add("copy-icon");
                                AwesomeDude.setIcon(copyIcon, AwesomeIcon.COPY);
                                Tooltip.install(copyIcon, new Tooltip("Copy address to clipboard"));
                            }

                            @Override
                            public void updateItem(final WithdrawalListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    setGraphic(copyIcon);
                                    copyIcon.setOnMouseClicked(e -> {
                                        Clipboard clipboard = Clipboard.getSystemClipboard();
                                        ClipboardContent content = new ClipboardContent();
                                        content.putString(item.addressStringProperty().get());
                                        clipboard.setContent(content);
                                    });

                                }
                                else {
                                    setGraphic(null);
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
                new Callback<TableColumn<WithdrawalListItem, WithdrawalListItem>, TableCell<WithdrawalListItem,
                        WithdrawalListItem>>() {

                    @Override
                    public TableCell<WithdrawalListItem, WithdrawalListItem> call(TableColumn<WithdrawalListItem,
                            WithdrawalListItem> column) {
                        return new TableCell<WithdrawalListItem, WithdrawalListItem>() {

                            @Override
                            public void updateItem(final WithdrawalListItem item, boolean empty) {
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


