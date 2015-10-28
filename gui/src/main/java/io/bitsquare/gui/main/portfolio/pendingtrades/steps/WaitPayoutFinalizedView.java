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

package io.bitsquare.gui.main.portfolio.pendingtrades.steps;

import io.bitsquare.gui.main.portfolio.pendingtrades.PendingTradesViewModel;
import io.bitsquare.gui.util.Layout;

import static io.bitsquare.gui.util.FormBuilder.addMultilineLabel;
import static io.bitsquare.gui.util.FormBuilder.addTitledGroupBg;

public class WaitPayoutFinalizedView extends TradeStepDetailsView {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public WaitPayoutFinalizedView(PendingTradesViewModel model) {
        super(model);
    }

    @Override
    public void doActivate() {
        super.doActivate();
    }

    @Override
    public void doDeactivate() {
        super.doDeactivate();
    }

    public void setInfoLabelText(String text) {
        if (infoLabel != null)
            infoLabel.setText(text);
    }

    @Override
    protected void displayRequestCheckPayment() {
        // we cannot do anything here, beside restart in case of software bugs
    }

    @Override
    protected void displayOpenForDisputeForm() {
        infoLabel.setStyle(" -fx-text-fill: -bs-error-red;");
        infoLabel.setText("The trading peer has not finalized the payout transaction!\n" +
                "The max. period for the trade has elapsed (" +
                model.getDateFromBlocks(openDisputeTimeInBlocks) + ")." +
                "\nPlease contact now the arbitrator for opening a dispute.");

        addOpenDisputeButton();
    }

    @Override
    protected void disputeInProgress() {
        super.disputeInProgress();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Build view
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void buildGridEntries() {
        infoTitledGroupBg = addTitledGroupBg(gridPane, gridRow, 1, "Information");
        infoLabel = addMultilineLabel(gridPane, gridRow, Layout.FIRST_ROW_DISTANCE);
    }
}


