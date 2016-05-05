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
import io.bitsquare.btc.AddressEntryException;
import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.HyperlinkWithIcon;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.main.overlays.windows.WalletPasswordWindow;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.BtcAddressValidator;
import io.bitsquare.trade.Tradable;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.closed.ClosedTradableManager;
import io.bitsquare.trade.failed.FailedTradesManager;
import io.bitsquare.user.Preferences;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.*;
import org.jetbrains.annotations.NotNull;
import org.spongycastle.crypto.params.KeyParameter;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@FxmlView
public class WithdrawalView extends ActivatableView<VBox, Void> {

    @FXML
    Button withdrawButton;
    @FXML
    TableView<WithdrawalListItem> tableView;
    @FXML
    TextField withdrawFromTextField, withdrawToTextField, amountTextField;
    @FXML
    TableColumn<WithdrawalListItem, WithdrawalListItem> addressColumn, balanceColumn, selectColumn;

    private final WalletService walletService;
    private final TradeManager tradeManager;
    private final ClosedTradableManager closedTradableManager;
    private final FailedTradesManager failedTradesManager;
    private final BSFormatter formatter;
    private final Preferences preferences;
    private final BtcAddressValidator btcAddressValidator;
    private final WalletPasswordWindow walletPasswordWindow;
    private final ObservableList<WithdrawalListItem> observableList = FXCollections.observableArrayList();
    private final SortedList<WithdrawalListItem> sortedList = new SortedList<>(observableList);
    private Set<WithdrawalListItem> selectedItems = new HashSet<>();
    private BalanceListener balanceListener;
    private Set<String> fromAddresses;
    private Coin amountOfSelectedItems = Coin.ZERO;
    private ObjectProperty<Coin> senderAmountAsCoinProperty = new SimpleObjectProperty<>(Coin.ZERO);
    private ChangeListener<String> amountListener;
    private ChangeListener<Boolean> amountFocusListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private WithdrawalView(WalletService walletService, TradeManager tradeManager,
                           ClosedTradableManager closedTradableManager,
                           FailedTradesManager failedTradesManager,
                           BSFormatter formatter, Preferences preferences,
                           BtcAddressValidator btcAddressValidator, WalletPasswordWindow walletPasswordWindow) {
        this.walletService = walletService;
        this.tradeManager = tradeManager;
        this.closedTradableManager = closedTradableManager;
        this.failedTradesManager = failedTradesManager;
        this.formatter = formatter;
        this.preferences = preferences;
        this.btcAddressValidator = btcAddressValidator;
        this.walletPasswordWindow = walletPasswordWindow;
    }

    @Override
    public void initialize() {
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new Label("No funds are available for withdrawal"));
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        setAddressColumnCellFactory();
        setBalanceColumnCellFactory();
        setSelectColumnCellFactory();

        addressColumn.setComparator((o1, o2) -> o1.getAddressString().compareTo(o2.getAddressString()));
        balanceColumn.setComparator((o1, o2) -> o1.getBalance().compareTo(o2.getBalance()));
        balanceColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(balanceColumn);

