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

package io.bitsquare.gui.main.portfolio.pendingtrades.steps.buyer;

import io.bitsquare.app.BitsquareApp;
import io.bitsquare.app.Log;
import io.bitsquare.btc.Restrictions;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.funds.FundsView;
import io.bitsquare.gui.main.funds.transactions.TransactionsView;
import io.bitsquare.gui.main.popups.Popup;
import io.bitsquare.gui.main.portfolio.pendingtrades.PendingTradesViewModel;
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.TradeStepView;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.Layout;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;

import static io.bitsquare.gui.util.FormBuilder.*;

public class BuyerStep5View extends TradeStepView {
    private final ChangeListener<Boolean> focusedPropertyListener;

    protected Label btcTradeAmountLabel;
    protected Label fiatTradeAmountLabel;
    private TextField withdrawAddressTextField;
    private Button withdrawButton;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerStep5View(PendingTradesViewModel model) {
        super(model);

        focusedPropertyListener = (ov, oldValue, newValue) -> {
            if (oldValue && !newValue)
                model.withdrawAddressFocusOut(withdrawAddressTextField.getText());
        };
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

    @Override
    protected void addContent() {
        addTitledGroupBg(gridPane, gridRow, 4, "Summary of completed trade ", 0);
        Tuple2<Label, TextField> btcTradeAmountPair = addLabelTextField(gridPane, gridRow, getBtcTradeAmountLabel(), model.getTradeVolume(), Layout.FIRST_ROW_DISTANCE);
        btcTradeAmountLabel = btcTradeAmountPair.first;

        Tuple2<Label, TextField> fiatTradeAmountPair = addLabelTextField(gridPane, ++gridRow, getFiatTradeAmountLabel(), model.getFiatVolume());
        fiatTradeAmountLabel = fiatTradeAmountPair.first;

        addLabelTextField(gridPane, ++gridRow, "Total fees paid:", model.getTotalFees());

        addLabelTextField(gridPane, ++gridRow, "Refunded security deposit:", model.getSecurityDeposit());

        addTitledGroupBg(gridPane, ++gridRow, 2, "Withdraw your bitcoins", Layout.GROUP_DISTANCE);
        addLabelTextField(gridPane, gridRow, "Amount to withdraw:", model.getPayoutAmount(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        withdrawAddressTextField = addLabelInputTextField(gridPane, ++gridRow, "Withdraw to address:").second;
        withdrawButton = addButtonAfterGroup(gridPane, ++gridRow, "Withdraw to external wallet");
        withdrawButton.setOnAction(e -> {
            withdrawButton.setDisable(true);
            reviewWithdrawal();
        });

        String id = "tradeCompleteInfo";
        if (BitsquareApp.DEV_MODE)
            withdrawAddressTextField.setText("mi8k5f9L972VgDaT4LgjAhriC9hHEPL7EW");
        else
            new Popup().headLine("Trade completed")
                    .instruction("You can withdraw your funds now to your external Bitcoin wallet.")
                    .dontShowAgainId(id, preferences)
                    .show();
    }

    private void doWithdrawal() {
        model.dataModel.onWithdrawRequest(withdrawAddressTextField.getText(),
                () -> {
                    String id = "tradeCompleteWithdrawCompletedInfo";
                    if (preferences.showAgain(id)) {
                        new Popup().headLine("Withdrawal completed").instruction("Your completed trades are stored under \"Portfolio/History\".\n" +
                                "You can review all your bitcoin transactions under \"Funds/Transactions\"")
                                .actionButtonText("Go to \"Transactions\"")
                                .onAction(() -> model.dataModel.navigation.navigateTo(MainView.class, FundsView.class, TransactionsView.class))
                                .dontShowAgainId(id, preferences)
                                .show();
                    }
                    withdrawButton.setDisable(true);
                },
                (errorMessage, throwable) -> {
                    withdrawButton.setDisable(false);
                    if (throwable != null && throwable.getMessage() != null)
                        new Popup().error(errorMessage + "\n\n" + throwable.getMessage()).show();
                    else
                        new Popup().error(errorMessage).show();
                });
    }

    private void reviewWithdrawal() {
        Coin senderAmount = trade.getPayoutAmount();
        WalletService walletService = model.dataModel.walletService;
        String fromAddresses = walletService.getAddressEntryByOfferId(trade.getId()).getAddressString();
        String toAddresses = withdrawAddressTextField.getText();

        if (Restrictions.isAboveFixedTxFeeAndDust(senderAmount)) {
            try {
                Coin requiredFee = walletService.getRequiredFee(fromAddresses, toAddresses, senderAmount, null);
                Coin receiverAmount = senderAmount.subtract(requiredFee);
                if (BitsquareApp.DEV_MODE) {
                    doWithdrawal();
                } else {
                    BSFormatter formatter = model.formatter;
                    String id = "reviewWithdrawalAtTradeComplete";
                    new Popup().headLine("Confirm withdrawal request")
                            .confirmation("Sending: " + formatter.formatCoinWithCode(senderAmount) + "\n" +
                                    "From address: " + fromAddresses + "\n" +
                                    "To receiving address: " + toAddresses + ".\n" +
                                    "Required transaction fee is: " + formatter.formatCoinWithCode(requiredFee) + "\n\n" +
                                    "The recipient will receive: " + formatter.formatCoinWithCode(receiverAmount) + "\n\n" +
                                    "Are you sure you want to withdraw that amount?")
                            .actionButtonText("Yes")
                            .onAction(this::doWithdrawal)
                            .closeButtonText("Cancel")
                            .onClose(() -> withdrawButton.setDisable(false))
                            .dontShowAgainId(id, preferences)
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

    protected String getBtcTradeAmountLabel() {
        return "You have bought:";
    }

    protected String getFiatTradeAmountLabel() {
        return "You have paid:";
    }
}
