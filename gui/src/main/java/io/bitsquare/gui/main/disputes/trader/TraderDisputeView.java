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

package io.bitsquare.gui.main.disputes.trader;

import com.google.common.io.ByteStreams;
import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bitsquare.arbitration.Dispute;
import io.bitsquare.arbitration.DisputeManager;
import io.bitsquare.arbitration.messages.DisputeCommunicationMessage;
import io.bitsquare.arbitration.payload.Attachment;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.HyperlinkWithIcon;
import io.bitsquare.gui.components.TableGroupHeadline;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.main.overlays.windows.ContractWindow;
import io.bitsquare.gui.main.overlays.windows.DisputeSummaryWindow;
import io.bitsquare.gui.main.overlays.windows.TradeDetailsWindow;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.GUIUtil;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Paint;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.reactfx.util.FxTimer;
import org.reactfx.util.Timer;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// will be probably only used for arbitration communication, will be renamed and the icon changed
@FxmlView
public class TraderDisputeView extends ActivatableView<VBox, Void> {

    private final DisputeManager disputeManager;
    protected final KeyRing keyRing;
    private final TradeManager tradeManager;
    private final Stage stage;
    private final BSFormatter formatter;
    private final DisputeSummaryWindow disputeSummaryWindow;
    private final ContractWindow contractWindow;
    private final TradeDetailsWindow tradeDetailsWindow;

    private final List<Attachment> tempAttachments = new ArrayList<>();

    private TableView<Dispute> disputesTable;
    private Dispute selectedDispute;
    private ChangeListener<Dispute> disputeChangeListener;
    private ListView<DisputeCommunicationMessage> messageListView;
    private TextArea inputTextArea;
    private AnchorPane messagesAnchorPane;
    private VBox messagesInputBox;
    private ProgressIndicator sendMsgProgressIndicator;
    private Label sendMsgInfoLabel;
    private ChangeListener<Boolean> arrivedPropertyListener;
    private ChangeListener<Boolean> storedInMailboxPropertyListener;
    @Nullable
    private DisputeCommunicationMessage disputeCommunicationMessage;
    private ListChangeListener<DisputeCommunicationMessage> disputeDirectMessageListListener;
    private ChangeListener<String> inputTextAreaListener;
    private ChangeListener<Boolean> selectedDisputeClosedPropertyListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TraderDisputeView(DisputeManager disputeManager, KeyRing keyRing, TradeManager tradeManager, Stage stage,
                             BSFormatter formatter, DisputeSummaryWindow disputeSummaryWindow,
                             ContractWindow contractWindow, TradeDetailsWindow tradeDetailsWindow) {
        this.disputeManager = disputeManager;
        this.keyRing = keyRing;
        this.tradeManager = tradeManager;
        this.stage = stage;
        this.formatter = formatter;
        this.disputeSummaryWindow = disputeSummaryWindow;
        this.contractWindow = contractWindow;
        this.tradeDetailsWindow = tradeDetailsWindow;
    }

    @Override
    public void initialize() {
        disputesTable = new TableView<>();
        VBox.setVgrow(disputesTable, Priority.SOMETIMES);
        disputesTable.setMinHeight(150);
        root.getChildren().add(disputesTable);

        TableColumn<Dispute, Dispute> tradeIdColumn = getTradeIdColumn();
        disputesTable.getColumns().add(tradeIdColumn);
        TableColumn<Dispute, Dispute> roleColumn = getRoleColumn();
        disputesTable.getColumns().add(roleColumn);
        TableColumn<Dispute, Dispute> dateColumn = getDateColumn();
        disputesTable.getColumns().add(dateColumn);
        TableColumn<Dispute, Dispute> contractColumn = getContractColumn();
        disputesTable.getColumns().add(contractColumn);
        TableColumn<Dispute, Dispute> stateColumn = getStateColumn();
        disputesTable.getColumns().add(stateColumn);

        disputesTable.getSortOrder().add(dateColumn);
        disputesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Label placeholder = new Label("There are no open tickets");
        placeholder.setWrapText(true);
        disputesTable.setPlaceholder(placeholder);
        disputesTable.getSelectionModel().clearSelection();

        disputeChangeListener = (observableValue, oldValue, newValue) -> onSelectDispute(newValue);
    }

