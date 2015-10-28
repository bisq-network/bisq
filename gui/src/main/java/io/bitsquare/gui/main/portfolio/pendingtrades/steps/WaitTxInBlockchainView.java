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

import io.bitsquare.gui.components.TxIdTextField;
import io.bitsquare.gui.main.portfolio.pendingtrades.PendingTradesViewModel;
import io.bitsquare.gui.util.Layout;
import javafx.beans.value.ChangeListener;

import static io.bitsquare.gui.util.FormBuilder.*;

public class WaitTxInBlockchainView extends TradeStepDetailsView {
    private final ChangeListener<String> txIdChangeListener;
    private TxIdTextField txIdTextField;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public WaitTxInBlockchainView(PendingTradesViewModel model) {
        super(model);

        txIdChangeListener = (ov, oldValue, newValue) -> txIdTextField.setup(newValue);
    }

    @Override
    public void doActivate() {
        super.doActivate();

        model.getTxId().addListener(txIdChangeListener);
        txIdTextField.setup(model.getTxId().get());
    }

    @Override
    public void doDeactivate() {
        super.doDeactivate();

        model.getTxId().removeListener(txIdChangeListener);
        txIdTextField.cleanup();
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
        addTitledGroupBg(gridPane, gridRow, 1, "Blockchain confirmation");
        txIdTextField = addLabelTxIdTextField(gridPane, gridRow, "Deposit transaction ID:", Layout.FIRST_ROW_DISTANCE).second;

        infoTitledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 1, "Information", Layout.GROUP_DISTANCE);
        infoLabel = addMultilineLabel(gridPane, gridRow, Layout.FIRST_ROW_AND_GROUP_DISTANCE);
    }
}


