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

import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bitsquare.alert.PrivateNotificationManager;
import io.bitsquare.app.Version;
import io.bitsquare.arbitration.Dispute;
import io.bitsquare.arbitration.DisputeManager;
import io.bitsquare.arbitration.messages.DisputeCommunicationMessage;
import io.bitsquare.arbitration.payload.Attachment;
import io.bitsquare.common.Timer;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.common.crypto.PubKeyRing;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.BusyAnimation;
import io.bitsquare.gui.components.HyperlinkWithIcon;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.components.TableGroupHeadline;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.main.overlays.windows.ContractWindow;
import io.bitsquare.gui.main.overlays.windows.DisputeSummaryWindow;
import io.bitsquare.gui.main.overlays.windows.SendPrivateNotificationWindow;
import io.bitsquare.gui.main.overlays.windows.TradeDetailsWindow;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.GUIUtil;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.trade.Contract;
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
import javafx.fxml.FXML;
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
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

// will be probably only used for arbitration communication, will be renamed and the icon changed
@FxmlView
public class TraderDisputeView extends ActivatableView<VBox, Void> {

    @FXML
    Insets rootPadding;

    private final DisputeManager disputeManager;
    protected final KeyRing keyRing;
    private final TradeManager tradeManager;
    private final Stage stage;
    protected final BSFormatter formatter;
    private final DisputeSummaryWindow disputeSummaryWindow;
    private PrivateNotificationManager privateNotificationManager;
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
    protected FilteredList<Dispute> filteredList;
    private InputTextField filterTextField;
    private ChangeListener<String> filterTextFieldListener;
    protected HBox filterBox;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TraderDisputeView(DisputeManager disputeManager, KeyRing keyRing, TradeManager tradeManager, Stage stage,
                             BSFormatter formatter, DisputeSummaryWindow disputeSummaryWindow, PrivateNotificationManager privateNotificationManager,
                             ContractWindow contractWindow, TradeDetailsWindow tradeDetailsWindow, P2PService p2PService) {
        this.disputeManager = disputeManager;
        this.keyRing = keyRing;
        this.tradeManager = tradeManager;
        this.stage = stage;
        this.formatter = formatter;
        this.disputeSummaryWindow = disputeSummaryWindow;
        this.privateNotificationManager = privateNotificationManager;
        this.contractWindow = contractWindow;
        this.tradeDetailsWindow = tradeDetailsWindow;
        this.p2PService = p2PService;
    }

    @Override
    public void initialize() {
        rootPadding = new Insets(MainView.scale(10), MainView.scale(10), MainView.scale(0), MainView.scale(10));

        Label label = new Label("Filter list:");
        HBox.setMargin(label, new Insets(MainView.scale(5), MainView.scale(0), MainView.scale(0), MainView.scale(0)));
        filterTextField = new InputTextField();
        filterTextField.setText("open");
        filterTextFieldListener = (observable, oldValue, newValue) -> applyFilteredListPredicate(filterTextField.getText());

        filterBox = new HBox();
        filterBox.setSpacing(5);
        filterBox.getChildren().addAll(label, filterTextField);
        VBox.setVgrow(filterBox, Priority.NEVER);
        filterBox.setVisible(false);
        filterBox.setManaged(false);

        tableView = new TableView<>();
        VBox.setVgrow(tableView, Priority.SOMETIMES);
        tableView.setMinHeight(MainView.scale(150));

        root.getChildren().addAll(filterBox, tableView);

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

        TableColumn<Dispute, Dispute> buyerOnionAddressColumn = getBuyerOnionAddressColumn();
        tableView.getColumns().add(buyerOnionAddressColumn);

        TableColumn<Dispute, Dispute> sellerOnionAddressColumn = getSellerOnionAddressColumn();
        tableView.getColumns().add(sellerOnionAddressColumn);


        TableColumn<Dispute, Dispute> marketColumn = getMarketColumn();
        tableView.getColumns().add(marketColumn);

        TableColumn<Dispute, Dispute> roleColumn = getRoleColumn();
        tableView.getColumns().add(roleColumn);

        TableColumn<Dispute, Dispute> stateColumn = getStateColumn();
        tableView.getColumns().add(stateColumn);

        tradeIdColumn.setComparator((o1, o2) -> o1.getTradeId().compareTo(o2.getTradeId()));
        dateColumn.setComparator((o1, o2) -> o1.getOpeningDate().compareTo(o2.getOpeningDate()));
        buyerOnionAddressColumn.setComparator((o1, o2) -> getBuyerOnionAddressColumnLabel(o1).compareTo(getBuyerOnionAddressColumnLabel(o2)));
        sellerOnionAddressColumn.setComparator((o1, o2) -> getSellerOnionAddressColumnLabel(o1).compareTo(getSellerOnionAddressColumnLabel(o2)));
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
            AnchorPane.setBottomAnchor(messageListView, newValue ? MainView.scale(0) : MainView.scale(120));
        };