    @Override
    protected void activate() {
        FilteredList<Dispute> filteredList = new FilteredList<>(disputeManager.getDisputesAsObservableList());
        setFilteredListPredicate(filteredList);
        SortedList<Dispute> sortedList = new SortedList<>(filteredList);
        sortedList.setComparator((o1, o2) -> o2.getOpeningDate().compareTo(o1.getOpeningDate()));
        disputesTable.setItems(sortedList);
        disputesTable.getSelectionModel().selectedItemProperty().addListener(disputeChangeListener);

        Dispute selectedItem = disputesTable.getSelectionModel().getSelectedItem();
        if (selectedItem != null)
            disputesTable.getSelectionModel().select(selectedItem);

        scrollToBottom();
    }

    @Override
    protected void deactivate() {
        disputesTable.getSelectionModel().selectedItemProperty().removeListener(disputeChangeListener);

        if (disputeCommunicationMessage != null) {
            disputeCommunicationMessage.arrivedProperty().removeListener(arrivedPropertyListener);
            disputeCommunicationMessage.storedInMailboxProperty().removeListener(storedInMailboxPropertyListener);
        }

        if (selectedDispute != null) {
            selectedDispute.isClosedProperty().removeListener(selectedDisputeClosedPropertyListener);
            ObservableList<DisputeCommunicationMessage> disputeCommunicationMessages = selectedDispute.getDisputeCommunicationMessagesAsObservableList();
            if (disputeCommunicationMessages != null) {
                disputeCommunicationMessages.removeListener(disputeDirectMessageListListener);
            }
        }

        if (inputTextArea != null)
            inputTextArea.textProperty().removeListener(inputTextAreaListener);

    }

