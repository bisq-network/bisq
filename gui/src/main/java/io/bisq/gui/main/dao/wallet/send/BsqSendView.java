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

package io.bisq.gui.main.dao.wallet.send;

import com.google.common.util.concurrent.FutureCallback;
import io.bisq.common.locale.Res;
import io.bisq.core.btc.Restrictions;
import io.bisq.core.btc.wallet.BsqBalanceListener;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.util.CoinUtil;
import io.bisq.gui.Navigation;
import io.bisq.gui.common.view.ActivatableView;
import io.bisq.gui.common.view.FxmlView;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.main.MainView;
import io.bisq.gui.main.dao.wallet.BsqBalanceUtil;
import io.bisq.gui.main.funds.FundsView;
import io.bisq.gui.main.funds.deposit.DepositView;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.BsqFormatter;
import io.bisq.gui.util.Layout;
import io.bisq.gui.util.validation.BsqAddressValidator;
import io.bisq.gui.util.validation.BsqValidator;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static io.bisq.gui.util.FormBuilder.*;

@FxmlView
public class BsqSendView extends ActivatableView<GridPane, Void> {
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
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
    private BsqSendView(BsqWalletService bsqWalletService, BtcWalletService btcWalletService,
                        BsqFormatter bsqFormatter, BSFormatter btcFormatter, Navigation navigation,
                        BsqBalanceUtil bsqBalanceUtil, BsqValidator bsqValidator,
                        BsqAddressValidator bsqAddressValidator) {
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
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
                            });

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