        disputeDirectMessageListListener = c -> scrollToBottom();

        keyEventEventHandler = event -> {
            if (new KeyCodeCombination(KeyCode.L, KeyCombination.ALT_DOWN).match(event)) {
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
                stringBuilder.append("Summary of all disputes (No. of disputes: " + disputeGroups.size() + ")\n\n");
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
                                .append("** Trader's ID: ")
                                .append(dispute.getTraderId())
                                .append("\n*******************************************************************************************\n")
                                .append("\n");
                        dispute.getDisputeCommunicationMessagesAsObservableList().stream().forEach(m -> {
                            String role = m.isSenderIsTrader() ? ">> Trader's msg: " : "<< Arbitrator's msg: ";
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
                        .width(MainView.scale(1000))
                        .actionButtonText("Copy")
                        .onAction(() -> Utilities.copyToClipboard(message))
                        .show();
            } else if (new KeyCodeCombination(KeyCode.U, KeyCombination.ALT_DOWN).match(event)) {
                // Hidden shortcut to re-open a dispute. Allow it also for traders not only arbitrator.
                if (selectedDispute != null) {
                    if (selectedDisputeClosedPropertyListener != null)
                        selectedDispute.isClosedProperty().removeListener(selectedDisputeClosedPropertyListener);
                    selectedDispute.setIsClosed(false);
                }
            } else if (new KeyCodeCombination(KeyCode.R, KeyCombination.ALT_DOWN).match(event)) {
                if (selectedDispute != null) {
                    PubKeyRing pubKeyRing = selectedDispute.getTraderPubKeyRing();
                    NodeAddress nodeAddress;
                    if (pubKeyRing.equals(selectedDispute.getContract().getBuyerPubKeyRing()))
                        nodeAddress = selectedDispute.getContract().getBuyerNodeAddress();
                    else
                        nodeAddress = selectedDispute.getContract().getSellerNodeAddress();

                    new SendPrivateNotificationWindow(pubKeyRing, nodeAddress)
                            .onAddAlertMessage(privateNotificationManager::sendPrivateNotificationMessageIfKeyIsValid)
                            .show();
                }
            }
        };
    }

    @Override
    protected void activate() {
        filterTextField.textProperty().addListener(filterTextFieldListener);
        disputeManager.cleanupDisputes();

        filteredList = new FilteredList<>(disputeManager.getDisputesAsObservableList());
        applyFilteredListPredicate(filterTextField.getText());

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

        // If doPrint=true we print out a html page which opens tabs with all deposit txs 
        // (firefox needs about:config change to allow > 20 tabs)
        // Useful to check if there any funds in not finished trades (no payout tx done).
        // Last check 10.02.2017 found 8 trades and we contacted all traders as far as possible (email if available 
        // otherwise in-app private notification)
        boolean doPrint = false;
        if (doPrint) {
            try {
                DateFormat formatter = new SimpleDateFormat("dd/MM/yy");
                Date startDate = formatter.parse("10/02/17");
                startDate = new Date(0); // print all from start

                HashMap<String, Dispute> map = new HashMap<>();
                disputeManager.getDisputesAsObservableList().stream().forEach(dispute -> {
                    map.put(dispute.getDepositTxId(), dispute);
                });

                final Date finalStartDate = startDate;
                List<Dispute> disputes = new ArrayList<>(map.values());
                disputes.sort((o1, o2) -> o1.getOpeningDate().compareTo(o2.getOpeningDate()));
                List<List<Dispute>> subLists = Lists.partition(disputes, 1000);
                StringBuilder sb = new StringBuilder();
                subLists.stream().forEach(list -> {
                    StringBuilder sb1 = new StringBuilder("\n<html><head><script type=\"text/javascript\">function load(){\n");
                    StringBuilder sb2 = new StringBuilder("\n}</script></head><body onload=\"load()\">\n");
                    list.stream().forEach(dispute -> {
                        if (dispute.getOpeningDate().after(finalStartDate)) {
                            String txId = dispute.getDepositTxId();
                            sb1.append("window.open(\"https://blockchain.info/tx/").append(txId).append("\", '_blank');\n");

                            sb2.append("Dispute ID: ").append(dispute.getId()).
                                    append(" Tx ID: ").
                                    append("<a href=\"https://blockchain.info/tx/").append(txId).append("\">").
                                    append(txId).append("</a> ").
                                    append("Opening date: ").append(formatter.format(dispute.getOpeningDate())).append("<br/>\n");
                        }
                    });
                    sb2.append("</body></html>");
                    String res = sb1.toString() + sb2.toString();

                    sb.append(res).append("\n\n\n");
                });
                log.info(sb.toString());
            } catch (ParseException ignore) {
            }
        }
    }

    @Override
    protected void deactivate() {
        filterTextField.textProperty().removeListener(filterTextFieldListener);
        sortedList.comparatorProperty().unbind();
        selectedDisputeSubscription.unsubscribe();
        removeListenersOnSelectDispute();

        if (scene != null)
            scene.removeEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);
    }

