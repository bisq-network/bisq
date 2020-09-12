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
import bisq.core.trade.DelayedPayoutTxValidation;

import bisq.common.UserThread;

import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ChangeListener;

public class BuyerStep1View extends TradeStepView {
    private ChangeListener<Boolean> pendingTradesInitializedListener;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerStep1View(PendingTradesViewModel model) {
        super(model);
    }

    @Override
    public void activate() {
        super.activate();

        // We need to have the trades initialized before we can call validatePayoutTx.
        BooleanProperty pendingTradesInitialized = model.dataModel.tradeManager.getPendingTradesInitialized();
        if (pendingTradesInitialized.get()) {
            validatePayoutTx();
        } else {
            pendingTradesInitializedListener = (observable, oldValue, newValue) -> {
                if (newValue) {
                    validatePayoutTx();
                    UserThread.execute(() -> pendingTradesInitialized.removeListener(pendingTradesInitializedListener));
                }
            };
            pendingTradesInitialized.addListener(pendingTradesInitializedListener);
        }
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
            DelayedPayoutTxValidation.validatePayoutTx(trade,
                    trade.getDelayedPayoutTx(),
                    model.dataModel.daoFacade,
                    model.dataModel.btcWalletService);
        } catch (DelayedPayoutTxValidation.MissingTxException ignore) {
            // We don't react on those errors as a failed trade might get listed initially but getting removed from the
            // trade manager after initPendingTrades which happens after activate might be called.
            log.error("");
        } catch (DelayedPayoutTxValidation.ValidationException e) {
            if (!model.dataModel.tradeManager.isAllowFaultyDelayedTxs()) {
                new Popup().warning(Res.get("portfolio.pending.invalidDelayedPayoutTx", e.getMessage())).show();
            }
        }
    }

}


