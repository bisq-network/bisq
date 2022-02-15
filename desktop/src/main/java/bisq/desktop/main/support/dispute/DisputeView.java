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

package bisq.desktop.main.support.dispute;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.PeerInfoIcon;
import bisq.desktop.components.PeerInfoIconDispute;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.ContractWindow;
import bisq.desktop.main.overlays.windows.DisputeSummaryWindow;
import bisq.desktop.main.overlays.windows.SendLogFilesWindow;
import bisq.desktop.main.overlays.windows.SendPrivateNotificationWindow;
import bisq.desktop.main.overlays.windows.TradeDetailsWindow;
import bisq.desktop.main.overlays.windows.VerifyDisputeResultSignatureWindow;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.alert.PrivateNotificationManager;
import bisq.core.dao.DaoFacade;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.offer.OfferRestrictions;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeList;
import bisq.core.support.dispute.DisputeManager;
import bisq.core.support.dispute.DisputeResult;
import bisq.core.support.dispute.DisputeSession;
import bisq.core.support.dispute.agent.DisputeAgentLookupMap;
import bisq.core.support.dispute.mediation.MediationManager;
import bisq.core.support.dispute.mediation.mediator.MediatorManager;
import bisq.core.support.dispute.refund.refundagent.RefundAgentManager;
import bisq.core.support.messages.ChatMessage;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.user.Preferences;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.utils.Utils;

import bisq.common.UserThread;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;

import com.jfoenix.controls.JFXBadge;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import javafx.geometry.Insets;
import javafx.geometry.Pos;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;
import javafx.util.Duration;

import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import lombok.Getter;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.getIconForLabel;
import static bisq.desktop.util.FormBuilder.getRegularIconButton;

public abstract class DisputeView extends ActivatableView<VBox, Void> implements PeerInfoIcon.notify, DisputeChatPopup.ChatCallback {
    public enum FilterResult {
        NO_MATCH("No Match"),
        NO_FILTER("No filter text"),
        OPEN_DISPUTES("Open disputes"),
        TRADE_ID("Trade ID"),
        OPENING_DATE("Opening date"),
        BUYER_NODE_ADDRESS("Buyer node address"),
        SELLER_NODE_ADDRESS("Seller node address"),
        BUYER_ACCOUNT_DETAILS("Buyer account details"),
        SELLER_ACCOUNT_DETAILS("Seller account details"),
        DEPOSIT_TX("Deposit tx ID"),
        PAYOUT_TX("Payout tx ID"),
        DEL_PAYOUT_TX("Delayed payout tx ID"),
        RESULT_MESSAGE("Result message"),
        REASON("Reason"),
        JSON("Contract as json");

        // Used in tooltip at search string to show where the match was found
        @Getter
        private final String displayString;

        FilterResult(String displayString) {

            this.displayString = displayString;
        }
    }

    protected final DisputeManager<? extends DisputeList<Dispute>> disputeManager;
    protected final KeyRing keyRing;
    private final P2PService p2PService;
    private final TradeManager tradeManager;
    protected final CoinFormatter formatter;
    protected final Preferences preferences;
    protected final DisputeSummaryWindow disputeSummaryWindow;
    private final PrivateNotificationManager privateNotificationManager;
    private final ContractWindow contractWindow;
    private final TradeDetailsWindow tradeDetailsWindow;

    private final AccountAgeWitnessService accountAgeWitnessService;
    private final MediatorManager mediatorManager;
    private final RefundAgentManager refundAgentManager;
    protected final DaoFacade daoFacade;
    private final boolean useDevPrivilegeKeys;

    protected TableView<Dispute> tableView;
    private SortedList<Dispute> sortedList;

    @Getter
    protected Dispute selectedDispute;

    private Subscription selectedDisputeSubscription;
    protected FilteredList<Dispute> filteredList;
    protected InputTextField filterTextField;
    private ChangeListener<String> filterTextFieldListener;
    protected AutoTooltipButton sigCheckButton, reOpenButton, closeButton, sendPrivateNotificationButton, reportButton, fullReportButton;
    private final Map<String, ListChangeListener<ChatMessage>> disputeChatMessagesListeners = new HashMap<>();
    @Nullable
    private ListChangeListener<Dispute> disputesListener; // Only set in mediation cases
    protected Label alertIconLabel;
    protected TableColumn<Dispute, Dispute> stateColumn;
    private Map<String, ListChangeListener<ChatMessage>> listenerByDispute = new HashMap<>();
    private Map<String, Button> chatButtonByDispute = new HashMap<>();
    private Map<String, JFXBadge> chatBadgeByDispute = new HashMap<>();
    private Map<String, JFXBadge> newBadgeByDispute = new HashMap<>();
    private Map<String, PeerInfoIconDispute> avatarMap = new HashMap<>();
    protected DisputeChatPopup chatPopup;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public DisputeView(DisputeManager<? extends DisputeList<Dispute>> disputeManager,
                       KeyRing keyRing,
                       P2PService p2PService,
                       TradeManager tradeManager,
                       CoinFormatter formatter,
                       Preferences preferences,
                       DisputeSummaryWindow disputeSummaryWindow,
                       PrivateNotificationManager privateNotificationManager,
                       ContractWindow contractWindow,
                       TradeDetailsWindow tradeDetailsWindow,
                       AccountAgeWitnessService accountAgeWitnessService,
                       MediatorManager mediatorManager,
                       RefundAgentManager refundAgentManager,
                       DaoFacade daoFacade,
                       boolean useDevPrivilegeKeys) {
        this.disputeManager = disputeManager;
        this.keyRing = keyRing;
        this.p2PService = p2PService;
        this.tradeManager = tradeManager;
        this.formatter = formatter;
        this.preferences = preferences;
        this.disputeSummaryWindow = disputeSummaryWindow;
        this.privateNotificationManager = privateNotificationManager;
        this.contractWindow = contractWindow;
        this.tradeDetailsWindow = tradeDetailsWindow;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.mediatorManager = mediatorManager;
        this.refundAgentManager = refundAgentManager;
        this.daoFacade = daoFacade;
        this.useDevPrivilegeKeys = useDevPrivilegeKeys;
        chatPopup = new DisputeChatPopup(disputeManager, formatter, preferences, this);
    }

