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

package bisq.desktop.main.portfolio.pendingtrades.steps.buyer;

import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.portfolio.pendingtrades.PendingTradesViewModel;
import bisq.desktop.main.portfolio.pendingtrades.steps.TradeStepView;

import bisq.core.locale.Res;
import bisq.core.trade.bisq_v1.TradeDataValidation;

public class BuyerStep1View extends TradeStepView {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerStep1View(PendingTradesViewModel model) {
        super(model);
    }

    @Override
    protected void onPendingTradesInitialized() {
        super.onPendingTradesInitialized();
        validatePayoutTx();
        validateDepositInputs();
        checkForTimeout();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Info
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getInfoBlockTitle() {
        return Res.get("portfolio.pending.step1.waitForConf");
    }

    @Override
    protected String getInfoText() {
        return Res.get("portfolio.pending.step1.info", Res.get("shared.You"));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Warning
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getFirstHalfOverWarnText() {
        return Res.get("portfolio.pending.step1.warn");
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dispute
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getPeriodOverWarnText() {
        return Res.get("portfolio.pending.step1.openForDispute");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void validatePayoutTx() {
        try {
            TradeDataValidation.validateDelayedPayoutTx(trade,
                    trade.getDelayedPayoutTx(),
                    model.dataModel.daoFacade,
                    model.dataModel.btcWalletService);
        } catch (TradeDataValidation.MissingTxException ignore) {
            // We don't react on those errors as a failed trade might get listed initially but getting removed from the
            // trade manager after initPendingTrades which happens after activate might be called.
        } catch (TradeDataValidation.ValidationException e) {
            if (!model.dataModel.tradeManager.isAllowFaultyDelayedTxs()) {
                new Popup().warning(Res.get("portfolio.pending.invalidTx", e.getMessage())).show();
            }
        }
    }

    // Verify that deposit tx inputs are matching the trade fee txs outputs.
    private void validateDepositInputs() {
        try {
            TradeDataValidation.validateDepositInputs(trade);
        } catch (TradeDataValidation.ValidationException e) {
            if (!model.dataModel.tradeManager.isAllowFaultyDelayedTxs()) {
                new Popup().warning(Res.get("portfolio.pending.invalidTx", e.getMessage())).show();
            }
        }
    }

}


