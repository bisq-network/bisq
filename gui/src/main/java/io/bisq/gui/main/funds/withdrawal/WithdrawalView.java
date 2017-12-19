/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.funds.withdrawal;

import com.google.common.util.concurrent.FutureCallback;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bisq.common.UserThread;
import io.bisq.common.locale.Res;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.AddressEntryException;
import io.bisq.core.btc.InsufficientFundsException;
import io.bisq.core.btc.Restrictions;
import io.bisq.core.btc.listeners.BalanceListener;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.trade.Tradable;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.TradeManager;
import io.bisq.core.trade.closed.ClosedTradableManager;
import io.bisq.core.trade.failed.FailedTradesManager;
import io.bisq.core.user.Preferences;
import io.bisq.core.util.CoinUtil;
import io.bisq.gui.common.view.ActivatableView;
import io.bisq.gui.common.view.FxmlView;
import io.bisq.gui.components.HyperlinkWithIcon;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.main.overlays.windows.WalletPasswordWindow;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.GUIUtil;
import io.bisq.gui.util.validation.BtcAddressValidator;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.Wallet;
import org.jetbrains.annotations.NotNull;
import org.spongycastle.crypto.params.KeyParameter;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

@FxmlView
public class WithdrawalView extends ActivatableView<VBox, Void> {

    @FXML
    Label inputsLabel, amountLabel, fromLabel, toLabel;
    @FXML
    Button withdrawButton;
    @FXML
    TableView<WithdrawalListItem> tableView;
    @FXML
    TextField withdrawFromTextField, withdrawToTextField, amountTextField;
    @FXML
    RadioButton useAllInputsRadioButton, useCustomInputsRadioButton, feeExcludedRadioButton, feeIncludedRadioButton;
    @FXML
    TableColumn<WithdrawalListItem, WithdrawalListItem> addressColumn, balanceColumn, selectColumn;

