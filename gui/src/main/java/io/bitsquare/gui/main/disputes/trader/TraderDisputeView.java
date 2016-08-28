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
import io.bitsquare.common.Timer;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.BusyAnimation;
import io.bitsquare.gui.components.HyperlinkWithIcon;
import io.bitsquare.gui.components.TableGroupHeadline;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.main.overlays.windows.ContractWindow;
import io.bitsquare.gui.main.overlays.windows.DisputeSummaryWindow;
import io.bitsquare.gui.main.overlays.windows.TradeDetailsWindow;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.GUIUtil;
import io.bitsquare.p2p.P2PService;
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
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Paint;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
    private P2PService p2PService;

    private final List<Attachment> tempAttachments = new ArrayList<>();

    private TableView<Dispute> tableView;
    private SortedList<Dispute> sortedList;

    private Dispute selectedDispute;
    private ListView<DisputeCommunicationMessage> messageListView;
    private TextArea inputTextArea;
    private AnchorPane messagesAnchorPane;
    private VBox messagesInputBox;
    private BusyAnimation sendMsgBusyAnimation;
    private Label sendMsgInfoLabel;
    private ChangeListener<Boolean> arrivedPropertyListener;
    private ChangeListener<Boolean> storedInMailboxPropertyListener;
    @Nullable
    private DisputeCommunicationMessage disputeCommunicationMessage;
    private ListChangeListener<DisputeCommunicationMessage> disputeDirectMessageListListener;
    private ChangeListener<Boolean> selectedDisputeClosedPropertyListener;
    private Subscription selectedDisputeSubscription;
    private TableGroupHeadline tableGroupHeadline;
    private ObservableList<DisputeCommunicationMessage> disputeCommunicationMessages;
    private Button sendButton;
    private Subscription inputTextAreaTextSubscription;
    private EventHandler<KeyEvent> keyEventEventHandler;
    private Scene scene;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TraderDisputeView(DisputeManager disputeManager, KeyRing keyRing, TradeManager tradeManager, Stage stage,
                             BSFormatter formatter, DisputeSummaryWindow disputeSummaryWindow,
                             ContractWindow contractWindow, TradeDetailsWindow tradeDetailsWindow, P2PService p2PService) {
        this.disputeManager = disputeManager;
        this.keyRing = keyRing;
        this.tradeManager = tradeManager;
        this.stage = stage;
        this.formatter = formatter;
        this.disputeSummaryWindow = disputeSummaryWindow;
        this.contractWindow = contractWindow;
        this.tradeDetailsWindow = tradeDetailsWindow;
        this.p2PService = p2PService;
    }

    @Override
    public void initialize() {
        tableView = new TableView<>();
        VBox.setVgrow(tableView, Priority.SOMETIMES);
        tableView.setMinHeight(150);
        root.getChildren().add(tableView);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Label placeholder = new Label("There are no open tickets");
        placeholder.setWrapText(true);
        tableView.setPlaceholder(placeholder);
        tableView.getSelectionModel().clearSelection();

        tableView.getColumns().add(getSelectColumn());

        TableColumn<Dispute, Dispute> contractColumn = getContractColumn();
        tableView.getColumns().add(contractColumn);

        TableColumn<Dispute, Dispute> dateColumn = getDateColumn();
        tableView.getColumns().add(dateColumn);

        TableColumn<Dispute, Dispute> tradeIdColumn = getTradeIdColumn();
        tableView.getColumns().add(tradeIdColumn);

        TableColumn<Dispute, Dispute> marketColumn = getMarketColumn();
        tableView.getColumns().add(marketColumn);

        TableColumn<Dispute, Dispute> roleColumn = getRoleColumn();
        tableView.getColumns().add(roleColumn);

        TableColumn<Dispute, Dispute> stateColumn = getStateColumn();
        tableView.getColumns().add(stateColumn);

        tradeIdColumn.setComparator((o1, o2) -> o1.getTradeId().compareTo(o2.getTradeId()));
        dateColumn.setComparator((o1, o2) -> o1.getOpeningDate().compareTo(o2.getOpeningDate()));
        marketColumn.setComparator((o1, o2) -> formatter.getCurrencyPair(o1.getContract().offer.getCurrencyCode()).compareTo(o2.getContract().offer.getCurrencyCode()));

        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(dateColumn);

        /*inputTextAreaListener = (observable, oldValue, newValue) ->
                sendButton.setDisable(newValue.length() == 0
                        && tempAttachments.size() == 0 &&
                        selectedDispute.disputeResultProperty().get() == null);*/

        selectedDisputeClosedPropertyListener = (observable, oldValue, newValue) -> {
            messagesInputBox.setVisible(!newValue);
            messagesInputBox.setManaged(!newValue);
            AnchorPane.setBottomAnchor(messageListView, newValue ? 0d : 120d);
        };

        disputeDirectMessageListListener = c -> scrollToBottom();

        keyEventEventHandler = event -> {
            if (new KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN).match(event)) {
                Map<String, List<Dispute>> map = new HashMap<>();
                disputeManager.getDisputesAsObservableList().stream().forEach(dispute -> {
                    String tradeId = dispute.getTradeId();
                    List<Dispute> list;
                    if (!map.containsKey(tradeId))
                        map.put(tradeId, new ArrayList<>());

                    list = map.get(tradeId);
                    list.add(dispute);
                });
                List<List<Dispute>> disputeGroups = new ArrayList<>();
                map.entrySet().stream().forEach(entry -> {
                    disputeGroups.add(entry.getValue());
                });
                disputeGroups.sort((o1, o2) -> !o1.isEmpty() && !o2.isEmpty() ? o1.get(0).getOpeningDate().compareTo(o2.get(0).getOpeningDate()) : 0);
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("Summary of all disputes (Nr. of disputes: " + disputeGroups.size() + ")\n\n");
                disputeGroups.stream().forEach(disputeGroup -> {
                    Dispute dispute0 = disputeGroup.get(0);
                    stringBuilder.append("##########################################################################################/\n")
                            .append("## Trade ID: ")
                            .append(dispute0.getTradeId())
                            .append("\n")
                            .append("## Date: ")
                            .append(formatter.formatDateTime(dispute0.getOpeningDate()))
                            .append("\n")
                            .append("## Is support ticket: ")
                            .append(dispute0.isSupportTicket())
                            .append("\n");
                    if (dispute0.disputeResultProperty().get() != null && dispute0.disputeResultProperty().get().getReason() != null) {
                        stringBuilder.append("## Reason: ")
                                .append(dispute0.disputeResultProperty().get().getReason())
                                .append("\n");
                    }
                    stringBuilder.append("##########################################################################################/\n")
                            .append("\n");
                    disputeGroup.stream().forEach(dispute -> {
                        stringBuilder
                                .append("*******************************************************************************************\n")
                                .append("** Traders ID: ")
                                .append(dispute.getTraderId())
                                .append("\n*******************************************************************************************\n")
                                .append("\n");
                        dispute.getDisputeCommunicationMessagesAsObservableList().stream().forEach(m -> {
                            String role = m.isSenderIsTrader() ? ">> Traders msg: " : "<< Arbitrators msg: ";
                            stringBuilder.append(role)
                                    .append(m.getMessage())
                                    .append("\n");
                        });
                        stringBuilder.append("\n");
                    });
                    stringBuilder.append("\n");
                });
                String message = stringBuilder.toString();
                new Popup().headLine("All disputes (" + disputeGroups.size() + ")")
                        .information(message)
                        .width(1000)
                        .actionButtonText("Copy")
                        .onAction(() -> Utilities.copyToClipboard(message))
                        .show();
            }
        };
    }

    @Override
    protected void activate() {
        disputeManager.cleanupDisputes();

        FilteredList<Dispute> filteredList = new FilteredList<>(disputeManager.getDisputesAsObservableList());
        setFilteredListPredicate(filteredList);

        sortedList = new SortedList<>(filteredList);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);

        // sortedList.setComparator((o1, o2) -> o2.getOpeningDate().compareTo(o1.getOpeningDate()));
        selectedDisputeSubscription = EasyBind.subscribe(tableView.getSelectionModel().selectedItemProperty(), this::onSelectDispute);

        Dispute selectedItem = tableView.getSelectionModel().getSelectedItem();
        if (selectedItem != null)
            tableView.getSelectionModel().select(selectedItem);

        scrollToBottom();

        scene = root.getScene();
        if (scene != null)
            scene.addEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);
    }

    @Override
    protected void deactivate() {
        sortedList.comparatorProperty().unbind();
        selectedDisputeSubscription.unsubscribe();
        removeListenersOnSelectDispute();

        if (scene != null)
            scene.removeEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);
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

        Timer timer = UserThread.runAfter(() -> {
            sendMsgInfoLabel.setVisible(true);
            sendMsgInfoLabel.setManaged(true);
            sendMsgInfoLabel.setText("Sending Message...");

            sendMsgBusyAnimation.play();
        }, 500, TimeUnit.MILLISECONDS);

        arrivedPropertyListener = (observable, oldValue, newValue) -> {
            if (newValue) {
                hideSendMsgInfo(timer);
            }
        };
        if (disputeCommunicationMessage != null && disputeCommunicationMessage.arrivedProperty() != null)
            disputeCommunicationMessage.arrivedProperty().addListener(arrivedPropertyListener);
        storedInMailboxPropertyListener = (observable, oldValue, newValue) -> {
            if (newValue) {
                sendMsgInfoLabel.setVisible(true);
                sendMsgInfoLabel.setManaged(true);
                sendMsgInfoLabel.setText("Receiver is not online. Message is saved to his mailbox.");
                hideSendMsgInfo(timer);
            }
        };
        if (disputeCommunicationMessage != null)
            disputeCommunicationMessage.storedInMailboxProperty().addListener(storedInMailboxPropertyListener);
    }

    private void hideSendMsgInfo(Timer timer) {
        timer.stop();
        inputTextArea.setDisable(false);

        UserThread.runAfter(() -> {
            sendMsgInfoLabel.setVisible(false);
            sendMsgInfoLabel.setManaged(false);
        }, 5);
        sendMsgBusyAnimation.stop();
    }

    private void onCloseDispute(Dispute dispute) {
        disputeSummaryWindow.onFinalizeDispute(() -> messagesAnchorPane.getChildren().remove(messagesInputBox))
                .show(dispute);
    }

    private void onRequestUpload() {
        int totalSize = tempAttachments.stream().mapToInt(a -> a.getBytes().length).sum();
        if (tempAttachments.size() < 3) {
            FileChooser fileChooser = new FileChooser();
            int maxMsgSize = Connection.getMaxMsgSize();
            int maxSizeInKB = maxMsgSize / 1024;
            fileChooser.setTitle("Open file to attach (max. file size: " + maxSizeInKB + " kb)");
           /* if (Utilities.isUnix())
                fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));*/
            File result = fileChooser.showOpenDialog(stage);
            if (result != null) {
                try {
                    URL url = result.toURI().toURL();
                    try (InputStream inputStream = url.openStream()) {
                        byte[] filesAsBytes = ByteStreams.toByteArray(inputStream);
                        int size = filesAsBytes.length;
                        int newSize = totalSize + size;
                        if (newSize > maxMsgSize) {
                            new Popup().warning("The total size of your attachments is " + (newSize / 1024) + " kb and is exceeding the max. allowed " +
                                    "message size of " + maxSizeInKB + " kB.").show();
                        } else if (size > maxMsgSize) {
                            new Popup().warning("The max. allowed file size is " + maxSizeInKB + " kB.").show();
                        } else {
                            tempAttachments.add(new Attachment(result.getName(), filesAsBytes));
                            inputTextArea.setText(inputTextArea.getText() + "\n[Attachment " + result.getName() + "]");
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

    private void removeListenersOnSelectDispute() {
        if (selectedDispute != null) {
            if (selectedDisputeClosedPropertyListener != null)
                selectedDispute.isClosedProperty().removeListener(selectedDisputeClosedPropertyListener);

            if (disputeCommunicationMessages != null && disputeDirectMessageListListener != null)
                disputeCommunicationMessages.removeListener(disputeDirectMessageListListener);
        }

        if (disputeCommunicationMessage != null) {
            if (arrivedPropertyListener != null)
                disputeCommunicationMessage.arrivedProperty().removeListener(arrivedPropertyListener);
            if (storedInMailboxPropertyListener != null)
                disputeCommunicationMessage.storedInMailboxProperty().removeListener(storedInMailboxPropertyListener);
        }

        if (messageListView != null)
            messageListView.prefWidthProperty().unbind();

        if (tableGroupHeadline != null)
            tableGroupHeadline.prefWidthProperty().unbind();

        if (messagesAnchorPane != null)
            messagesAnchorPane.prefWidthProperty().unbind();

        if (inputTextAreaTextSubscription != null)
            inputTextAreaTextSubscription.unsubscribe();
    }

    private void addListenersOnSelectDispute() {
        if (tableGroupHeadline != null) {
            tableGroupHeadline.prefWidthProperty().bind(root.widthProperty());
            messageListView.prefWidthProperty().bind(root.widthProperty());
            messagesAnchorPane.prefWidthProperty().bind(root.widthProperty());
            disputeCommunicationMessages.addListener(disputeDirectMessageListListener);
            selectedDispute.isClosedProperty().addListener(selectedDisputeClosedPropertyListener);
            inputTextAreaTextSubscription = EasyBind.subscribe(inputTextArea.textProperty(), t -> sendButton.setDisable(t.isEmpty()));
        }
    }

    private void onSelectDispute(Dispute dispute) {
        removeListenersOnSelectDispute();
        if (dispute == null) {
            if (root.getChildren().size() > 1)
                root.getChildren().remove(1);

            selectedDispute = null;
        } else if (selectedDispute != dispute) {
            this.selectedDispute = dispute;

            boolean isTrader = disputeManager.isTrader(selectedDispute);

            tableGroupHeadline = new TableGroupHeadline();
            tableGroupHeadline.setText("Messages");

            AnchorPane.setTopAnchor(tableGroupHeadline, 10d);
            AnchorPane.setRightAnchor(tableGroupHeadline, 0d);
            AnchorPane.setBottomAnchor(tableGroupHeadline, 0d);
            AnchorPane.setLeftAnchor(tableGroupHeadline, 0d);

            disputeCommunicationMessages = selectedDispute.getDisputeCommunicationMessagesAsObservableList();
            SortedList<DisputeCommunicationMessage> sortedList = new SortedList<>(disputeCommunicationMessages);
            sortedList.setComparator((o1, o2) -> o1.getDate().compareTo(o2.getDate()));
            messageListView = new ListView<>(sortedList);
            messageListView.setId("message-list-view");

            messageListView.setMinHeight(150);
            AnchorPane.setTopAnchor(messageListView, 30d);
            AnchorPane.setRightAnchor(messageListView, 0d);
            AnchorPane.setLeftAnchor(messageListView, 0d);

            messagesAnchorPane = new AnchorPane();
            VBox.setVgrow(messagesAnchorPane, Priority.ALWAYS);

            inputTextArea = new TextArea();
            inputTextArea.setPrefHeight(70);
            inputTextArea.setWrapText(true);

            sendButton = new Button("Send");
            sendButton.setDefaultButton(true);
            sendButton.setOnAction(e -> {
                if (p2PService.isBootstrapped()) {
                    String text = inputTextArea.getText();
                    if (!text.isEmpty())
                        onSendMessage(text, selectedDispute);
                } else {
                    new Popup().information("You need to wait until you are fully connected to the network.\n" +
                            "That might take up to about 2 minutes at startup.").show();
                }
            });
            inputTextAreaTextSubscription = EasyBind.subscribe(inputTextArea.textProperty(), t -> sendButton.setDisable(t.isEmpty()));

            Button uploadButton = new Button("Add attachments");
            uploadButton.setOnAction(e -> onRequestUpload());

            sendMsgInfoLabel = new Label();
            sendMsgInfoLabel.setVisible(false);
            sendMsgInfoLabel.setManaged(false);
            sendMsgInfoLabel.setPadding(new Insets(5, 0, 0, 0));

            sendMsgBusyAnimation = new BusyAnimation(false);

            if (!selectedDispute.isClosed()) {
                HBox buttonBox = new HBox();
                buttonBox.setSpacing(10);
                buttonBox.getChildren().addAll(sendButton, uploadButton, sendMsgBusyAnimation, sendMsgInfoLabel);

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
                        public ChangeListener<Boolean> sendMsgBusyAnimationListener;
                        final Pane bg = new Pane();
                        final ImageView arrow = new ImageView();
                        final Label headerLabel = new Label();
                        final Label messageLabel = new Label();
                        final Label copyIcon = new Label();
                        final HBox attachmentsBox = new HBox();
                        final AnchorPane messageAnchorPane = new AnchorPane();
                        final Label statusIcon = new Label();
                        final double arrowWidth = 15d;
                        final double attachmentsBoxHeight = 20d;
                        final double border = 10d;
                        final double bottomBorder = 25d;
                        final double padding = border + 10d;
                        final double msgLabelPaddingRight = padding + 20d;

                        {
                            bg.setMinHeight(30);
                            messageLabel.setWrapText(true);
                            headerLabel.setTextAlignment(TextAlignment.CENTER);
                            attachmentsBox.setSpacing(5);
                            statusIcon.setStyle("-fx-font-size: 10;");
                            Tooltip.install(copyIcon, new Tooltip("Copy to clipboard"));
                            messageAnchorPane.getChildren().addAll(bg, arrow, headerLabel, messageLabel, copyIcon, attachmentsBox, statusIcon);
                        }

                        @Override
                        public void updateItem(final DisputeCommunicationMessage item, boolean empty) {
                            super.updateItem(item, empty);

                            if (item != null && !empty) {
                                copyIcon.setOnMouseClicked(e -> Utilities.copyToClipboard(messageLabel.getText()));

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
                                AnchorPane.setTopAnchor(copyIcon, 25d);
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
                                    copyIcon.setStyle("-fx-text-fill: white;");
                                } else if (isMyMsg) {
                                    headerLabel.setStyle("-fx-text-fill: -fx-accent; -fx-font-size: 11;");
                                    bg.setId("message-bubble-blue");
                                    messageLabel.setStyle("-fx-text-fill: white;");
                                    copyIcon.setStyle("-fx-text-fill: white;");
                                    if (isTrader)
                                        arrow.setId("bubble_arrow_blue_left");
                                    else
                                        arrow.setId("bubble_arrow_blue_right");

                                    if (sendMsgBusyAnimationListener != null)
                                        sendMsgBusyAnimation.isRunningProperty().removeListener(sendMsgBusyAnimationListener);

                                    sendMsgBusyAnimationListener = (observable, oldValue, newValue) -> {
                                        if (!newValue) {
                                            if (item.arrivedProperty().get())
                                                showArrivedIcon();
                                            else if (item.storedInMailboxProperty().get())
                                                showMailboxIcon();
                                        }
                                    };
                                    sendMsgBusyAnimation.isRunningProperty().addListener(sendMsgBusyAnimationListener);

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
                                    copyIcon.setStyle("-fx-text-fill: black;");
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
                                    AnchorPane.setRightAnchor(messageLabel, msgLabelPaddingRight);
                                    AnchorPane.setRightAnchor(copyIcon, padding);
                                    AnchorPane.setLeftAnchor(attachmentsBox, padding);
                                    AnchorPane.setRightAnchor(attachmentsBox, padding);
                                } else if (senderIsTrader) {
                                    AnchorPane.setLeftAnchor(headerLabel, padding + arrowWidth);
                                    AnchorPane.setLeftAnchor(bg, border + arrowWidth);
                                    AnchorPane.setRightAnchor(bg, border);
                                    AnchorPane.setLeftAnchor(arrow, border);
                                    AnchorPane.setLeftAnchor(messageLabel, padding + arrowWidth);
                                    AnchorPane.setRightAnchor(messageLabel, msgLabelPaddingRight);
                                    AnchorPane.setRightAnchor(copyIcon, padding);
                                    AnchorPane.setLeftAnchor(attachmentsBox, padding + arrowWidth);
                                    AnchorPane.setRightAnchor(attachmentsBox, padding);
                                    AnchorPane.setRightAnchor(statusIcon, padding);
                                } else {
                                    AnchorPane.setRightAnchor(headerLabel, padding + arrowWidth);
                                    AnchorPane.setLeftAnchor(bg, border);
                                    AnchorPane.setRightAnchor(bg, border + arrowWidth);
                                    AnchorPane.setRightAnchor(arrow, border);
                                    AnchorPane.setLeftAnchor(messageLabel, padding);
                                    AnchorPane.setRightAnchor(messageLabel, msgLabelPaddingRight + arrowWidth);
                                    AnchorPane.setRightAnchor(copyIcon, padding + arrowWidth);
                                    AnchorPane.setLeftAnchor(attachmentsBox, padding);
                                    AnchorPane.setRightAnchor(attachmentsBox, padding + arrowWidth);
                                    AnchorPane.setLeftAnchor(statusIcon, padding);
                                }

                                AnchorPane.setBottomAnchor(statusIcon, 7d);
                                headerLabel.setText(formatter.formatDateTime(item.getDate()));
                                messageLabel.setText(item.getMessage());
                                attachmentsBox.getChildren().clear();
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
                                    AnchorPane.setBottomAnchor(messageLabel, bottomBorder + 10);
                                }

                                // Need to set it here otherwise style is not correct
                                AwesomeDude.setIcon(copyIcon, AwesomeIcon.COPY, "16.0");
                                copyIcon.getStyleClass().add("copy-icon-disputes");

                                // TODO There are still some cell rendering issues on updates
                                setGraphic(messageAnchorPane);
                            } else {
                                if (sendMsgBusyAnimation != null && sendMsgBusyAnimationListener != null)
                                    sendMsgBusyAnimation.isRunningProperty().removeListener(sendMsgBusyAnimationListener);

                                messageAnchorPane.prefWidthProperty().unbind();

                                AnchorPane.clearConstraints(bg);
                                AnchorPane.clearConstraints(headerLabel);
                                AnchorPane.clearConstraints(arrow);
                                AnchorPane.clearConstraints(messageLabel);
                                AnchorPane.clearConstraints(copyIcon);
                                AnchorPane.clearConstraints(statusIcon);
                                AnchorPane.clearConstraints(attachmentsBox);

                                copyIcon.setOnMouseClicked(null);
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

        addListenersOnSelectDispute();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table
    ///////////////////////////////////////////////////////////////////////////////////////////

    private TableColumn<Dispute, Dispute> getSelectColumn() {
        TableColumn<Dispute, Dispute> column = new TableColumn<Dispute, Dispute>("Select") {
            {
                setMinWidth(110);
                setMaxWidth(110);
                setSortable(false);
            }
        };
        column.setCellValueFactory((addressListItem) ->
                new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        column.setCellFactory(
                new Callback<TableColumn<Dispute, Dispute>, TableCell<Dispute,
                        Dispute>>() {

                    @Override
                    public TableCell<Dispute, Dispute> call(TableColumn<Dispute,
                            Dispute> column) {
                        return new TableCell<Dispute, Dispute>() {

                            Button button;

                            @Override
                            public void updateItem(final Dispute item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    if (button == null) {
                                        button = new Button("Select");
                                        button.setOnAction(e -> tableView.getSelectionModel().select(item));
                                        setGraphic(button);
                                    }
                                } else {
                                    setGraphic(null);
                                    if (button != null) {
                                        button.setOnAction(null);
                                        button = null;
                                    }
                                }
                            }
                        };
                    }
                });
        return column;
    }

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

    private TableColumn<Dispute, Dispute> getMarketColumn() {
        TableColumn<Dispute, Dispute> column = new TableColumn<Dispute, Dispute>("Market") {
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
                                    setText(formatter.getCurrencyPair(item.getContract().offer.getCurrencyCode()));
                                else
                                    setText("");
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
                                        setText(item.isDisputeOpenerIsBuyer() ? "BTC buyer/Offerer" : "BTC seller/Offerer");
                                    else
                                        setText(item.isDisputeOpenerIsBuyer() ? "BTC buyer/Taker" : "BTC seller/Taker");
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


