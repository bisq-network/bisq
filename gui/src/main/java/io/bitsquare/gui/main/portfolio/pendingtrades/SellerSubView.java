/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main.portfolio.pendingtrades;

import io.bitsquare.app.Log;
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.TradeWizardItem;
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.seller.*;
import io.bitsquare.locale.Res;
import org.fxmisc.easybind.EasyBind;

public class SellerSubView extends TradeSubView {
    private TradeWizardItem step1;
    private TradeWizardItem step2;
    private TradeWizardItem step3;
    private TradeWizardItem step4;
    private TradeWizardItem step5;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerSubView(PendingTradesViewModel model) {
        super(model);
    }

    @Override
    protected void activate() {
        viewStateSubscription = EasyBind.subscribe(model.getSellerState(), this::onViewStateChanged);
        super.activate();
    }

    @Override
    protected void addWizards() {
        step1 = new TradeWizardItem(SellerStep1View.class, Res.get("portfolio.pending.step1.waitForConf"));
        step2 = new TradeWizardItem(SellerStep2View.class, Res.get("portfolio.pending.step2_seller.waitPaymentStarted"));
        step3 = new TradeWizardItem(SellerStep3View.class, Res.get("portfolio.pending.step3_seller.confirmPaymentReceived"));
        step4 = new TradeWizardItem(SellerStep4View.class, Res.get("portfolio.pending.step4.waitPaymentUnlocked"));
        step5 = new TradeWizardItem(SellerStep5View.class, Res.get("portfolio.pending.step5.completed"));


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

    @Override
    protected void onViewStateChanged(PendingTradesViewModel.State viewState) {
        if (viewState != null) {
            Log.traceCall(viewState.toString());
            PendingTradesViewModel.SellerState sellerState = (PendingTradesViewModel.SellerState) viewState;

            step1.setDisabled();
            step2.setDisabled();
            step3.setDisabled();
            step4.setDisabled();
            step5.setDisabled();

            switch (sellerState) {
                case UNDEFINED:
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
                    showItem(step3);

                    // We don't use a wizard for that step as it only gets displayed in case the other peer is offline
                    tradeStepView = new SellerStep3bView(model);
                    contentPane.getChildren().setAll(tradeStepView);
                    break;
                case WAIT_FOR_BROADCAST_AFTER_UNLOCK:
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
                    break;
                default:
                    log.warn("unhandled viewState " + sellerState);
                    break;
            }
        }
    }
}



