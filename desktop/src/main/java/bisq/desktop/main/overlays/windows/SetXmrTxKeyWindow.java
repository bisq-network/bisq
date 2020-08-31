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

package bisq.desktop.main.overlays.windows;

import bisq.desktop.components.InputTextField;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.util.validation.RegexValidator;

import bisq.core.locale.Res;
import bisq.core.trade.autoconf.xmr.XmrTxProofModel;

import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import javafx.geometry.HPos;
import javafx.geometry.Insets;

import lombok.Getter;

import javax.annotation.Nullable;

import static bisq.common.app.DevEnv.isDevMode;
import static bisq.desktop.util.FormBuilder.addInputTextField;

public class SetXmrTxKeyWindow extends Overlay<SetXmrTxKeyWindow> {

    private InputTextField txHashInputTextField, txKeyInputTextField;
    @Getter
    private RegexValidator regexValidator;

    public SetXmrTxKeyWindow() {
        type = Type.Attention;
    }

    public void show() {
        if (headLine == null)
            headLine = Res.get("setXMRTxKeyWindow.headline");

        width = 868;
        createGridPane();
        addHeadLine();
        addContent();
        addButtons();

        regexValidator = new RegexValidator();
        regexValidator.setPattern("[a-fA-F0-9]{64}");
        regexValidator.setErrorMessage(Res.get("portfolio.pending.step2_buyer.confirmStart.proof.invalidInput"));
        txHashInputTextField.setValidator(regexValidator);
        txKeyInputTextField.setValidator(regexValidator);
        if (isDevMode()) {
            // pre-populate the fields with test data when in dev mode
            txHashInputTextField.setText(XmrTxProofModel.DEV_TX_HASH);
            txKeyInputTextField.setText(XmrTxProofModel.DEV_TX_KEY);
        }

        applyStyles();
        display();
    }

    @Override
    protected void createGridPane() {
        gridPane = new GridPane();
        gridPane.setHgap(5);
        gridPane.setVgap(5);
        gridPane.setPadding(new Insets(64, 64, 64, 64));
        gridPane.setPrefWidth(width);

        ColumnConstraints columnConstraints1 = new ColumnConstraints();
        columnConstraints1.setHalignment(HPos.RIGHT);
        columnConstraints1.setHgrow(Priority.SOMETIMES);
        gridPane.getColumnConstraints().addAll(columnConstraints1);
    }

    @Nullable
    public String getTxHash() {
        return txHashInputTextField != null ? txHashInputTextField.getText() : null;
    }

    @Nullable
    public String getTxKey() {
        return txKeyInputTextField != null ? txKeyInputTextField.getText() : null;
    }

    private void addContent() {
        txHashInputTextField = addInputTextField(gridPane, ++rowIndex, Res.get("setXMRTxKeyWindow.txHash"), 10);
        txKeyInputTextField = addInputTextField(gridPane, ++rowIndex, Res.get("setXMRTxKeyWindow.txKey"));
    }
}
