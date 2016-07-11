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

package io.bitsquare.gui.main.portfolio.pendingtrades.steps.seller;

import io.bitsquare.gui.main.portfolio.pendingtrades.PendingTradesViewModel;
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.TradeStepView;

public class SellerStep3bView extends TradeStepView {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerStep3bView(PendingTradesViewModel model) {
        super(model);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Info
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Override
    protected String getInfoBlockTitle() {
        return "Wait until peer finalizes the payout transaction";
    }

    @Override
    protected String getInfoText() {
        return "We requested from the trading peer to sign and finalize the payout transaction.\n" +
                "It might be that the other peer is offline, so we need to wait until he finalizes the " +
                "transaction when he goes online again.";
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Warning
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getWarningText() {
        setInformationHeadline();
        return "The trading peer has not finalized the payout transaction!\n" +
                "He might be offline. You need to wait until he finalizes the payout transaction.\n" +
                "If the trade has not been completed on " +
                model.getDateForOpenDispute() +
                " the arbitrator will investigate.";
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dispute
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getOpenForDisputeText() {
        return "The trading peer has not finalized the payout transaction!\n" +
                "The max. period for the trade has elapsed.\n" +
                "Please contact the arbitrator for opening a dispute.";
    }

    @Override
    protected void applyOnDisputeOpened() {
    }
}


