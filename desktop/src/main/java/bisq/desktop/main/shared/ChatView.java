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

package bisq.desktop.main.shared;

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.BisqTextArea;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.components.TableGroupHeadline;
import bisq.desktop.main.overlays.notifications.Notification;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.GUIUtil;

import bisq.core.locale.Res;
import bisq.core.support.SupportManager;
import bisq.core.support.SupportSession;
import bisq.core.support.messages.ChatMessage;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.util.Utilities;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.value.ChangeListener;

import javafx.event.EventHandler;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public class ChatView extends AnchorPane {

    // UI
    private TextArea inputTextArea;
    private Button sendButton;
    private ListView<ChatMessage> messageListView;
    private Label sendMsgInfoLabel;
    private BusyAnimation sendMsgBusyAnimation;
    private TableGroupHeadline tableGroupHeadline;
    private VBox messagesInputBox;
    @Nullable
    private ScrollBar verticalScrollBar;

    // Options
    @Getter
    Node extraButton;
    @Getter
    private ReadOnlyDoubleProperty widthProperty;
    @Setter
    boolean showClipboardButton;
    @Setter
    boolean displayHeader;

    // Communication stuff, to be renamed to something more generic
    private ChatMessage chatMessage;
    private ObservableList<ChatMessage> chatMessages;
    private ListChangeListener<ChatMessage> disputeDirectMessageListListener;
    private Subscription inputTextAreaTextSubscription;
    private ChangeListener<Boolean> storedInMailboxPropertyListener, acknowledgedPropertyListener;
    private ChangeListener<String> sendMessageErrorPropertyListener;

    private EventHandler<KeyEvent> keyEventEventHandler;
    private SupportManager supportManager;
    private Optional<SupportSession> optionalSupportSession = Optional.empty();
    private String counterpartyName;

    public ChatView(SupportManager supportManager, String counterpartyName) {
        this.supportManager = supportManager;
        this.counterpartyName = counterpartyName;
        showClipboardButton = true;
        displayHeader = true;
    }

    public void initialize() {
        disputeDirectMessageListListener = c -> scrollToBottom();

        keyEventEventHandler = event -> {
            if (Utilities.isAltOrCtrlPressed(KeyCode.ENTER, event)) {
                optionalSupportSession.ifPresent(supportSession -> {
                    if (supportSession.chatIsOpen() && inputTextArea.isFocused()) {
                        onTrySendMessage();
                    }
                });
            }
        };
    }

    public void activate() {
        addEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);
    }

    public void deactivate() {
        removeEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);
        removeListenersOnSessionChange();
    }

    public void display(SupportSession supportSession, ReadOnlyDoubleProperty widthProperty) {
        display(supportSession, null, widthProperty);
    }

    public void display(SupportSession supportSession,
                        @Nullable Node extraButton,
                        ReadOnlyDoubleProperty widthProperty) {
        optionalSupportSession = Optional.of(supportSession);
        removeListenersOnSessionChange();
        this.getChildren().clear();
        verticalScrollBar = null;
        this.extraButton = extraButton;
        this.widthProperty = widthProperty;

        tableGroupHeadline = new TableGroupHeadline();
        tableGroupHeadline.setText(Res.get("support.messages"));

        AnchorPane.setTopAnchor(tableGroupHeadline, 10d);
        AnchorPane.setRightAnchor(tableGroupHeadline, 0d);
        AnchorPane.setBottomAnchor(tableGroupHeadline, 0d);
        AnchorPane.setLeftAnchor(tableGroupHeadline, 0d);

        chatMessages = supportSession.getObservableChatMessageList();
        SortedList<ChatMessage> sortedList = new SortedList<>(chatMessages);
        sortedList.setComparator(Comparator.comparingLong(ChatMessage::getDate));
        messageListView = new ListView<>(sortedList);
        messageListView.setId("message-list-view");

        messageListView.setMinHeight(150);
        AnchorPane.setTopAnchor(messageListView, 30d);
        AnchorPane.setRightAnchor(messageListView, 0d);
        AnchorPane.setLeftAnchor(messageListView, 0d);

        VBox.setVgrow(this, Priority.ALWAYS);

        inputTextArea = new BisqTextArea();
        inputTextArea.setPrefHeight(70);
        inputTextArea.setWrapText(true);

        if (!supportSession.isDisputeAgent()) {
            inputTextArea.setPromptText(Res.get("support.input.prompt"));
        }

        sendButton = new AutoTooltipButton(Res.get("support.send"));
        sendButton.setDefaultButton(true);
        sendButton.setOnAction(e -> onTrySendMessage());
        inputTextAreaTextSubscription = EasyBind.subscribe(inputTextArea.textProperty(), t -> sendButton.setDisable(t.isEmpty()));

        Button clipboardButton = new AutoTooltipButton(Res.get("shared.copyToClipboard"));
        clipboardButton.setOnAction(e -> copyChatMessagesToClipboard(clipboardButton));
        clipboardButton.setStyle("-fx-pref-width: 50; -fx-padding: 3 3 3 3;");

        sendMsgInfoLabel = new AutoTooltipLabel();
        sendMsgInfoLabel.setVisible(false);
        sendMsgInfoLabel.setManaged(false);
        sendMsgInfoLabel.setPadding(new Insets(5, 0, 0, 0));

        sendMsgBusyAnimation = new BusyAnimation(false);

        if (displayHeader)
            this.getChildren().add(tableGroupHeadline);

        if (supportSession.chatIsOpen()) {
            HBox buttonBox = new HBox();
            buttonBox.setSpacing(10);
            if (showClipboardButton)
                buttonBox.getChildren().addAll(sendButton, clipboardButton, sendMsgBusyAnimation, sendMsgInfoLabel);
            else
                buttonBox.getChildren().addAll(sendButton, sendMsgBusyAnimation, sendMsgInfoLabel);

            if (extraButton != null) {
                Pane spacer = new Pane();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                buttonBox.getChildren().addAll(spacer, extraButton);
            }

            messagesInputBox = new VBox();
            messagesInputBox.setSpacing(10);
            messagesInputBox.getChildren().addAll(inputTextArea, buttonBox);
            VBox.setVgrow(buttonBox, Priority.ALWAYS);

            AnchorPane.setRightAnchor(messagesInputBox, 0d);
            AnchorPane.setBottomAnchor(messagesInputBox, 5d);
            AnchorPane.setLeftAnchor(messagesInputBox, 0d);

            AnchorPane.setBottomAnchor(messageListView, 120d);

            this.getChildren().addAll(messageListView, messagesInputBox);
        } else {
            AnchorPane.setBottomAnchor(messageListView, 0d);
            this.getChildren().add(messageListView);
        }

        messageListView.setCellFactory(new Callback<>() {
            @Override
            public ListCell<ChatMessage> call(ListView<ChatMessage> list) {
                return new ListCell<>() {
                    ChangeListener<Boolean> sendMsgBusyAnimationListener;
                    Pane bg = new Pane();
                    ImageView arrow = new ImageView();
                    Label headerLabel = new AutoTooltipLabel();
                    Label messageLabel = new AutoTooltipLabel();
                    Label copyIcon = new Label();
                    AnchorPane messageAnchorPane = new AnchorPane();
                    Label statusIcon = new Label();
                    Label statusInfoLabel = new Label();
                    HBox statusHBox = new HBox();
                    double arrowWidth = 15d;
                    double border = 10d;
                    double bottomBorder = 25d;
                    double padding = border + 10d;
                    double msgLabelPaddingRight = padding + 20d;

                    {
                        bg.setMinHeight(30);
                        messageLabel.setWrapText(true);
                        headerLabel.setTextAlignment(TextAlignment.CENTER);
                        statusIcon.getStyleClass().add("small-text");
                        statusInfoLabel.getStyleClass().add("small-text");
                        statusInfoLabel.setPadding(new Insets(3, 0, 0, 0));
                        copyIcon.setTooltip(new Tooltip(Res.get("shared.copyToClipboard")));
                        statusHBox.setSpacing(5);
                        statusHBox.getChildren().addAll(statusIcon, statusInfoLabel);
                        messageAnchorPane.getChildren().addAll(bg, arrow, headerLabel, messageLabel, copyIcon, statusHBox);
                    }

                    @Override
                    protected void updateItem(ChatMessage message, boolean empty) {
                        super.updateItem(message, empty);
                        if (message != null && !empty) {
                            copyIcon.setOnMouseClicked(e -> Utilities.copyToClipboard(messageLabel.getText()));
                            messageLabel.setOnMouseClicked(event -> {
                                if (2 > event.getClickCount()) {
                                    return;
                                }
                                GUIUtil.showSelectableTextModal(headerLabel.getText(), messageLabel.getText());
                            });

                            if (!messageAnchorPane.prefWidthProperty().isBound())
                                messageAnchorPane.prefWidthProperty()
                                        .bind(messageListView.widthProperty().subtract(padding + GUIUtil.getScrollbarWidth(messageListView)));

                            AnchorPane.clearConstraints(bg);
                            AnchorPane.clearConstraints(headerLabel);
                            AnchorPane.clearConstraints(arrow);
                            AnchorPane.clearConstraints(messageLabel);
                            AnchorPane.clearConstraints(copyIcon);
                            AnchorPane.clearConstraints(statusHBox);

                            AnchorPane.setTopAnchor(bg, 15d);
                            AnchorPane.setBottomAnchor(bg, bottomBorder);
                            AnchorPane.setTopAnchor(headerLabel, 0d);
                            AnchorPane.setBottomAnchor(arrow, bottomBorder + 5d);
                            AnchorPane.setTopAnchor(messageLabel, 25d);
                            AnchorPane.setTopAnchor(copyIcon, 25d);

                            boolean senderIsTrader = message.isSenderIsTrader();
                            boolean isMyMsg = supportSession.isClient() == senderIsTrader;

                            arrow.setVisible(!message.isSystemMessage());
                            arrow.setManaged(!message.isSystemMessage());
                            statusHBox.setVisible(false);

                            headerLabel.getStyleClass().removeAll("message-header", "my-message-header", "success-text",
                                    "highlight-static");
                            messageLabel.getStyleClass().removeAll("my-message", "message");
                            copyIcon.getStyleClass().removeAll("my-message", "message");

                            if (message.isSystemMessage()) {
                                headerLabel.getStyleClass().addAll("message-header", "success-text");
                                bg.setId("message-bubble-green");
                                messageLabel.getStyleClass().add("my-message");
                                copyIcon.getStyleClass().add("my-message");
                                message.addWeakMessageStateListener(() -> updateMsgState(message));
                                updateMsgState(message);
                            } else if (isMyMsg) {
                                headerLabel.getStyleClass().add("my-message-header");
                                bg.setId("message-bubble-blue");
                                messageLabel.getStyleClass().add("my-message");
                                copyIcon.getStyleClass().add("my-message");
                                if (supportSession.isClient())
                                    arrow.setId("bubble_arrow_blue_left");
                                else
                                    arrow.setId("bubble_arrow_blue_right");

                                if (sendMsgBusyAnimationListener != null)
                                    sendMsgBusyAnimation.isRunningProperty().removeListener(sendMsgBusyAnimationListener);

                                sendMsgBusyAnimationListener = (observable, oldValue, newValue) -> {
                                    if (!newValue)
                                        updateMsgState(message);
                                };

                                sendMsgBusyAnimation.isRunningProperty().addListener(sendMsgBusyAnimationListener);
                                message.addWeakMessageStateListener(() -> updateMsgState(message));
                                updateMsgState(message);
                            } else {
                                headerLabel.getStyleClass().add("message-header");
                                bg.setId("message-bubble-grey");
                                messageLabel.getStyleClass().add("message");
                                copyIcon.getStyleClass().add("message");
                                if (supportSession.isClient())
                                    arrow.setId("bubble_arrow_grey_right");
                                else
                                    arrow.setId("bubble_arrow_grey_left");
                            }

                            if (message.isSystemMessage()) {
                                AnchorPane.setLeftAnchor(headerLabel, padding);
                                AnchorPane.setRightAnchor(headerLabel, padding);
                                AnchorPane.setLeftAnchor(bg, border);
                                AnchorPane.setRightAnchor(bg, border);
                                AnchorPane.setLeftAnchor(messageLabel, padding);
                                AnchorPane.setRightAnchor(messageLabel, msgLabelPaddingRight);
                                AnchorPane.setRightAnchor(copyIcon, padding);
                                AnchorPane.setLeftAnchor(statusHBox, padding);
                            } else if (senderIsTrader) {
                                AnchorPane.setLeftAnchor(headerLabel, padding + arrowWidth);
                                AnchorPane.setLeftAnchor(bg, border + arrowWidth);
                                AnchorPane.setRightAnchor(bg, border);
                                AnchorPane.setLeftAnchor(arrow, border);
                                AnchorPane.setLeftAnchor(messageLabel, padding + arrowWidth);
                                AnchorPane.setRightAnchor(messageLabel, msgLabelPaddingRight);
                                AnchorPane.setRightAnchor(copyIcon, padding);
                                AnchorPane.setRightAnchor(statusHBox, padding);
                            } else {
                                AnchorPane.setRightAnchor(headerLabel, padding + arrowWidth);
                                AnchorPane.setRightAnchor(bg, border + arrowWidth);
                                AnchorPane.setLeftAnchor(bg, border);
                                AnchorPane.setRightAnchor(arrow, border);
                                AnchorPane.setLeftAnchor(messageLabel, padding);
                                AnchorPane.setRightAnchor(messageLabel, msgLabelPaddingRight + arrowWidth);
                                AnchorPane.setRightAnchor(copyIcon, padding + arrowWidth);
                                AnchorPane.setLeftAnchor(statusHBox, padding);
                            }
                            AnchorPane.setBottomAnchor(statusHBox, 7d);
                            String metaData = DisplayUtils.formatDateTime(new Date(message.getDate()));
                            if (!message.isSystemMessage())
                                metaData = (isMyMsg ? "Sent " : "Received ") + metaData
                                        + (isMyMsg ? "" : " from " + counterpartyName);
                            headerLabel.setText(metaData);
                            messageLabel.setText(message.getMessage());
                            AnchorPane.setBottomAnchor(messageLabel, bottomBorder + 10);

                            // Need to set it here otherwise style is not correct
                            AwesomeDude.setIcon(copyIcon, AwesomeIcon.COPY, "16.0");
                            copyIcon.getStyleClass().addAll("icon", "copy-icon-disputes");

                            // TODO There are still some cell rendering issues on updates
                            setGraphic(messageAnchorPane);
                        } else {
                            if (sendMsgBusyAnimation != null && sendMsgBusyAnimationListener != null)
                                sendMsgBusyAnimation.isRunningProperty().removeListener(sendMsgBusyAnimationListener);

                            messageAnchorPane.prefWidthProperty().unbind();

                            copyIcon.setOnMouseClicked(null);
                            messageLabel.setOnMouseClicked(null);
                            setGraphic(null);
                        }
                    }

                    private void updateMsgState(ChatMessage message) {
                        boolean visible;
                        AwesomeIcon icon = null;
                        String text = null;
                        statusIcon.getStyleClass().add("status-icon");
                        statusInfoLabel.getStyleClass().add("status-icon");
                        statusHBox.setOpacity(1);
                        log.debug("updateMsgState msg-{}, ack={}, arrived={}", message.getMessage(),
                                message.acknowledgedProperty().get(), message.arrivedProperty().get());
                        if (message.acknowledgedProperty().get()) {
                            visible = true;
                            icon = AwesomeIcon.OK_SIGN;
                            text = Res.get("support.acknowledged");
                        } else if (message.storedInMailboxProperty().get()) {
                            visible = true;
                            icon = AwesomeIcon.ENVELOPE;
                            text = Res.get("support.savedInMailbox");
                        } else if (message.ackErrorProperty().get() != null) {
                            visible = true;
                            icon = AwesomeIcon.EXCLAMATION_SIGN;
                            text = Res.get("support.error", message.ackErrorProperty().get());
                            statusIcon.getStyleClass().add("error-text");
                            statusInfoLabel.getStyleClass().add("error-text");
                        } else if (message.arrivedProperty().get()) {
                            visible = true;
                            icon = AwesomeIcon.MAIL_REPLY;
                            text = Res.get("support.transient");
                        } else {
                            visible = false;
                            log.debug("updateMsgState called but no msg state available. message={}", message);
                        }

                        statusHBox.setVisible(visible);
                        if (visible) {
                            AwesomeDude.setIcon(statusIcon, icon, "14");
                            statusIcon.setTooltip(new Tooltip(text));
                            statusInfoLabel.setText(text);
                        }
                    }
                };
            }
        });

        addListenersOnSessionChange(widthProperty);
        scrollToBottom();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onTrySendMessage() {
        if (supportManager.isBootstrapped()) {
            String text = inputTextArea.getText();
            if (!text.isEmpty()) {
                if (text.length() < 5_000) {
                    onSendMessage(text);
                } else {
                    new Popup().information(Res.get("popup.warning.messageTooLong")).show();
                }
            }
        } else {
            new Popup().information(Res.get("popup.warning.notFullyConnected")).show();
        }
    }

    private void copyChatMessagesToClipboard(Button sourceBtn) {
        optionalSupportSession.ifPresent(session -> {
            StringBuilder stringBuilder = new StringBuilder();
            chatMessages.forEach(i -> {
                String metaData = DisplayUtils.formatDateTime(new Date(i.getDate()));
                metaData = metaData + (i.isSystemMessage() ? " (System message)" :
                        (i.isSenderIsTrader() ? " (from Trader)" : " (from Agent)"));
                stringBuilder.append(metaData).append("\n").append(i.getMessage()).append("\n\n");
            });
            Utilities.copyToClipboard(stringBuilder.toString());
            new Notification()
                    .notification(Res.get("shared.copiedToClipboard"))
                    .hideCloseButton()
                    .autoClose()
                    .show();
        });
    }

    private void onSendMessage(String inputText) {
        if (chatMessage != null) {
            chatMessage.acknowledgedProperty().removeListener(acknowledgedPropertyListener);
            chatMessage.storedInMailboxProperty().removeListener(storedInMailboxPropertyListener);
            chatMessage.sendMessageErrorProperty().removeListener(sendMessageErrorPropertyListener);
        }

        chatMessage = sendDisputeDirectMessage(inputText);
        scrollToBottom();

        inputTextArea.setDisable(true);
        inputTextArea.clear();

        chatMessage.startAckTimer();

        Timer timer = UserThread.runAfter(() -> {
            sendMsgInfoLabel.setVisible(true);
            sendMsgInfoLabel.setManaged(true);
            sendMsgInfoLabel.setText(Res.get("support.sendingMessage"));

            sendMsgBusyAnimation.play();
        }, 500, TimeUnit.MILLISECONDS);

        acknowledgedPropertyListener = (observable, oldValue, newValue) -> {
            if (newValue) {
                sendMsgInfoLabel.setVisible(false);
                hideSendMsgInfo(timer);
            }
        };
        storedInMailboxPropertyListener = (observable, oldValue, newValue) -> {
            if (newValue) {
                sendMsgInfoLabel.setVisible(true);
                sendMsgInfoLabel.setManaged(true);
                sendMsgInfoLabel.setText(Res.get("support.receiverNotOnline"));
                hideSendMsgInfo(timer);
            }
        };
        sendMessageErrorPropertyListener = (observable, oldValue, newValue) -> {
            if (newValue != null) {
                sendMsgInfoLabel.setVisible(true);
                sendMsgInfoLabel.setManaged(true);
                sendMsgInfoLabel.setText(Res.get("support.sendMessageError", newValue));
                hideSendMsgInfo(timer);
            }
        };
        if (chatMessage != null) {
            chatMessage.acknowledgedProperty().addListener(acknowledgedPropertyListener);
            chatMessage.storedInMailboxProperty().addListener(storedInMailboxPropertyListener);
            chatMessage.sendMessageErrorProperty().addListener(sendMessageErrorPropertyListener);
        }
    }

    private ChatMessage sendDisputeDirectMessage(String text) {
        return optionalSupportSession.map(supportSession -> {
            ChatMessage message = new ChatMessage(
                    supportManager.getSupportType(),
                    supportSession.getTradeId(),
                    supportSession.getClientId(),
                    supportSession.isClient(),
                    text,
                    supportManager.getMyAddress()
            );
            supportManager.addAndPersistChatMessage(message);
            return supportManager.sendChatMessage(message);
        }).orElse(null);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Helpers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void hideSendMsgInfo(Timer timer) {
        timer.stop();
        inputTextArea.setDisable(false);

        UserThread.runAfter(() -> {
            sendMsgInfoLabel.setVisible(false);
            sendMsgInfoLabel.setManaged(false);
        }, 5);
        sendMsgBusyAnimation.stop();
    }

    public void scrollToBottom() {
        if (messageListView != null) {
            UserThread.execute(() -> {
                int lastIndex = messageListView.getItems().size() - 1;
                if (lastIndex >= 0) {
                    messageListView.scrollTo(lastIndex);
                    UserThread.execute(this::scrollVerticalBarToBottom);
                }
            });
        }
    }

    private void scrollVerticalBarToBottom() {
        if (messageListView != null) {
            ScrollBar scrollBar = getVerticalScrollBar();
            if (scrollBar != null) {
                scrollBar.setValue(scrollBar.getMax());
            }
        }
    }

    @Nullable
    private ScrollBar getVerticalScrollBar() {
        if (verticalScrollBar == null || verticalScrollBar.getScene() == null) {
            verticalScrollBar = messageListView.lookupAll(".scroll-bar").stream()
                    .filter(node -> node instanceof ScrollBar)
                    .map(node -> (ScrollBar) node)
                    .filter(scrollBar -> scrollBar.getOrientation() == Orientation.VERTICAL)
                    .findFirst()
                    .orElse(null);
        }
        return verticalScrollBar;
    }

    public void setInputBoxVisible(boolean visible) {
        if (messagesInputBox != null) {
            messagesInputBox.setVisible(visible);
            messagesInputBox.setManaged(visible);
            AnchorPane.setBottomAnchor(messageListView, visible ? 120d : 0d);
        }
    }

    public void removeInputBox() {
        this.getChildren().remove(messagesInputBox);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bindings
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addListenersOnSessionChange(ReadOnlyDoubleProperty widthProperty) {
        if (tableGroupHeadline != null) {
            tableGroupHeadline.prefWidthProperty().bind(widthProperty);
            messageListView.prefWidthProperty().bind(widthProperty);
            this.prefWidthProperty().bind(widthProperty);
            chatMessages.addListener(disputeDirectMessageListListener);
            inputTextAreaTextSubscription = EasyBind.subscribe(inputTextArea.textProperty(), t -> sendButton.setDisable(t.isEmpty()));
        }
    }

    private void removeListenersOnSessionChange() {
        if (chatMessages != null && disputeDirectMessageListListener != null)
            chatMessages.removeListener(disputeDirectMessageListListener);

        if (chatMessage != null) {
            if (acknowledgedPropertyListener != null)
                chatMessage.arrivedProperty().removeListener(acknowledgedPropertyListener);
            if (storedInMailboxPropertyListener != null)
                chatMessage.storedInMailboxProperty().removeListener(storedInMailboxPropertyListener);
        }

        if (messageListView != null)
            messageListView.prefWidthProperty().unbind();

        if (tableGroupHeadline != null)
            tableGroupHeadline.prefWidthProperty().unbind();

        this.prefWidthProperty().unbind();

        if (inputTextAreaTextSubscription != null)
            inputTextAreaTextSubscription.unsubscribe();
    }

}
