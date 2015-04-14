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
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.StartFiatView;
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.TradeWizardItem;
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.WaitFiatReceivedView;
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.WaitPayoutLockTimeView;
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.WaitTxInBlockchainView;
import io.bitsquare.locale.BSResources;

import javafx.beans.value.ChangeListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuyerSubView extends TradeSubView {
    private static final Logger log = LoggerFactory.getLogger(BuyerSubView.class);

    private TradeWizardItem waitTxInBlockchain;
    private TradeWizardItem startFiat;
    private TradeWizardItem waitFiatReceived;
    private TradeWizardItem payoutUnlock;
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
    public void activate() {
        super.activate();
        model.getBuyerState().addListener(stateChangeListener);
        applyState(model.getBuyerState().get());
    }

    @Override
    public void deactivate() {
        super.deactivate();
        model.getBuyerState().removeListener(stateChangeListener);
    }

    @Override
    protected void addWizards() {
        waitTxInBlockchain = new TradeWizardItem(WaitTxInBlockchainView.class, "Wait for blockchain confirmation");
        startFiat = new TradeWizardItem(StartFiatView.class, "Start EUR payment");
        waitFiatReceived = new TradeWizardItem(WaitFiatReceivedView.class, "Wait until EUR payment arrived");
        payoutUnlock = new TradeWizardItem(WaitPayoutLockTimeView.class, "Wait for payout unlock");
        completed = new TradeWizardItem(CompletedView.class, "Completed");

        leftVBox.getChildren().setAll(waitTxInBlockchain, startFiat, waitFiatReceived, payoutUnlock, completed);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // State
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void applyState(PendingTradesViewModel.BuyerState state) {
        log.debug("applyState " + state);

        waitTxInBlockchain.setDisabled();
        startFiat.setDisabled();
        waitFiatReceived.setDisabled();
        payoutUnlock.setDisabled();
        completed.setDisabled();

        if (tradeStepDetailsView != null)
            tradeStepDetailsView.deactivate();

        switch (state) {
            case WAIT_FOR_BLOCKCHAIN_CONFIRMATION:
                showItem(waitTxInBlockchain);

                ((WaitTxInBlockchainView) tradeStepDetailsView).setInfoLabelText("Deposit transaction has been published. You need to wait for at least " +
                        "one block chain confirmation.");
               /* ((WaitTxInBlockchainView) tradeStepDetailsView).setInfoDisplayField("You need to wait for at least one block chain confirmation to" +
                        " be sure that the deposit funding has not been double spent. For higher trade volumes we" +
                        " recommend to wait up to 6 confirmations.");*/
                break;
            case REQUEST_START_FIAT_PAYMENT:
                waitTxInBlockchain.setCompleted();
                showItem(startFiat);
                break;
            case WAIT_FOR_FIAT_PAYMENT_RECEIPT:
                waitTxInBlockchain.setCompleted();
                startFiat.setCompleted();
                showItem(waitFiatReceived);

                ((WaitFiatReceivedView) tradeStepDetailsView).setInfoLabelText(BSResources.get("Waiting for the Bitcoin sellers confirmation " +
                                "that the {0} payment has arrived.",
                        model.getCurrencyCode()));
              /*  ((WaitFiatReceivedView) tradeStepDetailsView).setInfoDisplayField(BSResources.get("When the confirmation that the {0} payment arrived at " +
                                "the Bitcoin sellers payment account, the payout transaction will be published.",
                        model.getCurrencyCode()));*/
                break;
            case WAIT_FOR_UNLOCK_PAYOUT:
                waitTxInBlockchain.setCompleted();
                startFiat.setCompleted();
                waitFiatReceived.setCompleted();
                showItem(payoutUnlock);

                ((WaitPayoutLockTimeView) tradeStepDetailsView).setInfoLabelText("The payout transaction is signed and finalized by both parties." +
                        "\nFor reducing bank charge back risks you need to wait until the payout gets unlocked to transfer your Bitcoin.");
                break;
            case REQUEST_WITHDRAWAL:
                waitTxInBlockchain.setCompleted();
                startFiat.setCompleted();
                waitFiatReceived.setCompleted();
                payoutUnlock.setCompleted();
                showItem(completed);

                CompletedView completedView = (CompletedView) tradeStepDetailsView;
                completedView.setBtcTradeAmountLabelText("You have bought:");
                completedView.setFiatTradeAmountLabelText("You have paid:");
                completedView.setBtcTradeAmountTextFieldText(model.getTradeVolume());
                completedView.setFiatTradeAmountTextFieldText(model.getFiatVolume());
                completedView.setFeesTextFieldText(model.getTotalFees());
                completedView.setSecurityDepositTextFieldText(model.getSecurityDeposit());
                completedView.setSummaryInfoDisplayText("Your security deposit has been refunded to you. " +
                        "You can review the details to that trade any time in the closed trades screen.");
                completedView.setWithdrawAmountTextFieldText(model.getPayoutAmount());
                break;
            /*case MESSAGE_SENDING_FAILED:
                Popups.openWarningPopup("Sending message to trading peer failed.", model.getErrorMessage());
                break;
            case EXCEPTION:
                if (model.getTradeException() != null)
                    Popups.openExceptionPopup(model.getTradeException());
                else
                    Popups.openErrorPopup("An error occurred", model.getErrorMessage());
                break;*/
            default:
                log.warn("unhandled buyerState " + state);
                break;
        }

        if (tradeStepDetailsView != null)
            tradeStepDetailsView.activate();
    }
}