    private final BtcWalletService walletService;
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
    private Coin totalAvailableAmountOfSelectedItems = Coin.ZERO;
    private Coin amountAsCoin = Coin.ZERO;
    private Coin sendersAmount = Coin.ZERO;
    private ChangeListener<String> amountListener;
    private ChangeListener<Boolean> amountFocusListener;
    private ChangeListener<Toggle> feeToggleGroupListener, inputsToggleGroupListener;
    private ToggleGroup feeToggleGroup, inputsToggleGroup;
    private final BooleanProperty useAllInputs = new SimpleBooleanProperty(true);
    private boolean feeExcluded;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private WithdrawalView(BtcWalletService walletService,
                           TradeManager tradeManager,
                           ClosedTradableManager closedTradableManager,
                           FailedTradesManager failedTradesManager,
                           BSFormatter formatter, Preferences preferences,
                           BtcAddressValidator btcAddressValidator,
                           WalletPasswordWindow walletPasswordWindow) {
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
        inputsLabel.setText(Res.getWithCol("funds.withdrawal.inputs"));
        useAllInputsRadioButton.setText(Res.get("funds.withdrawal.useAllInputs"));
        useCustomInputsRadioButton.setText(Res.get("funds.withdrawal.useCustomInputs"));
        amountLabel.setText(Res.getWithCol("funds.withdrawal.receiverAmount", Res.getBaseCurrencyCode()));
        feeExcludedRadioButton.setText(Res.get("funds.withdrawal.feeExcluded"));
        feeIncludedRadioButton.setText(Res.get("funds.withdrawal.feeIncluded"));
        fromLabel.setText(Res.get("funds.withdrawal.fromLabel", Res.getBaseCurrencyCode()));
        toLabel.setText(Res.get("funds.withdrawal.toLabel", Res.getBaseCurrencyCode()));
        withdrawButton.setText(Res.get("funds.withdrawal.withdrawButton"));

        addressColumn.setText(Res.get("shared.address"));
        balanceColumn.setText(Res.get("shared.balanceWithCur", Res.getBaseCurrencyCode()));
        selectColumn.setText(Res.get("shared.select"));

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new Label(Res.get("funds.withdrawal.noFundsAvailable")));
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
                    amountAsCoin = formatter.parseToCoin(amountTextField.getText());
                } catch (Throwable t) {
                    log.error("Error at amountTextField input. " + t.toString());
                }
            }
        };
        amountFocusListener = (observable, oldValue, newValue) -> {
            if (oldValue && !newValue) {
                if (amountAsCoin.isPositive())
                    amountTextField.setText(formatter.formatCoin(amountAsCoin));
                else
                    amountTextField.setText("");
            }
        };
        feeToggleGroup = new ToggleGroup();
        feeExcludedRadioButton.setToggleGroup(feeToggleGroup);
        feeIncludedRadioButton.setToggleGroup(feeToggleGroup);
        feeToggleGroupListener = (observable, oldValue, newValue) -> {
            feeExcluded = newValue == feeExcludedRadioButton;
            amountLabel.setText(feeExcluded ?
                    Res.getWithCol("funds.withdrawal.receiverAmount", Res.getBaseCurrencyCode()) :
                    Res.getWithCol("funds.withdrawal.senderAmount", Res.getBaseCurrencyCode()));
        };

        inputsToggleGroup = new ToggleGroup();
        useAllInputsRadioButton.setToggleGroup(inputsToggleGroup);
        useCustomInputsRadioButton.setToggleGroup(inputsToggleGroup);
        inputsToggleGroupListener = (observable, oldValue, newValue) -> {
            useAllInputs.set(newValue == useAllInputsRadioButton);

            updateInputSelection();
        };
    }

    private void updateInputSelection() {
        observableList.stream().forEach(item -> {
            item.setSelected(useAllInputs.get());
            selectForWithdrawal(item);
        });
        tableView.refresh();
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
        feeToggleGroup.selectedToggleProperty().addListener(feeToggleGroupListener);
        inputsToggleGroup.selectedToggleProperty().addListener(inputsToggleGroupListener);

        if (feeToggleGroup.getSelectedToggle() == null)
            feeToggleGroup.selectToggle(feeExcludedRadioButton);

        if (inputsToggleGroup.getSelectedToggle() == null)
            inputsToggleGroup.selectToggle(useAllInputsRadioButton);

        updateInputSelection();
    }

    @Override
    protected void deactivate() {
        sortedList.comparatorProperty().unbind();
        observableList.forEach(WithdrawalListItem::cleanup);
        walletService.removeBalanceListener(balanceListener);
        amountTextField.textProperty().removeListener(amountListener);
        amountTextField.focusedProperty().removeListener(amountFocusListener);
        feeToggleGroup.selectedToggleProperty().removeListener(feeToggleGroupListener);
        inputsToggleGroup.selectedToggleProperty().removeListener(inputsToggleGroupListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    public void onWithdraw() {
        try {
            // We do not know sendersAmount if senderPaysFee is true. We repeat fee calculation after first attempt if senderPaysFee is true.
            Transaction feeEstimationTransaction = walletService.getFeeEstimationTransactionForMultipleAddresses(fromAddresses, amountAsCoin);
            if (feeExcluded && feeEstimationTransaction != null) {
                sendersAmount = amountAsCoin.add(feeEstimationTransaction.getFee());
                feeEstimationTransaction = walletService.getFeeEstimationTransactionForMultipleAddresses(fromAddresses, sendersAmount);
            }
            checkNotNull(feeEstimationTransaction, "feeEstimationTransaction must not be null");
            Coin fee = feeEstimationTransaction.getFee();
            sendersAmount = feeExcluded ? amountAsCoin.add(fee) : amountAsCoin;
            Coin receiverAmount = feeExcluded ? amountAsCoin : amountAsCoin.subtract(fee);
            if (areInputsValid()) {
                int txSize = feeEstimationTransaction.bitcoinSerialize().length;
                log.info("Fee for tx with size {}: {} " + Res.getBaseCurrencyCode() + "", txSize, fee.toPlainString());

                if (receiverAmount.isPositive()) {
                    double feePerByte = CoinUtil.getFeePerByte(fee, txSize);
                    double kb = txSize / 1000d;
                    new Popup<>().headLine(Res.get("funds.withdrawal.confirmWithdrawalRequest"))
                            .confirmation(Res.get("shared.sendFundsDetailsWithFee",
                                    formatter.formatCoinWithCode(sendersAmount),
                                    withdrawFromTextField.getText(),
                                    withdrawToTextField.getText(),
                                    formatter.formatCoinWithCode(fee),
                                    feePerByte,
                                    kb,
                                    formatter.formatCoinWithCode(receiverAmount)))
                            .actionButtonText(Res.get("shared.yes"))
                            .onAction(() -> doWithdraw(sendersAmount, fee, new FutureCallback<Transaction>() {
                                @Override
                                public void onSuccess(@javax.annotation.Nullable Transaction transaction) {
                                    if (transaction != null) {
                                        log.debug("onWithdraw onSuccess tx ID:" + transaction.getHashAsString());
                                    } else {
                                        log.error("onWithdraw transaction is null");
                                    }

                                    List<Trade> trades = new ArrayList<>(tradeManager.getTradableList());
                                    trades.stream()
                                            .filter(Trade::isPayoutPublished)
                                            .forEach(trade -> {
                                                walletService.getAddressEntry(trade.getId(), AddressEntry.Context.TRADE_PAYOUT)
                                                        .ifPresent(addressEntry -> {
                                                            if (walletService.getBalanceForAddress(addressEntry.getAddress()).isZero())
                                                                tradeManager.addTradeToClosedTrades(trade);
                                                        });
                                            });
                                }

                                @Override
                                public void onFailure(@NotNull Throwable t) {
                                    log.error("onWithdraw onFailure");
                                }
                            }))
                            .closeButtonText(Res.get("shared.cancel"))
                            .show();
                } else {
                    new Popup<>().warning(Res.get("portfolio.pending.step5_buyer.amountTooLow")).show();
                }
            }
        } catch (InsufficientFundsException e) {
            new Popup<>().warning(Res.get("funds.withdrawal.warn.amountExceeds")+ "\n\nError message:\n" + e.getMessage()).show();
        } catch (Throwable e) {
            e.printStackTrace();
            log.error(e.toString());
            new Popup<>().warning(e.toString()).show();
        }
    }

    private void selectForWithdrawal(WithdrawalListItem item) {
        if (item.isSelected())
            selectedItems.add(item);
        else
            selectedItems.remove(item);

        fromAddresses = selectedItems.stream()
                .map(WithdrawalListItem::getAddressString)
                .collect(Collectors.toSet());

        if (!selectedItems.isEmpty()) {
            totalAvailableAmountOfSelectedItems = Coin.valueOf(selectedItems.stream().mapToLong(e -> e.getBalance().getValue()).sum());
            if (totalAvailableAmountOfSelectedItems.isPositive()) {
                amountAsCoin = totalAvailableAmountOfSelectedItems;
                amountTextField.setText(formatter.formatCoin(amountAsCoin));
            } else {
                amountAsCoin = Coin.ZERO;
                totalAvailableAmountOfSelectedItems = Coin.ZERO;
                amountTextField.setText("");
                withdrawFromTextField.setText("");
            }

            if (selectedItems.size() == 1) {
                withdrawFromTextField.setText(selectedItems.stream().findAny().get().getAddressEntry().getAddressString());
                withdrawFromTextField.setTooltip(null);
            } else {
                int abbr = Math.max(10, 66 / selectedItems.size());
                String addressesShortened = selectedItems.stream()
                        .map(e -> StringUtils.abbreviate(e.getAddressString(), abbr))
                        .collect(Collectors.joining(", "));
                String text = Res.get("funds.withdrawal.withdrawMultipleAddresses", addressesShortened);
                withdrawFromTextField.setText(text);

                String addresses = selectedItems.stream()
                        .map(WithdrawalListItem::getAddressString)
                        .collect(Collectors.joining(",\n"));
                String tooltipText = Res.get("funds.withdrawal.withdrawMultipleAddresses.tooltip", addresses);
                withdrawFromTextField.setTooltip(new Tooltip(tooltipText));
            }
        } else {
            reset();
        }
    }

    private void openBlockExplorer(WithdrawalListItem item) {
        if (item.getAddressString() != null)
            GUIUtil.openWebPage(preferences.getBlockChainExplorer().addressUrl + item.getAddressString());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateList() {
        observableList.forEach(WithdrawalListItem::cleanup);
        observableList.setAll(tradeManager.getAddressEntriesForAvailableBalanceStream()
                .map(addressEntry -> new WithdrawalListItem(addressEntry, walletService, formatter))
                .collect(Collectors.toList()));

        updateInputSelection();
    }

    private void doWithdraw(Coin amount, Coin fee, FutureCallback<Transaction> callback) {
        if (walletService.isEncrypted()) {
            UserThread.runAfter(() -> walletPasswordWindow.onAesKey(aesKey ->
                    sendFunds(amount, fee, aesKey, callback))
                    .show(), 300, TimeUnit.MILLISECONDS);
        } else {
            sendFunds(amount, fee, null, callback);
        }
    }

    private void sendFunds(Coin amount, Coin fee, KeyParameter aesKey, FutureCallback<Transaction> callback) {
        try {
            walletService.sendFundsForMultipleAddresses(fromAddresses, withdrawToTextField.getText(), amount, fee, null, aesKey, callback);
            reset();
            updateList();
        } catch (AddressFormatException e) {
            new Popup<>().warning(Res.get("validation.btc.invalidAddress")).show();
        } catch (Wallet.DustySendRequested e) {
            new Popup<>().warning(Res.get("validation.btc.amountBelowDust", formatter.formatCoinWithCode(Restrictions.getMinNonDustOutput()))).show();
        } catch (AddressEntryException e) {
            new Popup<>().error(e.getMessage()).show();
        } catch (InsufficientMoneyException e) {
            log.warn(e.getMessage());
            new Popup<>().warning(Res.get("funds.withdrawal.notEnoughFunds") + "\n\nError message:\n" + e.getMessage()).show();
        } catch (Throwable e) {
            log.warn(e.toString());
            new Popup<>().warning(e.toString()).show();
        }
    }

    private void reset() {
        withdrawFromTextField.setText("");
        withdrawFromTextField.setPromptText(Res.get("funds.withdrawal.selectAddress"));
        withdrawFromTextField.setTooltip(null);

        totalAvailableAmountOfSelectedItems = Coin.ZERO;
        amountAsCoin = Coin.ZERO;
        sendersAmount = Coin.ZERO;
        amountTextField.setText("");
        amountTextField.setPromptText(Res.get("funds.withdrawal.setAmount"));

        withdrawToTextField.setText("");
        withdrawToTextField.setPromptText(Res.get("funds.withdrawal.fillDestAddress"));

        selectedItems.clear();
        tableView.getSelectionModel().clearSelection();
    }

    private Optional<Tradable> getTradable(WithdrawalListItem item) {
        String offerId = item.getAddressEntry().getOfferId();
        Optional<Tradable> tradableOptional = closedTradableManager.getTradableById(offerId);
        if (tradableOptional.isPresent()) {
            return tradableOptional;
        } else if (failedTradesManager.getTradeById(offerId).isPresent()) {
            return Optional.of(failedTradesManager.getTradeById(offerId).get());
        } else {
            return Optional.<Tradable>empty();
        }
    }

    private boolean areInputsValid() {
        if (!sendersAmount.isPositive()) {
            new Popup<>().warning(Res.get("validation.negative")).show();
            return false;
        }

        if (!btcAddressValidator.validate(withdrawToTextField.getText()).isValid) {
            new Popup<>().warning(Res.get("validation.btc.invalidAddress")).show();
            return false;
        }
        if (!totalAvailableAmountOfSelectedItems.isPositive()) {
            new Popup<>().warning(Res.get("funds.withdrawal.warn.noSourceAddressSelected")).show();
            return false;
        }

        if (sendersAmount.compareTo(totalAvailableAmountOfSelectedItems) > 0) {
            new Popup<>().warning(Res.get("funds.withdrawal.warn.amountExceeds")).show();
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
                                    hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForAddress", address)));
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

                            CheckBox checkBox = new CheckBox();

                            @Override
                            public void updateItem(final WithdrawalListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    checkBox.setOnAction(e -> {
                                        item.setSelected(checkBox.isSelected());
                                        selectForWithdrawal(item);

                                        // If all are selected we select useAllInputsRadioButton
                                        if (observableList.size() == selectedItems.size()) {
                                            inputsToggleGroup.selectToggle(useAllInputsRadioButton);
                                        } else {
                                            // We don't want to get deselected all when we activate the useCustomInputsRadioButton
                                            // so we temporarily disable the listener
                                            inputsToggleGroup.selectedToggleProperty().removeListener(inputsToggleGroupListener);
                                            inputsToggleGroup.selectToggle(useCustomInputsRadioButton);
                                            useAllInputs.set(false);
                                            inputsToggleGroup.selectedToggleProperty().addListener(inputsToggleGroupListener);
                                        }
                                    });
                                    setGraphic(checkBox);
                                    checkBox.setSelected(item.isSelected());
                                } else {
                                    checkBox.setOnAction(null);
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }
}


