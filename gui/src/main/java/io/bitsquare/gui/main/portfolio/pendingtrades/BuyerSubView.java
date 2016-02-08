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

import io.bitsquare.gui.main.portfolio.pendingtrades.steps.TradeWizardItem;
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.buyer.*;
import javafx.beans.value.ChangeListener;

public class BuyerSubView extends TradeSubView {
    private TradeWizardItem step1;
    private TradeWizardItem step2;
    private TradeWizardItem step3;
    private TradeWizardItem step4;
    private TradeWizardItem step5;

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
        step1 = new TradeWizardItem(BuyerStep1View.class, "Wait for blockchain confirmation");
        step2 = new TradeWizardItem(BuyerStep2View.class, "Start payment");
        step3 = new TradeWizardItem(BuyerStep3View.class, "Wait until payment arrived");
        step4 = new TradeWizardItem(BuyerStep4View.class, "Wait for payout unlock");
        step5 = new TradeWizardItem(BuyerStep5View.class, "Completed");

        if (model.getLockTime() > 0) {
            addWizardsToGridPane(step1);
            addWizardsToGridPane(step2);
            addWizardsToGridPane(step3);
            addWizardsToGridPane(step4);
            addWizardsToGridPane(step5);

        } else {
            addWizardsToGridPane(step1);
            addWizardsToGridPane(step2);
            addWizardsToGridPane(step3);
            addWizardsToGridPane(step5);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // State
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyState(PendingTradesViewModel.BuyerState state) {
        log.debug("applyState " + state);

        step1.setDisabled();
        step2.setDisabled();
        step3.setDisabled();
        step4.setDisabled();
        step5.setDisabled();

        if (tradeStepView != null)
            tradeStepView.doDeactivate();

        switch (state) {
            case UNDEFINED:
                contentPane.getChildren().clear();
                leftVBox.getChildren().clear();
                break;
            case WAIT_FOR_BLOCKCHAIN_CONFIRMATION:
                showItem(step1);
                break;
            case REQUEST_START_FIAT_PAYMENT:
                step1.setCompleted();
                showItem(step2);
                break;
            case WAIT_FOR_FIAT_PAYMENT_RECEIPT:
                step1.setCompleted();
                step2.setCompleted();
                showItem(step3);
                break;
            case WAIT_FOR_UNLOCK_PAYOUT:
                if (model.getLockTime() > 0) {
                    step1.setCompleted();
                    step2.setCompleted();
                    step3.setCompleted();
                    showItem(step4);
                }
                break;
            case REQUEST_WITHDRAWAL:
                step1.setCompleted();
                step2.setCompleted();
                step3.setCompleted();
                step4.setCompleted();
                showItem(step5);

                BuyerStep5View buyerStep5View = (BuyerStep5View) tradeStepView;
                buyerStep5View.setBtcTradeAmountLabelText("You have bought:");
                buyerStep5View.setFiatTradeAmountLabelText("You have paid:");
                buyerStep5View.setBtcTradeAmountTextFieldText(model.getTradeVolume());
                buyerStep5View.setFiatTradeAmountTextFieldText(model.getFiatVolume());
                buyerStep5View.setFeesTextFieldText(model.getTotalFees());
                buyerStep5View.setSecurityDepositTextFieldText(model.getSecurityDeposit());
                buyerStep5View.setWithdrawAmountTextFieldText(model.getPayoutAmount());
                break;
            default:
                log.warn("unhandled buyerState " + state);
                break;
        }

        if (tradeStepView != null)
            tradeStepView.doActivate();
    }
}



