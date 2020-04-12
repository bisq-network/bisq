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

public class BuyerStep1View extends TradeStepView {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerStep1View(PendingTradesViewModel model) {
        super(model);
    }

    @Override
    public void activate() {
        super.activate();

        try {
            DelayedPayoutTxValidation.validatePayoutTx(trade,
                    trade.getDelayedPayoutTx(),
                    model.dataModel.daoFacade,
                    model.dataModel.btcWalletService);
        } catch (DelayedPayoutTxValidation.DonationAddressException |
                DelayedPayoutTxValidation.InvalidTxException |
//                DelayedPayoutTxValidation.AmountMismatchException | // todo activate june 2020
                DelayedPayoutTxValidation.InvalidLockTimeException e) {
            new Popup().warning(Res.get("portfolio.pending.invalidDelayedPayoutTx", e.getMessage())).show();
        } catch (DelayedPayoutTxValidation.MissingDelayedPayoutTxException |
                DelayedPayoutTxValidation.AmountMismatchException ignore) {
            // We don't react on those errors as a failed trade might get listed initially but getting removed from the
            // trade manager after initPendingTrades which happens after activate might be called.

            // Allow amount mismatch until june 2020 to get trades through as it's due to a bug that has now been fixed.
            // No new trades with mismatch can be created after v1.3.1
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
}


