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

import io.bitsquare.alert.PrivateNotification;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.main.overlays.Overlay;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.p2p.messaging.SendMailboxMessageListener;
import io.bitsquare.trade.offer.Offer;
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

import static io.bitsquare.gui.util.FormBuilder.addLabelInputTextField;
import static io.bitsquare.gui.util.FormBuilder.addLabelTextArea;

public class SendPrivateNotificationWindow extends Overlay<SendPrivateNotificationWindow> {
    private static final Logger log = LoggerFactory.getLogger(SendPrivateNotificationWindow.class);
    private Button sendButton;
    private SendPrivateNotificationHandler sendPrivateNotificationHandler;
    private Offer offer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface
    ///////////////////////////////////////////////////////////////////////////////////////////
    public interface SendPrivateNotificationHandler {
        boolean handle(PrivateNotification privateNotification, Offer offer, String privKey, SendMailboxMessageListener sendMailboxMessageListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SendPrivateNotificationWindow(Offer offer) {
        this.offer = offer;
        type = Type.Attention;
    }

    public void show() {
        if (headLine == null)
            headLine = "Send private message";

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
        InputTextField keyInputTextField = addLabelInputTextField(gridPane, ++rowIndex, "Key for private notification:", 10).second;
        Tuple2<Label, TextArea> labelTextAreaTuple2 = addLabelTextArea(gridPane, ++rowIndex, "Private notification:", "Enter notification");
        TextArea alertMessageTextArea = labelTextAreaTuple2.second;
        Label first = labelTextAreaTuple2.first;
        first.setMinWidth(200);

        sendButton = new Button("Send private notification");
        sendButton.setOnAction(e -> {
            if (alertMessageTextArea.getText().length() > 0 && keyInputTextField.getText().length() > 0) {
                if (!sendPrivateNotificationHandler.handle(
                        new PrivateNotification(alertMessageTextArea.getText()),
                        offer,
                        keyInputTextField.getText(),
                        new SendMailboxMessageListener() {
                            @Override
                            public void onArrived() {
                                log.trace("PrivateNotificationMessage arrived at peer.");
                                new Popup<>().feedback("Message arrived.").onClose(SendPrivateNotificationWindow.this::hide).show();
                            }

                            @Override
                            public void onStoredInMailbox() {
                                log.trace("PrivateNotificationMessage was stored in mailbox.");
                                new Popup<>().feedback("Message stored in mailbox.").onClose(SendPrivateNotificationWindow.this::hide).show();
                            }

                            @Override
                            public void onFault(String errorMessage) {
                                new Popup<>().feedback("Message sending failed. error=" + errorMessage).onClose(SendPrivateNotificationWindow.this::hide).show();
                            }
                        }))
                    new Popup().warning("The key you entered was not correct.").width(300).onClose(() -> blurAgain()).show();
            }
        });

        closeButton = new Button("Close");
        closeButton.setOnAction(e -> {
            hide();
            closeHandlerOptional.ifPresent(closeHandler -> closeHandler.run());
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
