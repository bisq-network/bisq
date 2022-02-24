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

package bisq.core.support.dispute;

import bisq.core.locale.Res;
import bisq.core.proto.CoreProtoResolver;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.mediation.FileTransferReceiver;
import bisq.core.support.dispute.mediation.FileTransferSender;
import bisq.core.support.dispute.mediation.FileTransferSession;
import bisq.core.support.messages.ChatMessage;
import bisq.core.trade.model.bisq_v1.Contract;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.NetworkNode;

import bisq.common.crypto.PubKeyRing;
import bisq.common.proto.ProtoUtil;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.CollectionUtils;
import bisq.common.util.ExtraDataMapValidator;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
@EqualsAndHashCode
@Getter
public final class Dispute implements NetworkPayload, PersistablePayload {

    public enum State {
        NEEDS_UPGRADE,
        NEW,
        OPEN,
        REOPENED,
        CLOSED;

        public static Dispute.State fromProto(protobuf.Dispute.State state) {
            return ProtoUtil.enumFromProto(Dispute.State.class, state.name());
        }

        public static protobuf.Dispute.State toProtoMessage(Dispute.State state) {
            return protobuf.Dispute.State.valueOf(state.name());
        }
    }

    private final String tradeId;
    private final String id;
    private final int traderId;
    private final boolean disputeOpenerIsBuyer;
    private final boolean disputeOpenerIsMaker;
    // PubKeyRing of trader who opened the dispute
    private final PubKeyRing traderPubKeyRing;
    private final long tradeDate;
    private final long tradePeriodEnd;
    private Contract contract;
    @Nullable
    private final byte[] contractHash;
    @Nullable
    private final byte[] depositTxSerialized;
    @Nullable
    private final byte[] payoutTxSerialized;
    @Nullable
    private final String depositTxId;
    @Nullable
    private final String payoutTxId;
    private String contractAsJson;
    @Nullable
    private final String makerContractSignature;
    @Nullable
    private final String takerContractSignature;
    private final PubKeyRing agentPubKeyRing; // dispute agent
    private final boolean isSupportTicket;
    private final ObservableList<ChatMessage> chatMessages = FXCollections.observableArrayList();
    // disputeResultProperty.get is Nullable!
    private final ObjectProperty<DisputeResult> disputeResultProperty = new SimpleObjectProperty<>();
    private final long openingDate;
    @Nullable
    @Setter
    private String disputePayoutTxId;
    @Setter
    // Added v1.2.0
    private SupportType supportType;
    // Only used at refundAgent so that he knows how the mediator resolved the case
    @Setter
    @Nullable
    private String mediatorsDisputeResult;
    @Setter
    @Nullable
    private String delayedPayoutTxId;

    // Added at v1.4.0
    @Setter
    @Nullable
    private String donationAddressOfDelayedPayoutTx;
    // Added at v1.6.0
    private Dispute.State disputeState = State.NEW;

    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    @Setter
    private Map<String, String> extraDataMap;

    // We do not persist uid, it is only used by dispute agents to guarantee an uid.
    @Setter
    @Nullable
    private transient String uid;
    @Setter
    private transient long payoutTxConfirms = -1;
    @Setter
    private transient boolean payoutDone = false;

    private transient final BooleanProperty isClosedProperty = new SimpleBooleanProperty();
    private transient final IntegerProperty badgeCountProperty = new SimpleIntegerProperty();

    private transient FileTransferReceiver fileTransferSession = null;

    public FileTransferReceiver createOrGetFileTransferReceiver(NetworkNode networkNode, NodeAddress peerNodeAddress, FileTransferSession.FtpCallback callback) throws IOException {
        // the receiver stores its state temporarily here in the dispute
        // this method gets called to retrieve the session each time a part of the log files is received
        if (fileTransferSession == null) {
            fileTransferSession = new FileTransferReceiver(networkNode, peerNodeAddress, this.tradeId, this.traderId, this.getRoleStringForLogFile(), callback);
        }
        return fileTransferSession;
    }

