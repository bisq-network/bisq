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

import com.google.common.util.concurrent.FutureCallback;
import io.bitsquare.app.BitsquareApp;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.Restrictions;
import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.popups.OfferDetailsPopup;
import io.bitsquare.gui.popups.Popup;
import io.bitsquare.gui.popups.TradeDetailsPopup;
import io.bitsquare.gui.popups.WalletPasswordPopup;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.BtcAddressValidator;
import io.bitsquare.trade.Tradable;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.closed.ClosedTradableManager;
import io.bitsquare.trade.failed.FailedTradesManager;
import io.bitsquare.trade.offer.OpenOffer;
import io.bitsquare.trade.offer.OpenOfferManager;
import io.bitsquare.user.Preferences;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.jetbrains.annotations.NotNull;
import org.spongycastle.crypto.params.KeyParameter;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@FxmlView
public class WithdrawalView extends ActivatableView<VBox, Void> {

    @FXML
    Button withdrawButton;
    @FXML
    TableView<WithdrawalListItem> table;
    @FXML
    TextField withdrawFromTextField, withdrawToTextField, amountTextField;
    @FXML
    TableColumn<WithdrawalListItem, WithdrawalListItem> detailsColumn, addressColumn, balanceColumn, confidenceColumn, selectColumn;

    private final WalletService walletService;
    private final TradeManager tradeManager;
    private final ClosedTradableManager closedTradableManager;
    private final FailedTradesManager failedTradesManager;
    private final OpenOfferManager openOfferManager;
    private final BSFormatter formatter;
    private final Preferences preferences;
    private final BtcAddressValidator btcAddressValidator;
    private final WalletPasswordPopup walletPasswordPopup;
    private final OfferDetailsPopup offerDetailsPopup;
    private final TradeDetailsPopup tradeDetailsPopup;
    private final ObservableList<WithdrawalListItem> addressList = FXCollections.observableArrayList();

    @Inject
    private WithdrawalView(WalletService walletService, TradeManager tradeManager, ClosedTradableManager closedTradableManager,
                           FailedTradesManager failedTradesManager, OpenOfferManager openOfferManager, BSFormatter formatter, Preferences preferences,
                           BtcAddressValidator btcAddressValidator, WalletPasswordPopup walletPasswordPopup,
                           OfferDetailsPopup offerDetailsPopup, TradeDetailsPopup tradeDetailsPopup) {
        this.walletService = walletService;
        this.tradeManager = tradeManager;
        this.closedTradableManager = closedTradableManager;
        this.failedTradesManager = failedTradesManager;
        this.openOfferManager = openOfferManager;
        this.formatter = formatter;
        this.preferences = preferences;
        this.btcAddressValidator = btcAddressValidator;
        this.walletPasswordPopup = walletPasswordPopup;
        this.offerDetailsPopup = offerDetailsPopup;
        this.tradeDetailsPopup = tradeDetailsPopup;
    }


    @Override
    public void initialize() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No funds for withdrawal available"));

        setLabelColumnCellFactory();
        setAddressColumnCellFactory();
        setBalanceColumnCellFactory();
        setConfidenceColumnCellFactory();
        setSelectColumnCellFactory();

