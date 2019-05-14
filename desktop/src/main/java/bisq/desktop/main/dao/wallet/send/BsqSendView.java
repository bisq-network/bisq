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
import bisq.core.util.BSFormatter;
import bisq.core.util.BsqFormatter;
import bisq.core.util.CoinUtil;
import bisq.core.util.validation.BtcAddressValidator;

import bisq.network.p2p.P2PService;

import bisq.common.handlers.ResultHandler;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;

import javafx.beans.value.ChangeListener;

import static bisq.desktop.util.FormBuilder.addButtonAfterGroup;
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
    private final BSFormatter btcFormatter;
    private final Navigation navigation;
    private final BsqBalanceUtil bsqBalanceUtil;
    private final BsqValidator bsqValidator;
    private final BtcValidator btcValidator;
    private final BsqAddressValidator bsqAddressValidator;
    private final BtcAddressValidator btcAddressValidator;

    private int gridRow = 0;
    private InputTextField amountInputTextField, btcAmountInputTextField;
    private Button sendBsqButton, sendBtcButton;
    private InputTextField receiversAddressInputTextField, receiversBtcAddressInputTextField;
    private ChangeListener<Boolean> focusOutListener;
    private TitledGroupBg btcTitledGroupBg;
    private ChangeListener<String> inputTextFieldListener;


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
                        BSFormatter btcFormatter,
                        Navigation navigation,
                        BsqBalanceUtil bsqBalanceUtil,
                        BsqValidator bsqValidator,
                        BtcValidator btcValidator,
                        BsqAddressValidator bsqAddressValidator,
                        BtcAddressValidator btcAddressValidator) {
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

        receiversAddressInputTextField.focusedProperty().addListener(focusOutListener);
        amountInputTextField.focusedProperty().addListener(focusOutListener);
        receiversBtcAddressInputTextField.focusedProperty().addListener(focusOutListener);
        btcAmountInputTextField.focusedProperty().addListener(focusOutListener);

        receiversAddressInputTextField.textProperty().addListener(inputTextFieldListener);
        amountInputTextField.textProperty().addListener(inputTextFieldListener);
        receiversBtcAddressInputTextField.textProperty().addListener(inputTextFieldListener);
        btcAmountInputTextField.textProperty().addListener(inputTextFieldListener);

        bsqWalletService.addBsqBalanceListener(this);

        onUpdateBalances();
    }

    private void onUpdateBalances() {
        onUpdateBalances(bsqWalletService.getAvailableConfirmedBalance(),
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
    }

    @Override
    public void onUpdateBalances(Coin availableConfirmedBalance,
                                 Coin availableNonBsqBalance,
                                 Coin unverifiedBalance,
                                 Coin unconfirmedChangeBalance,
                                 Coin lockedForVotingBalance,
                                 Coin lockupBondsBalance,
                                 Coin unlockingBondsBalance) {
        bsqValidator.setAvailableBalance(availableConfirmedBalance);
        boolean isValid = bsqAddressValidator.validate(receiversAddressInputTextField.getText()).isValid &&
                bsqValidator.validate(amountInputTextField.getText()).isValid;
        sendBsqButton.setDisable(!isValid);

        boolean isBtcValid = btcAddressValidator.validate(receiversBtcAddressInputTextField.getText()).isValid &&
                btcValidator.validate(btcAmountInputTextField.getText()).isValid;
        sendBtcButton.setDisable(!isBtcValid);

        setSendBtcGroupVisibleState(availableNonBsqBalance.isPositive());
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

        sendBsqButton = addButtonAfterGroup(root, ++gridRow, Res.get("dao.wallet.send.send"));

        sendBsqButton.setOnAction((event) -> {
            // TODO break up in methods
            if (GUIUtil.isReadyForTxBroadcast(p2PService, walletsSetup)) {
                String receiversAddressString = bsqFormatter.getAddressFromBsqAddress(receiversAddressInputTextField.getText()).toString();
                Coin receiverAmount = bsqFormatter.parseToCoin(amountInputTextField.getText());
                try {
                    Transaction preparedSendTx = bsqWalletService.getPreparedSendBsqTx(receiversAddressString, receiverAmount);
                    Transaction txWithBtcFee = btcWalletService.completePreparedSendBsqTx(preparedSendTx, true);
                    Transaction signedTx = bsqWalletService.signTx(txWithBtcFee);
                    Coin miningFee = signedTx.getFee();
                    int txSize = signedTx.bitcoinSerialize().length;
                    showPublishTxPopup(receiverAmount,
                            txWithBtcFee,
                            TxType.TRANSFER_BSQ,
                            miningFee,
                            txSize,
                            receiversAddressInputTextField.getText(),
                            bsqFormatter,
                            btcFormatter,
                            () -> {
                                receiversAddressInputTextField.setText("");
                                amountInputTextField.setText("");
                            });
                } catch (BsqChangeBelowDustException e) {
                    String msg = Res.get("popup.warning.bsqChangeBelowDustException", bsqFormatter.formatCoinWithCode(e.getOutputValue()));
                    new Popup<>().warning(msg).show();
                } catch (Throwable t) {
                    handleError(t);
                }
            } else {
                GUIUtil.showNotReadyForTxBroadcastPopups(p2PService, walletsSetup);
            }
        });
    }

    private void setSendBtcGroupVisibleState(boolean visible) {
        btcTitledGroupBg.setVisible(visible);
        receiversBtcAddressInputTextField.setVisible(visible);
        btcAmountInputTextField.setVisible(visible);
        sendBtcButton.setVisible(visible);

        btcTitledGroupBg.setManaged(visible);
        receiversBtcAddressInputTextField.setManaged(visible);
        btcAmountInputTextField.setManaged(visible);
        sendBtcButton.setManaged(visible);
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

        sendBtcButton = addButtonAfterGroup(root, ++gridRow, Res.get("dao.wallet.send.sendBtc"));

        sendBtcButton.setOnAction((event) -> {
            if (GUIUtil.isReadyForTxBroadcast(p2PService, walletsSetup)) {
                String receiversAddressString = receiversBtcAddressInputTextField.getText();
                Coin receiverAmount = bsqFormatter.parseToBTC(btcAmountInputTextField.getText());
                try {
                    Transaction preparedSendTx = bsqWalletService.getPreparedSendBtcTx(receiversAddressString, receiverAmount);
                    Transaction txWithBtcFee = btcWalletService.completePreparedSendBsqTx(preparedSendTx, true);
                    Transaction signedTx = bsqWalletService.signTx(txWithBtcFee);
                    Coin miningFee = signedTx.getFee();

                    if (miningFee.getValue() >= receiverAmount.getValue())
                        GUIUtil.showWantToBurnBTCPopup(miningFee, receiverAmount, btcFormatter);
                    else {
                        int txSize = signedTx.bitcoinSerialize().length;
                        showPublishTxPopup(receiverAmount,
                                txWithBtcFee,
                                TxType.INVALID,
                                miningFee,
                                txSize, receiversBtcAddressInputTextField.getText(),
                                btcFormatter,
                                btcFormatter,
                                () -> {
                                    receiversBtcAddressInputTextField.setText("");
                                    btcAmountInputTextField.setText("");
                                });

                    }
                } catch (BsqChangeBelowDustException e) {
                    String msg = Res.get("popup.warning.btcChangeBelowDustException", btcFormatter.formatCoinWithCode(e.getOutputValue()));
                    new Popup<>().warning(msg).show();
                } catch (Throwable t) {
                    handleError(t);
                }
            } else {
                GUIUtil.showNotReadyForTxBroadcastPopups(p2PService, walletsSetup);
            }
        });
    }

    private void handleError(Throwable t) {
        if (t instanceof InsufficientMoneyException) {
            final Coin missingCoin = ((InsufficientMoneyException) t).missing;
            final String missing = missingCoin != null ? missingCoin.toFriendlyString() : "null";
            new Popup<>().warning(Res.get("popup.warning.insufficientBtcFundsForBsqTx", missing))
                    .actionButtonTextWithGoTo("navigation.funds.depositFunds")
                    .onAction(() -> navigation.navigateTo(MainView.class, FundsView.class, DepositView.class))
                    .show();
        } else {
            log.error(t.toString());
            t.printStackTrace();
            new Popup<>().warning(t.getMessage()).show();
        }
    }

    private void showPublishTxPopup(Coin receiverAmount,
                                    Transaction txWithBtcFee,
                                    TxType txType,
                                    Coin miningFee,
                                    int txSize, String address,
                                    BSFormatter amountFormatter, // can be BSQ or BTC formatter
                                    BSFormatter feeFormatter,
                                    ResultHandler resultHandler) {
        new Popup<>().headLine(Res.get("dao.wallet.send.sendFunds.headline"))
                .confirmation(Res.get("dao.wallet.send.sendFunds.details",
                        amountFormatter.formatCoinWithCode(receiverAmount),
                        address,
                        feeFormatter.formatCoinWithCode(miningFee),
                        CoinUtil.getFeePerByte(miningFee, txSize),
                        txSize / 1000d,
                        amountFormatter.formatCoinWithCode(receiverAmount)))
                .actionButtonText(Res.get("shared.yes"))
                .onAction(() -> {
                    walletsManager.publishAndCommitBsqTx(txWithBtcFee, txType, new TxBroadcaster.Callback() {
                        @Override
                        public void onSuccess(Transaction transaction) {
                            log.debug("Successfully sent tx with id {}", txWithBtcFee.getHashAsString());
                        }

                        @Override
                        public void onFailure(TxBroadcastException exception) {
                            new Popup<>().warning(exception.toString());
                        }
                    });
                    resultHandler.handleResult();
                })
                .closeButtonText(Res.get("shared.cancel"))
                .show();
    }
}