    public FileTransferSender createFileTransferSender(NetworkNode networkNode, NodeAddress peerNodeAddress, FileTransferSession.FtpCallback callback) {
        return new FileTransferSender(networkNode, peerNodeAddress, this.tradeId, this.traderId, this.getRoleStringForLogFile(), callback);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Dispute(long openingDate,
                   String tradeId,
                   int traderId,
                   boolean disputeOpenerIsBuyer,
                   boolean disputeOpenerIsMaker,
                   PubKeyRing traderPubKeyRing,
                   long tradeDate,
                   long tradePeriodEnd,
                   Contract contract,
                   @Nullable byte[] contractHash,
                   @Nullable byte[] depositTxSerialized,
                   @Nullable byte[] payoutTxSerialized,
                   @Nullable String depositTxId,
                   @Nullable String payoutTxId,
                   String contractAsJson,
                   @Nullable String makerContractSignature,
                   @Nullable String takerContractSignature,
                   PubKeyRing agentPubKeyRing,
                   boolean isSupportTicket,
                   SupportType supportType) {
        this.openingDate = openingDate;
        this.tradeId = tradeId;
        this.traderId = traderId;
        this.disputeOpenerIsBuyer = disputeOpenerIsBuyer;
        this.disputeOpenerIsMaker = disputeOpenerIsMaker;
        this.traderPubKeyRing = traderPubKeyRing;
        this.tradeDate = tradeDate;
        this.tradePeriodEnd = tradePeriodEnd;
        this.contract = contract;
        this.contractHash = contractHash;
        this.depositTxSerialized = depositTxSerialized;
        this.payoutTxSerialized = payoutTxSerialized;
        this.depositTxId = depositTxId;
        this.payoutTxId = payoutTxId;
        this.contractAsJson = contractAsJson;
        this.makerContractSignature = makerContractSignature;
        this.takerContractSignature = takerContractSignature;
        this.agentPubKeyRing = agentPubKeyRing;
        this.isSupportTicket = isSupportTicket;
        this.supportType = supportType;

        id = tradeId + "_" + traderId;
        uid = UUID.randomUUID().toString();
        refreshAlertLevel(true);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.Dispute toProtoMessage() {
        // Needed to avoid ConcurrentModificationException
        List<ChatMessage> clonedChatMessages = new ArrayList<>(chatMessages);
        protobuf.Dispute.Builder builder = protobuf.Dispute.newBuilder()
                .setTradeId(tradeId)
                .setTraderId(traderId)
                .setDisputeOpenerIsBuyer(disputeOpenerIsBuyer)
                .setDisputeOpenerIsMaker(disputeOpenerIsMaker)
                .setTraderPubKeyRing(traderPubKeyRing.toProtoMessage())
                .setTradeDate(tradeDate)
                .setTradePeriodEnd(tradePeriodEnd)
                .setContract(contract.toProtoMessage())
                .setContractAsJson(contractAsJson)
                .setAgentPubKeyRing(agentPubKeyRing.toProtoMessage())
                .setIsSupportTicket(isSupportTicket)
                .addAllChatMessage(clonedChatMessages.stream()
                        .map(msg -> msg.toProtoNetworkEnvelope().getChatMessage())
                        .collect(Collectors.toList()))
                .setIsClosed(this.isClosed())
                .setOpeningDate(openingDate)
                .setState(Dispute.State.toProtoMessage(disputeState))
                .setId(id);

        Optional.ofNullable(contractHash).ifPresent(e -> builder.setContractHash(ByteString.copyFrom(e)));
        Optional.ofNullable(depositTxSerialized).ifPresent(e -> builder.setDepositTxSerialized(ByteString.copyFrom(e)));
        Optional.ofNullable(payoutTxSerialized).ifPresent(e -> builder.setPayoutTxSerialized(ByteString.copyFrom(e)));
        Optional.ofNullable(depositTxId).ifPresent(builder::setDepositTxId);
        Optional.ofNullable(payoutTxId).ifPresent(builder::setPayoutTxId);
        Optional.ofNullable(disputePayoutTxId).ifPresent(builder::setDisputePayoutTxId);
        Optional.ofNullable(makerContractSignature).ifPresent(builder::setMakerContractSignature);
        Optional.ofNullable(takerContractSignature).ifPresent(builder::setTakerContractSignature);
        Optional.ofNullable(disputeResultProperty.get()).ifPresent(result -> builder.setDisputeResult(disputeResultProperty.get().toProtoMessage()));
        Optional.ofNullable(supportType).ifPresent(result -> builder.setSupportType(SupportType.toProtoMessage(supportType)));
        Optional.ofNullable(mediatorsDisputeResult).ifPresent(result -> builder.setMediatorsDisputeResult(mediatorsDisputeResult));
        Optional.ofNullable(delayedPayoutTxId).ifPresent(result -> builder.setDelayedPayoutTxId(delayedPayoutTxId));
        Optional.ofNullable(donationAddressOfDelayedPayoutTx).ifPresent(result -> builder.setDonationAddressOfDelayedPayoutTx(donationAddressOfDelayedPayoutTx));
        Optional.ofNullable(getExtraDataMap()).ifPresent(builder::putAllExtraData);
        return builder.build();
    }

    public static Dispute fromProto(protobuf.Dispute proto, CoreProtoResolver coreProtoResolver) {
        Dispute dispute = new Dispute(proto.getOpeningDate(),
                proto.getTradeId(),
                proto.getTraderId(),
                proto.getDisputeOpenerIsBuyer(),
                proto.getDisputeOpenerIsMaker(),
                PubKeyRing.fromProto(proto.getTraderPubKeyRing()),
                proto.getTradeDate(),
                proto.getTradePeriodEnd(),
                Contract.fromProto(proto.getContract(), coreProtoResolver),
                ProtoUtil.byteArrayOrNullFromProto(proto.getContractHash()),
                ProtoUtil.byteArrayOrNullFromProto(proto.getDepositTxSerialized()),
                ProtoUtil.byteArrayOrNullFromProto(proto.getPayoutTxSerialized()),
                ProtoUtil.stringOrNullFromProto(proto.getDepositTxId()),
                ProtoUtil.stringOrNullFromProto(proto.getPayoutTxId()),
                proto.getContractAsJson(),
                ProtoUtil.stringOrNullFromProto(proto.getMakerContractSignature()),
                ProtoUtil.stringOrNullFromProto(proto.getTakerContractSignature()),
                PubKeyRing.fromProto(proto.getAgentPubKeyRing()),
                proto.getIsSupportTicket(),
                SupportType.fromProto(proto.getSupportType()));

        dispute.setExtraDataMap(CollectionUtils.isEmpty(proto.getExtraDataMap()) ?
                null : ExtraDataMapValidator.getValidatedExtraDataMap(proto.getExtraDataMap()));

        dispute.chatMessages.addAll(proto.getChatMessageList().stream()
                .map(ChatMessage::fromPayloadProto)
                .collect(Collectors.toList()));

        if (proto.hasDisputeResult())
            dispute.disputeResultProperty.set(DisputeResult.fromProto(proto.getDisputeResult()));
        dispute.disputePayoutTxId = ProtoUtil.stringOrNullFromProto(proto.getDisputePayoutTxId());

        String mediatorsDisputeResult = proto.getMediatorsDisputeResult();
        if (!mediatorsDisputeResult.isEmpty()) {
            dispute.setMediatorsDisputeResult(mediatorsDisputeResult);
        }

        String delayedPayoutTxId = proto.getDelayedPayoutTxId();
        if (!delayedPayoutTxId.isEmpty()) {
            dispute.setDelayedPayoutTxId(delayedPayoutTxId);
        }

        String donationAddressOfDelayedPayoutTx = proto.getDonationAddressOfDelayedPayoutTx();
        if (!donationAddressOfDelayedPayoutTx.isEmpty()) {
            dispute.setDonationAddressOfDelayedPayoutTx(donationAddressOfDelayedPayoutTx);
        }

        if (Dispute.State.fromProto(proto.getState()) == State.NEEDS_UPGRADE) {
            // old disputes did not have a state field, so choose an appropriate state:
            dispute.setState(proto.getIsClosed() ? State.CLOSED : State.OPEN);
            if (dispute.getDisputeState() == State.CLOSED) {
                // mark chat messages as read for pre-existing CLOSED disputes
                // otherwise at upgrade, all old disputes would have 1 unread chat message
                // because currently when a dispute is closed, the last chat message is not marked read
                dispute.getChatMessages().forEach(m -> m.setWasDisplayed(true));
            }
        } else {
            dispute.setState(Dispute.State.fromProto(proto.getState()));
        }

        dispute.refreshAlertLevel(true);
        return dispute;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addAndPersistChatMessage(ChatMessage chatMessage) {
        if (!chatMessages.contains(chatMessage)) {
            chatMessages.add(chatMessage);
        } else {
            log.error("disputeDirectMessage already exists");
        }
    }

    public boolean removeAllChatMessages() {
        if (chatMessages.size() > 1) {
            // removes all chat except the initial guidelines message.
            String firstMessageUid = chatMessages.get(0).getUid();
            chatMessages.removeIf((msg) -> !msg.getUid().equals(firstMessageUid));
            return true;
        }
        return false;
    }

    public void maybeClearSensitiveData() {
        String change = "";
        if (contract.maybeClearSensitiveData()) {
            change += "contract;";
        }
        String edited = contract.sanitizeContractAsJson(contractAsJson);
        if (!edited.equals(contractAsJson)) {
            contractAsJson = edited;
            change += "contractAsJson;";
        }
        if (removeAllChatMessages()) {
            change += "chat messages;";
        }
        if (change.length() > 0) {
            log.info("cleared sensitive data from {} of dispute for trade {}", change, Utilities.getShortId(getTradeId()));
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setIsClosed() {
        setState(State.CLOSED);
    }

    public void reOpen() {
        setState(State.REOPENED);
    }

    public void setState(Dispute.State disputeState) {
        this.disputeState = disputeState;
        this.isClosedProperty.set(disputeState == State.CLOSED);
    }

    public void setDisputeResult(DisputeResult disputeResult) {
        disputeResultProperty.set(disputeResult);
    }

    public void setExtraData(String key, String value) {
        if (key == null || value == null) {
            return;
        }
        if (extraDataMap == null) {
            extraDataMap = new HashMap<>();
        }
        extraDataMap.put(key, value);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getShortTradeId() {
        return Utilities.getShortId(tradeId);
    }

    public ReadOnlyBooleanProperty isClosedProperty() {
        return isClosedProperty;
    }
    public ReadOnlyIntegerProperty getBadgeCountProperty() {
        return badgeCountProperty;
    }
    public ReadOnlyObjectProperty<DisputeResult> disputeResultProperty() {
        return disputeResultProperty;
    }

    public Date getTradeDate() {
        return new Date(tradeDate);
    }

    public Date getTradePeriodEnd() {
        return new Date(tradePeriodEnd);
    }

    public Date getOpeningDate() {
        return new Date(openingDate);
    }

    public boolean isNew() {
        return this.disputeState == State.NEW;
    }

    public boolean isClosed() {
        return this.disputeState == State.CLOSED;
    }

    public void refreshAlertLevel(boolean senderFlag) {
        // if the dispute is "new" that is 1 alert that has to be propagated upstream
        // or if there are unread messages that is 1 alert that has to be propagated upstream
        if (isNew() || unreadMessageCount(senderFlag) > 0) {
            badgeCountProperty.setValue(1);
        } else {
            badgeCountProperty.setValue(0);
        }
    }

    public long unreadMessageCount(boolean senderFlag) {
        return chatMessages.stream()
                .filter(m -> m.isSenderIsTrader() == senderFlag || m.isSystemMessage())
                .filter(m -> !m.isWasDisplayed())
                .count();
    }

    public void setDisputeSeen(boolean senderFlag) {
        if (this.disputeState == State.NEW)
            setState(State.OPEN);
        refreshAlertLevel(senderFlag);
    }

    public void setChatMessagesSeen(boolean senderFlag) {
        getChatMessages().forEach(m -> m.setWasDisplayed(true));
        refreshAlertLevel(senderFlag);
    }

    public String getRoleString() {
        if (disputeOpenerIsMaker) {
            if (disputeOpenerIsBuyer)
                return Res.get("support.buyerOfferer");
            else
                return Res.get("support.sellerOfferer");
        } else {
            if (disputeOpenerIsBuyer)
                return Res.get("support.buyerTaker");
            else
                return Res.get("support.sellerTaker");
        }
    }

    public String getRoleStringForLogFile() {
        return (disputeOpenerIsBuyer ? "BUYER" : "SELLER") + "_"
                + (disputeOpenerIsMaker ? "MAKER" : "TAKER");
    }

    @Override
    public String toString() {
        return "Dispute{" +
                "\n     tradeId='" + tradeId + '\'' +
                ",\n     id='" + id + '\'' +
                ",\n     uid='" + uid + '\'' +
                ",\n     state=" + disputeState +
                ",\n     traderId=" + traderId +
                ",\n     disputeOpenerIsBuyer=" + disputeOpenerIsBuyer +
                ",\n     disputeOpenerIsMaker=" + disputeOpenerIsMaker +
                ",\n     traderPubKeyRing=" + traderPubKeyRing +
                ",\n     tradeDate=" + tradeDate +
                ",\n     tradePeriodEnd=" + tradePeriodEnd +
                ",\n     contract=" + contract +
                ",\n     contractHash=" + Utilities.bytesAsHexString(contractHash) +
                ",\n     depositTxSerialized=" + Utilities.bytesAsHexString(depositTxSerialized) +
                ",\n     payoutTxSerialized=" + Utilities.bytesAsHexString(payoutTxSerialized) +
                ",\n     depositTxId='" + depositTxId + '\'' +
                ",\n     payoutTxId='" + payoutTxId + '\'' +
                ",\n     contractAsJson='" + contractAsJson + '\'' +
                ",\n     makerContractSignature='" + makerContractSignature + '\'' +
                ",\n     takerContractSignature='" + takerContractSignature + '\'' +
                ",\n     agentPubKeyRing=" + agentPubKeyRing +
                ",\n     isSupportTicket=" + isSupportTicket +
                ",\n     chatMessages=" + chatMessages +
                ",\n     isClosedProperty=" + isClosedProperty +
                ",\n     disputeResultProperty=" + disputeResultProperty +
                ",\n     disputePayoutTxId='" + disputePayoutTxId + '\'' +
                ",\n     openingDate=" + openingDate +
                ",\n     supportType=" + supportType +
                ",\n     mediatorsDisputeResult='" + mediatorsDisputeResult + '\'' +
                ",\n     delayedPayoutTxId='" + delayedPayoutTxId + '\'' +
                ",\n     donationAddressOfDelayedPayoutTx='" + donationAddressOfDelayedPayoutTx + '\'' +
                "\n}";
    }
}
