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

package io.bisq.gui.main.portfolio.pendingtrades.steps.buyer;

import io.bisq.common.UserThread;
import io.bisq.common.app.DevEnv;
import io.bisq.common.app.Log;
import io.bisq.common.handlers.FaultHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.locale.Res;
import io.bisq.common.util.Tuple2;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.AddressEntryException;
import io.bisq.core.btc.InsufficientFundsException;
import io.bisq.core.btc.Restrictions;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.user.DontShowAgainLookup;
import io.bisq.core.util.CoinUtil;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.components.TitledGroupBg;
import io.bisq.gui.main.MainView;
import io.bisq.gui.main.funds.FundsView;
import io.bisq.gui.main.funds.transactions.TransactionsView;
import io.bisq.gui.main.overlays.notifications.Notification;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.main.portfolio.pendingtrades.PendingTradesViewModel;
import io.bisq.gui.main.portfolio.pendingtrades.steps.TradeStepView;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.Layout;
import io.bisq.gui.util.validation.BtcAddressValidator;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.spongycastle.crypto.params.KeyParameter;

import java.util.concurrent.TimeUnit;

import static io.bisq.gui.util.FormBuilder.*;

public class BuyerStep4View extends TradeStepView {
    // private final ChangeListener<Boolean> focusedPropertyListener;

    private InputTextField withdrawAddressTextField;
    private Button withdrawToExternalWalletButton, useSavingsWalletButton;
    private TitledGroupBg withdrawTitledGroupBg;
    private Label withdrawAddressLabel;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerStep4View(PendingTradesViewModel model) {
        super(model);

       /* focusedPropertyListener = (ov, oldValue, newValue) -> {
            if (oldValue && !newValue)
                model.withdrawAddressFocusOut(withdrawAddressTextField.getText());
        };*/
    }

    @Override
    public void activate() {
        super.activate();

        // TODO valid. handler need improvement
        //withdrawAddressTextField.focusedProperty().addListener(focusedPropertyListener);
        //withdrawAddressTextField.setValidator(model.getBtcAddressValidator());
        // withdrawButton.disableProperty().bind(model.getWithdrawalButtonDisable());

        // We need to handle both cases: Address not set and address already set (when returning from other view)
        // We get address validation after focus out, so first make sure we loose focus and then set it again as hint for user to put address in
        //TODO app wide focus
       /* UserThread.execute(() -> {
            withdrawAddressTextField.requestFocus();
           UserThread.execute(() -> {
                this.requestFocus();
                UserThread.execute(() -> withdrawAddressTextField.requestFocus());
            });
        });*/

        hideNotificationGroup();
    }