        if (BitsquareApp.DEV_MODE)
            withdrawToTextField.setText("mxAkWWaQBqwqcYstKzqLku3kzR6pbu2zHq");
    }

    private boolean areInputsValid() {
        return btcAddressValidator.validate(withdrawFromTextField.getText()).and(
                btcAddressValidator.validate(withdrawToTextField.getText())).isValid;
    }

    private void openTxDetails(WithdrawalListItem item) {
        try {
            Utilities.openWebPage(preferences.getBlockChainExplorer().addressUrl + item.getAddressString());
        } catch (Exception e) {
            log.error(e.getMessage());
            new Popup().warning("Opening browser failed. Please check your internet " +
                    "connection.").show();
        }
    }

    @Override
    protected void activate() {
        withdrawButton.disableProperty().bind(Bindings.createBooleanBinding(() -> !areInputsValid(),
                withdrawFromTextField.textProperty(), amountTextField.textProperty(), withdrawToTextField.textProperty()));
        table.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
            if (newValue != null) {
                if (Coin.ZERO.compareTo(newValue.getBalance()) <= 0) {
                    amountTextField.setText(newValue.getBalance().toPlainString());
                    withdrawFromTextField.setText(newValue.getAddressEntry().getAddressString());
                } else {
                    withdrawFromTextField.setText("");
                    withdrawFromTextField.setPromptText("No fund to withdrawal on that address.");
                    amountTextField.setText("");
                    amountTextField.setPromptText("Invalid amount");
                }
            }
        });

        fillList();
        table.setItems(addressList);

        walletService.addBalanceListener(new BalanceListener() {
            @Override
            public void onBalanceChanged(Coin balance) {
                fillList();
            }
        });
    }

    @Override
    protected void deactivate() {
        addressList.forEach(WithdrawalListItem::cleanup);
        withdrawButton.disableProperty().unbind();
    }

    @FXML
    public void onWithdraw() {
        Coin senderAmount = formatter.parseToCoin(amountTextField.getText());
        if (Restrictions.isAboveDust(senderAmount)) {
            FutureCallback<Transaction> callback = new FutureCallback<Transaction>() {
                @Override
                public void onSuccess(@javax.annotation.Nullable Transaction transaction) {
                    if (transaction != null) {
                        log.info("onWithdraw onSuccess tx ID:" + transaction.getHashAsString());
                    } else {
                        log.error("onWithdraw transaction is null");
                    }
                }

                @Override
                public void onFailure(@NotNull Throwable t) {
                    log.error("onWithdraw onFailure");
                }
            };
            try {
                Coin requiredFee = walletService.getRequiredFee(withdrawFromTextField.getText(),
                        withdrawToTextField.getText(), senderAmount);
                Coin receiverAmount = senderAmount.subtract(requiredFee);
                if (BitsquareApp.DEV_MODE) {
                    doWithdraw(receiverAmount, callback);
                } else {
                    new Popup().headLine("Confirm your withdrawal request")
                            .message("Sending: " + formatter.formatCoinWithCode(senderAmount) + "\n" +
                                    "From address: " + withdrawFromTextField.getText() + "\n" +
                                    "To receiving address: " + withdrawToTextField.getText() + ".\n\n" +
                                    "Required transaction fee is: " + formatter.formatCoinWithCode(requiredFee) + "\n" +
                                    "Recipient will receive: " + formatter.formatCoinWithCode(receiverAmount) + "\n\n" +
                                    "Are you sure you want to withdraw that amount?")
                            .onAction(() -> doWithdraw(receiverAmount, callback))
                            .show();

                }
            } catch (AddressFormatException | InsufficientMoneyException e) {
                e.printStackTrace();
                log.error(e.getMessage());
            }
        } else {
            new Popup().warning("The amount to transfer is lower than the transaction fee and the min. possible tx value.").show();
        }
    }

    private void doWithdraw(Coin amount, FutureCallback<Transaction> callback) {
        if (walletService.getWallet().isEncrypted())
            walletPasswordPopup.show().onAesKey(aesKey -> sendFunds(amount, aesKey, callback));
        else
            sendFunds(amount, null, callback);
        fillList();
    }

    private void sendFunds(Coin amount, KeyParameter aesKey, FutureCallback<Transaction> callback) {
        try {
            walletService.sendFunds(withdrawFromTextField.getText(), withdrawToTextField.getText(), amount, aesKey, callback);
        } catch (AddressFormatException e) {
            new Popup().error("The address is not correct. Please check the address format.").show();
        } catch (InsufficientMoneyException e) {
            log.warn(e.getMessage());
            new Popup().error("You don't have enough fund in your wallet.").show();
        }

        withdrawFromTextField.setText("");
        withdrawFromTextField.setPromptText("Select a source address from the table");
        amountTextField.setText("");
        amountTextField.setPromptText("");
        withdrawToTextField.setText("");
        withdrawToTextField.setPromptText("");
    }

    private void fillList() {
        addressList.clear();

        List<AddressEntry> addressEntryList = walletService.getAddressEntryList();

        List<String> reservedTrades = Stream.concat(openOfferManager.getOpenOffers().stream(), tradeManager.getTrades().stream())
                .map(tradable -> tradable.getOffer().getId())
                .collect(Collectors.toList());

        addressList.addAll(addressEntryList.stream()
                .filter(e -> walletService.getBalanceForAddress(e.getAddress()).isPositive())
                .filter(e -> !reservedTrades.contains(e.getOfferId()))
                .map(anAddressEntryList -> new WithdrawalListItem(anAddressEntryList, walletService, formatter))
                .collect(Collectors.toList()));
    }

    private void setLabelColumnCellFactory() {
        detailsColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        detailsColumn.setCellFactory(new Callback<TableColumn<WithdrawalListItem, WithdrawalListItem>,
                TableCell<WithdrawalListItem,
                        WithdrawalListItem>>() {

            @Override
            public TableCell<WithdrawalListItem, WithdrawalListItem> call(TableColumn<WithdrawalListItem,
                    WithdrawalListItem> column) {
                return new TableCell<WithdrawalListItem, WithdrawalListItem>() {

                    @Override
                    public void updateItem(final WithdrawalListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            if (detailsAvailable(item)) {
                                Hyperlink hyperlink = new Hyperlink(item.getLabel());
                                if (item.getAddressEntry().getOfferId() != null) {
                                    Tooltip tooltip = new Tooltip(item.getAddressEntry().getShortOfferId());
                                    Tooltip.install(hyperlink, tooltip);

                                    hyperlink.setOnAction(event -> openDetails(item));
                                    setGraphic(hyperlink);
                                }
                            } else {
                                Label label = new Label("No info available");
                                setGraphic(label);
                            }

                        } else {
                            setGraphic(null);
                            setId(null);
                        }
                    }
                };
            }
        });
    }

    private void setAddressColumnCellFactory() {
        addressColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        addressColumn.setCellFactory(
                new Callback<TableColumn<WithdrawalListItem, WithdrawalListItem>, TableCell<WithdrawalListItem,
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
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setSelectColumnCellFactory() {
        selectColumn.setCellValueFactory((addressListItem) ->
                new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        selectColumn.setCellFactory(
                new Callback<TableColumn<WithdrawalListItem, WithdrawalListItem>, TableCell<WithdrawalListItem,
                        WithdrawalListItem>>() {

                    @Override
                    public TableCell<WithdrawalListItem, WithdrawalListItem> call(TableColumn<WithdrawalListItem,
                            WithdrawalListItem> column) {
                        return new TableCell<WithdrawalListItem, WithdrawalListItem>() {

                            Button button = new Button("Select");

                            @Override
                            public void updateItem(final WithdrawalListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    button.setDefaultButton(true);
                                    button.setMouseTransparent(true);
                                    setGraphic(button);
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private boolean detailsAvailable(WithdrawalListItem item) {
        String offerId = item.getAddressEntry().getOfferId();
        return closedTradableManager.getTradableById(offerId).isPresent() ||
                failedTradesManager.getTradeById(offerId).isPresent();
    }

    private void openDetails(WithdrawalListItem item) {
        String offerId = item.getAddressEntry().getOfferId();
        Optional<Tradable> tradableOptional = closedTradableManager.getTradableById(offerId);
        if (tradableOptional.isPresent()) {
            Tradable tradable = tradableOptional.get();
            if (tradable instanceof Trade) {
                tradeDetailsPopup.show((Trade) tradable);
            } else if (tradable instanceof OpenOffer) {
                offerDetailsPopup.show(tradable.getOffer());
            }
        } else if (failedTradesManager.getTradeById(offerId).isPresent()) {
            tradeDetailsPopup.show(failedTradesManager.getTradeById(offerId).get());
        } else {
            log.warn("no details available. A test with detailsAvailable() is missing.");
        }
    }
}


