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

package bisq.desktop.main.portfolio.pendingtrades;

import bisq.desktop.main.portfolio.pendingtrades.steps.TradeWizardItem;
import bisq.desktop.main.portfolio.pendingtrades.steps.buyer.BuyerStep1View;
import bisq.desktop.main.portfolio.pendingtrades.steps.buyer.BuyerStep2View;
import bisq.desktop.main.portfolio.pendingtrades.steps.buyer.BuyerStep3View;
import bisq.desktop.main.portfolio.pendingtrades.steps.buyer.BuyerStep4View;

import bisq.core.locale.Res;
import bisq.core.trade.Trade;

import org.fxmisc.easybind.EasyBind;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BuyerSubView extends TradeSubView {
    private TradeWizardItem step1;
    private TradeWizardItem step2;
    private TradeWizardItem step3;
    private TradeWizardItem step4;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerSubView(PendingTradesViewModel model) {
        super(model);
    }

    @Override
    protected void activate() {
        viewStateSubscription = EasyBind.subscribe(model.getBuyerState(), this::onViewStateChanged);
        super.activate();
    }

    @Override
    protected void addWizards() {
        step1 = new TradeWizardItem(BuyerStep1View.class, Res.get("portfolio.pending.step1.waitForConf"), "1");
        step2 = new TradeWizardItem(BuyerStep2View.class, Res.get("portfolio.pending.step2_buyer.startPayment"), "2");
        step3 = new TradeWizardItem(BuyerStep3View.class, Res.get("portfolio.pending.step3_buyer.waitPaymentArrived"), "3");
        step4 = new TradeWizardItem(BuyerStep4View.class, Res.get("portfolio.pending.step5.completed"), "4");

        // This is a proposed solution the number 1 mediation issue of "Unable to confirm payment received".
        // If user double clicks on buyer step 2 (Start Payment), and if a dispute has been closed, then it will move
        // the trade state back to BUYER_CONFIRMED_IN_UI_FIAT_PAYMENT_INITIATED.  This will then cause the existing
        // workflow to show a button requesting that the trader resend the message.
        step2.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Trade trade = model.dataModel.getTrade();
                if (trade != null) {
                    log.info("User double-clicked on trade Step 2, id={}, state={}", trade.getShortId(), trade.stateProperty().get());
                    if (trade.disputeStateProperty().get() == Trade.DisputeState.MEDIATION_CLOSED && trade.getPhase() == Trade.Phase.FIAT_SENT) {
                        log.warn("Reverting trade to BUYER_CONFIRMED_IN_UI_FIAT_PAYMENT_INITIATED so that payment message may be re-sent");
                        trade.setState(Trade.State.BUYER_CONFIRMED_IN_UI_FIAT_PAYMENT_INITIATED);
                    }
                }
            }
        });

        addWizardsToGridPane(step1);
        addLineSeparatorToGridPane();
        addWizardsToGridPane(step2);
        addLineSeparatorToGridPane();
        addWizardsToGridPane(step3);
        addLineSeparatorToGridPane();
        addWizardsToGridPane(step4);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // State
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onViewStateChanged(PendingTradesViewModel.State viewState) {
        super.onViewStateChanged(viewState);

        if (viewState != null) {
            PendingTradesViewModel.BuyerState buyerState = (PendingTradesViewModel.BuyerState) viewState;

            step1.setDisabled();
            step2.setDisabled();
            step3.setDisabled();
            step4.setDisabled();

            switch (buyerState) {
                case UNDEFINED:
                    break;
                case STEP1:
                    showItem(step1);
                    break;
                case STEP2:
                    step1.setCompleted();
                    showItem(step2);
                    break;
                case STEP3:
                    step1.setCompleted();
                    step2.setCompleted();
                    showItem(step3);
                    break;
                case STEP4:
                    step1.setCompleted();
                    step2.setCompleted();
                    step3.setCompleted();
                    showItem(step4);
                    break;
                default:
                    log.warn("unhandled buyerState " + buyerState);
                    break;
            }
        }
    }
}