    @Override
    public void initialize() {
        Label label = new AutoTooltipLabel(Res.get("support.filter"));
        HBox.setMargin(label, new Insets(5, 0, 0, 0));
        HBox.setHgrow(label, Priority.NEVER);

        filterTextField = new InputTextField();
        filterTextField.setPromptText(Res.get("support.filter.prompt"));
        Tooltip tooltip = new Tooltip();
        tooltip.setShowDelay(Duration.millis(100));
        tooltip.setShowDuration(Duration.seconds(10));
        filterTextField.setTooltip(tooltip);
        filterTextFieldListener = (observable, oldValue, newValue) -> applyFilteredListPredicate(filterTextField.getText());
        HBox.setHgrow(filterTextField, Priority.ALWAYS);

        alertIconLabel = new Label();
        Text icon = getIconForLabel(MaterialDesignIcon.ALERT_CIRCLE_OUTLINE, "2em", alertIconLabel);
        icon.getStyleClass().add("alert-icon");
        HBox.setMargin(alertIconLabel, new Insets(4, 0, 0, 10));
        alertIconLabel.setMouseTransparent(false);
        alertIconLabel.setVisible(false);
        alertIconLabel.setManaged(false);

        reOpenButton = new AutoTooltipButton(Res.get("support.reOpenButton.label"));
        reOpenButton.setDisable(true);
        reOpenButton.setVisible(false);
        reOpenButton.setManaged(false);
        HBox.setHgrow(reOpenButton, Priority.NEVER);
        reOpenButton.setOnAction(e -> {
            reOpenDisputeFromButton();
        });

        closeButton = new AutoTooltipButton(Res.get("support.closeTicket"));
        closeButton.setDisable(true);
        closeButton.setVisible(false);
        closeButton.setManaged(false);
        HBox.setHgrow(closeButton, Priority.NEVER);
        closeButton.setOnAction(e -> {
            closeDisputeFromButton();
        });

        sendPrivateNotificationButton = new AutoTooltipButton(Res.get("support.sendNotificationButton.label"));
        sendPrivateNotificationButton.setDisable(true);
        sendPrivateNotificationButton.setVisible(false);
        sendPrivateNotificationButton.setManaged(false);
        HBox.setHgrow(sendPrivateNotificationButton, Priority.NEVER);
        sendPrivateNotificationButton.setOnAction(e -> {
            sendPrivateNotification();
        });

        reportButton = new AutoTooltipButton(Res.get("support.reportButton.label"));
        reportButton.setVisible(false);
        reportButton.setManaged(false);
        HBox.setHgrow(reportButton, Priority.NEVER);
        reportButton.setOnAction(e -> {
            showCompactReport();
        });

        fullReportButton = new AutoTooltipButton(Res.get("support.fullReportButton.label"));
        fullReportButton.setVisible(false);
        fullReportButton.setManaged(false);
        HBox.setHgrow(fullReportButton, Priority.NEVER);
        fullReportButton.setOnAction(e -> {
            showFullReport();
        });

        sigCheckButton = new AutoTooltipButton(Res.get("support.sigCheck.button"));
        HBox.setHgrow(sigCheckButton, Priority.NEVER);
        sigCheckButton.setOnAction(e -> {
            new VerifyDisputeResultSignatureWindow(mediatorManager, refundAgentManager).show();
        });

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox filterBox = new HBox();
        filterBox.setSpacing(5);
        filterBox.getChildren().addAll(label,
                filterTextField,
                alertIconLabel,
                spacer,
                reOpenButton,
                closeButton,
                sendPrivateNotificationButton,
                reportButton,
                fullReportButton,
                sigCheckButton);
        VBox.setVgrow(filterBox, Priority.NEVER);

        tableView = new TableView<>();
        VBox.setVgrow(tableView, Priority.SOMETIMES);
        tableView.setMinHeight(150);

        root.getChildren().addAll(filterBox, tableView);

        setupTable();
    }

    @Override
    protected void activate() {
        filterTextField.textProperty().addListener(filterTextFieldListener);

        ObservableList<Dispute> disputesAsObservableList = disputeManager.getDisputesAsObservableList();
        filteredList = new FilteredList<>(disputesAsObservableList);
        applyFilteredListPredicate(filterTextField.getText());

        sortedList = new SortedList<>(filteredList);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);

