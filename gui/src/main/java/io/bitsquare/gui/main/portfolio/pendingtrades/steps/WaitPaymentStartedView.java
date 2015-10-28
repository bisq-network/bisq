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

public class WaitPaymentStartedView extends WaitTxInBlockchainView {
    public WaitPaymentStartedView(PendingTradesViewModel model) {
        super(model);
    }

    @Override
    public void doActivate() {
        super.doActivate();
    }

    @Override
    protected void displayRequestCheckPayment() {
        // does not make sense to warn here
    }

    @Override
    protected void displayOpenForDisputeForm() {
        addDisputeInfoLabel();
        infoLabel.setText("The buyer has not started his payment!\n" +
                "The max. period for the trade has elapsed (" +
                model.getDateFromBlocks(openDisputeTimeInBlocks) + ")." +
                "\nPlease contact the arbitrator for opening a dispute.");

        addOpenDisputeButton();
    }
}


