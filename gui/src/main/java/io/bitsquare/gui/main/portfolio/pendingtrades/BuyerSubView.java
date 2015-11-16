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

public class BuyerSubView extends TradeSubView {
    private TradeWizardItem waitTxInBlockchain;
    private TradeWizardItem startPayment;
    private TradeWizardItem waitPaymentReceived;
    private TradeWizardItem waitPayoutUnlock;
    private TradeWizardItem completed;

    private final ChangeListener<PendingTradesViewModel.BuyerState> stateChangeListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerSubView(PendingTradesViewModel model) {
        super(model);
        stateChangeListener = (ov, oldValue, newValue) -> applyState(newValue);
    }

    @Override
    protected void activate() {
        super.activate();
        model.getBuyerState().addListener(stateChangeListener);
        applyState(model.getBuyerState().get());
    }

    @Override
    protected void deactivate() {
        super.deactivate();
        model.getBuyerState().removeListener(stateChangeListener);
    }

    @Override
    protected void addWizards() {
        waitTxInBlockchain = new TradeWizardItem(WaitTxInBlockchainView.class, "Wait for blockchain confirmation");
        startPayment = new TradeWizardItem(StartPaymentView.class, "Start payment");
        waitPaymentReceived = new TradeWizardItem(WaitPaymentReceivedView.class, "Wait until payment arrived");
        waitPayoutUnlock = new TradeWizardItem(WaitPayoutLockTimeView.class, "Wait for payout unlock");
        completed = new TradeWizardItem(CompletedView.class, "Completed");

        if (model.getLockTime() > 0)
            leftVBox.getChildren().setAll(waitTxInBlockchain, startPayment, waitPaymentReceived, waitPayoutUnlock, completed);
        else
            leftVBox.getChildren().setAll(waitTxInBlockchain, startPayment, waitPaymentReceived, completed);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // State
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void applyState(PendingTradesViewModel.BuyerState state) {
        log.debug("applyState " + state);

        waitTxInBlockchain.setDisabled();
        startPayment.setDisabled();
        waitPaymentReceived.setDisabled();
        waitPayoutUnlock.setDisabled();
        completed.setDisabled();

        if (tradeStepDetailsView != null)
            tradeStepDetailsView.doDeactivate();

        switch (state) {
            case UNDEFINED:
                contentPane.getChildren().clear();
                leftVBox.getChildren().clear();
                break;
            case WAIT_FOR_BLOCKCHAIN_CONFIRMATION:
                showItem(waitTxInBlockchain);

                ((WaitTxInBlockchainView) tradeStepDetailsView).setInfoLabelText("Deposit transaction has been published. You need to wait for at least " +
                        "one blockchain confirmation.");
                break;
            case REQUEST_START_FIAT_PAYMENT:
                waitTxInBlockchain.setCompleted();
                showItem(startPayment);
                break;
            case WAIT_FOR_FIAT_PAYMENT_RECEIPT:
                waitTxInBlockchain.setCompleted();
                startPayment.setCompleted();
                showItem(waitPaymentReceived);

                ((WaitPaymentReceivedView) tradeStepDetailsView).setInfoLabelText(BSResources.get("Waiting for the Bitcoin sellers confirmation " +
                                "that the {0} payment has arrived.",
                        model.getCurrencyCode()));
                break;
            case WAIT_FOR_UNLOCK_PAYOUT:
                if (model.getLockTime() > 0) {
                    waitTxInBlockchain.setCompleted();
                    startPayment.setCompleted();
                    waitPaymentReceived.setCompleted();
                    showItem(waitPayoutUnlock);

                    ((WaitPayoutLockTimeView) tradeStepDetailsView).setInfoLabelText("The payout transaction is signed and finalized by both parties." +
                            "\nFor reducing bank charge back risks you need to wait until the payout gets unlocked to transfer your Bitcoin.");
                }
                break;
            case REQUEST_WITHDRAWAL:
                waitTxInBlockchain.setCompleted();
                startPayment.setCompleted();
                waitPaymentReceived.setCompleted();
                waitPayoutUnlock.setCompleted();
                showItem(completed);

                CompletedView completedView = (CompletedView) tradeStepDetailsView;
                completedView.setBtcTradeAmountLabelText("You have bought:");
                completedView.setFiatTradeAmountLabelText("You have paid:");
                completedView.setBtcTradeAmountTextFieldText(model.getTradeVolume());
                completedView.setFiatTradeAmountTextFieldText(model.getFiatVolume());
                completedView.setFeesTextFieldText(model.getTotalFees());
                completedView.setSecurityDepositTextFieldText(model.getSecurityDeposit());
                completedView.setWithdrawAmountTextFieldText(model.getPayoutAmount());
                break;
            default:
                log.warn("unhandled buyerState " + state);
                break;
        }

        if (tradeStepDetailsView != null)
            tradeStepDetailsView.doActivate();
    }
}



