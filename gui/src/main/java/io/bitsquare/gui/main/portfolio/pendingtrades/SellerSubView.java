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

import io.bitsquare.gui.main.portfolio.pendingtrades.steps.*;
import io.bitsquare.locale.BSResources;
import javafx.beans.value.ChangeListener;

public class SellerSubView extends TradeSubView {
    private TradeWizardItem waitTxInBlockchain;
    private TradeWizardItem waitPaymentStarted;
    private TradeWizardItem confirmPaymentReceived;
    private TradeWizardItem waitPayoutUnlock;
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
    protected void activate() {
        super.activate();
        model.getSellerState().addListener(stateChangeListener);
        applyState(model.getSellerState().get());
    }

    @Override
    protected void deactivate() {
        super.deactivate();
        model.getSellerState().removeListener(stateChangeListener);
    }

    @Override
    protected void addWizards() {
        waitTxInBlockchain = new TradeWizardItem(WaitTxInBlockchainView.class, "Wait for blockchain confirmation");
        waitPaymentStarted = new TradeWizardItem(WaitPaymentStartedView.class, "Wait until payment has started");
        confirmPaymentReceived = new TradeWizardItem(ConfirmPaymentReceivedView.class, "Confirm payment received");
        waitPayoutUnlock = new TradeWizardItem(WaitPayoutLockTimeView.class, "Wait for payout unlock");
        completed = new TradeWizardItem(CompletedView.class, "Completed");

        if (model.getLockTime() > 0)
            leftVBox.getChildren().setAll(waitTxInBlockchain, waitPaymentStarted, confirmPaymentReceived, waitPayoutUnlock, completed);
        else
            leftVBox.getChildren().setAll(waitTxInBlockchain, waitPaymentStarted, confirmPaymentReceived, completed);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // State
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyState(PendingTradesViewModel.SellerState viewState) {
        log.debug("applyState " + viewState);

        waitTxInBlockchain.setDisabled();
        waitPaymentStarted.setDisabled();
        confirmPaymentReceived.setDisabled();
        waitPayoutUnlock.setDisabled();
        completed.setDisabled();

        if (tradeStepDetailsView != null)
            tradeStepDetailsView.doDeactivate();

        switch (viewState) {
            case UNDEFINED:
                contentPane.getChildren().clear();
                leftVBox.getChildren().clear();
                break;
            case WAIT_FOR_BLOCKCHAIN_CONFIRMATION:
                showItem(waitTxInBlockchain);

                ((WaitTxInBlockchainView) tradeStepDetailsView).setInfoLabelText("Deposit transaction has been published. " +
                        "The bitcoin buyer need to wait for at least one blockchain confirmation.");
                break;
            case WAIT_FOR_FIAT_PAYMENT_STARTED:
                waitTxInBlockchain.setCompleted();
                showItem(waitPaymentStarted);

                ((WaitTxInBlockchainView) tradeStepDetailsView).setInfoLabelText(BSResources.get("Deposit transaction has at least one blockchain " +
                                "confirmation. " +
                                "Waiting that other trader starts the {0} payment.",
                        model.getCurrencyCode()));
                break;
            case REQUEST_CONFIRM_FIAT_PAYMENT_RECEIVED:
                waitTxInBlockchain.setCompleted();
                waitPaymentStarted.setCompleted();
                showItem(confirmPaymentReceived);

                if (model.isBlockChainMethod()) {
                    ((ConfirmPaymentReceivedView) tradeStepDetailsView).setInfoLabelText(BSResources.get("The bitcoin buyer has started the {0} payment. " +
                                    "Check your Altcoin wallet or Block explorer and confirm when you have received the payment.",
                            model.getCurrencyCode()));
                } else {
                    ((ConfirmPaymentReceivedView) tradeStepDetailsView).setInfoLabelText(BSResources.get("The bitcoin buyer has started the {0} payment. " +
                                    "Check your payment account and confirm when you have received the payment.",
                            model.getCurrencyCode()));
                }

                break;
            case WAIT_FOR_PAYOUT_TX:
                waitTxInBlockchain.setCompleted();
                waitPaymentStarted.setCompleted();
                confirmPaymentReceived.setCompleted();
                showItem(waitPayoutUnlock);

                // We don't use a wizard for that step as it only gets displayed in case the other peer is offline
                tradeStepDetailsView = new WaitPayoutFinalizedView(model);
                contentPane.getChildren().setAll(tradeStepDetailsView);

                ((WaitPayoutFinalizedView) tradeStepDetailsView).setInfoLabelText("We requested the trading peer to sign and finalize the payout " +
                        "transaction.\n" +
                        "It might be that the other peer is offline, so we need to wait until he finalize the transaction when he goes online again.");
                break;
            case WAIT_FOR_UNLOCK_PAYOUT:
                waitTxInBlockchain.setCompleted();
                waitPaymentStarted.setCompleted();
                confirmPaymentReceived.setCompleted();
                showItem(waitPayoutUnlock);

                ((WaitPayoutLockTimeView) tradeStepDetailsView).setInfoLabelText("The payout transaction is signed and finalized by both parties." +
                        "\nFor reducing bank charge back risks you need to wait until the payout gets unlocked to transfer your bitcoin.");
                break;
            case REQUEST_WITHDRAWAL:
                waitTxInBlockchain.setCompleted();
                waitPaymentStarted.setCompleted();
                confirmPaymentReceived.setCompleted();
                waitPayoutUnlock.setCompleted();
                showItem(completed);

                CompletedView completedView = (CompletedView) tradeStepDetailsView;
                completedView.setBtcTradeAmountLabelText("You have sold:");
                completedView.setFiatTradeAmountLabelText("You have received:");
                completedView.setBtcTradeAmountTextFieldText(model.getTradeVolume());
                completedView.setFiatTradeAmountTextFieldText(model.getFiatVolume());
                completedView.setFeesTextFieldText(model.getTotalFees());
                completedView.setSecurityDepositTextFieldText(model.getSecurityDeposit());

                completedView.setWithdrawAmountTextFieldText(model.getPayoutAmount());
                break;
            default:
                log.warn("unhandled viewState " + viewState);
                break;
        }

        if (tradeStepDetailsView != null)
            tradeStepDetailsView.doActivate();
    }
}



