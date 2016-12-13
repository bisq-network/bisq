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

package io.bitsquare.gui.main.dao.proposals.active;

import io.bitsquare.dao.proposals.Proposal;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.main.overlays.Overlay;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import static io.bitsquare.gui.util.FormBuilder.addLabelInputTextField;
import static io.bitsquare.gui.util.FormBuilder.addLabelTextField;

public class FundProposalWindow extends Overlay<FundProposalWindow> {
    private static final Logger log = LoggerFactory.getLogger(FundProposalWindow.class);
    private Proposal proposal;
    private InputTextField amount;
    private TextField info;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public FundProposalWindow() {
        type = Type.Instruction;
    }

    public void show() {
        if (headLine == null)
            headLine = "Fund proposal";

        createGridPane();
        addHeadLine();
        addSeparator();
        addContent();
        addCloseButton();
        applyStyles();
        display();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void setupKeyHandler(Scene scene) {
        if (!hideCloseButton) {
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    e.consume();
                    doClose();
                }
            });
        }
    }

    public FundProposalWindow applyProposal(Proposal proposal) {
        this.proposal = proposal;
        return this;
    }

    public InputTextField getAmount() {
        return amount;
    }

    private void addContent() {
        info = addLabelTextField(gridPane, ++rowIndex, "Proposal ID:").second;
        amount = addLabelInputTextField(gridPane, ++rowIndex, "Amount in BTC:").second;

        info.setText(proposal.shortId());
    }

    @Override
    protected void addCloseButton() {
        super.addCloseButton();
        if (actionButton != null) {
            actionButton.setOnAction(event -> {
                actionHandlerOptional.ifPresent(Runnable::run);
            });
        }
    }
}
