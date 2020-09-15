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
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.ContractWindow;
import bisq.desktop.main.overlays.windows.DisputeSummaryWindow;
import bisq.desktop.main.overlays.windows.SendPrivateNotificationWindow;
import bisq.desktop.main.overlays.windows.TradeDetailsWindow;
import bisq.desktop.main.shared.ChatView;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.GUIUtil;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.alert.PrivateNotificationManager;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeList;
import bisq.core.support.dispute.DisputeManager;
import bisq.core.support.dispute.DisputeResult;
import bisq.core.support.dispute.DisputeSession;
import bisq.core.support.messages.ChatMessage;
import bisq.core.trade.Contract;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeManager;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import com.google.common.collect.Lists;

import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import javafx.geometry.Insets;

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

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.Getter;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.getIconForLabel;

public abstract class DisputeView extends ActivatableView<VBox, Void> {

    protected final DisputeManager<? extends DisputeList<Dispute>> disputeManager;
    protected final KeyRing keyRing;
    private final TradeManager tradeManager;
    protected final CoinFormatter formatter;
    protected final DisputeSummaryWindow disputeSummaryWindow;
    private final PrivateNotificationManager privateNotificationManager;
    private final ContractWindow contractWindow;
    private final TradeDetailsWindow tradeDetailsWindow;

    private final AccountAgeWitnessService accountAgeWitnessService;
    private final boolean useDevPrivilegeKeys;

    protected TableView<Dispute> tableView;
    private SortedList<Dispute> sortedList;

    @Getter
    protected Dispute selectedDispute;

    protected ChatView chatView;

    private ChangeListener<Boolean> selectedDisputeClosedPropertyListener;
    private Subscription selectedDisputeSubscription;
    protected FilteredList<Dispute> filteredList;
    protected InputTextField filterTextField;
    private ChangeListener<String> filterTextFieldListener;
    protected AutoTooltipButton reOpenButton, sendPrivateNotificationButton, reportButton, fullReportButton;
    private Map<String, ListChangeListener<ChatMessage>> disputeChatMessagesListeners = new HashMap<>();
    @Nullable
    private ListChangeListener<Dispute> disputesListener; // Only set in mediation cases
    protected Label alertIconLabel;
    protected TableColumn<Dispute, Dispute> stateColumn;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public DisputeView(DisputeManager<? extends DisputeList<Dispute>> disputeManager,
                       KeyRing keyRing,
                       TradeManager tradeManager,
                       CoinFormatter formatter,
                       DisputeSummaryWindow disputeSummaryWindow,
                       PrivateNotificationManager privateNotificationManager,
                       ContractWindow contractWindow,
                       TradeDetailsWindow tradeDetailsWindow,
                       AccountAgeWitnessService accountAgeWitnessService,
                       boolean useDevPrivilegeKeys) {
        this.disputeManager = disputeManager;
        this.keyRing = keyRing;
        this.tradeManager = tradeManager;
        this.formatter = formatter;
        this.disputeSummaryWindow = disputeSummaryWindow;
        this.privateNotificationManager = privateNotificationManager;
        this.contractWindow = contractWindow;
        this.tradeDetailsWindow = tradeDetailsWindow;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.useDevPrivilegeKeys = useDevPrivilegeKeys;
    }

    @Override
    public void initialize() {
        Label label = new AutoTooltipLabel(Res.get("support.filter"));
        HBox.setMargin(label, new Insets(5, 0, 0, 0));
        HBox.setHgrow(label, Priority.NEVER);

        filterTextField = new InputTextField();
        filterTextFieldListener = (observable, oldValue, newValue) -> applyFilteredListPredicate(filterTextField.getText());
        HBox.setHgrow(filterTextField, Priority.NEVER);

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

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox filterBox = new HBox();
        filterBox.setSpacing(5);
        filterBox.getChildren().addAll(label,
                filterTextField,
                alertIconLabel,
                spacer,
                reOpenButton,
                sendPrivateNotificationButton,
                reportButton,
                fullReportButton);
        VBox.setVgrow(filterBox, Priority.NEVER);

        tableView = new TableView<>();
        VBox.setVgrow(tableView, Priority.SOMETIMES);
        tableView.setMinHeight(150);

        root.getChildren().addAll(filterBox, tableView);

        setupTable();

        selectedDisputeClosedPropertyListener = (observable, oldValue, newValue) -> chatView.setInputBoxVisible(!newValue);

        chatView = new ChatView(disputeManager, formatter);
        chatView.initialize();
    }

