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
import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.locale.Res;

import bisq.common.app.DevEnv;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import javafx.beans.value.ChangeListener;

import static bisq.desktop.util.FormBuilder.add2ButtonsAfterGroup;
import static bisq.desktop.util.FormBuilder.addTopLabelInputTextFieldWithVBox;

public class UnlockDisputeAgentRegistrationWindow extends Overlay<UnlockDisputeAgentRegistrationWindow> {
    private final boolean useDevPrivilegeKeys;
    private Button unlockButton;
    private InputTextField keyInputTextField;
    private PrivKeyHandler privKeyHandler;
    private ChangeListener<String> changeListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface PrivKeyHandler {
        boolean checkKey(String privKey);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public UnlockDisputeAgentRegistrationWindow(boolean useDevPrivilegeKeys) {
        this.useDevPrivilegeKeys = useDevPrivilegeKeys;
        if (keyInputTextField != null)
            keyInputTextField.textProperty().addListener(changeListener);

        type = Type.Attention;
    }

    public void show() {
        if (gridPane != null) {
            rowIndex = -1;
            gridPane.getChildren().clear();
        }

        if (headLine == null)
            headLine = Res.get("enterPrivKeyWindow.headline");

        createGridPane();
        addHeadLine();
        addInputFields();
        addButtons();
        applyStyles();
        display();
    }

    public UnlockDisputeAgentRegistrationWindow onKey(PrivKeyHandler privKeyHandler) {
        this.privKeyHandler = privKeyHandler;
        return this;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void cleanup() {
    }

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

    private void addInputFields() {
        final Tuple3<Label, InputTextField, VBox> labelInputTextFieldTuple2 = addTopLabelInputTextFieldWithVBox(gridPane,
                ++rowIndex, Res.get("shared.enterPrivKey"), 3);
        GridPane.setColumnSpan(labelInputTextFieldTuple2.third, 2);
        Label label = labelInputTextFieldTuple2.first;
        label.setWrapText(true);

        keyInputTextField = labelInputTextFieldTuple2.second;
        if (useDevPrivilegeKeys)
            keyInputTextField.setText(DevEnv.DEV_PRIVILEGE_PRIV_KEY);
        changeListener = (observable, oldValue, newValue) -> unlockButton.setDisable(newValue.length() == 0);
        keyInputTextField.textProperty().addListener(changeListener);
    }

    @Override
    protected void addButtons() {
        final Tuple2<Button, Button> buttonButtonTuple2 = add2ButtonsAfterGroup(gridPane, ++rowIndex,
                Res.get("shared.unlock"), Res.get("shared.close"));
        unlockButton = buttonButtonTuple2.first;
        unlockButton.setDisable(keyInputTextField.getText().length() == 0);
        unlockButton.setOnAction(e -> {
            if (privKeyHandler.checkKey(keyInputTextField.getText()))
                hide();
            else
                new Popup().warning(Res.get("shared.invalidKey")).width(300).onClose(this::blurAgain).show();
        });

        Button closeButton = buttonButtonTuple2.second;
        closeButton.setOnAction(event -> {
            hide();
            closeHandlerOptional.ifPresent(Runnable::run);
        });
    }

}
