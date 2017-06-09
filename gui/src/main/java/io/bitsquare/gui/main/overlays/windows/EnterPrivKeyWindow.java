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

package io.bitsquare.gui.main.overlays.windows;

import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.overlays.Overlay;
import io.bitsquare.gui.main.overlays.popups.Popup;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

public class EnterPrivKeyWindow extends Overlay<EnterPrivKeyWindow> {
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

    public EnterPrivKeyWindow() {
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
            headLine = "Registration open for invited arbitrators only";

        createGridPane();
        addHeadLine();
        addSeparator();
        addInputFields();
        addButtons();
        applyStyles();
        display();
    }

    public EnterPrivKeyWindow onKey(PrivKeyHandler privKeyHandler) {
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
        Label label = new Label("Enter private key:");
        label.setWrapText(true);
        GridPane.setMargin(label, new Insets(MainView.scale(3), MainView.scale(0), MainView.scale(0), MainView.scale(0)));
        GridPane.setRowIndex(label, ++rowIndex);

        keyInputTextField = new InputTextField();
        GridPane.setMargin(keyInputTextField, new Insets(MainView.scale(3), MainView.scale(0), MainView.scale(0), MainView.scale(0)));
        GridPane.setRowIndex(keyInputTextField, rowIndex);
        GridPane.setColumnIndex(keyInputTextField, 1);
        changeListener = (observable, oldValue, newValue) -> {
            unlockButton.setDisable(newValue.length() == 0);
        };
        keyInputTextField.textProperty().addListener(changeListener);
        gridPane.getChildren().addAll(label, keyInputTextField);
    }

    private void addButtons() {
        unlockButton = new Button("Unlock");
        unlockButton.setDefaultButton(true);
        unlockButton.setDisable(keyInputTextField.getText().length() == 0);
        unlockButton.setOnAction(e -> {
            if (privKeyHandler.checkKey(keyInputTextField.getText()))
                hide();
            else
                new Popup().warning("The key you entered was not correct.").width(300).onClose(() -> blurAgain()).show();
        });

        Button cancelButton = new Button("Close");
        cancelButton.setOnAction(event -> {
            hide();
            closeHandlerOptional.ifPresent(closeHandler -> closeHandler.run());
        });

        HBox hBox = new HBox();
        hBox.setSpacing(MainView.scale(10));
        GridPane.setRowIndex(hBox, ++rowIndex);
        GridPane.setColumnIndex(hBox, 1);
        hBox.getChildren().addAll(unlockButton, cancelButton);
        gridPane.getChildren().add(hBox);
    }

}
