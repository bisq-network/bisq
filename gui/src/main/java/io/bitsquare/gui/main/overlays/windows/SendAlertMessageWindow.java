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

import io.bitsquare.app.DevFlags;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.main.overlays.Overlay;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.messages.alert.Alert;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.gui.util.FormBuilder.*;

public class SendAlertMessageWindow extends Overlay<SendAlertMessageWindow> {
    private static final Logger log = LoggerFactory.getLogger(SendAlertMessageWindow.class);

    private Button sendButton;
    private SendAlertMessageHandler sendAlertMessageHandler;
    private RemoveAlertMessageHandler removeAlertMessageHandler;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface
    ///////////////////////////////////////////////////////////////////////////////////////////
    public interface SendAlertMessageHandler {
        boolean handle(Alert alert, String privKey);
    }

    public interface RemoveAlertMessageHandler {
        boolean handle(String privKey);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SendAlertMessageWindow() {
        type = Type.Attention;
    }

    public void show() {
        if (headLine == null)
            headLine = Res.get("sendAlertMessageWindow.headline");

        width = 800;
        createGridPane();
        addHeadLine();
        addSeparator();
        addContent();
        applyStyles();
        display();
    }

    public SendAlertMessageWindow onAddAlertMessage(SendAlertMessageHandler sendAlertMessageHandler) {
        this.sendAlertMessageHandler = sendAlertMessageHandler;
        return this;
    }

    public SendAlertMessageWindow onRemoveAlertMessage(RemoveAlertMessageHandler removeAlertMessageHandler) {
        this.removeAlertMessageHandler = removeAlertMessageHandler;
        return this;
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

    private void addContent() {
        InputTextField keyInputTextField = addLabelInputTextField(gridPane, ++rowIndex,
                Res.get("shared.unlock"), 10).second;
        if (DevFlags.USE_DEV_PRIVILEGE_KEYS)
            keyInputTextField.setText("6ac43ea1df2a290c1c8391736aa42e4339c5cb4f110ff0257a13b63211977b7a");

        Tuple2<Label, TextArea> labelTextAreaTuple2 = addLabelTextArea(gridPane, ++rowIndex,
                Res.get("sendAlertMessageWindow.alertMsg"),
                Res.get("sendAlertMessageWindow.enterMsg"));
        TextArea alertMessageTextArea = labelTextAreaTuple2.second;
        Label first = labelTextAreaTuple2.first;
        first.setMinWidth(150);
        CheckBox isUpdateCheckBox = addLabelCheckBox(gridPane, ++rowIndex,
                Res.get("sendAlertMessageWindow.isUpdate"), "").second;
        isUpdateCheckBox.setSelected(true);

        InputTextField versionInputTextField = addLabelInputTextField(gridPane, ++rowIndex,
                Res.get("sendAlertMessageWindow.version")).second;
        versionInputTextField.disableProperty().bind(isUpdateCheckBox.selectedProperty().not());

        sendButton = new Button(Res.get("sendAlertMessageWindow.send"));
        sendButton.setOnAction(e -> {
            if (alertMessageTextArea.getText().length() > 0 && keyInputTextField.getText().length() > 0) {
                if (sendAlertMessageHandler.handle(
                        new Alert(alertMessageTextArea.getText(),
                                isUpdateCheckBox.isSelected(),
                                versionInputTextField.getText()),
                        keyInputTextField.getText()))
                    hide();
                else
                    new Popup().warning(Res.get("shared.invalidKey")).width(300).onClose(this::blurAgain).show();
            }
        });

        Button removeAlertMessageButton = new Button(Res.get("sendAlertMessageWindow.remove"));
        removeAlertMessageButton.setOnAction(e -> {
            if (keyInputTextField.getText().length() > 0) {
                if (removeAlertMessageHandler.handle(keyInputTextField.getText()))
                    hide();
                else
                    new Popup().warning(Res.get("shared.invalidKey")).width(300).onClose(this::blurAgain).show();
            }
        });

        closeButton = new Button(Res.get("shared.close"));
        closeButton.setOnAction(e -> {
            hide();
            closeHandlerOptional.ifPresent(Runnable::run);
        });

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        GridPane.setRowIndex(hBox, ++rowIndex);
        GridPane.setColumnIndex(hBox, 1);
        hBox.getChildren().addAll(sendButton, removeAlertMessageButton, closeButton);
        gridPane.getChildren().add(hBox);
        GridPane.setMargin(hBox, new Insets(10, 0, 0, 0));
    }
}
