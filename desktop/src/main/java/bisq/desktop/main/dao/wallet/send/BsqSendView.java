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

package bisq.desktop.main.dao.wallet.send;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.MainView;
import bisq.desktop.main.dao.wallet.BsqBalanceUtil;
import bisq.desktop.main.funds.FundsView;
import bisq.desktop.main.funds.deposit.DepositView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.TxDetailsBsq;
import bisq.desktop.main.overlays.windows.TxInputSelectionWindow;
import bisq.desktop.main.overlays.windows.WalletPasswordWindow;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.BsqAddressValidator;
import bisq.desktop.util.validation.BsqValidator;
import bisq.desktop.util.validation.BtcValidator;

import bisq.core.btc.exceptions.BsqChangeBelowDustException;
import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.listeners.BsqBalanceListener;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.locale.Res;
import bisq.core.monetary.Volume;
import bisq.core.user.DontShowAgainLookup;
import bisq.core.user.Preferences;
import bisq.core.util.FormattingUtils;
import bisq.core.util.ParsingUtils;
import bisq.core.util.VolumeUtil;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.coin.CoinUtil;
import bisq.core.util.validation.BtcAddressValidator;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;
import bisq.common.handlers.ResultHandler;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;

import javafx.beans.value.ChangeListener;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.addInputTextField;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class BsqSendView extends ActivatableView<GridPane, Void> implements BsqBalanceListener {
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final WalletsManager walletsManager;
    private final WalletsSetup walletsSetup;
    private final P2PService p2PService;
    private final BsqFormatter bsqFormatter;
    private final CoinFormatter btcFormatter;
    private final Navigation navigation;
    private final BsqBalanceUtil bsqBalanceUtil;
    private final BsqValidator bsqValidator;
    private final BtcValidator btcValidator;
    private final BsqAddressValidator bsqAddressValidator;
    private final BtcAddressValidator btcAddressValidator;
    private final Preferences preferences;
    private final WalletPasswordWindow walletPasswordWindow;

    private int gridRow = 0;
    private InputTextField amountInputTextField, btcAmountInputTextField;
    private Button sendBsqButton, sendBtcButton, bsqInputControlButton, btcInputControlButton;
    private InputTextField receiversAddressInputTextField, receiversBtcAddressInputTextField;
    private ChangeListener<Boolean> focusOutListener;
    private TitledGroupBg btcTitledGroupBg;
    private ChangeListener<String> inputTextFieldListener;
    @Nullable
    private Set<TransactionOutput> bsqUtxoCandidates;
    @Nullable
    private Set<TransactionOutput> btcUtxoCandidates;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BsqSendView(BsqWalletService bsqWalletService,
                        BtcWalletService btcWalletService,
                        WalletsManager walletsManager,
                        WalletsSetup walletsSetup,
                        P2PService p2PService,
                        BsqFormatter bsqFormatter,
                        @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                        Navigation navigation,
                        BsqBalanceUtil bsqBalanceUtil,
                        BsqValidator bsqValidator,
                        BtcValidator btcValidator,
                        BsqAddressValidator bsqAddressValidator,
                        BtcAddressValidator btcAddressValidator,
                        Preferences preferences,
                        WalletPasswordWindow walletPasswordWindow) {
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.walletsManager = walletsManager;
        this.walletsSetup = walletsSetup;
        this.p2PService = p2PService;
        this.bsqFormatter = bsqFormatter;
        this.btcFormatter = btcFormatter;
        this.navigation = navigation;
        this.bsqBalanceUtil = bsqBalanceUtil;
        this.bsqValidator = bsqValidator;
        this.btcValidator = btcValidator;
        this.bsqAddressValidator = bsqAddressValidator;
        this.btcAddressValidator = btcAddressValidator;
        this.preferences = preferences;
        this.walletPasswordWindow = walletPasswordWindow;
    }

    @Override
    public void initialize() {
        gridRow = bsqBalanceUtil.addGroup(root, gridRow);

        addSendBsqGroup();
        addSendBtcGroup();

        focusOutListener = (observable, oldValue, newValue) -> {
            if (!newValue)
                onUpdateBalances();
        };
        inputTextFieldListener = (observable, oldValue, newValue) -> onUpdateBalances();

        setSendBtcGroupVisibleState(false);
    }

    @Override
    protected void activate() {
        setSendBtcGroupVisibleState(false);
        bsqBalanceUtil.activate();

        receiversAddressInputTextField.resetValidation();
        amountInputTextField.resetValidation();
        receiversBtcAddressInputTextField.resetValidation();
        btcAmountInputTextField.resetValidation();

        sendBsqButton.setOnAction((event) -> onSendBsq());
        bsqInputControlButton.setOnAction((event) -> onBsqInputControl());
        sendBtcButton.setOnAction((event) -> onSendBtc());
        btcInputControlButton.setOnAction((event) -> onBtcInputControl());

        receiversAddressInputTextField.focusedProperty().addListener(focusOutListener);
        amountInputTextField.focusedProperty().addListener(focusOutListener);
        receiversBtcAddressInputTextField.focusedProperty().addListener(focusOutListener);
        btcAmountInputTextField.focusedProperty().addListener(focusOutListener);

        receiversAddressInputTextField.textProperty().addListener(inputTextFieldListener);
        amountInputTextField.textProperty().addListener(inputTextFieldListener);
        receiversBtcAddressInputTextField.textProperty().addListener(inputTextFieldListener);
        btcAmountInputTextField.textProperty().addListener(inputTextFieldListener);

        bsqWalletService.addBsqBalanceListener(this);

        // We reset the input selection at activate to have all inputs selected, otherwise the user
        // might get confused if he had deselected inputs earlier and cannot spend the full balance.
        bsqUtxoCandidates = null;
        btcUtxoCandidates = null;

        onUpdateBalances();
    }

    private void onUpdateBalances() {
        onUpdateBalances(getSpendableBsqBalance(),
                bsqWalletService.getAvailableNonBsqBalance(),
                bsqWalletService.getUnverifiedBalance(),
                bsqWalletService.getUnconfirmedChangeBalance(),
                bsqWalletService.getLockedForVotingBalance(),
                bsqWalletService.getLockupBondsBalance(),
                bsqWalletService.getUnlockingBondsBalance());
    }


    @Override
    protected void deactivate() {
        bsqBalanceUtil.deactivate();

        receiversAddressInputTextField.focusedProperty().removeListener(focusOutListener);
        amountInputTextField.focusedProperty().removeListener(focusOutListener);
        receiversBtcAddressInputTextField.focusedProperty().removeListener(focusOutListener);
        btcAmountInputTextField.focusedProperty().removeListener(focusOutListener);

        receiversAddressInputTextField.textProperty().removeListener(inputTextFieldListener);
        amountInputTextField.textProperty().removeListener(inputTextFieldListener);
        receiversBtcAddressInputTextField.textProperty().removeListener(inputTextFieldListener);
        btcAmountInputTextField.textProperty().removeListener(inputTextFieldListener);

        bsqWalletService.removeBsqBalanceListener(this);

        sendBsqButton.setOnAction(null);
        btcInputControlButton.setOnAction(null);
        sendBtcButton.setOnAction(null);
        bsqInputControlButton.setOnAction(null);
    }

    @Override
    public void onUpdateBalances(Coin availableBalance,
                                 Coin availableNonBsqBalance,
                                 Coin unverifiedBalance,
                                 Coin unconfirmedChangeBalance,
                                 Coin lockedForVotingBalance,
                                 Coin lockupBondsBalance,
                                 Coin unlockingBondsBalance) {
        updateBsqValidator(availableBalance);
        updateBtcValidator(availableNonBsqBalance);

        setSendBtcGroupVisibleState(availableNonBsqBalance.isPositive());
    }

    public void fillFromTradeData(Tuple2<Volume, String> tuple) {
        amountInputTextField.setText(VolumeUtil.formatVolume(tuple.first));
        receiversAddressInputTextField.setText(tuple.second);
    }

    private void updateBsqValidator(Coin availableBalance) {
        bsqValidator.setAvailableBalance(availableBalance);
        boolean isValid = bsqAddressValidator.validate(receiversAddressInputTextField.getText()).isValid &&
                bsqValidator.validate(amountInputTextField.getText()).isValid;
        sendBsqButton.setDisable(!isValid);
    }

    private void updateBtcValidator(Coin availableBalance) {
        btcValidator.setMaxValue(availableBalance);
        boolean isValid = btcAddressValidator.validate(receiversBtcAddressInputTextField.getText()).isValid &&
                btcValidator.validate(btcAmountInputTextField.getText()).isValid;
        sendBtcButton.setDisable(!isValid);
    }

    private void addSendBsqGroup() {
        TitledGroupBg titledGroupBg = addTitledGroupBg(root, ++gridRow, 2, Res.get("dao.wallet.send.sendFunds"), Layout.GROUP_DISTANCE);
        GridPane.setColumnSpan(titledGroupBg, 3);

        receiversAddressInputTextField = addInputTextField(root, gridRow,
                Res.get("dao.wallet.send.receiverAddress"), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        receiversAddressInputTextField.setValidator(bsqAddressValidator);
        GridPane.setColumnSpan(receiversAddressInputTextField, 3);

        amountInputTextField = addInputTextField(root, ++gridRow, Res.get("dao.wallet.send.setAmount", bsqFormatter.formatCoinWithCode(Restrictions.getMinNonDustOutput())));
        amountInputTextField.setValidator(bsqValidator);
        GridPane.setColumnSpan(amountInputTextField, 3);

        focusOutListener = (observable, oldValue, newValue) -> {
            if (!newValue)
                onUpdateBalances();
        };

        Tuple2<Button, Button> tuple = FormBuilder.add2ButtonsAfterGroup(root, ++gridRow,
                Res.get("dao.wallet.send.send"), Res.get("dao.wallet.send.inputControl"));
        sendBsqButton = tuple.first;
        bsqInputControlButton = tuple.second;
    }

    private void onSendBsq() {
        if (!GUIUtil.isReadyForTxBroadcastOrShowPopup(p2PService, walletsSetup)) {
            return;
        }

        String receiversAddressString = bsqFormatter.getAddressFromBsqAddress(receiversAddressInputTextField.getText()).toString();
        Coin receiverAmount = ParsingUtils.parseToCoin(amountInputTextField.getText(), bsqFormatter);
        try {
            Transaction preparedSendTx = bsqWalletService.getPreparedSendBsqTx(receiversAddressString,
                    receiverAmount, bsqUtxoCandidates);
            Transaction txWithBtcFee = btcWalletService.completePreparedSendBsqTx(preparedSendTx);
            Transaction signedTx = bsqWalletService.signTxAndVerifyNoDustOutputs(txWithBtcFee);
            Coin miningFee = signedTx.getFee();
            int txVsize = signedTx.getVsize();
            showPublishTxPopup(receiverAmount,
                    txWithBtcFee,
                    TxType.TRANSFER_BSQ,
                    miningFee,
                    txVsize,
                    receiversAddressInputTextField.getText(),
                    bsqFormatter,
                    btcFormatter,
                    () -> {
                        receiversAddressInputTextField.setText("");
                        amountInputTextField.setText("");

                        receiversAddressInputTextField.resetValidation();
                        amountInputTextField.resetValidation();
                    });
        } catch (BsqChangeBelowDustException e) {
            String msg = Res.get("popup.warning.bsqChangeBelowDustException", bsqFormatter.formatCoinWithCode(e.getOutputValue()));
            new Popup().warning(msg).show();
        } catch (Throwable t) {
            handleError(t);
        }
    }

    private void onBsqInputControl() {
        List<TransactionOutput> unspentTransactionOutputs = bsqWalletService.getSpendableBsqTransactionOutputs();
        if (bsqUtxoCandidates == null) {
            bsqUtxoCandidates = new HashSet<>(unspentTransactionOutputs);
        } else {
            // If we had some selection stored we need to update to already spent entries
            bsqUtxoCandidates = bsqUtxoCandidates.stream().
                    filter(e -> unspentTransactionOutputs.contains(e)).
                    collect(Collectors.toSet());
        }
        TxInputSelectionWindow txInputSelectionWindow = new TxInputSelectionWindow(unspentTransactionOutputs,
                bsqUtxoCandidates,
                preferences,
                bsqFormatter);
        txInputSelectionWindow.onAction(() -> setBsqUtxoCandidates(txInputSelectionWindow.getCandidates()))
                .show();
    }

    private void setBsqUtxoCandidates(Set<TransactionOutput> candidates) {
        this.bsqUtxoCandidates = candidates;
        updateBsqValidator(getSpendableBsqBalance());
        amountInputTextField.refreshValidation();
    }

    // We have used input selection it is the sum of our selected inputs, otherwise the availableBalance
    private Coin getSpendableBsqBalance() {
        return bsqUtxoCandidates != null ?
                Coin.valueOf(bsqUtxoCandidates.stream().mapToLong(e -> e.getValue().value).sum()) :
                bsqWalletService.getAvailableBalance();
    }

    private void setSendBtcGroupVisibleState(boolean visible) {
        btcTitledGroupBg.setVisible(visible);
        receiversBtcAddressInputTextField.setVisible(visible);
        btcAmountInputTextField.setVisible(visible);
        sendBtcButton.setVisible(visible);
        btcInputControlButton.setVisible(visible);

        btcTitledGroupBg.setManaged(visible);
        receiversBtcAddressInputTextField.setManaged(visible);
        btcAmountInputTextField.setManaged(visible);
        sendBtcButton.setManaged(visible);
        btcInputControlButton.setManaged(visible);
    }

    private void addSendBtcGroup() {
        btcTitledGroupBg = addTitledGroupBg(root, ++gridRow, 2, Res.get("dao.wallet.send.sendBtcFunds"), Layout.GROUP_DISTANCE);
        GridPane.setColumnSpan(btcTitledGroupBg, 3);
        receiversBtcAddressInputTextField = addInputTextField(root, gridRow,
                Res.get("dao.wallet.send.receiverBtcAddress"), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        receiversBtcAddressInputTextField.setValidator(btcAddressValidator);
        GridPane.setColumnSpan(receiversBtcAddressInputTextField, 3);

        btcAmountInputTextField = addInputTextField(root, ++gridRow, Res.get("dao.wallet.send.btcAmount"));
        btcAmountInputTextField.setValidator(btcValidator);
        GridPane.setColumnSpan(btcAmountInputTextField, 3);

        Tuple2<Button, Button> tuple = FormBuilder.add2ButtonsAfterGroup(root, ++gridRow,
                Res.get("dao.wallet.send.sendBtc"), Res.get("dao.wallet.send.inputControl"));
        sendBtcButton = tuple.first;
        btcInputControlButton = tuple.second;
    }

    private void onBtcInputControl() {
        List<TransactionOutput> unspentTransactionOutputs = bsqWalletService.getSpendableNonBsqTransactionOutputs();
        if (btcUtxoCandidates == null) {
            btcUtxoCandidates = new HashSet<>(unspentTransactionOutputs);
        } else {
            // If we had some selection stored we need to update to already spent entries
            btcUtxoCandidates = btcUtxoCandidates.stream().
                    filter(e -> unspentTransactionOutputs.contains(e)).
                    collect(Collectors.toSet());
        }
        TxInputSelectionWindow txInputSelectionWindow = new TxInputSelectionWindow(unspentTransactionOutputs,
                btcUtxoCandidates,
                preferences,
                btcFormatter);
        txInputSelectionWindow.onAction(() -> setBtcUtxoCandidates(txInputSelectionWindow.getCandidates())).
                show();
    }

    private void setBtcUtxoCandidates(Set<TransactionOutput> candidates) {
        this.btcUtxoCandidates = candidates;
        updateBtcValidator(getSpendableBtcBalance());
        btcAmountInputTextField.refreshValidation();
    }

    private Coin getSpendableBtcBalance() {
        return btcUtxoCandidates != null ?
                Coin.valueOf(btcUtxoCandidates.stream().mapToLong(e -> e.getValue().value).sum()) :
                bsqWalletService.getAvailableNonBsqBalance();
    }

    private void onSendBtc() {
        if (!GUIUtil.isReadyForTxBroadcastOrShowPopup(p2PService, walletsSetup)) {
            return;
        }

        String receiversAddressString = receiversBtcAddressInputTextField.getText();
        Coin receiverAmount = bsqFormatter.parseToBTC(btcAmountInputTextField.getText());
        try {
            Transaction preparedSendTx = bsqWalletService.getPreparedSendBtcTx(receiversAddressString, receiverAmount, btcUtxoCandidates);
            Transaction txWithBtcFee = btcWalletService.completePreparedSendBsqTx(preparedSendTx);
            Transaction signedTx = bsqWalletService.signTxAndVerifyNoDustOutputs(txWithBtcFee);
            Coin miningFee = signedTx.getFee();

            if (miningFee.getValue() >= receiverAmount.getValue())
                GUIUtil.showWantToBurnBTCPopup(miningFee, receiverAmount, btcFormatter);
            else {
                int txVsize = signedTx.getVsize();
                showPublishTxPopup(receiverAmount,
                        txWithBtcFee,
                        TxType.INVALID,
                        miningFee,
                        txVsize, receiversBtcAddressInputTextField.getText(),
                        btcFormatter,
                        btcFormatter,
                        () -> {
                            receiversBtcAddressInputTextField.setText("");
                            btcAmountInputTextField.setText("");

                            receiversBtcAddressInputTextField.resetValidation();
                            btcAmountInputTextField.resetValidation();

                        });
            }
        } catch (BsqChangeBelowDustException e) {
            String msg = Res.get("popup.warning.btcChangeBelowDustException", btcFormatter.formatCoinWithCode(e.getOutputValue()));
            new Popup().warning(msg).show();
        } catch (Throwable t) {
            handleError(t);
        }
    }

    private void handleError(Throwable t) {
        if (t instanceof InsufficientMoneyException) {
            final Coin missingCoin = ((InsufficientMoneyException) t).missing;
            final String missing = missingCoin != null ? missingCoin.toFriendlyString() : "null";
            new Popup().warning(Res.get("popup.warning.insufficientBtcFundsForBsqTx", missing))
                    .actionButtonTextWithGoTo("navigation.funds.depositFunds")
                    .onAction(() -> navigation.navigateTo(MainView.class, FundsView.class, DepositView.class))
                    .show();
        } else {
            log.error(t.toString());
            t.printStackTrace();
            new Popup().warning(t.getMessage()).show();
        }
    }

    private void showPublishTxPopup(Coin receiverAmount,
                                    Transaction txWithBtcFee,
                                    TxType txType,
                                    Coin miningFee,
                                    int txVsize, String address,
                                    CoinFormatter amountFormatter, // can be BSQ or BTC formatter
                                    CoinFormatter feeFormatter,
                                    ResultHandler resultHandler) {
        new Popup().headLine(Res.get("dao.wallet.send.sendFunds.headline"))
                .confirmation(Res.get("dao.wallet.send.sendFunds.details",
                        amountFormatter.formatCoinWithCode(receiverAmount),
                        address,
                        feeFormatter.formatCoinWithCode(miningFee),
                        CoinUtil.getFeePerVbyte(miningFee, txVsize),
                        txVsize / 1000d,
                        amountFormatter.formatCoinWithCode(receiverAmount)))
                .actionButtonText(Res.get("shared.yes"))
                .onAction(() -> {
                    doWithdraw(txWithBtcFee, txType, new TxBroadcaster.Callback() {
                        @Override
                        public void onSuccess(Transaction transaction) {
                            log.debug("Successfully sent tx with id {}", txWithBtcFee.getTxId().toString());
                            String key = "showTransactionSentBsq";
                            if (DontShowAgainLookup.showAgain(key)) {
                                new TxDetailsBsq(txWithBtcFee.getTxId().toString(), address, amountFormatter.formatCoinWithCode(receiverAmount))
                                        .dontShowAgainId(key)
                                        .show();
                            }
                        }

                        @Override
                        public void onFailure(TxBroadcastException exception) {
                            new Popup().warning(exception.toString());
                        }
                    });
                    resultHandler.handleResult();
                })
                .closeButtonText(Res.get("shared.cancel"))
                .show();
    }

    private void doWithdraw(Transaction txWithBtcFee, TxType txType, TxBroadcaster.Callback callback) {
        if (btcWalletService.isEncrypted()) {
            UserThread.runAfter(() -> walletPasswordWindow.onAesKey(aesKey ->
                            sendFunds(txWithBtcFee, txType, callback))
                    .show(), 300, TimeUnit.MILLISECONDS);
        } else {
            sendFunds(txWithBtcFee, txType, callback);
        }
    }

    private void sendFunds(Transaction txWithBtcFee, TxType txType, TxBroadcaster.Callback callback) {
        walletsManager.publishAndCommitBsqTx(txWithBtcFee, txType, callback);
    }
}