        // double-click on a row opens chat window
        tableView.setRowFactory( tv -> {
            TableRow<Dispute> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    openChat(row.getItem());
                }
            });
            return row;
        });

        selectedDisputeSubscription = EasyBind.subscribe(tableView.getSelectionModel().selectedItemProperty(), this::onSelectDispute);

        Dispute selectedItem = tableView.getSelectionModel().getSelectedItem();
        if (selectedItem != null)
            tableView.getSelectionModel().select(selectedItem);
        else if (sortedList.size() > 0)
            tableView.getSelectionModel().select(0);

        GUIUtil.requestFocus(tableView);
    }

    @Override
    protected void deactivate() {
        filterTextField.textProperty().removeListener(filterTextFieldListener);
        sortedList.comparatorProperty().unbind();
        selectedDisputeSubscription.unsubscribe();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Reopen feature is only use in mediation from both mediator and traders
    protected void setupReOpenDisputeListener() {
        disputesListener = c -> {
            c.next();
            if (c.wasAdded()) {
                onDisputesAdded(c.getAddedSubList());
            } else if (c.wasRemoved()) {
                onDisputesRemoved(c.getRemoved());
            }
        };
    }

    // Reopen feature is only use in mediation from both mediator and traders
    protected void activateReOpenDisputeListener() {
        // Register listeners on all disputes for potential re-opening
        onDisputesAdded(disputeManager.getDisputesAsObservableList());
        disputeManager.getDisputesAsObservableList().addListener(disputesListener);

        disputeManager.getDisputesAsObservableList().forEach(dispute -> {
            if (dispute.isClosed()) {
                ObservableList<ChatMessage> chatMessages = dispute.getChatMessages();
                // If last message is not a result message we re-open as we might have received a new message from the
                // trader/mediator/arbitrator who has reopened the case
                if (!chatMessages.isEmpty() &&
                        !chatMessages.get(chatMessages.size() - 1).isResultMessage(dispute) &&
                        dispute.unreadMessageCount(senderFlag()) > 0) {
                    onSelectDispute(dispute);
                    reOpenDispute();
                }
            }
        });
    }

    // Reopen feature is only use in mediation from both mediator and traders
    protected void deactivateReOpenDisputeListener() {
        onDisputesRemoved(disputeManager.getDisputesAsObservableList());
        disputeManager.getDisputesAsObservableList().removeListener(disputesListener);
    }

    protected abstract SupportType getType();

    protected abstract DisputeSession getConcreteDisputeChatSession(Dispute dispute);

    protected abstract boolean senderFlag();    // implemented in the agent / client views

    protected void applyFilteredListPredicate(String filterString) {
        AtomicReference<FilterResult> filterResult = new AtomicReference<>(FilterResult.NO_FILTER);
        filteredList.setPredicate(dispute -> {
            filterResult.set(getFilterResult(dispute, filterString));
            return filterResult.get() != FilterResult.NO_MATCH;
        });

        if (filterResult.get() == FilterResult.NO_MATCH) {
            filterTextField.getTooltip().setText("No matches found");
        } else if (filterResult.get() == FilterResult.NO_FILTER) {
            filterTextField.getTooltip().setText("No filter applied");
        } else if (filterResult.get() == FilterResult.OPEN_DISPUTES) {
            filterTextField.getTooltip().setText("Show all open disputes");
        } else {
            filterTextField.getTooltip().setText("Data matching filter string: " + filterResult.get().getDisplayString());
        }
    }

    protected FilterResult getFilterResult(Dispute dispute, String filterTerm) {
        String filter = filterTerm.toLowerCase();
        if (filter.isEmpty()) {
            return FilterResult.NO_FILTER;
        }

        // For open filter we do not want to continue further as json data would cause a match
        if (filter.equalsIgnoreCase("open")) {
            return !dispute.isClosed() ? FilterResult.OPEN_DISPUTES : FilterResult.NO_MATCH;
        }

        if (dispute.getTradeId().toLowerCase().contains(filter)) {
            return FilterResult.TRADE_ID;
        }

        if (DisplayUtils.formatDate(dispute.getOpeningDate()).toLowerCase().contains(filter)) {
            return FilterResult.OPENING_DATE;
        }

        if (dispute.getContract().getBuyerNodeAddress().getFullAddress().contains(filter)) {
            return FilterResult.BUYER_NODE_ADDRESS;
        }

        if (dispute.getContract().getSellerNodeAddress().getFullAddress().contains(filter)) {
            return FilterResult.SELLER_NODE_ADDRESS;
        }

        if (dispute.getContract().getBuyerPaymentAccountPayload() != null &&
                dispute.getContract().getBuyerPaymentAccountPayload().getPaymentDetails().toLowerCase().contains(filter)) {
            return FilterResult.BUYER_ACCOUNT_DETAILS;
        }

        if (dispute.getContract().getSellerPaymentAccountPayload() != null &&
                dispute.getContract().getSellerPaymentAccountPayload().getPaymentDetails().toLowerCase().contains(filter)) {
            return FilterResult.SELLER_ACCOUNT_DETAILS;
        }

        if (dispute.getDepositTxId() != null && dispute.getDepositTxId().contains(filter)) {
            return FilterResult.DEPOSIT_TX;
        }
        if (dispute.getPayoutTxId() != null && dispute.getPayoutTxId().contains(filter)) {
            return FilterResult.PAYOUT_TX;
        }

        if (dispute.getDelayedPayoutTxId() != null && dispute.getDelayedPayoutTxId().contains(filter)) {
            return FilterResult.DEL_PAYOUT_TX;
        }

        DisputeResult disputeResult = dispute.getDisputeResultProperty().get();
        if (disputeResult != null) {
            ChatMessage chatMessage = disputeResult.getChatMessage();
            if (chatMessage != null && chatMessage.getMessage().toLowerCase().contains(filter)) {
                return FilterResult.RESULT_MESSAGE;
            }

            if (disputeResult.getReason().name().toLowerCase().contains(filter)) {
                return FilterResult.REASON;
            }
        }

        if (dispute.getContractAsJson().toLowerCase().contains(filter)) {
            return FilterResult.JSON;
        }

        return FilterResult.NO_MATCH;
    }

    // a derived version in the ClientView for users pops up an "Are you sure" box first.
    // this version includes the sending of an automatic message to the user, see addMediationReOpenedMessage
    protected void reOpenDisputeFromButton() {
        if (reOpenDispute()) {
            disputeManager.addMediationReOpenedMessage(selectedDispute, false);
        }
    }

    // only applicable to traders
    // only allow them to close the dispute if the trade is paid out
    // the reason for having this is that sometimes traders end up with closed disputes that are not "closed" @pazza
    protected void closeDisputeFromButton() {
        disputeManager.findTrade(selectedDispute).ifPresent(
                (trade) -> {
                    if (trade.isFundsLockedIn()) {
                        new Popup().warning(Res.get("support.warning.traderCloseOwnDisputeWarning")).show();
                    } else {
                        selectedDispute.setIsClosed();
                        disputeManager.requestPersistence();
                        onSelectDispute(selectedDispute);
                    }
                });
    }

    protected void handleOnProcessDispute(Dispute dispute) {
        // overridden by clients that use it (dispute agents)
    }

    protected boolean reOpenDispute() {
        if (selectedDispute != null &&
                selectedDispute.isClosed() &&
                isNodeAddressOk(selectedDispute,
                        !disputeManager.isTrader(selectedDispute))) {
            selectedDispute.reOpen();
            handleOnProcessDispute(selectedDispute);
            disputeManager.requestPersistence();
            onSelectDispute(selectedDispute);
            return true;
        } else {
            new Popup().warning(Res.get("support.reOpenByTrader.failed")).show();
            return false;
        }
    }

    private boolean isNodeAddressOk(Dispute dispute, boolean isMediator) {
        NodeAddress disputeNodeAddress = isMediator ? dispute.getContract().getMediatorNodeAddress() :
                dispute.getContract().getMyNodeAddress(keyRing.getPubKeyRing());

        if (OfferRestrictions.requiresNodeAddressUpdate() && !Utils.isV3Address(disputeNodeAddress.getHostName())) {
            return false;
        }

        return Objects.equals(p2PService.getNetworkNode().getNodeAddress(), disputeNodeAddress);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void onOpenContract(Dispute dispute) {
        dispute.setDisputeSeen(senderFlag());
        contractWindow.show(dispute);
    }

    private void onSelectDispute(Dispute dispute) {
        if (dispute == null) {
            selectedDispute = null;
        } else if (selectedDispute != dispute) {
            selectedDispute = dispute;
        }

        reOpenButton.setDisable(selectedDispute == null || !selectedDispute.isClosed());
        closeButton.setDisable(selectedDispute == null || selectedDispute.isClosed());
        sendPrivateNotificationButton.setDisable(selectedDispute == null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Reopen feature is only use in mediation from both mediator and traders
    private void onDisputesAdded(List<? extends Dispute> addedDisputes) {
        addedDisputes.forEach(dispute -> {
            ListChangeListener<ChatMessage> listener = c -> {
                c.next();
                if (c.wasAdded()) {
                    c.getAddedSubList().forEach(chatMessage -> {
                        if (dispute.isClosed()) {
                            if (chatMessage.isResultMessage(dispute)) {
                                onSelectDispute(null);
                            } else {
                                onSelectDispute(dispute);
                                reOpenDispute();
                            }
                        }
                    });
                }
                // We never remove chat messages so no remove listener
            };
            dispute.getChatMessages().addListener(listener);
            disputeChatMessagesListeners.put(dispute.getId(), listener);
        });
    }

    // Reopen feature is only use in mediation from both mediator and traders
    private void onDisputesRemoved(List<? extends Dispute> removedDisputes) {
        removedDisputes.forEach(dispute -> {
            String id = dispute.getId();
            if (disputeChatMessagesListeners.containsKey(id)) {
                ListChangeListener<ChatMessage> listener = disputeChatMessagesListeners.get(id);
                dispute.getChatMessages().removeListener(listener);
                disputeChatMessagesListeners.remove(id);
            }
        });
    }

    private void sendPrivateNotification() {
        if (selectedDispute != null) {
            PubKeyRing pubKeyRing = selectedDispute.getTraderPubKeyRing();
            NodeAddress nodeAddress;
            Contract contract = selectedDispute.getContract();
            if (pubKeyRing.equals(contract.getBuyerPubKeyRing())) {
                nodeAddress = contract.getBuyerNodeAddress();
            } else {
                nodeAddress = contract.getSellerNodeAddress();
            }

            new SendPrivateNotificationWindow(
                    privateNotificationManager,
                    pubKeyRing,
                    nodeAddress,
                    useDevPrivilegeKeys
            ).show();
        }
    }

    private void showCompactReport() {
        Map<String, List<Dispute>> map = new HashMap<>();
        Map<String, List<Dispute>> disputesByReason = new HashMap<>();
        disputeManager.getDisputesAsObservableList().forEach(dispute -> {
            String tradeId = dispute.getTradeId();
            List<Dispute> list;
            if (!map.containsKey(tradeId))
                map.put(tradeId, new ArrayList<>());

            list = map.get(tradeId);
            list.add(dispute);
        });

        List<List<Dispute>> allDisputes = new ArrayList<>();
        map.forEach((key, value) -> allDisputes.add(value));
        allDisputes.sort(Comparator.comparing(o -> !o.isEmpty() ? o.get(0).getOpeningDate() : new Date(0)));
        StringBuilder stringBuilder = new StringBuilder();
        StringBuilder csvStringBuilder = new StringBuilder();
        csvStringBuilder.append("Dispute nr").append(";")
                .append("Closed during cycle").append(";")
                .append("Status").append(";")
                .append("Trade date").append(";")
                .append("Trade ID").append(";")
                .append("Offer version").append(";")
                .append("Opening date").append(";")
                .append("Close date").append(";")
                .append("Duration").append(";")
                .append("Currency").append(";")
                .append("Trade amount").append(";")
                .append("Payment method").append(";")
                .append("Buyer account details").append(";")
                .append("Seller account details").append(";")
                .append("Buyer address").append(";")
                .append("Seller address").append(";")
                .append("Buyer security deposit").append(";")
                .append("Seller security deposit").append(";")
                .append("Dispute opened by").append(";")
                .append("Payout to buyer").append(";")
                .append("Payout to seller").append(";")
                .append("Winner").append(";")
                .append("Reason").append(";")
                .append("Summary notes").append(";")
                .append("Summary notes (other trader)");

        Map<Integer, Date> blockStartDateByCycleIndex = daoFacade.getBlockStartDateByCycleIndex();

        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy MM dd HH:mm:ss");
        AtomicInteger disputeIndex = new AtomicInteger();
        allDisputes.forEach(disputesPerTrade -> {
            if (disputesPerTrade.size() > 0) {
                Dispute firstDispute = disputesPerTrade.get(0);
                Date openingDate = firstDispute.getOpeningDate();
                Contract contract = firstDispute.getContract();
                String buyersRole = contract.isBuyerMakerAndSellerTaker() ? "Buyer as maker" : "Buyer as taker";
                String sellersRole = contract.isBuyerMakerAndSellerTaker() ? "Seller as taker" : "Seller as maker";
                String opener = firstDispute.isDisputeOpenerIsBuyer() ? buyersRole : sellersRole;
                DisputeResult disputeResult = firstDispute.getDisputeResultProperty().get();
                String winner = disputeResult != null &&
                        disputeResult.getWinner() == DisputeResult.Winner.BUYER ? "Buyer" : "Seller";
                String buyerPayoutAmount = disputeResult != null ? disputeResult.getBuyerPayoutAmount().toFriendlyString() : "";
                String sellerPayoutAmount = disputeResult != null ? disputeResult.getSellerPayoutAmount().toFriendlyString() : "";

                int index = disputeIndex.incrementAndGet();
                String tradeDateString = dateFormatter.format(firstDispute.getTradeDate());
                String openingDateString = dateFormatter.format(openingDate);

                // Index we display starts with 1 not with 0
                int cycleIndex = 0;
                if (disputeResult != null) {
                    Date closeDate = disputeResult.getCloseDate();
                    cycleIndex = blockStartDateByCycleIndex.entrySet().stream()
                            .filter(e -> e.getValue().after(closeDate))
                            .findFirst()
                            .map(Map.Entry::getKey)
                            .orElse(0);
                }
                stringBuilder.append("\n").append("Dispute nr.: ").append(index).append("\n");

                if (cycleIndex > 0) {
                    stringBuilder.append("Closed during cycle: ").append(cycleIndex).append("\n");
                }
                stringBuilder.append("Trade date: ").append(tradeDateString)
                        .append("\n")
                        .append("Opening date: ").append(openingDateString)
                        .append("\n");
                String tradeId = firstDispute.getTradeId();
                csvStringBuilder.append("\n").append(index).append(";");
                if (cycleIndex > 0) {
                    csvStringBuilder.append(cycleIndex).append(";");
                } else {
                    csvStringBuilder.append(";");
                }
                csvStringBuilder.append(firstDispute.isClosed() ? "Closed" : "Open").append(";")
                        .append(tradeDateString).append(";")
                        .append(firstDispute.getShortTradeId()).append(";")
                        .append(tradeId, tradeId.length() - 3, tradeId.length()).append(";")
                        .append(openingDateString).append(";");

                String summaryNotes = "";
                if (disputeResult != null) {
                    Date closeDate = disputeResult.getCloseDate();
                    long duration = closeDate.getTime() - openingDate.getTime();

                    String closeDateString = dateFormatter.format(closeDate);
                    String durationAsWords = FormattingUtils.formatDurationAsWords(duration);
                    stringBuilder.append("Close date: ").append(closeDateString).append("\n")
                            .append("Dispute duration: ").append(durationAsWords).append("\n");
                    csvStringBuilder.append(closeDateString).append(";")
                            .append(durationAsWords).append(";");
                } else {
                    csvStringBuilder.append(";").append(";");
                }

                String paymentMethod = Res.get(contract.getPaymentMethodId());
                String currency = CurrencyUtil.getNameAndCode(contract.getOfferPayload().getCurrencyCode());
                String tradeAmount = contract.getTradeAmount().toFriendlyString();
                String buyerDeposit = Coin.valueOf(contract.getOfferPayload().getBuyerSecurityDeposit()).toFriendlyString();
                String sellerDeposit = Coin.valueOf(contract.getOfferPayload().getSellerSecurityDeposit()).toFriendlyString();
                stringBuilder.append("Payment method: ")
                        .append(paymentMethod)
                        .append("\n")
                        .append("Currency: ")
                        .append(currency)
                        .append("\n")
                        .append("Trade amount: ")
                        .append(tradeAmount)
                        .append("\n")
                        .append("Buyer/seller security deposit: ")
                        .append(buyerDeposit)
                        .append("/")
                        .append(sellerDeposit)
                        .append("\n")
                        .append("Dispute opened by: ")
                        .append(opener)
                        .append("\n")
                        .append("Payout to buyer/seller (winner): ")
                        .append(buyerPayoutAmount).append("/")
                        .append(sellerPayoutAmount).append(" (")
                        .append(winner)
                        .append(")\n");

                String buyerPaymentAccountPayload = Utilities.toTruncatedString(
                        contract.getBuyerPaymentAccountPayload() != null ? contract.getBuyerPaymentAccountPayload().getPaymentDetails().
                                replace("\n", " ").replace(";", ".") : "NA", 100);
                String sellerPaymentAccountPayload = Utilities.toTruncatedString(
                        contract.getSellerPaymentAccountPayload() != null ? contract.getSellerPaymentAccountPayload().getPaymentDetails()
                                .replace("\n", " ").replace(";", ".") : "NA", 100);
                String buyerNodeAddress = contract.getBuyerNodeAddress().getFullAddress();
                String sellerNodeAddress = contract.getSellerNodeAddress().getFullAddress();
                csvStringBuilder.append(currency).append(";")
                        .append(tradeAmount.replace(" BTC", "")).append(";")
                        .append(paymentMethod).append(";")
                        .append(buyerPaymentAccountPayload).append(";")
                        .append(sellerPaymentAccountPayload).append(";")
                        .append(buyerNodeAddress.replace(".onion:9999", "")).append(";")
                        .append(sellerNodeAddress.replace(".onion:9999", "")).append(";")
                        .append(buyerDeposit.replace(" BTC", "")).append(";")
                        .append(sellerDeposit.replace(" BTC", "")).append(";")
                        .append(opener).append(";")
                        .append(buyerPayoutAmount.replace(" BTC", "")).append(";")
                        .append(sellerPayoutAmount.replace(" BTC", "")).append(";")
                        .append(winner).append(";");

                if (disputeResult != null) {
                    DisputeResult.Reason reason = disputeResult.getReason();
                    if (firstDispute.disputeResultProperty().get().getReason() != null) {
                        disputesByReason.putIfAbsent(reason.name(), new ArrayList<>());
                        disputesByReason.get(reason.name()).add(firstDispute);
                        stringBuilder.append("Reason: ")
                                .append(reason.name())
                                .append("\n");

                        csvStringBuilder.append(reason.name()).append(";");
                    } else {
                        csvStringBuilder.append(";");
                    }

                    summaryNotes = disputeResult.getSummaryNotesProperty().get();
                    stringBuilder.append("Summary notes: ").append(summaryNotes).append("\n");

                    csvStringBuilder.append(summaryNotes).append(";");
                } else {
                    csvStringBuilder.append(";");
                }

                // We might have a different summary notes at second trader. Only if it
                // is different we show it.
                if (disputesPerTrade.size() > 1) {
                    Dispute dispute1 = disputesPerTrade.get(1);
                    DisputeResult disputeResult1 = dispute1.getDisputeResultProperty().get();
                    if (disputeResult1 != null) {
                        String summaryNotes1 = disputeResult1.getSummaryNotesProperty().get();
                        if (!summaryNotes1.equals(summaryNotes)) {
                            stringBuilder.append("Summary notes (different message to other trader was used): ").append(summaryNotes1).append("\n");

                            csvStringBuilder.append(summaryNotes1).append(";");
                        } else {
                            csvStringBuilder.append(";");
                        }
                    }
                }
            }
        });
        stringBuilder.append("\n").append("Summary of reasons for disputes: ").append("\n");
        disputesByReason.forEach((k, v) -> {
            stringBuilder.append(k).append(": ").append(v.size()).append("\n");
        });

        String message = stringBuilder.toString();
        new Popup().headLine("Report for " + allDisputes.size() + " disputes")
                .maxMessageLength(500)
                .information(message)
                .width(1200)
                .actionButtonText("Copy to clipboard")
                .onAction(() -> Utilities.copyToClipboard(message))
                .secondaryActionButtonText("Copy as csv data")
                .onSecondaryAction(() -> Utilities.copyToClipboard(csvStringBuilder.toString()))
                .show();
    }

    private void showFullReport() {
        Map<String, List<Dispute>> map = new HashMap<>();
        disputeManager.getDisputesAsObservableList().forEach(dispute -> {
            String tradeId = dispute.getTradeId();
            List<Dispute> list;
            if (!map.containsKey(tradeId))
                map.put(tradeId, new ArrayList<>());

            list = map.get(tradeId);
            list.add(dispute);
        });
        List<List<Dispute>> disputeGroups = new ArrayList<>();
        map.forEach((key, value) -> disputeGroups.add(value));
        disputeGroups.sort(Comparator.comparing(o -> !o.isEmpty() ? o.get(0).getOpeningDate() : new Date(0)));
        StringBuilder stringBuilder = new StringBuilder();

        // We don't translate that as it is not intended for the public
        disputeGroups.forEach(disputeGroup -> {
            Dispute dispute0 = disputeGroup.get(0);
            stringBuilder.append("##########################################################################################/\n")
                    .append("## Trade ID: ")
                    .append(dispute0.getTradeId())
                    .append("\n")
                    .append("## Date: ")
                    .append(DisplayUtils.formatDateTime(dispute0.getOpeningDate()))
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
            disputeGroup.forEach(dispute -> {
                stringBuilder
                        .append("*******************************************************************************************\n")
                        .append("** Trader's ID: ")
                        .append(dispute.getTraderId())
                        .append("\n*******************************************************************************************\n")
                        .append("\n");
                dispute.getChatMessages().forEach(m -> {
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
        // We don't translate that as it is not intended for the public
        new Popup().headLine("Detailed text dump for " + disputeGroups.size() + " disputes")
                .maxMessageLength(1000)
                .information(message)
                .width(1200)
                .actionButtonText("Copy to clipboard")
                .onAction(() -> Utilities.copyToClipboard(message))
                .show();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void setupTable() {
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Label placeholder = new AutoTooltipLabel(Res.get("support.noTickets"));
        placeholder.setWrapText(true);
        tableView.setPlaceholder(placeholder);
        tableView.getSelectionModel().clearSelection();

        tableView.getColumns().add(getContractColumn());
        maybeAddProcessColumnsForAgent();   // agent view prefers action buttons on the left

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

        tableView.getColumns().add(getRoleColumn());

        maybeAddAgentColumn();

        stateColumn = getStateColumn();
        tableView.getColumns().add(stateColumn);

        // client view has the chat button to the right
        maybeAddChatColumnForClient();

        tradeIdColumn.setComparator(Comparator.comparing(Dispute::getTradeId));
        dateColumn.setComparator(Comparator.comparing(Dispute::getOpeningDate));
        buyerOnionAddressColumn.setComparator(Comparator.comparing(this::getBuyerOnionAddressColumnLabel));
        sellerOnionAddressColumn.setComparator(Comparator.comparing(this::getSellerOnionAddressColumnLabel));
        marketColumn.setComparator((o1, o2) -> CurrencyUtil.getCurrencyPair(o1.getContract().getOfferPayload().getCurrencyCode()).compareTo(o2.getContract().getOfferPayload().getCurrencyCode()));

        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(dateColumn);
    }

    protected void maybeAddProcessColumnsForAgent() {
        // Only relevant client views will impl it
    }

    protected void maybeAddChatColumnForClient() {
        // Only relevant client views will impl it
    }

    protected void maybeAddAgentColumn() {
        // Only relevant client views will impl it
    }

    // Relevant client views will override that
    protected NodeAddress getAgentNodeAddress(Contract contract) {
        return null;
    }

    private TableColumn<Dispute, Dispute> getContractColumn() {
        TableColumn<Dispute, Dispute> column = new AutoTooltipTableColumn<>(Res.get("shared.details")) {
            {
                setMaxWidth(80);
                setMinWidth(65);
                getStyleClass().addAll("first-column", "avatar-column");
                setSortable(false);
            }
        };
        column.setCellValueFactory((dispute) -> new ReadOnlyObjectWrapper<>(dispute.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<Dispute, Dispute> call(TableColumn<Dispute, Dispute> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final Dispute item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    Button button = getRegularIconButton(MaterialDesignIcon.INFORMATION_OUTLINE);
                                    JFXBadge badge = new JFXBadge(new Label(""), Pos.BASELINE_RIGHT);
                                    badge.setPosition(Pos.TOP_RIGHT);
                                    badge.setVisible(item.isNew());
                                    badge.setText("New");
                                    badge.getStyleClass().add("new");
                                    newBadgeByDispute.put(item.getId(), badge);
                                    HBox hBox = new HBox(button, badge);
                                    setGraphic(hBox);
                                    button.setOnAction(e -> {
                                        tableView.getSelectionModel().select(this.getIndex());
                                        onOpenContract(item);
                                        item.setDisputeSeen(senderFlag());
                                        badge.setVisible(item.isNew());
                                    });
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
        return column;
    }

    protected TableColumn<Dispute, Dispute> getProcessColumn() {
        TableColumn<Dispute, Dispute> column = new AutoTooltipTableColumn<>(Res.get("support.process")) {
            {
                setMaxWidth(50);
                setMinWidth(50);
                getStyleClass().addAll("avatar-column");
                setSortable(false);
            }
        };
        column.setCellValueFactory((dispute) -> new ReadOnlyObjectWrapper<>(dispute.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<Dispute, Dispute> call(TableColumn<Dispute, Dispute> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final Dispute item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    Button button = getRegularIconButton(MaterialDesignIcon.GAVEL);
                                    button.setOnAction(e -> {
                                        tableView.getSelectionModel().select(this.getIndex());
                                        handleOnProcessDispute(item);
                                        item.setDisputeSeen(senderFlag());
                                        newBadgeByDispute.get(item.getId()).setVisible(item.isNew());
                                    });
                                    HBox hBox = new HBox(button);
                                    hBox.setAlignment(Pos.CENTER);
                                    setGraphic(hBox);
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
        return column;
    }

    protected TableColumn<Dispute, Dispute> getChatColumn() {
        TableColumn<Dispute, Dispute> column = new AutoTooltipTableColumn<>(Res.get("support.chat")) {
            {
                setMaxWidth(40);
                setMinWidth(40);
                getStyleClass().addAll("avatar-column");
                setSortable(false);
            }
        };
        column.setCellValueFactory((dispute) -> new ReadOnlyObjectWrapper<>(dispute.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<Dispute, Dispute> call(TableColumn<Dispute, Dispute> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final Dispute item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    String id = item.getId();
                                    Button button;
                                    if (!chatButtonByDispute.containsKey(id)) {
                                        button = FormBuilder.getIconButton(MaterialDesignIcon.COMMENT_MULTIPLE_OUTLINE);
                                        chatButtonByDispute.put(id, button);
                                        button.setTooltip(new Tooltip(Res.get("tradeChat.openChat")));
                                    } else {
                                        button = chatButtonByDispute.get(id);
                                    }
                                    JFXBadge chatBadge;
                                    if (!chatBadgeByDispute.containsKey(id)) {
                                        chatBadge = new JFXBadge(button);
                                        chatBadgeByDispute.put(id, chatBadge);
                                        chatBadge.setPosition(Pos.TOP_RIGHT);
                                    } else {
                                        chatBadge = chatBadgeByDispute.get(id);
                                    }
                                    button.setOnAction(e -> {
                                        tableView.getSelectionModel().select(this.getIndex());
                                        openChat(item);
                                    });
                                    if (!listenerByDispute.containsKey(id)) {
                                        ListChangeListener<ChatMessage> listener = c -> updateChatMessageCount(item, chatBadge);
                                        listenerByDispute.put(id, listener);
                                        item.getChatMessages().addListener(listener);
                                    }
                                    updateChatMessageCount(item, chatBadge);
                                    setGraphic(chatBadge);
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<Dispute, Dispute> getDateColumn() {
        TableColumn<Dispute, Dispute> column = new AutoTooltipTableColumn<>(Res.get("shared.date")) {
            {
                setMinWidth(180);
            }
        };
        column.setCellValueFactory((dispute) -> new ReadOnlyObjectWrapper<>(dispute.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<Dispute, Dispute> call(TableColumn<Dispute, Dispute> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final Dispute item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(DisplayUtils.formatDateTime(item.getOpeningDate()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<Dispute, Dispute> getTradeIdColumn() {
        TableColumn<Dispute, Dispute> column = new AutoTooltipTableColumn<>(Res.get("shared.tradeId")) {
            {
                setMinWidth(110);
            }
        };
        column.setCellValueFactory((dispute) -> new ReadOnlyObjectWrapper<>(dispute.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<Dispute, Dispute> call(TableColumn<Dispute, Dispute> column) {
                        return new TableCell<>() {
                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final Dispute item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    Optional<Trade> tradeOptional = tradeManager.getTradeById(item.getTradeId());
                                    if (tradeOptional.isPresent()) {
                                        field = new HyperlinkWithIcon(item.getShortTradeId());
                                        field.setMouseTransparent(false);
                                        field.setTooltip(new Tooltip(Res.get("tooltip.openPopupForDetails")));
                                        field.setOnAction(event -> tradeDetailsWindow.show(tradeOptional.get()));
                                    } else {
                                        setText(item.getShortTradeId());
                                    }
                                    setGraphic(field);
                                } else {
                                    setGraphic(null);
                                    setText("");
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
        TableColumn<Dispute, Dispute> column = new AutoTooltipTableColumn<>(Res.get("support.buyerAddress")) {
            {
                setMinWidth(160);
            }
        };
        column.setCellValueFactory((dispute) -> new ReadOnlyObjectWrapper<>(dispute.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<Dispute, Dispute> call(TableColumn<Dispute, Dispute> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final Dispute item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setText(getBuyerOnionAddressColumnLabel(item));
                                    PeerInfoIconDispute peerInfoIconDispute = findOrCreateAvatar(tableRowProperty().get().getIndex(), item.getContract(), true);
                                    setGraphic(peerInfoIconDispute);
                                } else {
                                    setText("");
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<Dispute, Dispute> getSellerOnionAddressColumn() {
        TableColumn<Dispute, Dispute> column = new AutoTooltipTableColumn<>(Res.get("support.sellerAddress")) {
            {
                setMinWidth(160);
            }
        };
        column.setCellValueFactory((dispute) -> new ReadOnlyObjectWrapper<>(dispute.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<Dispute, Dispute> call(TableColumn<Dispute, Dispute> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final Dispute item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setText(getSellerOnionAddressColumnLabel(item));
                                    PeerInfoIconDispute peerInfoIconDispute = findOrCreateAvatar(tableRowProperty().get().getIndex(), item.getContract(), false);
                                    setGraphic(peerInfoIconDispute);
                                } else {
                                    setText("");
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
        return column;
    }


    private String getBuyerOnionAddressColumnLabel(Dispute item) {
        Contract contract = item.getContract();
        if (contract != null) {
            NodeAddress buyerNodeAddress = contract.getBuyerNodeAddress();
            if (buyerNodeAddress != null) {
                String nrOfDisputes = disputeManager.getNrOfDisputes(true, contract);
                long accountAge = accountAgeWitnessService.getAccountAge(contract.getBuyerPaymentAccountPayload(), contract.getBuyerPubKeyRing());
                String age = DisplayUtils.formatAccountAge(accountAge);
                String postFix = CurrencyUtil.isFiatCurrency(item.getContract().getOfferPayload().getCurrencyCode()) ? " / " + age : "";
                return buyerNodeAddress.getHostNameForDisplay() + " (" + nrOfDisputes + postFix + ")";
            } else
                return Res.get("shared.na");
        } else {
            return Res.get("shared.na");
        }
    }

    private String getSellerOnionAddressColumnLabel(Dispute item) {
        Contract contract = item.getContract();
        if (contract != null) {
            NodeAddress sellerNodeAddress = contract.getSellerNodeAddress();
            if (sellerNodeAddress != null) {
                String nrOfDisputes = disputeManager.getNrOfDisputes(false, contract);
                long accountAge = accountAgeWitnessService.getAccountAge(contract.getSellerPaymentAccountPayload(), contract.getSellerPubKeyRing());
                String age = DisplayUtils.formatAccountAge(accountAge);
                String postFix = CurrencyUtil.isFiatCurrency(item.getContract().getOfferPayload().getCurrencyCode()) ? " / " + age : "";
                return sellerNodeAddress.getHostNameForDisplay() + " (" + nrOfDisputes + postFix + ")";
            } else
                return Res.get("shared.na");
        } else {
            return Res.get("shared.na");
        }
    }

    private TableColumn<Dispute, Dispute> getMarketColumn() {
        TableColumn<Dispute, Dispute> column = new AutoTooltipTableColumn<>(Res.get("shared.market")) {
            {
                setMinWidth(80);
            }
        };
        column.setCellValueFactory((dispute) -> new ReadOnlyObjectWrapper<>(dispute.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<Dispute, Dispute> call(TableColumn<Dispute, Dispute> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final Dispute item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(CurrencyUtil.getCurrencyPair(item.getContract().getOfferPayload().getCurrencyCode()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<Dispute, Dispute> getRoleColumn() {
        TableColumn<Dispute, Dispute> column = new AutoTooltipTableColumn<>(Res.get("support.role")) {
            {
                setMinWidth(130);
            }
        };
        column.setCellValueFactory((dispute) -> new ReadOnlyObjectWrapper<>(dispute.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<Dispute, Dispute> call(TableColumn<Dispute, Dispute> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final Dispute item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setText(item.getRoleString());
                                } else {
                                    setText("");
                                }
                            }
                        };
                    }
                });
        return column;
    }

    protected TableColumn<Dispute, Dispute> getAgentColumn() {
        TableColumn<Dispute, Dispute> column = new AutoTooltipTableColumn<>(Res.get("support.agent")) {
            {
                setMinWidth(70);
            }
        };
        column.setCellValueFactory((dispute) -> new ReadOnlyObjectWrapper<>(dispute.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<Dispute, Dispute> call(TableColumn<Dispute, Dispute> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final Dispute item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    NodeAddress agentNodeAddress = getAgentNodeAddress(item.getContract());
                                    if (agentNodeAddress == null) {
                                        setText(Res.get("shared.na"));
                                        return;
                                    }

                                    String MatrixUserName = DisputeAgentLookupMap.getMatrixUserName(agentNodeAddress.getFullAddress());
                                    setText(MatrixUserName);
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
        TableColumn<Dispute, Dispute> column = new AutoTooltipTableColumn<>(Res.get("support.state")) {
            {
                setMinWidth(50);
            }
        };
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((dispute) -> new ReadOnlyObjectWrapper<>(dispute.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<Dispute, Dispute> call(TableColumn<Dispute, Dispute> column) {
                        return new TableCell<>() {


                            ReadOnlyBooleanProperty closedProperty;
                            ChangeListener<Boolean> listener;

                            @Override
                            public void updateItem(final Dispute item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    if (closedProperty != null) {
                                        closedProperty.removeListener(listener);
                                    }

                                    listener = (observable, oldValue, newValue) -> {
                                        setText(newValue ? Res.get("support.closed") : Res.get("support.open"));
                                        if (getTableRow() != null)
                                            getTableRow().setOpacity(newValue && item.getBadgeCountProperty().get() == 0 ? 0.4 : 1);
                                        if (item.isClosed() && item == chatPopup.getSelectedDispute())
                                            chatPopup.closeChat(); // close the chat popup when the associated ticket is closed
                                    };
                                    closedProperty = item.isClosedProperty();
                                    closedProperty.addListener(listener);
                                    boolean isClosed = item.isClosed();
                                    setText(isClosed ? Res.get("support.closed") : Res.get("support.open"));
                                    if (getTableRow() != null)
                                        getTableRow().setOpacity(isClosed && item.getBadgeCountProperty().get() == 0  ? 0.4 : 1);
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

    private void openChat(Dispute dispute) {
        chatPopup.openChat(dispute, getConcreteDisputeChatSession(dispute), getCounterpartyName());
        dispute.setDisputeSeen(senderFlag());
        newBadgeByDispute.get(dispute.getId()).setVisible(dispute.isNew());
        updateChatMessageCount(dispute, chatBadgeByDispute.get(dispute.getId()));
    }

    private void updateChatMessageCount(Dispute dispute, JFXBadge chatBadge) {
        if (chatBadge == null)
            return;
        // when the chat popup is active, we do not display new message count indicator for that item
        if (chatPopup.isChatShown() && selectedDispute != null && dispute.getId().equals(selectedDispute.getId())) {
            chatBadge.setText("");
            chatBadge.setEnabled(false);
            chatBadge.refreshBadge();
            // have to UserThread.execute or the new message will be sent to peer as "read"
            UserThread.execute(() -> dispute.setChatMessagesSeen(senderFlag()));
            return;
        }

        if (dispute.unreadMessageCount(senderFlag()) > 0) {
            chatBadge.setText(String.valueOf(dispute.unreadMessageCount(senderFlag())));
            chatBadge.setEnabled(true);
        } else {
            chatBadge.setText("");
            chatBadge.setEnabled(false);
        }
        chatBadge.refreshBadge();
        dispute.refreshAlertLevel(senderFlag());
    }

    private String getCounterpartyName() {
        if (senderFlag()) {
            return Res.get("offerbook.trader");
        } else {
            return (disputeManager instanceof MediationManager) ? Res.get("shared.mediator") : Res.get("shared.refundAgent");
        }
    }

    private PeerInfoIconDispute findOrCreateAvatar(Integer tableRowId, Contract contract, boolean isBuyer) {
        NodeAddress nodeAddress = isBuyer ? contract.getBuyerNodeAddress() : contract.getSellerNodeAddress();
        String key = tableRowId + nodeAddress.getHostNameWithoutPostFix() + (isBuyer ? "BUYER" : "SELLER");
        Long accountAge = isBuyer ?
                accountAgeWitnessService.getAccountAge(contract.getBuyerPaymentAccountPayload(), contract.getBuyerPubKeyRing()) :
                accountAgeWitnessService.getAccountAge(contract.getSellerPaymentAccountPayload(), contract.getSellerPubKeyRing());
        PeerInfoIconDispute peerInfoIcon = new PeerInfoIconDispute(
                nodeAddress,
                disputeManager.getNrOfDisputes(isBuyer, contract),
                accountAge,
                preferences);
        peerInfoIcon.setCallback(this);
        avatarMap.put(key, peerInfoIcon);
        return peerInfoIcon;
    }

    @Override
    public void avatarTagUpdated() {
        // callback from one avatar letting us know that the user updated the tag text.
        // we update all avatars, as some could be sharing the same tag
        log.info("Updating avatar tags, the avatarMap size is {}", avatarMap.size());
        avatarMap.forEach((key, avatarIcon) -> {
            avatarIcon.refreshTag();
        });
    }

    @Override
    public void onCloseDisputeFromChatWindow(Dispute dispute) {
        handleOnProcessDispute(dispute);
    }

    @Override
    public void onSendLogsFromChatWindow(Dispute dispute) {
        if (!(disputeManager instanceof MediationManager))
            return;
        MediationManager mediationManager = (MediationManager) disputeManager;
        new SendLogFilesWindow(dispute.getTradeId(), dispute.getTraderId(), mediationManager).show();
    }
}