    protected void setFilteredListPredicate(FilteredList<Dispute> filteredList) {
        filteredList.setPredicate(dispute -> !dispute.getArbitratorPubKeyRing().equals(keyRing.getPubKeyRing()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onOpenContract(Dispute dispute) {
        contractWindow.show(dispute);
    }

    private void onSendMessage(String inputText, Dispute dispute) {
        if (disputeCommunicationMessage != null) {
            disputeCommunicationMessage.arrivedProperty().removeListener(arrivedPropertyListener);
            disputeCommunicationMessage.storedInMailboxProperty().removeListener(storedInMailboxPropertyListener);
        }

        disputeCommunicationMessage = disputeManager.sendDisputeDirectMessage(dispute, inputText, new ArrayList<>(tempAttachments));
        tempAttachments.clear();
        scrollToBottom();

        inputTextArea.setDisable(true);
        inputTextArea.clear();

        final Timer timer = FxTimer.runLater(Duration.ofMillis(500), () -> {
            sendMsgInfoLabel.setVisible(true);
            sendMsgInfoLabel.setManaged(true);
            sendMsgInfoLabel.setText("Sending Message...");

            sendMsgProgressIndicator.setProgress(-1);
            sendMsgProgressIndicator.setVisible(true);
            sendMsgProgressIndicator.setManaged(true);
        });

        arrivedPropertyListener = (observable, oldValue, newValue) -> {
            if (newValue) {
                hideSendMsgInfo(timer);
            }
        };
        if (disputeCommunicationMessage.arrivedProperty() != null)
            disputeCommunicationMessage.arrivedProperty().addListener(arrivedPropertyListener);
        storedInMailboxPropertyListener = (observable, oldValue, newValue) -> {
            if (newValue) {
                sendMsgInfoLabel.setVisible(true);
                sendMsgInfoLabel.setManaged(true);
                sendMsgInfoLabel.setText("Receiver is not online. Message is saved to his mailbox.");
                hideSendMsgInfo(timer);
            }
        };
        disputeCommunicationMessage.storedInMailboxProperty().addListener(storedInMailboxPropertyListener);
    }

    private void hideSendMsgInfo(Timer timer) {
        timer.stop();
        inputTextArea.setDisable(false);

        FxTimer.runLater(Duration.ofMillis(5000), () -> {
            sendMsgInfoLabel.setVisible(false);
            sendMsgInfoLabel.setManaged(false);
        });
        sendMsgProgressIndicator.setProgress(0);
        sendMsgProgressIndicator.setVisible(false);
        sendMsgProgressIndicator.setManaged(false);
    }

    private void onCloseDispute(Dispute dispute) {
        disputeSummaryWindow.onFinalizeDispute(() -> messagesAnchorPane.getChildren().remove(messagesInputBox))
                .show(dispute);
    }

    private void onRequestUpload() {
        if (tempAttachments.size() < 3) {
            FileChooser fileChooser = new FileChooser();
            int maxSizeInKB = Connection.getMaxMsgSize() / 1024;
            fileChooser.setTitle("Open file to attach (max. file size: " + maxSizeInKB + " kb)");
           /* if (Utilities.isUnix())
                fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));*/
            File result = fileChooser.showOpenDialog(stage);
            if (result != null) {
                try {
                    URL url = result.toURI().toURL();
                    try (InputStream inputStream = url.openStream()) {
                        byte[] filesAsBytes = ByteStreams.toByteArray(inputStream);
                        if (filesAsBytes.length <= Connection.getMaxMsgSize()) {
                            tempAttachments.add(new Attachment(result.getName(), filesAsBytes));
                            inputTextArea.setText(inputTextArea.getText() + "\n[Attachment " + result.getName() + "]");
                        } else {
                            new Popup().warning("The max. allowed file size is " + maxSizeInKB + " kB.").show();
                        }
                    } catch (java.io.IOException e) {
                        e.printStackTrace();
                        log.error(e.getMessage());
                    }
                } catch (MalformedURLException e2) {
                    e2.printStackTrace();
                    log.error(e2.getMessage());
                }
            }
        } else {
            new Popup().warning("You cannot send more then 3 attachments in one message.").show();
        }
    }

    private void onOpenAttachment(Attachment attachment) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save file to disk");
        fileChooser.setInitialFileName(attachment.getFileName());
       /* if (Utilities.isUnix())
            fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));*/
        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            try (FileOutputStream fileOutputStream = new FileOutputStream(file.getAbsolutePath())) {
                fileOutputStream.write(attachment.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println(e.getMessage());
            }
        }
    }

    private void onSelectDispute(Dispute dispute) {
        if (selectedDispute != null) {
            selectedDispute.isClosedProperty().removeListener(selectedDisputeClosedPropertyListener);
            ObservableList<DisputeCommunicationMessage> disputeCommunicationMessages = selectedDispute.getDisputeCommunicationMessagesAsObservableList();
            if (disputeCommunicationMessages != null) {
                disputeCommunicationMessages.removeListener(disputeDirectMessageListListener);
            }
        }

        if (dispute == null) {
            if (root.getChildren().size() > 1)
                root.getChildren().remove(1);

            selectedDispute = null;
        } else if (selectedDispute != dispute) {
            this.selectedDispute = dispute;

            boolean isTrader = disputeManager.isTrader(selectedDispute);

            TableGroupHeadline tableGroupHeadline = new TableGroupHeadline();
            tableGroupHeadline.setText("Messages");
            tableGroupHeadline.prefWidthProperty().bind(root.widthProperty());
            AnchorPane.setTopAnchor(tableGroupHeadline, 10d);
            AnchorPane.setRightAnchor(tableGroupHeadline, 0d);
            AnchorPane.setBottomAnchor(tableGroupHeadline, 0d);
            AnchorPane.setLeftAnchor(tableGroupHeadline, 0d);

            ObservableList<DisputeCommunicationMessage> disputeCommunicationMessages = selectedDispute.getDisputeCommunicationMessagesAsObservableList();
            SortedList<DisputeCommunicationMessage> sortedList = new SortedList<>(disputeCommunicationMessages);
            sortedList.setComparator((o1, o2) -> o1.getDate().compareTo(o2.getDate()));
            disputeDirectMessageListListener = c -> scrollToBottom();
            disputeCommunicationMessages.addListener(disputeDirectMessageListListener);
            messageListView = new ListView<>(sortedList);
            messageListView.setId("message-list-view");
            messageListView.prefWidthProperty().bind(root.widthProperty());
            messageListView.setMinHeight(150);
            AnchorPane.setTopAnchor(messageListView, 30d);
            AnchorPane.setRightAnchor(messageListView, 0d);
            AnchorPane.setLeftAnchor(messageListView, 0d);

            messagesAnchorPane = new AnchorPane();
            messagesAnchorPane.prefWidthProperty().bind(root.widthProperty());
            VBox.setVgrow(messagesAnchorPane, Priority.ALWAYS);

            inputTextArea = new TextArea();
            inputTextArea.setPrefHeight(70);
            inputTextArea.setWrapText(true);

            Button sendButton = new Button("Send");
            sendButton.setDefaultButton(true);
            sendButton.setOnAction(e -> onSendMessage(inputTextArea.getText(), selectedDispute));
            sendButton.setDisable(true);
            inputTextAreaListener = (observable, oldValue, newValue) ->
                    sendButton.setDisable(newValue.length() == 0
                            && tempAttachments.size() == 0 &&
                            selectedDispute.disputeResultProperty().get() == null);
            inputTextArea.textProperty().addListener(inputTextAreaListener);

            Button uploadButton = new Button("Add attachments");
            uploadButton.setOnAction(e -> onRequestUpload());

            sendMsgInfoLabel = new Label();
            sendMsgInfoLabel.setVisible(false);
            sendMsgInfoLabel.setManaged(false);
            sendMsgInfoLabel.setPadding(new Insets(5, 0, 0, 0));

            sendMsgProgressIndicator = new ProgressIndicator(0);
            sendMsgProgressIndicator.setPrefHeight(24);
            sendMsgProgressIndicator.setPrefWidth(24);
            sendMsgProgressIndicator.setVisible(false);
            sendMsgProgressIndicator.setManaged(false);

            selectedDisputeClosedPropertyListener = (observable, oldValue, newValue) -> {
                messagesInputBox.setVisible(!newValue);
                messagesInputBox.setManaged(!newValue);
                AnchorPane.setBottomAnchor(messageListView, newValue ? 0d : 120d);
            };
            selectedDispute.isClosedProperty().addListener(selectedDisputeClosedPropertyListener);
            if (!selectedDispute.isClosed()) {
                HBox buttonBox = new HBox();
                buttonBox.setSpacing(10);
                buttonBox.getChildren().addAll(sendButton, uploadButton, sendMsgProgressIndicator, sendMsgInfoLabel);

                if (!isTrader) {
                    Button closeDisputeButton = new Button("Close ticket");
                    closeDisputeButton.setOnAction(e -> onCloseDispute(selectedDispute));
                    closeDisputeButton.setDefaultButton(true);
                    Pane spacer = new Pane();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    buttonBox.getChildren().addAll(spacer, closeDisputeButton);
                }

                messagesInputBox = new VBox();
                messagesInputBox.setSpacing(10);
                messagesInputBox.getChildren().addAll(inputTextArea, buttonBox);
                VBox.setVgrow(buttonBox, Priority.ALWAYS);

                AnchorPane.setRightAnchor(messagesInputBox, 0d);
                AnchorPane.setBottomAnchor(messagesInputBox, 5d);
                AnchorPane.setLeftAnchor(messagesInputBox, 0d);

                AnchorPane.setBottomAnchor(messageListView, 120d);

                messagesAnchorPane.getChildren().addAll(tableGroupHeadline, messageListView, messagesInputBox);
            } else {
                AnchorPane.setBottomAnchor(messageListView, 0d);
                messagesAnchorPane.getChildren().addAll(tableGroupHeadline, messageListView);
            }

            messageListView.setCellFactory(new Callback<ListView<DisputeCommunicationMessage>, ListCell<DisputeCommunicationMessage>>() {
                @Override
                public ListCell<DisputeCommunicationMessage> call(ListView<DisputeCommunicationMessage> list) {
                    return new ListCell<DisputeCommunicationMessage>() {
                        public ChangeListener<Number> sendMsgProgressIndicatorListener;
                        final Pane bg = new Pane();
                        final ImageView arrow = new ImageView();
                        final Label headerLabel = new Label();
                        final Label messageLabel = new Label();
                        final HBox attachmentsBox = new HBox();
                        final AnchorPane messageAnchorPane = new AnchorPane();
                        final Label statusIcon = new Label();
                        final double arrowWidth = 15d;
                        final double attachmentsBoxHeight = 20d;
                        final double border = 10d;
                        final double bottomBorder = 25d;
                        final double padding = border + 10d;

                        {
                            bg.setMinHeight(30);
                            messageLabel.setWrapText(true);
                            headerLabel.setTextAlignment(TextAlignment.CENTER);
                            attachmentsBox.setSpacing(5);
                            statusIcon.setStyle("-fx-font-size: 10;");
                            messageAnchorPane.getChildren().addAll(bg, arrow, headerLabel, messageLabel, attachmentsBox, statusIcon);
                        }

                        @Override
                        public void updateItem(final DisputeCommunicationMessage item, boolean empty) {
                            super.updateItem(item, empty);

                            if (item != null && !empty) {
                               /* messageAnchorPane.prefWidthProperty().bind(EasyBind.map(messageListView.widthProperty(),
                                        w -> (double) w - padding - GUIUtil.getScrollbarWidth(messageListView)));*/
                                if (!messageAnchorPane.prefWidthProperty().isBound())
                                    messageAnchorPane.prefWidthProperty()
                                            .bind(messageListView.widthProperty().subtract(padding + GUIUtil.getScrollbarWidth(messageListView)));

                                AnchorPane.setTopAnchor(bg, 15d);
                                AnchorPane.setBottomAnchor(bg, bottomBorder);
                                AnchorPane.setTopAnchor(headerLabel, 0d);
                                AnchorPane.setBottomAnchor(arrow, bottomBorder + 5d);
                                AnchorPane.setTopAnchor(messageLabel, 25d);
                                AnchorPane.setBottomAnchor(attachmentsBox, bottomBorder + 10);

                                boolean senderIsTrader = item.isSenderIsTrader();
                                boolean isMyMsg = isTrader ? senderIsTrader : !senderIsTrader;

                                arrow.setVisible(!item.isSystemMessage());
                                arrow.setManaged(!item.isSystemMessage());
                                statusIcon.setVisible(false);
                                if (item.isSystemMessage()) {
                                    headerLabel.setStyle("-fx-text-fill: -bs-green; -fx-font-size: 11;");
                                    bg.setId("message-bubble-green");
                                    messageLabel.setStyle("-fx-text-fill: white;");
                                } else if (isMyMsg) {
                                    headerLabel.setStyle("-fx-text-fill: -fx-accent; -fx-font-size: 11;");
                                    bg.setId("message-bubble-blue");
                                    messageLabel.setStyle("-fx-text-fill: white;");
                                    if (isTrader)
                                        arrow.setId("bubble_arrow_blue_left");
                                    else
                                        arrow.setId("bubble_arrow_blue_right");

                                    sendMsgProgressIndicatorListener = (observable, oldValue, newValue) -> {
                                        if ((double) oldValue == -1 && (double) newValue == 0) {
                                            if (item.arrivedProperty().get())
                                                showArrivedIcon();
                                            else if (item.storedInMailboxProperty().get())
                                                showMailboxIcon();
                                        }
                                    };
                                    sendMsgProgressIndicator.progressProperty().addListener(sendMsgProgressIndicatorListener);

                                    if (item.arrivedProperty().get())
                                        showArrivedIcon();
                                    else if (item.storedInMailboxProperty().get())
                                        showMailboxIcon();
                                    //TODO show that icon on error
                                    /*else if (sendMsgProgressIndicator.getProgress() == 0)
                                        showNotArrivedIcon();*/
                                } else {
                                    headerLabel.setStyle("-fx-text-fill: -bs-light-grey; -fx-font-size: 11;");
                                    bg.setId("message-bubble-grey");
                                    messageLabel.setStyle("-fx-text-fill: black;");
                                    if (isTrader)
                                        arrow.setId("bubble_arrow_grey_right");
                                    else
                                        arrow.setId("bubble_arrow_grey_left");
                                }

                                if (item.isSystemMessage()) {
                                    AnchorPane.setLeftAnchor(headerLabel, padding);
                                    AnchorPane.setRightAnchor(headerLabel, padding);
                                    AnchorPane.setLeftAnchor(bg, border);
                                    AnchorPane.setRightAnchor(bg, border);
                                    AnchorPane.setLeftAnchor(messageLabel, padding);
                                    AnchorPane.setRightAnchor(messageLabel, padding);
                                    AnchorPane.setLeftAnchor(attachmentsBox, padding);
                                    AnchorPane.setRightAnchor(attachmentsBox, padding);
                                } else if (senderIsTrader) {
                                    AnchorPane.setLeftAnchor(headerLabel, padding + arrowWidth);
                                    AnchorPane.setLeftAnchor(bg, border + arrowWidth);
                                    AnchorPane.setRightAnchor(bg, border);
                                    AnchorPane.setLeftAnchor(arrow, border);
                                    AnchorPane.setLeftAnchor(messageLabel, padding + arrowWidth);
                                    AnchorPane.setRightAnchor(messageLabel, padding);
                                    AnchorPane.setLeftAnchor(attachmentsBox, padding + arrowWidth);
                                    AnchorPane.setRightAnchor(attachmentsBox, padding);
                                    AnchorPane.setRightAnchor(statusIcon, padding);
                                } else {
                                    AnchorPane.setRightAnchor(headerLabel, padding + arrowWidth);
                                    AnchorPane.setLeftAnchor(bg, border);
                                    AnchorPane.setRightAnchor(bg, border + arrowWidth);
                                    AnchorPane.setRightAnchor(arrow, border);
                                    AnchorPane.setLeftAnchor(messageLabel, padding);
                                    AnchorPane.setRightAnchor(messageLabel, padding + arrowWidth);
                                    AnchorPane.setLeftAnchor(attachmentsBox, padding);
                                    AnchorPane.setRightAnchor(attachmentsBox, padding + arrowWidth);
                                    AnchorPane.setLeftAnchor(statusIcon, padding);
                                }

                                AnchorPane.setBottomAnchor(statusIcon, 7d);
                                headerLabel.setText(formatter.formatDateTime(item.getDate()));
                                messageLabel.setText(item.getMessage());
                                if (item.getAttachments().size() > 0) {
                                    AnchorPane.setBottomAnchor(messageLabel, bottomBorder + attachmentsBoxHeight + 10);
                                    attachmentsBox.getChildren().add(new Label("Attachments: ") {{
                                        setPadding(new Insets(0, 0, 3, 0));
                                        if (isMyMsg)
                                            setStyle("-fx-text-fill: white;");
                                        else
                                            setStyle("-fx-text-fill: black;");
                                    }});

                                    item.getAttachments().stream().forEach(attachment -> {
                                        final Label icon = new Label();
                                        setPadding(new Insets(0, 0, 3, 0));
                                        if (isMyMsg)
                                            icon.getStyleClass().add("attachment-icon");
                                        else
                                            icon.getStyleClass().add("attachment-icon-black");

                                        AwesomeDude.setIcon(icon, AwesomeIcon.FILE_TEXT);
                                        icon.setPadding(new Insets(-2, 0, 0, 0));
                                        icon.setTooltip(new Tooltip(attachment.getFileName()));
                                        icon.setOnMouseClicked(event -> onOpenAttachment(attachment));
                                        attachmentsBox.getChildren().add(icon);
                                    });
                                } else {
                                    attachmentsBox.getChildren().clear();
                                    AnchorPane.setBottomAnchor(messageLabel, bottomBorder + 10);
                                }


                                // TODO There are still some cell rendering issues on updates
                                setGraphic(messageAnchorPane);
                            } else {
                                if (sendMsgProgressIndicator != null && sendMsgProgressIndicatorListener != null)
                                    sendMsgProgressIndicator.progressProperty().removeListener(sendMsgProgressIndicatorListener);

                                messageAnchorPane.prefWidthProperty().unbind();

                                AnchorPane.clearConstraints(bg);
                                AnchorPane.clearConstraints(headerLabel);
                                AnchorPane.clearConstraints(arrow);
                                AnchorPane.clearConstraints(messageLabel);
                                AnchorPane.clearConstraints(statusIcon);
                                AnchorPane.clearConstraints(attachmentsBox);

                                setGraphic(null);
                            }
                        }

                      /*  private void showNotArrivedIcon() {
                            statusIcon.setVisible(true);
                            AwesomeDude.setIcon(statusIcon, AwesomeIcon.WARNING_SIGN, "14");
                            Tooltip.install(statusIcon, new Tooltip("Message did not arrive. Please try to send again."));
                            statusIcon.setTextFill(Paint.valueOf("#dd0000"));
                        }*/

                        private void showMailboxIcon() {
                            statusIcon.setVisible(true);
                            AwesomeDude.setIcon(statusIcon, AwesomeIcon.ENVELOPE_ALT, "14");
                            Tooltip.install(statusIcon, new Tooltip("Message saved in receivers mailbox"));
                            statusIcon.setTextFill(Paint.valueOf("#0f87c3"));
                        }

                        private void showArrivedIcon() {
                            statusIcon.setVisible(true);
                            AwesomeDude.setIcon(statusIcon, AwesomeIcon.OK, "14");
                            Tooltip.install(statusIcon, new Tooltip("Message arrived at receiver"));
                            statusIcon.setTextFill(Paint.valueOf("#0f87c3"));
                        }
                    };
                }
            });

            if (root.getChildren().size() > 1)
                root.getChildren().remove(1);
            root.getChildren().add(1, messagesAnchorPane);

            scrollToBottom();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table
    ///////////////////////////////////////////////////////////////////////////////////////////

    private TableColumn<Dispute, Dispute> getTradeIdColumn() {
        TableColumn<Dispute, Dispute> column = new TableColumn<Dispute, Dispute>("Trade ID") {
            {
                setMinWidth(130);
            }
        };
        column.setCellValueFactory((dispute) -> new ReadOnlyObjectWrapper<>(dispute.getValue()));
        column.setCellFactory(
                new Callback<TableColumn<Dispute, Dispute>, TableCell<Dispute, Dispute>>() {
                    @Override
                    public TableCell<Dispute, Dispute> call(TableColumn<Dispute, Dispute> column) {
                        return new TableCell<Dispute, Dispute>() {
                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final Dispute item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    field = new HyperlinkWithIcon(item.getShortTradeId(), true);
                                    Optional<Trade> tradeOptional = tradeManager.getTradeById(item.getTradeId());
                                    if (tradeOptional.isPresent()) {
                                        field.setMouseTransparent(false);
                                        field.setTooltip(new Tooltip("Open popup for details"));
                                        field.setOnAction(event -> tradeDetailsWindow.show(tradeOptional.get()));
                                    } else {
                                        field.setMouseTransparent(true);
                                    }
                                    setGraphic(field);
                                } else {
                                    setGraphic(null);
                                    if (field != null)
                                        field.setOnAction(null);
                                }
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<Dispute, Dispute> getRoleColumn() {
        TableColumn<Dispute, Dispute> column = new TableColumn<Dispute, Dispute>("Role") {
            {
                setMinWidth(130);
            }
        };
        column.setCellValueFactory((dispute) -> new ReadOnlyObjectWrapper<>(dispute.getValue()));
        column.setCellFactory(
                new Callback<TableColumn<Dispute, Dispute>, TableCell<Dispute, Dispute>>() {
                    @Override
                    public TableCell<Dispute, Dispute> call(TableColumn<Dispute, Dispute> column) {
                        return new TableCell<Dispute, Dispute>() {
                            @Override
                            public void updateItem(final Dispute item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    if (item.isDisputeOpenerIsOfferer())
                                        setText(item.isDisputeOpenerIsBuyer() ? "Buyer/Offerer" : "Seller/Offerer");
                                    else
                                        setText(item.isDisputeOpenerIsBuyer() ? "Buyer/Taker" : "Seller/Taker");
                                } else {
                                    setText("");
                                }
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<Dispute, Dispute> getDateColumn() {
        TableColumn<Dispute, Dispute> column = new TableColumn<Dispute, Dispute>("Date") {
            {
                setMinWidth(130);
            }
        };
        column.setCellValueFactory((dispute) -> new ReadOnlyObjectWrapper<>(dispute.getValue()));
        column.setCellFactory(
                new Callback<TableColumn<Dispute, Dispute>, TableCell<Dispute, Dispute>>() {
                    @Override
                    public TableCell<Dispute, Dispute> call(TableColumn<Dispute, Dispute> column) {
                        return new TableCell<Dispute, Dispute>() {
                            @Override
                            public void updateItem(final Dispute item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(formatter.formatDateTime(item.getOpeningDate()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<Dispute, Dispute> getContractColumn() {
        TableColumn<Dispute, Dispute> column = new TableColumn<Dispute, Dispute>("Contract") {
            {
                setMinWidth(80);
                setSortable(false);
            }
        };
        column.setCellValueFactory((dispute) -> new ReadOnlyObjectWrapper<>(dispute.getValue()));
        column.setCellFactory(
                new Callback<TableColumn<Dispute, Dispute>, TableCell<Dispute, Dispute>>() {

                    @Override
                    public TableCell<Dispute, Dispute> call(TableColumn<Dispute, Dispute> column) {
                        return new TableCell<Dispute, Dispute>() {
                            final Button button = new Button("Open contract");

                            {

                            }

                            @Override
                            public void updateItem(final Dispute item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    button.setOnAction(e -> onOpenContract(item));
                                    setGraphic(button);
                                } else {
                                    setGraphic(null);
                                    button.setOnAction(null);
                                }
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<Dispute, Dispute> getStateColumn() {
        TableColumn<Dispute, Dispute> column = new TableColumn<Dispute, Dispute>("State") {
            {
                setMinWidth(50);
            }
        };
        column.setCellValueFactory((dispute) -> new ReadOnlyObjectWrapper<>(dispute.getValue()));
        column.setCellFactory(
                new Callback<TableColumn<Dispute, Dispute>, TableCell<Dispute, Dispute>>() {
                    @Override
                    public TableCell<Dispute, Dispute> call(TableColumn<Dispute, Dispute> column) {
                        return new TableCell<Dispute, Dispute>() {


                            public ReadOnlyBooleanProperty closedProperty;
                            public ChangeListener<Boolean> listener;

                            @Override
                            public void updateItem(final Dispute item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    listener = (observable, oldValue, newValue) -> {
                                        setText(newValue ? "Closed" : "Open");
                                        getTableRow().setOpacity(newValue ? 0.4 : 1);
                                    };
                                    closedProperty = item.isClosedProperty();
                                    closedProperty.addListener(listener);
                                    boolean isClosed = item.isClosed();
                                    setText(isClosed ? "Closed" : "Open");
                                    getTableRow().setOpacity(isClosed ? 0.4 : 1);
                                } else {
                                    if (closedProperty != null)
                                        closedProperty.removeListener(listener);

                                    setText("");
                                }
                            }
                        };
                    }
                });
        return column;
    }

    private void scrollToBottom() {
        if (messageListView != null)
            UserThread.execute(() -> messageListView.scrollTo(Integer.MAX_VALUE));
    }

}


