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
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bitsquare.app.BitsquareApp;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.Restrictions;
import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.HyperlinkWithIcon;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.main.overlays.windows.OfferDetailsWindow;
import io.bitsquare.gui.main.overlays.windows.TradeDetailsWindow;
import io.bitsquare.gui.main.overlays.windows.WalletPasswordWindow;
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
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.jetbrains.annotations.NotNull;
import org.spongycastle.crypto.params.KeyParameter;

import javax.inject.Inject;
import java.util.Date;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
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
    TableColumn<WithdrawalListItem, WithdrawalListItem> dateColumn, detailsColumn, addressColumn, balanceColumn, selectColumn;

    private final WalletService walletService;
    private final TradeManager tradeManager;
    private final ClosedTradableManager closedTradableManager;
    private final FailedTradesManager failedTradesManager;
    private final OpenOfferManager openOfferManager;
    private final BSFormatter formatter;
    private final Preferences preferences;
    private final BtcAddressValidator btcAddressValidator;
    private final WalletPasswordWindow walletPasswordWindow;
    private final OfferDetailsWindow offerDetailsWindow;
    private final TradeDetailsWindow tradeDetailsWindow;
    private final ObservableList<WithdrawalListItem> fundedAddresses = FXCollections.observableArrayList();
    private Set<WithdrawalListItem> selectedItems = new HashSet<>();
    private BalanceListener balanceListener;
    private Set<String> fromAddresses;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private WithdrawalView(WalletService walletService, TradeManager tradeManager,
                           ClosedTradableManager closedTradableManager,
                           FailedTradesManager failedTradesManager, OpenOfferManager openOfferManager,
                           BSFormatter formatter, Preferences preferences,
                           BtcAddressValidator btcAddressValidator, WalletPasswordWindow walletPasswordWindow,
                           OfferDetailsWindow offerDetailsWindow, TradeDetailsWindow tradeDetailsWindow) {
        this.walletService = walletService;
        this.tradeManager = tradeManager;
        this.closedTradableManager = closedTradableManager;
        this.failedTradesManager = failedTradesManager;
        this.openOfferManager = openOfferManager;
        this.formatter = formatter;
        this.preferences = preferences;
        this.btcAddressValidator = btcAddressValidator;
        this.walletPasswordWindow = walletPasswordWindow;
        this.offerDetailsWindow = offerDetailsWindow;
        this.tradeDetailsWindow = tradeDetailsWindow;
    }

    @Override
    public void initialize() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No funds for withdrawal are available"));
        setDateColumnCellFactory();
        setDetailsColumnCellFactory();
        setAddressColumnCellFactory();
        setBalanceColumnCellFactory();
        setSelectColumnCellFactory();
        table.getSortOrder().add(dateColumn);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        balanceListener = new BalanceListener() {
            @Override
            public void onBalanceChanged(Coin balance, Transaction tx) {
                updateList();
            }
        };
    }

    @Override
    protected void activate() {
        updateList();

        reset();

        walletService.addBalanceListener(balanceListener);
        withdrawButton.disableProperty().bind(Bindings.createBooleanBinding(() -> !areInputsValid(),
                amountTextField.textProperty(), withdrawToTextField.textProperty()));
    }

    @Override
    protected void deactivate() {
        fundedAddresses.forEach(WithdrawalListItem::cleanup);
        withdrawButton.disableProperty().unbind();
        walletService.removeBalanceListener(balanceListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    public void onWithdraw() {
        Coin senderAmount = formatter.parseToCoin(amountTextField.getText());
        if (Restrictions.isAboveFixedTxFeeAndDust(senderAmount)) {
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
                Coin requiredFee = walletService.getRequiredFeeForMultipleAddresses(fromAddresses,
                        withdrawToTextField.getText(), senderAmount, null);
                Coin receiverAmount = senderAmount.subtract(requiredFee);
                if (BitsquareApp.DEV_MODE) {
                    doWithdraw(receiverAmount, callback);
                } else {
                    new Popup().headLine("Confirm withdrawal request")
                            .confirmation("Sending: " + formatter.formatCoinWithCode(senderAmount) + "\n" +
                                    "From address: " + withdrawFromTextField.getText() + "\n" +
                                    "To receiving address: " + withdrawToTextField.getText() + ".\n" +
                                    "Required transaction fee is: " + formatter.formatCoinWithCode(requiredFee) + "\n\n" +
                                    "The recipient will receive: " + formatter.formatCoinWithCode(receiverAmount) + "\n\n" +
                                    "Are you sure you want to withdraw that amount?")
                            .actionButtonText("Yes")
                            .onAction(() -> doWithdraw(receiverAmount, callback))
                            .closeButtonText("Cancel")
                            .show();

                }
            } catch (AddressFormatException e) {
                e.printStackTrace();
                log.error(e.getMessage());
            }
        } else {
            new Popup()
                    .warning("The amount to transfer is lower than the transaction fee and the min. possible tx value (dust).")
                    .show();
        }
    }

    private void selectForWithdrawal(WithdrawalListItem item, boolean isSelected) {
        if (isSelected)
            selectedItems.add(item);
        else
            selectedItems.remove(item);

        fromAddresses = selectedItems.stream()
                .map(WithdrawalListItem::getAddressString)
                .collect(Collectors.toSet());

        if (!selectedItems.isEmpty()) {
            Coin sum = Coin.valueOf(selectedItems.stream().mapToLong(e -> e.getBalance().getValue()).sum());
            if (sum.isPositive()) {
                amountTextField.setText(formatter.formatCoin(sum));
            } else {
                amountTextField.setText("");
                withdrawFromTextField.setText("");
            }

            if (selectedItems.size() == 1) {
                withdrawFromTextField.setText(selectedItems.stream().findAny().get().getAddressEntry().getAddressString());
                withdrawFromTextField.setTooltip(null);
            } else {
                String tooltipText = "Withdraw from multiple addresses:\n" +
                        selectedItems.stream()
                                .map(WithdrawalListItem::getAddressString)
                                .collect(Collectors.joining(",\n"));
                int abbr = Math.max(10, 66 / selectedItems.size());
                String text = "Withdraw from multiple addresses (" +
                        selectedItems.stream()
                                .map(e -> StringUtils.abbreviate(e.getAddressString(), abbr))
                                .collect(Collectors.joining(", ")) +
                        ")";
                withdrawFromTextField.setText(text);
                withdrawFromTextField.setTooltip(new Tooltip(tooltipText));
            }
        } else {
            reset();
        }
    }

    private void openBlockExplorer(WithdrawalListItem item) {
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

    private void openDetailPopup(WithdrawalListItem item) {
        Optional<Tradable> tradableOptional = getTradable(item);
        if (tradableOptional.isPresent()) {
            Tradable tradable = tradableOptional.get();
            if (tradable instanceof Trade) {
                tradeDetailsWindow.show((Trade) tradable);
            } else if (tradable instanceof OpenOffer) {
                offerDetailsWindow.show(tradable.getOffer());
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateList() {
        Set<String> reservedTrades = Stream.concat(openOfferManager.getOpenOffers().stream(), tradeManager.getTrades().stream())
                .map(tradable -> tradable.getOffer().getId())
                .collect(Collectors.toSet());

        fundedAddresses.forEach(WithdrawalListItem::cleanup);
        fundedAddresses.setAll(walletService.getAddressEntryList().stream()
                .filter(e -> walletService.getBalanceForAddress(e.getAddress()).isPositive())
                .filter(e -> !reservedTrades.contains(e.getOfferId()))
                .map(addressEntry -> new WithdrawalListItem(addressEntry, walletService, formatter))
                .collect(Collectors.toList()));

        fundedAddresses.sort((o1, o2) -> {
            Optional<Tradable> tradable1 = getTradable(o1);
            Optional<Tradable> tradable2 = getTradable(o2);
            // if we dont have a date we set it to now as it is likely a recent funding tx
            // TODO get tx date from wallet instead
            Date date1 = new Date();
            Date date2 = new Date();
            if (tradable1.isPresent())
                date1 = tradable1.get().getDate();

            if (tradable2.isPresent())
                date2 = tradable2.get().getDate();

            return date2.compareTo(date1);
        });
        table.setItems(fundedAddresses);
    }

    private void doWithdraw(Coin amount, FutureCallback<Transaction> callback) {
        if (walletService.getWallet().isEncrypted()) {
            walletPasswordWindow.onAesKey(aesKey -> sendFunds(amount, aesKey, callback)).show();
        } else {
            sendFunds(amount, null, callback);
        }
    }

    private void sendFunds(Coin amount, KeyParameter aesKey, FutureCallback<Transaction> callback) {
        try {
            walletService.sendFundsForMultipleAddresses(fromAddresses, withdrawToTextField.getText(), amount, null, aesKey, callback);
            reset();
            updateList();
        } catch (AddressFormatException e) {
            new Popup().warning("The address is not correct. Please check the address format.").show();
        } catch (InsufficientMoneyException e) {
            log.warn(e.getMessage());
            new Popup().warning("You don't have enough fund in your wallet.").show();
        }
    }

    private void reset() {
        selectedItems = new HashSet<>();

        table.getSelectionModel().clearSelection();

        withdrawFromTextField.setText("");
        withdrawFromTextField.setPromptText("Select a source address from the table");
        withdrawFromTextField.setTooltip(null);

        amountTextField.setText("");
        amountTextField.setPromptText("Set the amount to withdraw");

        withdrawToTextField.setText("");
        withdrawToTextField.setPromptText("Fill in your destination address");

        if (BitsquareApp.DEV_MODE)
            withdrawToTextField.setText("mi8k5f9L972VgDaT4LgjAhriC9hHEPL7EW");
    }

    private Optional<Tradable> getTradable(WithdrawalListItem item) {
        String offerId = item.getAddressEntry().getOfferId();
        Optional<Tradable> tradableOptional = closedTradableManager.getTradableById(offerId);
        if (tradableOptional.isPresent()) {
            return tradableOptional;
        } else if (failedTradesManager.getTradeById(offerId).isPresent()) {
            return Optional.of(failedTradesManager.getTradeById(offerId).get());
        } else {
            return Optional.empty();
        }
    }

    private boolean areInputsValid() {
        return btcAddressValidator.validate(withdrawToTextField.getText()).isValid &&
                amountTextField.getText().length() > 0 &&
                Restrictions.isAboveFixedTxFeeAndDust(formatter.parseToCoin(amountTextField.getText()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ColumnCellFactories
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setDateColumnCellFactory() {
        dateColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        dateColumn.setCellFactory(new Callback<TableColumn<WithdrawalListItem, WithdrawalListItem>,
                TableCell<WithdrawalListItem, WithdrawalListItem>>() {

            @Override
            public TableCell<WithdrawalListItem, WithdrawalListItem> call(TableColumn<WithdrawalListItem,
                    WithdrawalListItem> column) {
                return new TableCell<WithdrawalListItem, WithdrawalListItem>() {

                    @Override
                    public void updateItem(final WithdrawalListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            if (getTradable(item).isPresent())
                                setText(formatter.formatDateTime(getTradable(item).get().getDate()));
                            else
                                setText("No date available");
                        } else {
                            setText("");
                        }
                    }
                };
            }
        });
    }

    private void setDetailsColumnCellFactory() {
        detailsColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        detailsColumn.setCellFactory(new Callback<TableColumn<WithdrawalListItem, WithdrawalListItem>,
                TableCell<WithdrawalListItem, WithdrawalListItem>>() {

            @Override
            public TableCell<WithdrawalListItem, WithdrawalListItem> call(TableColumn<WithdrawalListItem,
                    WithdrawalListItem> column) {
                return new TableCell<WithdrawalListItem, WithdrawalListItem>() {

                    private HyperlinkWithIcon field;

                    @Override
                    public void updateItem(final WithdrawalListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            Optional<Tradable> tradableOptional = getTradable(item);
                            if (tradableOptional.isPresent()) {
                                AddressEntry addressEntry = item.getAddressEntry();
                                String details;
                                if (addressEntry.getContext() == AddressEntry.Context.TRADE) {
                                    String prefix;
                                    Tradable tradable = tradableOptional.get();
                                    if (tradable instanceof Trade)
                                        prefix = "Trade ID: ";
                                    else if (tradable instanceof OpenOffer)
                                        prefix = "Offer ID: ";
                                    else
                                        prefix = "";

                                    details = prefix + addressEntry.getShortOfferId();
                                } else if (addressEntry.getContext() == AddressEntry.Context.ARBITRATOR) {
                                    details = "Arbitration fee";
                                } else {
                                    details = "-";
                                }

                                field = new HyperlinkWithIcon(details, AwesomeIcon.INFO_SIGN);
                                field.setOnAction(event -> openDetailPopup(item));
                                field.setTooltip(new Tooltip("Open popup for details"));
                                setGraphic(field);
                            } else if (item.getAddressEntry().getContext() == AddressEntry.Context.ARBITRATOR) {
                                setGraphic(new Label("Arbitrators fee"));
                            } else {
                                setGraphic(new Label("No details available"));
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
                new Callback<TableColumn<WithdrawalListItem, WithdrawalListItem>, TableCell<WithdrawalListItem,
                        WithdrawalListItem>>() {

                    @Override
                    public TableCell<WithdrawalListItem, WithdrawalListItem> call(TableColumn<WithdrawalListItem,
                            WithdrawalListItem> column) {
                        return new TableCell<WithdrawalListItem, WithdrawalListItem>() {
                            private HyperlinkWithIcon hyperlinkWithIcon;

                            @Override
                            public void updateItem(final WithdrawalListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    String address = item.getAddressString();
                                    hyperlinkWithIcon = new HyperlinkWithIcon(address, AwesomeIcon.EXTERNAL_LINK);
                                    hyperlinkWithIcon.setOnAction(event -> openBlockExplorer(item));
                                    hyperlinkWithIcon.setTooltip(new Tooltip("Open external blockchain explorer for " +
                                            "address: " + address));
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

                            CheckBox checkBox;

                            @Override
                            public void updateItem(final WithdrawalListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    if (checkBox == null) {
                                        checkBox = new CheckBox();
                                        checkBox.setOnAction(e -> selectForWithdrawal(item, checkBox.isSelected()));
                                        setGraphic(checkBox);
                                    }
                                } else {
                                    setGraphic(null);
                                    if (checkBox != null) {
                                        checkBox.setOnAction(null);
                                        checkBox = null;
                                    }
                                }
                            }
                        };
                    }
                });
    }
}


