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
import bisq.desktop.components.InputTextField;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.alert.Alert;

import bisq.common.app.DevEnv;
import bisq.common.locale.Res;
import bisq.common.util.Tuple2;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import javafx.geometry.Insets;

import static bisq.desktop.util.FormBuilder.addLabelCheckBox;
import static bisq.desktop.util.FormBuilder.addLabelInputTextField;
import static bisq.desktop.util.FormBuilder.addLabelTextArea;

public class SendAlertMessageWindow extends Overlay<SendAlertMessageWindow> {
    private final boolean useDevPrivilegeKeys;
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

    public SendAlertMessageWindow(boolean useDevPrivilegeKeys) {
        this.useDevPrivilegeKeys = useDevPrivilegeKeys;
        type = Type.Attention;
    }

    public void show() {
        if (headLine == null)
            headLine = Res.get("sendAlertMessageWindow.headline");

        width = 900;
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
        if (useDevPrivilegeKeys)
            keyInputTextField.setText(DevEnv.DEV_PRIVILEGE_PRIV_KEY);

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

        Button sendButton = new AutoTooltipButton(Res.get("sendAlertMessageWindow.send"));
        sendButton.setOnAction(e -> {
            final String version = versionInputTextField.getText();
            boolean versionOK = false;
            final boolean isUpdate = isUpdateCheckBox.isSelected();
            if (isUpdate) {
                final String[] split = version.split("\\.");
                versionOK = split.length == 3;
                if (!versionOK) // Do not translate as only used by devs
                    new Popup<>().warning("Version number must be in semantic version format (contain 2 '.'). version=" + version)
                            .onClose(this::blurAgain)
                            .show();
            }
            if (!isUpdate || versionOK) {
                if (alertMessageTextArea.getText().length() > 0 && keyInputTextField.getText().length() > 0) {
                    if (sendAlertMessageHandler.handle(
                            new Alert(alertMessageTextArea.getText(),
                                    isUpdate,
                                    version),
                            keyInputTextField.getText()))
                        hide();
                    else
                        new Popup<>().warning(Res.get("shared.invalidKey")).width(300).onClose(this::blurAgain).show();
                }
            }
        });

        Button removeAlertMessageButton = new AutoTooltipButton(Res.get("sendAlertMessageWindow.remove"));
        removeAlertMessageButton.setOnAction(e -> {
            if (keyInputTextField.getText().length() > 0) {
                if (removeAlertMessageHandler.handle(keyInputTextField.getText()))
                    hide();
                else
                    new Popup<>().warning(Res.get("shared.invalidKey")).width(300).onClose(this::blurAgain).show();
            }
        });

        closeButton = new AutoTooltipButton(Res.get("shared.close"));
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
