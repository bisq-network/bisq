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

package io.bitsquare.gui.main.portfolio.pendingtrades;

import io.bitsquare.gui.main.portfolio.pendingtrades.steps.CompletedView;
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.ConfirmFiatReceivedView;
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.TradeWizardItem;
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.WaitPayoutFinalizedView;
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.WaitPayoutLockTimeView;
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.WaitTxInBlockchainView;
import io.bitsquare.locale.BSResources;

import javafx.beans.value.ChangeListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SellerSubView extends TradeSubView {
    private static final Logger log = LoggerFactory.getLogger(SellerSubView.class);

    private TradeWizardItem waitTxInBlockchain;
    private TradeWizardItem waitFiatStarted;
    private TradeWizardItem confirmFiatReceived;
    private TradeWizardItem payoutUnlock;
    private TradeWizardItem completed;

    private final ChangeListener<PendingTradesViewModel.SellerState> stateChangeListener;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerSubView(PendingTradesViewModel model) {
        super(model);
        stateChangeListener = (ov, oldValue, newValue) -> applyState(newValue);
    }

    @Override
    public void activate() {
        super.activate();
        model.getSellerState().addListener(stateChangeListener);
        applyState(model.getSellerState().get());
    }

    @Override
    public void deactivate() {
        super.deactivate();
        model.getSellerState().removeListener(stateChangeListener);
    }

    @Override
    protected void addWizards() {
        waitTxInBlockchain = new TradeWizardItem(WaitTxInBlockchainView.class, "Wait for blockchain confirmation");
        waitFiatStarted = new TradeWizardItem(WaitTxInBlockchainView.class, "Wait until EUR payment has started");
        confirmFiatReceived = new TradeWizardItem(ConfirmFiatReceivedView.class, "Confirm EUR payment received");
        payoutUnlock = new TradeWizardItem(WaitPayoutLockTimeView.class, "Wait for payout unlock");
        completed = new TradeWizardItem(CompletedView.class, "Completed");

        leftVBox.getChildren().setAll(waitTxInBlockchain, waitFiatStarted, confirmFiatReceived, payoutUnlock, completed);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // State
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void applyState(PendingTradesViewModel.SellerState viewState) {
        log.debug("applyState " + viewState);

        waitTxInBlockchain.setDisabled();
        waitFiatStarted.setDisabled();
        confirmFiatReceived.setDisabled();
        payoutUnlock.setDisabled();
        completed.setDisabled();

        if (tradeStepDetailsView != null)
            tradeStepDetailsView.deactivate();

        switch (viewState) {
            case UNDEFINED:
               /* showItem(waitTxInBlockchain);
                ((WaitTxInBlockchainView) tradeStepDetailsView).setInfoLabelText("Trade is in an incomplete state.");*/
                break;
            case WAIT_FOR_BLOCKCHAIN_CONFIRMATION:
                showItem(waitTxInBlockchain);

                ((WaitTxInBlockchainView) tradeStepDetailsView).setInfoLabelText("Deposit transaction has been published. " +
                        "The Bitcoin buyer need to wait for at least one block chain confirmation.");
               /* ((WaitTxInBlockchainView) tradeStepDetailsView).setInfoDisplayField(BSResources.get("The Bitcoin buyer needs to wait for at least one " +
                                "block chain confirmation before starting the {0} payment. " +
                                "That is needed to assure that the deposit input funding has not been " +
                                "double-spent. " +
                                "For higher trade volumes it is recommended to wait up to 6 confirmations.",
                        model.getCurrencyCode()));*/
                break;
            case WAIT_FOR_FIAT_PAYMENT_STARTED:
                waitTxInBlockchain.setCompleted();
                showItem(waitFiatStarted);

                ((WaitTxInBlockchainView) tradeStepDetailsView).setInfoLabelText(BSResources.get("Deposit transaction has at least one block chain " +
                                "confirmation. " +
                                "Waiting that other trader starts the {0} payment.",
                        model.getCurrencyCode()));
               /* ((WaitTxInBlockchainView) tradeStepDetailsView).setInfoDisplayField(BSResources.get("You will get informed when the other trader has " +
                                "indicated " +
                                "the {0} payment has been started.",
                        model.getCurrencyCode()));*/
                break;
            case REQUEST_CONFIRM_FIAT_PAYMENT_RECEIVED:
                waitTxInBlockchain.setCompleted();
                waitFiatStarted.setCompleted();
                showItem(confirmFiatReceived);

                ((ConfirmFiatReceivedView) tradeStepDetailsView).setInfoLabelText(BSResources.get("The Bitcoin buyer has started the {0} payment." +
                                "Check your payments account and confirm when you have received the payment.",
                        model.getCurrencyCode()));
                /*((ConfirmFiatReceivedView) tradeStepDetailsView).setInfoDisplayField(BSResources.get("It is important that you confirm when you have " +
                                "received the " +
                                "{0} payment as this will publish the payout transaction where you get returned " +
                                "your security deposit and the Bitcoin buyer receive the Bitcoin amount you sold.",
                        model.getCurrencyCode()));*/

                break;
            case WAIT_FOR_PAYOUT_TX:
                waitTxInBlockchain.setCompleted();
                waitFiatStarted.setCompleted();
                confirmFiatReceived.setCompleted();
                showItem(payoutUnlock);

                // We dont use a wizard for that step as it only gets displayed in case the other peer is offline
                tradeStepDetailsView = new WaitPayoutFinalizedView(model);
                contentPane.getChildren().setAll(tradeStepDetailsView);

                ((WaitPayoutFinalizedView) tradeStepDetailsView).setInfoLabelText("We requested the trading peer to sign and finalize the payout transaction." +
                        "\nIt might be that the other peer is offline, so we need to wait until he finalize the transaction when he goes online again.");
                break;
            case WAIT_FOR_UNLOCK_PAYOUT:
                waitTxInBlockchain.setCompleted();
                waitFiatStarted.setCompleted();
                confirmFiatReceived.setCompleted();
                showItem(payoutUnlock);

                ((WaitPayoutLockTimeView) tradeStepDetailsView).setInfoLabelText("The payout transaction is signed and finalized by both parties." +
                        "\nFor reducing bank charge back risks you need to wait until the payout gets unlocked to transfer your Bitcoin.");
                break;
            case REQUEST_WITHDRAWAL:
                waitTxInBlockchain.setCompleted();
                waitFiatStarted.setCompleted();
                confirmFiatReceived.setCompleted();
                payoutUnlock.setCompleted();
                showItem(completed);

                CompletedView completedView = (CompletedView) tradeStepDetailsView;
                completedView.setBtcTradeAmountLabelText("You have sold:");
                completedView.setFiatTradeAmountLabelText("You have received:");
                completedView.setBtcTradeAmountTextFieldText(model.getTradeVolume());
                completedView.setFiatTradeAmountTextFieldText(model.getFiatVolume());
                completedView.setFeesTextFieldText(model.getTotalFees());
                completedView.setSecurityDepositTextFieldText(model.getSecurityDeposit());
                completedView.setSummaryInfoDisplayText("Your security deposit has been refunded to you. " +
                        "You can review the details to that trade any time in the closed trades screen.");

                completedView.setWithdrawAmountTextFieldText(model.getPayoutAmount());
                break;
            case CLOSED:
                showItem(waitTxInBlockchain);
                ((WaitTxInBlockchainView) tradeStepDetailsView).setInfoLabelText("Trade is closed");
                break;
            case FAULT:
                showItem(waitTxInBlockchain);
                ((WaitTxInBlockchainView) tradeStepDetailsView).setInfoLabelText("Error occured");
                break;
            
           /* case MESSAGE_SENDING_FAILED:
                Popups.openWarningPopup("Sending message to trading peer failed.", model.getErrorMessage());
                break;
            case EXCEPTION:
                if (model.getTradeException() != null)
                    Popups.openExceptionPopup(model.getTradeException());
                else
                    Popups.openErrorPopup("An error occurred", model.getErrorMessage());
                break;*/
            default:
                log.warn("unhandled viewState " + viewState);
                break;
        }

        if (tradeStepDetailsView != null)
            tradeStepDetailsView.activate();
    }
}



