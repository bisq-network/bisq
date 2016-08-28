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

package io.bitsquare.gui.main.portfolio.pendingtrades.steps.buyer;

import io.bitsquare.gui.main.portfolio.pendingtrades.PendingTradesViewModel;
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.TradeStepView;

public class BuyerStep3View extends TradeStepView {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerStep3View(PendingTradesViewModel model) {
        super(model);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Info
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getInfoBlockTitle() {
        return "Wait for BTC seller's payment confirmation";
    }

    @Override
    protected String getInfoText() {
        return "Waiting for the BTC seller's confirmation " +
                "for the receipt of the " + model.dataModel.getCurrencyCode() + " payment.";
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Warning
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getWarningText() {
        setInformationHeadline();
        String substitute = model.isBlockChainMethod() ?
                "on the " + model.dataModel.getCurrencyCode() + "blockchain" :
                "at your payment provider (e.g. bank)";
        return "The BTC seller still has not confirmed your payment!\n" +
                "Please check " + substitute + " if the payment sending was successful.\n" +
                "If the BTC seller does not confirm the receipt of your payment until " +
                model.getDateForOpenDispute() +
                " the trade will be investigated by the arbitrator.";
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dispute
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getOpenForDisputeText() {
        return "The BTC seller has not confirmed your payment!\n" +
                "The max. period for the trade has elapsed.\n" +
                "Please contact the arbitrator for opening a dispute.";
    }

    @Override
    protected void applyOnDisputeOpened() {
    }
}


