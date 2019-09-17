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
import bisq.desktop.main.portfolio.pendingtrades.steps.seller.SellerStep1View;
import bisq.desktop.main.portfolio.pendingtrades.steps.seller.SellerStep2View;
import bisq.desktop.main.portfolio.pendingtrades.steps.seller.SellerStep3View;
import bisq.desktop.main.portfolio.pendingtrades.steps.seller.SellerStep4View;

import bisq.core.locale.Res;

import org.fxmisc.easybind.EasyBind;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SellerSubView extends TradeSubView {
    private TradeWizardItem step1;
    private TradeWizardItem step2;
    private TradeWizardItem step3;
    private TradeWizardItem step4;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    SellerSubView(PendingTradesViewModel model) {
        super(model);
    }

    @Override
    protected void activate() {
        viewStateSubscription = EasyBind.subscribe(model.getSellerState(), this::onViewStateChanged);
        super.activate();
    }

    @Override
    protected void addWizards() {
        step1 = new TradeWizardItem(SellerStep1View.class, Res.get("portfolio.pending.step1.waitForConf"), "1");
        step2 = new TradeWizardItem(SellerStep2View.class, Res.get("portfolio.pending.step2_seller.waitPaymentStarted"), "2");
        step3 = new TradeWizardItem(SellerStep3View.class, Res.get("portfolio.pending.step3_seller.confirmPaymentReceived"), "3");
        step4 = new TradeWizardItem(SellerStep4View.class, Res.get("portfolio.pending.step5.completed"), "4");

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
            PendingTradesViewModel.SellerState sellerState = (PendingTradesViewModel.SellerState) viewState;

            step1.setDisabled();
            step2.setDisabled();
            step3.setDisabled();
            step4.setDisabled();

            switch (sellerState) {
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
                    log.warn("unhandled viewState " + sellerState);
                    break;
            }
        }
    }
}



