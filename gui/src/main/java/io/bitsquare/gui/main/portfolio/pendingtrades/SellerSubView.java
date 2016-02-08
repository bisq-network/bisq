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
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.seller.*;
import javafx.beans.value.ChangeListener;
import javafx.scene.layout.GridPane;

public class SellerSubView extends TradeSubView {
    private TradeWizardItem step1;
    private TradeWizardItem step2;
    private TradeWizardItem step3;
    private TradeWizardItem step4;
    private TradeWizardItem step5;

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
        step1 = new TradeWizardItem(SellerStep1View.class, "Wait for blockchain confirmation");
        step2 = new TradeWizardItem(SellerStep2View.class, "Wait until payment has started");
        step3 = new TradeWizardItem(SellerStep3View.class, "Confirm payment received");
        step4 = new TradeWizardItem(SellerStep4aView.class, "Wait for payout unlock");
        step5 = new TradeWizardItem(SellerStep5View.class, "Completed");

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
            GridPane.setRowSpan(tradeProcessTitledGroupBg, 4);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // State
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyState(PendingTradesViewModel.SellerState viewState) {
        log.debug("applyState " + viewState);

        step1.setDisabled();
        step2.setDisabled();
        step3.setDisabled();
        step4.setDisabled();
        step5.setDisabled();

        if (tradeStepView != null)
            tradeStepView.doDeactivate();

        switch (viewState) {
            case UNDEFINED:
                contentPane.getChildren().clear();
                leftVBox.getChildren().clear();
                break;
            case WAIT_FOR_BLOCKCHAIN_CONFIRMATION:
                showItem(step1);
                break;
            case WAIT_FOR_FIAT_PAYMENT_STARTED:
                step1.setCompleted();
                showItem(step2);
                break;
            case REQUEST_CONFIRM_FIAT_PAYMENT_RECEIVED:
                step1.setCompleted();
                step2.setCompleted();
                showItem(step3);
                break;
            case WAIT_FOR_PAYOUT_TX:
                step1.setCompleted();
                step2.setCompleted();
                step3.setCompleted();
                showItem(step4);

                // We don't use a wizard for that step as it only gets displayed in case the other peer is offline
                tradeStepView = new SellerStep4bView(model);
                contentPane.getChildren().setAll(tradeStepView);
                break;
            case WAIT_FOR_UNLOCK_PAYOUT:
                step1.setCompleted();
                step2.setCompleted();
                step3.setCompleted();
                showItem(step4);
                break;
            case REQUEST_WITHDRAWAL:
                step1.setCompleted();
                step2.setCompleted();
                step3.setCompleted();
                step4.setCompleted();
                showItem(step5);

                SellerStep5View sellerStep5View = (SellerStep5View) tradeStepView;
                sellerStep5View.setBtcTradeAmountLabelText("You have sold:");
                sellerStep5View.setFiatTradeAmountLabelText("You have received:");
                sellerStep5View.setBtcTradeAmountTextFieldText(model.getTradeVolume());
                sellerStep5View.setFiatTradeAmountTextFieldText(model.getFiatVolume());
                sellerStep5View.setFeesTextFieldText(model.getTotalFees());
                sellerStep5View.setSecurityDepositTextFieldText(model.getSecurityDeposit());

                sellerStep5View.setWithdrawAmountTextFieldText(model.getPayoutAmount());
                break;
            default:
                log.warn("unhandled viewState " + viewState);
                break;
        }

        if (tradeStepView != null)
            tradeStepView.doActivate();
    }
}



