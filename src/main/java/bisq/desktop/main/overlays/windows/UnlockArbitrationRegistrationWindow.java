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

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.InputTextField;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.main.overlays.popups.Popup;

import bisq.common.app.DevEnv;
import bisq.common.locale.Res;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import javafx.geometry.Insets;

import javafx.beans.value.ChangeListener;

public class UnlockArbitrationRegistrationWindow extends Overlay<UnlockArbitrationRegistrationWindow> {
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

    public UnlockArbitrationRegistrationWindow(boolean useDevPrivilegeKeys) {
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
        addSeparator();
        addInputFields();
        addButtons();
        applyStyles();
        display();
    }

    public UnlockArbitrationRegistrationWindow onKey(PrivKeyHandler privKeyHandler) {
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
        Label label = new AutoTooltipLabel(Res.get("shared.enterPrivKey"));
        label.setWrapText(true);
        GridPane.setMargin(label, new Insets(3, 0, 0, 0));
        GridPane.setRowIndex(label, ++rowIndex);

        keyInputTextField = new InputTextField();
        if (useDevPrivilegeKeys)
            keyInputTextField.setText(DevEnv.DEV_PRIVILEGE_PRIV_KEY);
        GridPane.setMargin(keyInputTextField, new Insets(3, 0, 0, 0));
        GridPane.setRowIndex(keyInputTextField, rowIndex);
        GridPane.setColumnIndex(keyInputTextField, 1);
        changeListener = (observable, oldValue, newValue) -> unlockButton.setDisable(newValue.length() == 0);
        keyInputTextField.textProperty().addListener(changeListener);
        gridPane.getChildren().addAll(label, keyInputTextField);
    }

    private void addButtons() {
        unlockButton = new AutoTooltipButton(Res.get("shared.unlock"));
        unlockButton.setDefaultButton(true);
        unlockButton.setDisable(keyInputTextField.getText().length() == 0);
        unlockButton.setOnAction(e -> {
            if (privKeyHandler.checkKey(keyInputTextField.getText()))
                hide();
            else
                new Popup<>().warning(Res.get("shared.invalidKey")).width(300).onClose(this::blurAgain).show();
        });

        Button closeButton = new AutoTooltipButton(Res.get("shared.close"));
        closeButton.setOnAction(event -> {
            hide();
            closeHandlerOptional.ifPresent(Runnable::run);
        });

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        GridPane.setRowIndex(hBox, ++rowIndex);
        GridPane.setColumnIndex(hBox, 1);
        hBox.getChildren().addAll(unlockButton, closeButton);
        gridPane.getChildren().add(hBox);
    }

}
