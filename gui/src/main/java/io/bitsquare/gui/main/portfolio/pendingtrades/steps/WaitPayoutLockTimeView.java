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
import javafx.scene.control.TextField;

import static io.bitsquare.gui.util.FormBuilder.*;

public class WaitPayoutLockTimeView extends TradeStepDetailsView {
    private TextField blockTextField;
    private TextField timeTextField;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public WaitPayoutLockTimeView(PendingTradesViewModel model) {
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

    @Override
    protected void updateDateFromBlocks(long bestBlocKHeight) {
        long missingBlocks = model.getLockTime() - bestBlocKHeight;
        blockTextField.setText(String.valueOf(missingBlocks));
        timeTextField.setText(model.getDateFromBlocks(missingBlocks));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setInfoLabelText(String text) {
        if (infoLabel != null)
            infoLabel.setText(text);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Build view
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void buildGridEntries() {
        addTitledGroupBg(gridPane, gridRow, 2, "Payout transaction lock time");
        blockTextField = addLabelTextField(gridPane, gridRow, "Block(s) to wait until unlock:", "", Layout.FIRST_ROW_DISTANCE).second;
        timeTextField = addLabelTextField(gridPane, ++gridRow, "Approx. date when payout gets unlocked:").second;

        infoTitledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 1, "Information", Layout.GROUP_DISTANCE);
        infoLabel = addMultilineLabel(gridPane, gridRow, Layout.FIRST_ROW_AND_GROUP_DISTANCE);
    }
}