    protected void applyFilteredListPredicate(String filterString) {
        // If in trader view we must not display arbitrators own disputes as trader (must not happen anyway)
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
        long protocolVersion = dispute.getContract().offer.getProtocolVersion();
        if (protocolVersion == Version.TRADE_PROTOCOL_VERSION) {
            disputeSummaryWindow.onFinalizeDispute(() -> messagesAnchorPane.getChildren().remove(messagesInputBox))
                    .show(dispute);
        } else {
            new Popup<>()
                    .warning("The offer in that dispute has been created with an older version of Bitsquare.\n" +
                            "You cannot close that dispute with your version of the application.\n\n" +
                            "Please use an older version with protocol version " + protocolVersion)
                    .show();
        }
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
            if (selectedDispute != null)
                selectedDispute.isClosedProperty().addListener(selectedDisputeClosedPropertyListener);
            inputTextAreaTextSubscription = EasyBind.subscribe(inputTextArea.textProperty(), t -> sendButton.setDisable(t.isEmpty()));
        }
    }

    private void onSelectDispute(Dispute dispute) {
        removeListenersOnSelectDispute();
        if (dispute == null) {
            if (root.getChildren().size() > 2)
                root.getChildren().remove(2);

            selectedDispute = null;
        } else if (selectedDispute != dispute) {
            this.selectedDispute = dispute;

            boolean isTrader = disputeManager.isTrader(selectedDispute);

            tableGroupHeadline = new TableGroupHeadline();
            tableGroupHeadline.setText("Messages");

            AnchorPane.setTopAnchor(tableGroupHeadline, MainView.scale(10));
            AnchorPane.setRightAnchor(tableGroupHeadline, MainView.scale(0));
            AnchorPane.setBottomAnchor(tableGroupHeadline, MainView.scale(0));
            AnchorPane.setLeftAnchor(tableGroupHeadline, MainView.scale(0));

            disputeCommunicationMessages = selectedDispute.getDisputeCommunicationMessagesAsObservableList();
            SortedList<DisputeCommunicationMessage> sortedList = new SortedList<>(disputeCommunicationMessages);
            sortedList.setComparator((o1, o2) -> o1.getDate().compareTo(o2.getDate()));
            messageListView = new ListView<>(sortedList);
            messageListView.setId("message-list-view");

            messageListView.setMinHeight(MainView.scale(150));
            AnchorPane.setTopAnchor(messageListView, MainView.scale(30));
            AnchorPane.setRightAnchor(messageListView, MainView.scale(0));
            AnchorPane.setLeftAnchor(messageListView, MainView.scale(0));

            messagesAnchorPane = new AnchorPane();
            VBox.setVgrow(messagesAnchorPane, Priority.ALWAYS);

            inputTextArea = new TextArea();
            inputTextArea.setPrefHeight(MainView.scale(70));
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
            sendMsgInfoLabel.setPadding(new Insets(MainView.scale(5), MainView.scale(0), MainView.scale(0), MainView.scale(0)));

            sendMsgBusyAnimation = new BusyAnimation(false);

            if (!selectedDispute.isClosed()) {
                HBox buttonBox = new HBox();
                buttonBox.setSpacing(MainView.scale(10));
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
                messagesInputBox.setSpacing(MainView.scale(10));
                messagesInputBox.getChildren().addAll(inputTextArea, buttonBox);
                VBox.setVgrow(buttonBox, Priority.ALWAYS);

                AnchorPane.setRightAnchor(messagesInputBox, MainView.scale(0));
                AnchorPane.setBottomAnchor(messagesInputBox, MainView.scale(5));
                AnchorPane.setLeftAnchor(messagesInputBox, MainView.scale(0));

                AnchorPane.setBottomAnchor(messageListView, MainView.scale(120));

                messagesAnchorPane.getChildren().addAll(tableGroupHeadline, messageListView, messagesInputBox);
            } else {
                AnchorPane.setBottomAnchor(messageListView, MainView.scale(0));
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
                        final double arrowWidth = MainView.scale(15);
                        final double attachmentsBoxHeight = MainView.scale(20);
                        final double border = MainView.scale(10);
                        final double bottomBorder = MainView.scale(25);
                        final double padding = border + MainView.scale(10);
                        final double msgLabelPaddingRight = padding + MainView.scale(20);

                        {
                            bg.setMinHeight(MainView.scale(30));
                            messageLabel.setWrapText(true);
                            headerLabel.setTextAlignment(TextAlignment.CENTER);
                            attachmentsBox.setSpacing(MainView.scale(5));
                            statusIcon.setStyle("-fx-font-size: " + MainView.scale(10) + ";");
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

                                AnchorPane.setTopAnchor(bg, MainView.scale(15));
                                AnchorPane.setBottomAnchor(bg, bottomBorder);
                                AnchorPane.setTopAnchor(headerLabel, MainView.scale(0));
                                AnchorPane.setBottomAnchor(arrow, bottomBorder + MainView.scale(5));
                                AnchorPane.setTopAnchor(messageLabel, MainView.scale(25));
                                AnchorPane.setTopAnchor(copyIcon, MainView.scale(25));
                                AnchorPane.setBottomAnchor(attachmentsBox, bottomBorder + MainView.scale(10));

                                boolean senderIsTrader = item.isSenderIsTrader();
                                boolean isMyMsg = isTrader ? senderIsTrader : !senderIsTrader;

                                arrow.setVisible(!item.isSystemMessage());
                                arrow.setManaged(!item.isSystemMessage());
                                statusIcon.setVisible(false);
                                if (item.isSystemMessage()) {
                                    headerLabel.setStyle("-fx-text-fill: -bs-green; -fx-font-size: " + MainView.scale(11) + ";");
                                    bg.setId("message-bubble-green");
                                    messageLabel.setStyle("-fx-text-fill: white;");
                                    copyIcon.setStyle("-fx-text-fill: white;");
                                } else if (isMyMsg) {
                                    headerLabel.setStyle("-fx-text-fill: -fx-accent; -fx-font-size: " + MainView.scale(11) + ";");
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
                                    headerLabel.setStyle("-fx-text-fill: -bs-light-grey; -fx-font-size: " + MainView.scale(11) + ";");
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

                                AnchorPane.setBottomAnchor(statusIcon, MainView.scale(7));
                                headerLabel.setText(formatter.formatDateTime(item.getDate()));
                                messageLabel.setText(item.getMessage());
                                attachmentsBox.getChildren().clear();
                                if (item.getAttachments().size() > 0) {
                                    AnchorPane.setBottomAnchor(messageLabel, bottomBorder + attachmentsBoxHeight + MainView.scale(10));
                                    attachmentsBox.getChildren().add(new Label("Attachments: ") {{
                                        setPadding(new Insets(MainView.scale(0), MainView.scale(0), MainView.scale(3), MainView.scale(0)));
                                        if (isMyMsg)
                                            setStyle("-fx-text-fill: white;");
                                        else
                                            setStyle("-fx-text-fill: black;");
                                    }});

                                    item.getAttachments().stream().forEach(attachment -> {
                                        final Label icon = new Label();
                                        setPadding(new Insets(MainView.scale(0), MainView.scale(0), MainView.scale(3), MainView.scale(0)));
                                        if (isMyMsg)
                                            icon.getStyleClass().add("attachment-icon");
                                        else
                                            icon.getStyleClass().add("attachment-icon-black");

                                        AwesomeDude.setIcon(icon, AwesomeIcon.FILE_TEXT);
                                        icon.setPadding(new Insets(MainView.scale(-2), MainView.scale(0), MainView.scale(0), MainView.scale(0)));
                                        icon.setTooltip(new Tooltip(attachment.getFileName()));
                                        icon.setOnMouseClicked(event -> onOpenAttachment(attachment));
                                        attachmentsBox.getChildren().add(icon);
                                    });
                                } else {
                                    AnchorPane.setBottomAnchor(messageLabel, bottomBorder + MainView.scale(10));
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
                            Tooltip.install(statusIcon, new Tooltip("Message saved in receiver's mailbox"));
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

            if (root.getChildren().size() > 2)
                root.getChildren().remove(2);
            root.getChildren().add(2, messagesAnchorPane);

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
                setMinWidth(MainView.scale(80));
                setMaxWidth(MainView.scale(80));
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

    private TableColumn<Dispute, Dispute> getContractColumn() {
        TableColumn<Dispute, Dispute> column = new TableColumn<Dispute, Dispute>("Details") {
            {
                setMinWidth(MainView.scale(80));
                setSortable(false);
            }
        };
        column.setCellValueFactory((dispute) -> new ReadOnlyObjectWrapper<>(dispute.getValue()));
        column.setCellFactory(
                new Callback<TableColumn<Dispute, Dispute>, TableCell<Dispute, Dispute>>() {

                    @Override
                    public TableCell<Dispute, Dispute> call(TableColumn<Dispute, Dispute> column) {
                        return new TableCell<Dispute, Dispute>() {
                            final Button button = new Button("Details");

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

    private TableColumn<Dispute, Dispute> getDateColumn() {
        TableColumn<Dispute, Dispute> column = new TableColumn<Dispute, Dispute>("Date") {
            {
                setMinWidth(MainView.scale(180));
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

    private TableColumn<Dispute, Dispute> getTradeIdColumn() {
        TableColumn<Dispute, Dispute> column = new TableColumn<Dispute, Dispute>("Trade ID") {
            {
                setMinWidth(MainView.scale(110));
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

    private TableColumn<Dispute, Dispute> getBuyerOnionAddressColumn() {
        TableColumn<Dispute, Dispute> column = new TableColumn<Dispute, Dispute>("BTC buyer address") {
            {
                setMinWidth(MainView.scale(170));
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
                                    setText(getBuyerOnionAddressColumnLabel(item));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<Dispute, Dispute> getSellerOnionAddressColumn() {
        TableColumn<Dispute, Dispute> column = new TableColumn<Dispute, Dispute>("BTC seller address") {
            {
                setMinWidth(MainView.scale(170));
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
                                    setText(getSellerOnionAddressColumnLabel(item));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }


    protected String getBuyerOnionAddressColumnLabel(Dispute item) {
        Contract contract = item.getContract();
        if (contract != null) {
            NodeAddress buyerNodeAddress = contract.getBuyerNodeAddress();
            if (buyerNodeAddress != null)
                return buyerNodeAddress.getHostNameWithoutPostFix() + " (" + disputeManager.getNrOfDisputes(true, contract) + ")";
            else
                return "N/A";
        } else {
            return "N/A";
        }
    }

    protected String getSellerOnionAddressColumnLabel(Dispute item) {
        Contract contract = item.getContract();
        if (contract != null) {
            NodeAddress sellerNodeAddress = contract.getSellerNodeAddress();
            if (sellerNodeAddress != null)
                return sellerNodeAddress.getHostNameWithoutPostFix() + " (" + disputeManager.getNrOfDisputes(false, contract) + ")";
            else
                return "N/A";
        } else {
            return "N/A";
        }
    }

    private TableColumn<Dispute, Dispute> getMarketColumn() {
        TableColumn<Dispute, Dispute> column = new TableColumn<Dispute, Dispute>("Market") {
            {
                setMinWidth(MainView.scale(130));
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
                setMinWidth(MainView.scale(130));
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

    private TableColumn<Dispute, Dispute> getStateColumn() {
        TableColumn<Dispute, Dispute> column = new TableColumn<Dispute, Dispute>("State") {
            {
                setMinWidth(MainView.scale(50));
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
                                    if (closedProperty != null) {
                                        closedProperty.removeListener(listener);
                                        closedProperty = null;
                                    }
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


