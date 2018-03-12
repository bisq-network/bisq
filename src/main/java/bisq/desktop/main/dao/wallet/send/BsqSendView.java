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
import bisq.desktop.main.MainView;
import bisq.desktop.main.dao.wallet.BsqBalanceUtil;
import bisq.desktop.main.funds.FundsView;
import bisq.desktop.main.funds.deposit.DepositView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.BSFormatter;
import bisq.desktop.util.BsqFormatter;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.BsqAddressValidator;
import bisq.desktop.util.validation.BsqValidator;

import bisq.core.btc.Restrictions;
import bisq.core.btc.wallet.BsqBalanceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletsSetup;
import bisq.core.util.CoinUtil;

import bisq.network.p2p.P2PService;

import bisq.common.locale.Res;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import com.google.common.util.concurrent.FutureCallback;

import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;

import javafx.beans.value.ChangeListener;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.addButtonAfterGroup;
import static bisq.desktop.util.FormBuilder.addLabelInputTextField;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class BsqSendView extends ActivatableView<GridPane, Void> {
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final WalletsSetup walletsSetup;
    private final P2PService p2PService;
    private final BsqFormatter bsqFormatter;
    private final BSFormatter btcFormatter;
    private final Navigation navigation;
    private final BsqBalanceUtil bsqBalanceUtil;
    private final BsqValidator bsqValidator;
    private final BsqAddressValidator bsqAddressValidator;

    private int gridRow = 0;
    private InputTextField amountInputTextField;
    private Button sendButton;
    private InputTextField receiversAddressInputTextField;
    private ChangeListener<Boolean> focusOutListener;
    private BsqBalanceListener balanceListener;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BsqSendView(BsqWalletService bsqWalletService,
                        BtcWalletService btcWalletService,
                        WalletsSetup walletsSetup,
                        P2PService p2PService,
                        BsqFormatter bsqFormatter,
                        BSFormatter btcFormatter,
                        Navigation navigation,
                        BsqBalanceUtil bsqBalanceUtil,
                        BsqValidator bsqValidator,
                        BsqAddressValidator bsqAddressValidator) {
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.walletsSetup = walletsSetup;
        this.p2PService = p2PService;
        this.bsqFormatter = bsqFormatter;
        this.btcFormatter = btcFormatter;
        this.navigation = navigation;
        this.bsqBalanceUtil = bsqBalanceUtil;
        this.bsqValidator = bsqValidator;
        this.bsqAddressValidator = bsqAddressValidator;
    }

    @Override
    public void initialize() {
        gridRow = bsqBalanceUtil.addGroup(root, gridRow);

        addTitledGroupBg(root, ++gridRow, 3, Res.get("dao.wallet.send.sendFunds"), Layout.GROUP_DISTANCE);

        receiversAddressInputTextField = addLabelInputTextField(root, gridRow,
                Res.get("dao.wallet.send.receiverAddress"), Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        receiversAddressInputTextField.setPromptText(Res.get("dao.wallet.send.setDestinationAddress"));
        receiversAddressInputTextField.setValidator(bsqAddressValidator);

        amountInputTextField = addLabelInputTextField(root, ++gridRow, Res.get("dao.wallet.send.amount")).second;
        amountInputTextField.setPromptText(Res.get("dao.wallet.send.setAmount", Restrictions.getMinNonDustOutput().value));
        amountInputTextField.setValidator(bsqValidator);

        focusOutListener = (observable, oldValue, newValue) -> {
            if (!newValue)
                verifyInputs();
        };

        sendButton = addButtonAfterGroup(root, ++gridRow, Res.get("dao.wallet.send.send"));

        sendButton.setOnAction((event) -> {
            // TODO break up in methods
            if (GUIUtil.isReadyForTxBroadcast(p2PService, walletsSetup)) {
                String receiversAddressString = bsqFormatter.getAddressFromBsqAddress(receiversAddressInputTextField.getText()).toString();
                Coin receiverAmount = bsqFormatter.parseToCoin(amountInputTextField.getText());
                try {
                    Transaction preparedSendTx = bsqWalletService.getPreparedSendTx(receiversAddressString, receiverAmount);
                    Transaction txWithBtcFee = btcWalletService.completePreparedSendBsqTx(preparedSendTx, true);
                    Transaction signedTx = bsqWalletService.signTx(txWithBtcFee);
                    Coin miningFee = signedTx.getFee();
                    int txSize = signedTx.bitcoinSerialize().length;
                    new Popup<>().headLine(Res.get("dao.wallet.send.sendFunds.headline"))
                            .confirmation(Res.get("dao.wallet.send.sendFunds.details",
                                    bsqFormatter.formatCoinWithCode(receiverAmount),
                                    receiversAddressInputTextField.getText(),
                                    btcFormatter.formatCoinWithCode(miningFee),
                                    CoinUtil.getFeePerByte(miningFee, txSize),
                                    txSize / 1000d,
                                    bsqFormatter.formatCoinWithCode(receiverAmount)))
                            .actionButtonText(Res.get("shared.yes"))
                            .onAction(() -> {
                                bsqWalletService.commitTx(txWithBtcFee);
                                // We need to create another instance, otherwise the tx would trigger an invalid state exception
                                // if it gets committed 2 times
                                btcWalletService.commitTx(btcWalletService.getClonedTransaction(txWithBtcFee));

                                bsqWalletService.broadcastTx(signedTx, new FutureCallback<Transaction>() {
                                    @Override
                                    public void onSuccess(@Nullable Transaction transaction) {
                                        if (transaction != null) {
                                            log.debug("Successfully sent tx with id " + transaction.getHashAsString());
                                        }
                                    }

                                    @Override
                                    public void onFailure(@NotNull Throwable t) {
                                        log.error(t.toString());
                                        new Popup<>().warning(t.toString());
                                    }
                                }, 15);

                                receiversAddressInputTextField.setText("");
                                amountInputTextField.setText("");
                            })
                            .closeButtonText(Res.get("shared.cancel"))
                            .show();
                } catch (Throwable t) {
                    if (t instanceof InsufficientMoneyException) {
                        final Coin missingCoin = ((InsufficientMoneyException) t).missing;
                        final String missing = missingCoin != null ? missingCoin.toFriendlyString() : "null";
                        //noinspection unchecked
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
            } else {
                GUIUtil.showNotReadyForTxBroadcastPopups(p2PService, walletsSetup);
            }
        });

        balanceListener = (availableBalance, unverifiedBalance) -> verifyInputs();
    }

    @Override
    protected void activate() {
        bsqBalanceUtil.activate();
        receiversAddressInputTextField.focusedProperty().addListener(focusOutListener);
        amountInputTextField.focusedProperty().addListener(focusOutListener);
        bsqWalletService.addBsqBalanceListener(balanceListener);
        verifyInputs();
    }

    @Override
    protected void deactivate() {
        bsqBalanceUtil.deactivate();
        receiversAddressInputTextField.focusedProperty().removeListener(focusOutListener);
        amountInputTextField.focusedProperty().removeListener(focusOutListener);
        bsqWalletService.removeBsqBalanceListener(balanceListener);
    }

    private void verifyInputs() {
        bsqValidator.setAvailableBalance(bsqWalletService.getAvailableBalance());
        boolean isValid = bsqAddressValidator.validate(receiversAddressInputTextField.getText()).isValid &&
                bsqValidator.validate(amountInputTextField.getText()).isValid;
        sendButton.setDisable(!isValid);
    }
}