        balanceListener = new BalanceListener() {
            @Override
            public void onBalanceChanged(Coin balance, Transaction tx) {
                updateList();
            }
        };
        amountListener = (observable, oldValue, newValue) -> {
            if (amountTextField.focusedProperty().get()) {
                try {
                    senderAmountAsCoinProperty.set(formatter.parseToCoin(amountTextField.getText()));
                } catch (Throwable t) {
                    log.error("Error at amountTextField input. " + t.toString());
                }
            }
        };
        amountFocusListener = (observable, oldValue, newValue) -> {
            if (oldValue && !newValue) {
                if (senderAmountAsCoinProperty.get().isPositive())
                    amountTextField.setText(formatter.formatCoin(senderAmountAsCoinProperty.get()));
                else
                    amountTextField.setText("");
            }
        };
    }

    @Override
    protected void activate() {
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);
        updateList();

        reset();

        amountTextField.textProperty().addListener(amountListener);
        amountTextField.focusedProperty().addListener(amountFocusListener);
        walletService.addBalanceListener(balanceListener);
    }

    @Override
    protected void deactivate() {
        sortedList.comparatorProperty().unbind();
        observableList.forEach(WithdrawalListItem::cleanup);
        walletService.removeBalanceListener(balanceListener);
        amountTextField.textProperty().removeListener(amountListener);
        amountTextField.focusedProperty().removeListener(amountFocusListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    public void onWithdraw() {
        if (areInputsValid()) {
            FutureCallback<Transaction> callback = new FutureCallback<Transaction>() {
                @Override
                public void onSuccess(@javax.annotation.Nullable Transaction transaction) {
                    if (transaction != null) {
                        log.info("onWithdraw onSuccess tx ID:" + transaction.getHashAsString());
                    } else {
                        log.error("onWithdraw transaction is null");
                    }

                    List<Trade> trades = new ArrayList<>(tradeManager.getTrades());
                    trades.stream()
                            .filter(trade -> trade.getState().getPhase() == Trade.Phase.PAYOUT_PAID)
                            .forEach(trade -> {
                                if (walletService.getBalanceForAddress(walletService.getOrCreateAddressEntry(trade.getId(), AddressEntry.Context.TRADE_PAYOUT).getAddress()).isZero())
                                    tradeManager.addTradeToClosedTrades(trade);
                            });
                }

                @Override
                public void onFailure(@NotNull Throwable t) {
                    log.error("onWithdraw onFailure");
                }
            };
            try {
                // We need to use the max. amount (amountOfSelectedItems) as the senderAmount might be less then 
                // we have available and then the fee calculation would return 0
                // TODO Get a proper fee calculation from BitcoinJ directly
                Coin requiredFee = walletService.getRequiredFeeForMultipleAddresses(fromAddresses,
                        withdrawToTextField.getText(), amountOfSelectedItems);
                Coin receiverAmount = senderAmountAsCoinProperty.get().subtract(requiredFee);
                if (receiverAmount.isPositive()) {
                    if (BitsquareApp.DEV_MODE) {
                        doWithdraw(receiverAmount, callback);
                    } else {
                        new Popup().headLine("Confirm withdrawal request")
                                .confirmation("Sending: " + formatter.formatCoinWithCode(senderAmountAsCoinProperty.get()) + "\n" +
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
                } else {
                    new Popup().warning("The amount you would like to send is too low as the bitcoin transaction fee will be deducted.\n" +
                            "Please use a higher amount.").show();
                }
            } catch (Throwable e) {
                e.printStackTrace();
                log.error(e.getMessage());
                new Popup().warning(e.getMessage()).show();
            }
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
            amountOfSelectedItems = Coin.valueOf(selectedItems.stream().mapToLong(e -> e.getBalance().getValue()).sum());
            if (amountOfSelectedItems.isPositive()) {
                senderAmountAsCoinProperty.set(amountOfSelectedItems);
                amountTextField.setText(formatter.formatCoin(amountOfSelectedItems));
            } else {
                senderAmountAsCoinProperty.set(Coin.ZERO);
                amountOfSelectedItems = Coin.ZERO;
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateList() {
        observableList.forEach(WithdrawalListItem::cleanup);
        observableList.setAll(tradeManager.getAddressEntriesForAvailableBalanceStream()
                .map(addressEntry -> new WithdrawalListItem(addressEntry, walletService, formatter))
                .collect(Collectors.toList()));
    }

    private void doWithdraw(Coin amount, FutureCallback<Transaction> callback) {
        if (walletService.getWallet().isEncrypted()) {
            UserThread.runAfter(() -> walletPasswordWindow.onAesKey(aesKey ->
                    sendFunds(amount, aesKey, callback))
                    .show(), 300, TimeUnit.MILLISECONDS);
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
        } catch (Wallet.DustySendRequested e) {
            new Popup().warning("The amount you would like to send is below the dust limit and would be rejected by the bitcoin network.\n" +
                    "Please use a higher amount.").show();
        } catch (AddressEntryException e) {
            new Popup().error(e.getMessage()).show();
        } catch (InsufficientMoneyException e) {
            log.warn(e.getMessage());
            new Popup().warning("You don't have enough fund in your wallet.").show();
        } catch (Throwable e) {
            log.warn(e.getMessage());
            new Popup().warning(e.getMessage()).show();
        }
    }

    private void reset() {
        selectedItems = new HashSet<>();

        tableView.getSelectionModel().clearSelection();

        withdrawFromTextField.setText("");
        withdrawFromTextField.setPromptText("Select a source address from the table");
        withdrawFromTextField.setTooltip(null);

        amountOfSelectedItems = Coin.ZERO;
        senderAmountAsCoinProperty.set(Coin.ZERO);
        amountTextField.setText("");
        amountTextField.setPromptText("Set the amount to withdraw");

        withdrawToTextField.setText("");
        withdrawToTextField.setPromptText("Fill in your destination address");

        if (BitsquareApp.DEV_MODE)
            withdrawToTextField.setText("mjYhQYSbET2bXJDyCdNqYhqSye5QX2WHPz");
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
        if (!senderAmountAsCoinProperty.get().isPositive()) {
            new Popup().warning("Please fill in a valid value for the amount to send (max. 8 decimal places).").show();
            return false;
        }

        if (!btcAddressValidator.validate(withdrawToTextField.getText()).isValid) {
            new Popup().warning("Please fill in a valid receiver bitcoin address.").show();
            return false;
        }
        if (!amountOfSelectedItems.isPositive()) {
            new Popup().warning("You need to select a source address in the table above.").show();
            return false;
        }

        if (senderAmountAsCoinProperty.get().compareTo(amountOfSelectedItems) > 0) {
            new Popup().warning("Your amount exceeds the available amount for the selected address.\n" +
                    "Consider to select multiple addresses in the table above if you want to withdraw more.").show();
            return false;
        }

        return true;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ColumnCellFactories
    ///////////////////////////////////////////////////////////////////////////////////////////

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