    @Override
    public void deactivate() {
        Log.traceCall();
        super.deactivate();
        //withdrawAddressTextField.focusedProperty().removeListener(focusedPropertyListener);
        // withdrawButton.disableProperty().unbind();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Content
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("PointlessBooleanExpression")
    @Override
    protected void addContent() {
        addTitledGroupBg(gridPane, gridRow, 5, Res.get("portfolio.pending.step5_buyer.groupTitle"), 0);
        addLabelTextField(gridPane, gridRow, getBtcTradeAmountLabel(), model.getTradeVolume(), Layout.FIRST_ROW_DISTANCE);

        addLabelTextField(gridPane, ++gridRow, getFiatTradeAmountLabel(), model.getFiatVolume());
        addLabelTextField(gridPane, ++gridRow, Res.get("portfolio.pending.step5_buyer.refunded"), model.getSecurityDeposit());
        addLabelTextField(gridPane, ++gridRow, Res.get("portfolio.pending.step5_buyer.tradeFee"), model.getTradeFee());
        final String miningFee = model.dataModel.isMaker() ?
                Res.get("portfolio.pending.step5_buyer.makersMiningFee") :
                Res.get("portfolio.pending.step5_buyer.takersMiningFee");
        addLabelTextField(gridPane, ++gridRow, miningFee, model.getTxFee());
        withdrawTitledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 1, Res.get("portfolio.pending.step5_buyer.withdrawBTC"), Layout.GROUP_DISTANCE);
        addLabelTextField(gridPane, gridRow, Res.get("portfolio.pending.step5_buyer.amount"), model.getPayoutAmount(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        final Tuple2<Label, InputTextField> tuple2 = addLabelInputTextField(gridPane, ++gridRow, Res.get("portfolio.pending.step5_buyer.withdrawToAddress"));
        withdrawAddressLabel = tuple2.first;
        withdrawAddressLabel.setManaged(false);
        withdrawAddressLabel.setVisible(false);
        withdrawAddressTextField = tuple2.second;
        withdrawAddressTextField.setManaged(false);
        withdrawAddressTextField.setVisible(false);

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        useSavingsWalletButton = new Button(Res.get("portfolio.pending.step5_buyer.moveToBisqWallet"));
        useSavingsWalletButton.setDefaultButton(false);
        Label label = new Label(Res.get("shared.OR"));
        label.setPadding(new Insets(5, 0, 0, 0));
        withdrawToExternalWalletButton = new Button(Res.get("portfolio.pending.step5_buyer.withdrawExternal"));
        withdrawToExternalWalletButton.setDefaultButton(false);
        hBox.getChildren().addAll(useSavingsWalletButton, label, withdrawToExternalWalletButton);
        GridPane.setRowIndex(hBox, ++gridRow);
        GridPane.setColumnIndex(hBox, 1);
        GridPane.setMargin(hBox, new Insets(15, 10, 0, 0));
        gridPane.getChildren().add(hBox);

        useSavingsWalletButton.setOnAction(e -> {
            handleTradeCompleted();
            model.dataModel.tradeManager.addTradeToClosedTrades(trade);
        });
        withdrawToExternalWalletButton.setOnAction(e -> onWithdrawal());

        String key = "tradeCompleted" + trade.getId();
        //noinspection ConstantConditions
        if (!DevEnv.DEV_MODE && DontShowAgainLookup.showAgain(key)) {
            DontShowAgainLookup.dontShowAgain(key, true);
            new Notification().headLine(Res.get("notification.tradeCompleted.headline"))
                    .notification(Res.get("notification.tradeCompleted.msg"))
                    .autoClose()
                    .show();
        }
    }

    private void onWithdrawal() {
        withdrawAddressLabel.setManaged(true);
        withdrawAddressLabel.setVisible(true);
        withdrawAddressTextField.setManaged(true);
        withdrawAddressTextField.setVisible(true);
        GridPane.setRowSpan(withdrawTitledGroupBg, 2);
        withdrawToExternalWalletButton.setDefaultButton(true);
        withdrawToExternalWalletButton.setOnAction(e -> reviewWithdrawal());
    }

    @SuppressWarnings("PointlessBooleanExpression")
    private void reviewWithdrawal() {
        Coin amount = trade.getPayoutAmount();
        BtcWalletService walletService = model.dataModel.btcWalletService;

        AddressEntry fromAddressesEntry = walletService.getOrCreateAddressEntry(trade.getId(), AddressEntry.Context.TRADE_PAYOUT);
        String fromAddresses = fromAddressesEntry.getAddressString();
        String toAddresses = withdrawAddressTextField.getText();
        if (new BtcAddressValidator().validate(toAddresses).isValid) {
            Coin balance = walletService.getBalanceForAddress(fromAddressesEntry.getAddress());
            try {
                Transaction feeEstimationTransaction = walletService.getFeeEstimationTransaction(fromAddresses, toAddresses, amount, AddressEntry.Context.TRADE_PAYOUT);
                Coin fee = feeEstimationTransaction.getFee();
                //noinspection UnusedAssignment
                Coin receiverAmount = amount.subtract(fee);
                if (balance.isZero()) {
                    new Popup<>().warning(Res.get("portfolio.pending.step5_buyer.alreadyWithdrawn")).show();
                    model.dataModel.tradeManager.addTradeToClosedTrades(trade);
                } else {
                    if (toAddresses.isEmpty()) {
                        validateWithdrawAddress();
                    } else if (Restrictions.isAboveDust(amount, fee)) {
                        BSFormatter formatter = model.btcFormatter;
                        int txSize = feeEstimationTransaction.bitcoinSerialize().length;
                        double feePerByte = CoinUtil.getFeePerByte(fee, txSize);
                        double kb = txSize / 1000d;
                        String recAmount = formatter.formatCoinWithCode(receiverAmount);
                        new Popup<>().headLine(Res.get("portfolio.pending.step5_buyer.confirmWithdrawal"))
                                .confirmation(Res.get("shared.sendFundsDetailsWithFee",
                                        formatter.formatCoinWithCode(amount),
                                        fromAddresses,
                                        toAddresses,
                                        formatter.formatCoinWithCode(fee),
                                        feePerByte,
                                        kb,
                                        recAmount))
                                .actionButtonText(Res.get("shared.yes"))
                                .onAction(() -> doWithdrawal(amount, fee))
                                .closeButtonText(Res.get("shared.cancel"))
                                .onClose(() -> {
                                    useSavingsWalletButton.setDisable(false);
                                    withdrawToExternalWalletButton.setDisable(false);
                                })
                                .show();
                    } else {
                        new Popup<>().warning(Res.get("portfolio.pending.step5_buyer.amountTooLow")).show();
                    }
                }
            } catch (AddressFormatException e) {
                validateWithdrawAddress();
            } catch (AddressEntryException e) {
                log.error(e.getMessage());
            } catch (InsufficientFundsException e) {
                log.error(e.getMessage());
                e.printStackTrace();
                new Popup<>().warning(e.getMessage()).show();
            }
        } else {
            new Popup<>().warning(Res.get("validation.btc.invalidAddress")).show();
        }
    }

    private void doWithdrawal(Coin amount, Coin fee) {
        String toAddress = withdrawAddressTextField.getText();
        ResultHandler resultHandler = this::handleTradeCompleted;
        FaultHandler faultHandler = (errorMessage, throwable) -> {
            useSavingsWalletButton.setDisable(false);
            withdrawToExternalWalletButton.setDisable(false);
            if (throwable != null && throwable.getMessage() != null)
                new Popup<>().error(errorMessage + "\n\n" + throwable.getMessage()).show();
            else
                new Popup<>().error(errorMessage).show();
        };
        if (model.dataModel.btcWalletService.isEncrypted()) {
            UserThread.runAfter(() -> model.dataModel.walletPasswordWindow.onAesKey(aesKey ->
                    doWithdrawRequest(toAddress, amount, fee, aesKey, resultHandler, faultHandler))
                    .show(), 300, TimeUnit.MILLISECONDS);
        } else
            doWithdrawRequest(toAddress, amount, fee, null, resultHandler, faultHandler);
    }

    private void doWithdrawRequest(String toAddress, Coin amount, Coin fee, KeyParameter aesKey, ResultHandler resultHandler, FaultHandler faultHandler) {
        useSavingsWalletButton.setDisable(true);
        withdrawToExternalWalletButton.setDisable(true);
        model.dataModel.onWithdrawRequest(toAddress,
                amount,
                fee,
                aesKey,
                resultHandler,
                faultHandler);
    }

    @SuppressWarnings("PointlessBooleanExpression")
    private void handleTradeCompleted() {
        if (!DevEnv.DEV_MODE) {
            String key = "tradeCompleteWithdrawCompletedInfo";
            //noinspection unchecked
            new Popup<>().headLine(Res.get("portfolio.pending.step5_buyer.withdrawalCompleted.headline"))
                    .feedback(Res.get("portfolio.pending.step5_buyer.withdrawalCompleted.msg"))
                    .actionButtonTextWithGoTo("navigation.funds.transactions")
                    .onAction(() -> model.dataModel.navigation.navigateTo(MainView.class, FundsView.class, TransactionsView.class))
                    .dontShowAgainId(key)
                    .show();
        }
        useSavingsWalletButton.setDisable(true);
        withdrawToExternalWalletButton.setDisable(true);
        model.dataModel.btcWalletService.swapTradeEntryToAvailableEntry(trade.getId(), AddressEntry.Context.TRADE_PAYOUT);
    }

    private void validateWithdrawAddress() {
        withdrawAddressTextField.setValidator(model.btcAddressValidator);
        withdrawAddressTextField.requestFocus();
        useSavingsWalletButton.requestFocus();
    }

    protected String getBtcTradeAmountLabel() {
        return Res.get("portfolio.pending.step5_buyer.bought");
    }

    protected String getFiatTradeAmountLabel() {
        return Res.get("portfolio.pending.step5_buyer.paid");
    }
}