    @Override
    protected void activate() {
        filterTextField.textProperty().addListener(filterTextFieldListener);

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
        else if (sortedList.size() > 0)
            tableView.getSelectionModel().select(0);

        if (chatView != null) {
            chatView.activate();
            chatView.scrollToBottom();
        }


        // If doPrint=true we print out a html page which opens tabs with all deposit txs
        // (firefox needs about:config change to allow > 20 tabs)
        // Useful to check if there any funds in not finished trades (no payout tx done).
        // Last check 10.02.2017 found 8 trades and we contacted all traders as far as possible (email if available
        // otherwise in-app private notification)
        boolean doPrint = false;
        //noinspection ConstantConditions
        if (doPrint) {
            try {
                DateFormat formatter = new SimpleDateFormat("dd/MM/yy");
                //noinspection UnusedAssignment
                Date startDate = formatter.parse("10/02/17");
                startDate = new Date(0); // print all from start

                HashMap<String, Dispute> map = new HashMap<>();
                disputeManager.getDisputesAsObservableList().forEach(dispute -> map.put(dispute.getDepositTxId(), dispute));

                final Date finalStartDate = startDate;
                List<Dispute> disputes = new ArrayList<>(map.values());
                disputes.sort(Comparator.comparing(Dispute::getOpeningDate));
                List<List<Dispute>> subLists = Lists.partition(disputes, 1000);
                StringBuilder sb = new StringBuilder();
                // We don't translate that as it is not intended for the public
                subLists.forEach(list -> {
                    StringBuilder sb1 = new StringBuilder("\n<html><head><script type=\"text/javascript\">function load(){\n");
                    StringBuilder sb2 = new StringBuilder("\n}</script></head><body onload=\"load()\">\n");
                    list.forEach(dispute -> {
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
        GUIUtil.requestFocus(filterTextField);
    }

    @Override
    protected void deactivate() {
        filterTextField.textProperty().removeListener(filterTextFieldListener);
        sortedList.comparatorProperty().unbind();
        selectedDisputeSubscription.unsubscribe();
        removeListenersOnSelectDispute();

        if (chatView != null)
            chatView.deactivate();
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
                if (!chatMessages.isEmpty() && !chatMessages.get(chatMessages.size() - 1).isResultMessage(dispute)) {
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

    protected void applyFilteredListPredicate(String filterString) {
        // If in trader view we must not display arbitrators own disputes as trader (must not happen anyway)
        filteredList.setPredicate(dispute -> !dispute.getAgentPubKeyRing().equals(keyRing.getPubKeyRing()));
    }

    protected void reOpenDisputeFromButton() {
        reOpenDispute();
    }

    protected abstract void handleOnSelectDispute(Dispute dispute);

    protected void onCloseDispute(Dispute dispute) {
        long protocolVersion = dispute.getContract().getOfferPayload().getProtocolVersion();
        if (protocolVersion == Version.TRADE_PROTOCOL_VERSION) {
            disputeSummaryWindow.onFinalizeDispute(() -> chatView.removeInputBox())
                    .show(dispute);
        } else {
            new Popup().warning(Res.get("support.wrongVersion", protocolVersion)).show();
        }
    }

    protected void reOpenDispute() {
        if (selectedDispute != null) {
            selectedDispute.setIsClosed(false);
            handleOnSelectDispute(selectedDispute);
            disputeManager.getStorage().queueUpForSave();
        }
    }

    protected boolean anyMatchOfFilterString(Dispute dispute, String filterString) {
        boolean matchesTradeId = dispute.getTradeId().contains(filterString);
        boolean matchesDate = DisplayUtils.formatDate(dispute.getOpeningDate()).contains(filterString);
        boolean isBuyerOnion = dispute.getContract().getBuyerNodeAddress().getFullAddress().contains(filterString);
        boolean isSellerOnion = dispute.getContract().getSellerNodeAddress().getFullAddress().contains(filterString);
        boolean matchesBuyersPaymentAccountData = dispute.getContract().getBuyerPaymentAccountPayload().getPaymentDetails().contains(filterString);
        boolean matchesSellersPaymentAccountData = dispute.getContract().getSellerPaymentAccountPayload().getPaymentDetails().contains(filterString);
        return matchesTradeId || matchesDate || isBuyerOnion || isSellerOnion ||
                matchesBuyersPaymentAccountData || matchesSellersPaymentAccountData;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onOpenContract(Dispute dispute) {
        contractWindow.show(dispute);
    }

    private void removeListenersOnSelectDispute() {
        if (selectedDispute != null) {
            if (selectedDisputeClosedPropertyListener != null)
                selectedDispute.isClosedProperty().removeListener(selectedDisputeClosedPropertyListener);
        }
    }

    private void addListenersOnSelectDispute() {
        if (selectedDispute != null)
            selectedDispute.isClosedProperty().addListener(selectedDisputeClosedPropertyListener);
    }

    private void onSelectDispute(Dispute dispute) {
        removeListenersOnSelectDispute();
        if (dispute == null) {
            if (root.getChildren().size() > 2) {
                root.getChildren().remove(2);
            }

            selectedDispute = null;
        } else if (selectedDispute != dispute) {
            selectedDispute = dispute;
            if (chatView != null) {
                handleOnSelectDispute(dispute);
            }

            if (root.getChildren().size() > 2) {
                root.getChildren().remove(2);
            }
            root.getChildren().add(2, chatView);
        }

        reOpenButton.setDisable(selectedDispute == null || !selectedDispute.isClosed());
        sendPrivateNotificationButton.setDisable(selectedDispute == null);

        addListenersOnSelectDispute();
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
                stringBuilder.append("\n")
                        .append("Dispute nr. ")
                        .append(disputeIndex.incrementAndGet())
                        .append("\n")
                        .append("Opening date: ")
                        .append(DisplayUtils.formatDateTime(openingDate))
                        .append("\n");
                String summaryNotes0 = "";
                if (disputeResult != null) {
                    Date closeDate = disputeResult.getCloseDate();
                    long duration = closeDate.getTime() - openingDate.getTime();
                    stringBuilder.append("Close date: ")
                            .append(DisplayUtils.formatDateTime(closeDate))
                            .append("\n")
                            .append("Dispute duration: ")
                            .append(FormattingUtils.formatDurationAsWords(duration))
                            .append("\n");
                }

                stringBuilder.append("Payment method: ")
                        .append(Res.get(contract.getPaymentMethodId()))
                        .append("\n")
                        .append("Currency: ")
                        .append(CurrencyUtil.getNameAndCode(contract.getOfferPayload().getCurrencyCode()))
                        .append("\n")
                        .append("Trade amount: ")
                        .append(contract.getTradeAmount().toFriendlyString())
                        .append("\n")
                        .append("Buyer/seller security deposit: ")
                        .append(Coin.valueOf(contract.getOfferPayload().getBuyerSecurityDeposit()).toFriendlyString())
                        .append("/")
                        .append(Coin.valueOf(contract.getOfferPayload().getSellerSecurityDeposit()).toFriendlyString())
                        .append("\n")
                        .append("Dispute opened by: ")
                        .append(opener)
                        .append("\n")
                        .append("Payout to buyer/seller (winner): ")
                        .append(buyerPayoutAmount).append("/")
                        .append(sellerPayoutAmount).append(" (")
                        .append(winner)
                        .append(")\n");

                if (disputeResult != null) {
                    DisputeResult.Reason reason = disputeResult.getReason();
                    if (firstDispute.disputeResultProperty().get().getReason() != null) {
                        disputesByReason.putIfAbsent(reason.name(), new ArrayList<>());
                        disputesByReason.get(reason.name()).add(firstDispute);
                        stringBuilder.append("Reason: ")
                                .append(reason.name())
                                .append("\n");
                    }

                    summaryNotes0 = disputeResult.getSummaryNotesProperty().get();
                    stringBuilder.append("Summary notes: ").append(summaryNotes0).append("\n");
                }

                // We might have a different summary notes at second trader. Only if it
                // is different we show it.
                if (disputesPerTrade.size() > 1) {
                    Dispute dispute1 = disputesPerTrade.get(1);
                    DisputeResult disputeResult1 = dispute1.getDisputeResultProperty().get();
                    if (disputeResult1 != null) {
                        String summaryNotes1 = disputeResult1.getSummaryNotesProperty().get();
                        if (!summaryNotes1.equals(summaryNotes0)) {
                            stringBuilder.append("Summary notes (different message to other trader was used): ").append(summaryNotes1).append("\n");
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

        stateColumn = getStateColumn();
        tableView.getColumns().add(stateColumn);

        tradeIdColumn.setComparator(Comparator.comparing(Dispute::getTradeId));
        dateColumn.setComparator(Comparator.comparing(Dispute::getOpeningDate));
        buyerOnionAddressColumn.setComparator(Comparator.comparing(this::getBuyerOnionAddressColumnLabel));
        sellerOnionAddressColumn.setComparator(Comparator.comparing(this::getSellerOnionAddressColumnLabel));
        marketColumn.setComparator((o1, o2) -> CurrencyUtil.getCurrencyPair(o1.getContract().getOfferPayload().getCurrencyCode()).compareTo(o2.getContract().getOfferPayload().getCurrencyCode()));

        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(dateColumn);
    }

    private TableColumn<Dispute, Dispute> getSelectColumn() {
        TableColumn<Dispute, Dispute> column = new AutoTooltipTableColumn<>(Res.get("shared.select"));
        column.setMinWidth(80);
        column.setMaxWidth(80);
        column.setSortable(false);
        column.getStyleClass().add("first-column");

        column.setCellValueFactory((addressListItem) ->
                new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        column.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<Dispute, Dispute> call(TableColumn<Dispute,
                            Dispute> column) {
                        return new TableCell<>() {

                            Button button;

                            @Override
                            public void updateItem(final Dispute item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    if (button == null) {
                                        button = new AutoTooltipButton(Res.get("shared.select"));
                                        setGraphic(button);
                                    }
                                    button.setOnAction(e -> tableView.getSelectionModel().select(item));
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
        TableColumn<Dispute, Dispute> column = new AutoTooltipTableColumn<>(Res.get("shared.details")) {
            {
                setMinWidth(80);
                setSortable(false);
            }
        };
        column.setCellValueFactory((dispute) -> new ReadOnlyObjectWrapper<>(dispute.getValue()));
        column.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<Dispute, Dispute> call(TableColumn<Dispute, Dispute> column) {
                        return new TableCell<>() {
                            Button button;

                            @Override
                            public void updateItem(final Dispute item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    if (button == null) {
                                        button = new AutoTooltipButton(Res.get("shared.details"));
                                        setGraphic(button);
                                    }
                                    button.setOnAction(e -> onOpenContract(item));
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
                                    field = new HyperlinkWithIcon(item.getShortTradeId());
                                    Optional<Trade> tradeOptional = tradeManager.getTradeById(item.getTradeId());
                                    if (tradeOptional.isPresent()) {
                                        field.setMouseTransparent(false);
                                        field.setTooltip(new Tooltip(Res.get("tooltip.openPopupForDetails")));
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
        TableColumn<Dispute, Dispute> column = new AutoTooltipTableColumn<>(Res.get("support.buyerAddress")) {
            {
                setMinWidth(190);
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
        TableColumn<Dispute, Dispute> column = new AutoTooltipTableColumn<>(Res.get("support.sellerAddress")) {
            {
                setMinWidth(190);
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
                                    setText(getSellerOnionAddressColumnLabel(item));
                                else
                                    setText("");
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
                return buyerNodeAddress.getHostNameWithoutPostFix() + " (" + nrOfDisputes + postFix + ")";
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
                return sellerNodeAddress.getHostNameWithoutPostFix() + " (" + nrOfDisputes + postFix + ")";
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
                                    if (item.isDisputeOpenerIsMaker())
                                        setText(item.isDisputeOpenerIsBuyer() ? Res.get("support.buyerOfferer") : Res.get("support.sellerOfferer"));
                                    else
                                        setText(item.isDisputeOpenerIsBuyer() ? Res.get("support.buyerTaker") : Res.get("support.sellerTaker"));
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
                                            getTableRow().setOpacity(newValue ? 0.4 : 1);
                                    };
                                    closedProperty = item.isClosedProperty();
                                    closedProperty.addListener(listener);
                                    boolean isClosed = item.isClosed();
                                    setText(isClosed ? Res.get("support.closed") : Res.get("support.open"));
                                    if (getTableRow() != null)
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
}


