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

package io.bitsquare.gui.main.popups;

import io.bitsquare.app.BitsquareApp;
import io.bitsquare.gui.components.InputTextField;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import java.util.Optional;

public class EnterPrivKeyPopup extends Popup {
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

    public EnterPrivKeyPopup() {
        if (keyInputTextField != null)
            keyInputTextField.textProperty().addListener(changeListener);
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
        PopupManager.queueForDisplay(this);
    }

    public EnterPrivKeyPopup onClose(Runnable closeHandler) {
        this.closeHandlerOptional = Optional.of(closeHandler);
        return this;
    }

    public EnterPrivKeyPopup onKey(PrivKeyHandler privKeyHandler) {
        this.privKeyHandler = privKeyHandler;
        return this;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void cleanup() {
    }

    private void addInputFields() {
        Label label = new Label("Enter private key:");
        label.setWrapText(true);
        GridPane.setMargin(label, new Insets(3, 0, 0, 0));
        GridPane.setRowIndex(label, ++rowIndex);

        keyInputTextField = new InputTextField();
        if (BitsquareApp.DEV_MODE)
            keyInputTextField.setText("6ac43ea1df2a290c1c8391736aa42e4339c5cb4f110ff0257a13b63211977b7a");
        GridPane.setMargin(keyInputTextField, new Insets(3, 0, 0, 0));
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
        hBox.setSpacing(10);
        GridPane.setRowIndex(hBox, ++rowIndex);
        GridPane.setColumnIndex(hBox, 1);
        hBox.getChildren().addAll(unlockButton, cancelButton);
        gridPane.getChildren().add(hBox);
    }

}
