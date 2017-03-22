/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.overlays.windows;

import io.bisq.common.app.DevEnv;
import io.bisq.common.locale.Res;
import io.bisq.common.util.Tuple2;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.main.overlays.Overlay;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.network.p2p.SendMailboxMessageListener;
import io.bisq.protobuffer.payload.alert.PrivateNotification;
import io.bisq.protobuffer.payload.crypto.PubKeyRing;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bisq.gui.util.FormBuilder.addLabelInputTextField;
import static io.bisq.gui.util.FormBuilder.addLabelTextArea;

public class SendPrivateNotificationWindow extends Overlay<SendPrivateNotificationWindow> {
    private static final Logger log = LoggerFactory.getLogger(SendPrivateNotificationWindow.class);

    private final PubKeyRing pubKeyRing;
    private final NodeAddress nodeAddress;
    private Button sendButton;
    private SendPrivateNotificationHandler sendPrivateNotificationHandler;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface SendPrivateNotificationHandler {
        boolean handle(PrivateNotification privateNotification, PubKeyRing pubKeyRing, NodeAddress nodeAddress, String privKey, SendMailboxMessageListener sendMailboxMessageListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////


    public SendPrivateNotificationWindow(PubKeyRing pubKeyRing, NodeAddress nodeAddress) {
        this.pubKeyRing = pubKeyRing;
        this.nodeAddress = nodeAddress;
        type = Type.Attention;
    }

    public void show() {
        if (headLine == null)
            headLine = Res.get("sendPrivateNotificationWindow.headline");

        width = 800;
        createGridPane();
        addHeadLine();
        addSeparator();
        addContent();
        applyStyles();
        display();
    }

    public SendPrivateNotificationWindow onAddAlertMessage(SendPrivateNotificationHandler sendPrivateNotificationHandler) {
        this.sendPrivateNotificationHandler = sendPrivateNotificationHandler;
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
        if (DevEnv.USE_DEV_PRIVILEGE_KEYS)
            keyInputTextField.setText(DevEnv.DEV_PRIVILEGE_PRIV_KEY);

        Tuple2<Label, TextArea> labelTextAreaTuple2 = addLabelTextArea(gridPane, ++rowIndex,
                Res.get("sendPrivateNotificationWindow.privateNotification"),
                Res.get("sendPrivateNotificationWindow.enterNotification"));
        TextArea alertMessageTextArea = labelTextAreaTuple2.second;
        Label first = labelTextAreaTuple2.first;
        first.setMinWidth(200);

        sendButton = new Button(Res.get("sendPrivateNotificationWindow.send"));
        sendButton.setOnAction(e -> {
            if (alertMessageTextArea.getText().length() > 0 && keyInputTextField.getText().length() > 0) {
                if (!sendPrivateNotificationHandler.handle(
                        new PrivateNotification(alertMessageTextArea.getText()),
                        pubKeyRing,
                        nodeAddress,
                        keyInputTextField.getText(),
                        new SendMailboxMessageListener() {
                            @Override
                            public void onArrived() {
                                log.trace("PrivateNotificationMessage arrived at peer.");
                                new Popup<>().feedback(Res.get("shared.messageArrived"))
                                        .onClose(SendPrivateNotificationWindow.this::hide).show();
                            }

                            @Override
                            public void onStoredInMailbox() {
                                log.trace("PrivateNotificationMessage was stored in mailbox.");
                                new Popup<>().feedback(Res.get("shared.messageStoredInMailbox"))
                                        .onClose(SendPrivateNotificationWindow.this::hide).show();
                            }

                            @Override
                            public void onFault(String errorMessage) {
                                new Popup<>().feedback(Res.get("shared.messageSendingFailed", errorMessage))
                                        .onClose(SendPrivateNotificationWindow.this::hide).show();
                            }
                        }))
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
        hBox.getChildren().addAll(sendButton, closeButton);
        gridPane.getChildren().add(hBox);
        GridPane.setMargin(hBox, new Insets(10, 0, 0, 0));
    }


}
