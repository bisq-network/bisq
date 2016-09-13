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

public class SellerStep1View extends TradeStepView {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerStep1View(PendingTradesViewModel model) {
        super(model);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Info
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getInfoBlockTitle() {
        return "Wait for blockchain confirmation";
    }

    @Override
    protected String getInfoText() {
        return "Deposit transaction has been published.\n" +
                "The BTC buyer needs to wait for at least one blockchain confirmation before " +
                "starting the payment.";
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Warning
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getWarningText() {
        setWarningHeadline();
        return "The deposit transaction still did not get confirmed.\n" +
                "That might happen in rare cases when the funding fee of one trader from the external wallet was too low.";
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dispute
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getOpenForDisputeText() {
        return "The deposit transaction still did not get confirmed.\n" +
                "That might happen in rare cases when the funding fee of one trader from the external wallet was too low.\n" +
                "The max. period for the trade has elapsed.\n" +
                "\nPlease contact the arbitrator for opening a dispute.";
    }
}


