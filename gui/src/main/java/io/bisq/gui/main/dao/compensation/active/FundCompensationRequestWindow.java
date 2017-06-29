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

package io.bisq.gui.main.dao.compensation.active;

import io.bisq.common.locale.Res;
import io.bisq.core.dao.compensation.CompensationRequestPayload;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.main.overlays.Overlay;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

import javax.inject.Inject;

import static io.bisq.gui.util.FormBuilder.addLabelInputTextField;
import static io.bisq.gui.util.FormBuilder.addLabelTextField;

public class FundCompensationRequestWindow extends Overlay<FundCompensationRequestWindow> {
    private CompensationRequestPayload compensationRequest;
    private InputTextField amount;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public FundCompensationRequestWindow() {
        type = Type.Instruction;
    }

    public void show() {
        if (headLine == null)
            headLine = Res.get("dao.compensation.active.fund");

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

    public FundCompensationRequestWindow applyCompensationRequest(CompensationRequestPayload request) {
        this.compensationRequest = request;
        return this;
    }

    public InputTextField getAmount() {
        return amount;
    }

    private void addContent() {
        TextField info = addLabelTextField(gridPane, ++rowIndex, "Request ID:").second;
        amount = addLabelInputTextField(gridPane, ++rowIndex, "Amount in " + Res.getBaseCurrencyCode() + ":").second;

        info.setText(compensationRequest.getShortId());
    }

    @Override
    protected void addCloseButton() {
        super.addCloseButton();
        if (actionButton != null) {
            actionButton.setOnAction(event -> actionHandlerOptional.ifPresent(Runnable::run));
        }
    }
}
