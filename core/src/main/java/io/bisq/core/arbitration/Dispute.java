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

package io.bisq.core.arbitration;

import com.google.protobuf.ByteString;
import io.bisq.common.crypto.PubKeyRing;
import io.bisq.common.proto.network.NetworkPayload;
import io.bisq.common.storage.Storage;
import io.bisq.common.util.Utilities;
import io.bisq.core.arbitration.messages.DisputeCommunicationMessage;
import io.bisq.core.proto.CoreProtoResolver;
import io.bisq.core.trade.Contract;
import io.bisq.generated.protobuffer.PB;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Date;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@EqualsAndHashCode
@Getter
public final class Dispute implements NetworkPayload {
    private final String tradeId;
    private final String id;
    private final int traderId;
    private final boolean disputeOpenerIsBuyer;
    private final boolean disputeOpenerIsMaker;
    private final PubKeyRing traderPubKeyRing;
    private final long tradeDate;
    private final Contract contract;
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
    private final String contractAsJson;
    @Nullable
    private final String makerContractSignature;
    @Nullable
    private final String takerContractSignature;
    private final PubKeyRing arbitratorPubKeyRing;
    private final boolean isSupportTicket;
    private final ArrayList<DisputeCommunicationMessage> disputeCommunicationMessages = new ArrayList<>();

    private boolean isClosed;
    @Nullable
    private DisputeResult disputeResult;
    @Nullable
    private String disputePayoutTxId;

    private long openingDate;

    transient private Storage<DisputeList> storage;
    transient private ObservableList<DisputeCommunicationMessage> observableList;
    transient private BooleanProperty isClosedProperty;
    transient private ObjectProperty<DisputeResult> disputeResultProperty;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Dispute(Storage<DisputeList> storage,
                   String tradeId,
                   int traderId,
                   boolean disputeOpenerIsBuyer,
                   boolean disputeOpenerIsMaker,
                   PubKeyRing traderPubKeyRing,
                   long tradeDate,
                   Contract contract,
                   @Nullable byte[] contractHash,
                   @Nullable byte[] depositTxSerialized,
                   @Nullable byte[] payoutTxSerialized,
                   @Nullable String depositTxId,
                   @Nullable String payoutTxId,
                   String contractAsJson,
                   @Nullable String makerContractSignature,
                   @Nullable String takerContractSignature,
                   PubKeyRing arbitratorPubKeyRing,
                   boolean isSupportTicket) {
        this(tradeId,
                traderId,
                disputeOpenerIsBuyer,
                disputeOpenerIsMaker,
                traderPubKeyRing,
                tradeDate,
                contract,
                contractHash,
                depositTxSerialized,
                payoutTxSerialized,
                depositTxId,
                payoutTxId,
                contractAsJson,
                makerContractSignature,
                takerContractSignature,
                arbitratorPubKeyRing,
                isSupportTicket);

        openingDate = new Date().getTime();

        this.storage = storage;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Dispute(String tradeId,
                   int traderId,
                   boolean disputeOpenerIsBuyer,
                   boolean disputeOpenerIsMaker,
                   PubKeyRing traderPubKeyRing,
                   long tradeDate,
                   Contract contract,
                   @Nullable byte[] contractHash,
                   @Nullable byte[] depositTxSerialized,
                   @Nullable byte[] payoutTxSerialized,
                   @Nullable String depositTxId,
                   @Nullable String payoutTxId,
                   String contractAsJson,
                   @Nullable String makerContractSignature,
                   @Nullable String takerContractSignature,
                   PubKeyRing arbitratorPubKeyRing,
                   boolean isSupportTicket) {
        this.tradeId = tradeId;
        this.traderId = traderId;
        this.disputeOpenerIsBuyer = disputeOpenerIsBuyer;
        this.disputeOpenerIsMaker = disputeOpenerIsMaker;
        this.traderPubKeyRing = traderPubKeyRing;
        this.tradeDate = tradeDate;
        this.contract = contract;
        this.contractHash = contractHash;
        this.depositTxSerialized = depositTxSerialized;
        this.payoutTxSerialized = payoutTxSerialized;
        this.depositTxId = depositTxId;
        this.payoutTxId = payoutTxId;
        this.contractAsJson = contractAsJson;
        this.makerContractSignature = makerContractSignature;
        this.takerContractSignature = takerContractSignature;
        this.arbitratorPubKeyRing = arbitratorPubKeyRing;
        this.isSupportTicket = isSupportTicket;

        id = tradeId + "_" + traderId;

        init();
    }

    @Override
    public PB.Dispute toProtoMessage() {
        PB.Dispute.Builder builder = PB.Dispute.newBuilder()
                .setTradeId(tradeId)
                .setTraderId(traderId)
                .setDisputeOpenerIsBuyer(disputeOpenerIsBuyer)
                .setDisputeOpenerIsMaker(disputeOpenerIsMaker)
                .setTraderPubKeyRing(traderPubKeyRing.toProtoMessage())
                .setTradeDate(tradeDate)
                .setContract(contract.toProtoMessage())
                .setContractAsJson(contractAsJson)
                .setArbitratorPubKeyRing(arbitratorPubKeyRing.toProtoMessage())
                .setIsSupportTicket(isSupportTicket)
                .addAllDisputeCommunicationMessages(disputeCommunicationMessages.stream()
                        .map(msg -> msg.toProtoNetworkEnvelope().getDisputeCommunicationMessage())
                        .collect(Collectors.toList()))
                .setIsClosed(isClosed)
                .setOpeningDate(openingDate)
                .setId(id);

        Optional.ofNullable(contractHash).ifPresent(tx -> builder.setContractHash(ByteString.copyFrom(contractHash)));
        Optional.ofNullable(depositTxSerialized).ifPresent(tx -> builder.setDepositTxSerialized(ByteString.copyFrom(tx)));
        Optional.ofNullable(payoutTxSerialized).ifPresent(tx -> builder.setPayoutTxSerialized(ByteString.copyFrom(tx)));
        Optional.ofNullable(depositTxId).ifPresent(builder::setDepositTxId);
        Optional.ofNullable(payoutTxId).ifPresent(builder::setPayoutTxId);
        Optional.ofNullable(disputePayoutTxId).ifPresent(builder::setDisputePayoutTxId);
        Optional.ofNullable(makerContractSignature).ifPresent(builder::setMakerContractSignature);
        Optional.ofNullable(takerContractSignature).ifPresent(builder::setTakerContractSignature);
        Optional.ofNullable(disputeResult).ifPresent(result -> builder.setDisputeResult(disputeResult.toProtoMessage()));
        return builder.build();
    }

    public static Dispute fromProto(PB.Dispute proto, CoreProtoResolver coreProtoResolver) {
        final Dispute dispute = new Dispute(proto.getTradeId(),
                proto.getTraderId(),
                proto.getDisputeOpenerIsBuyer(),
                proto.getDisputeOpenerIsMaker(),
                PubKeyRing.fromProto(proto.getTraderPubKeyRing()),
                proto.getTradeDate(),
                Contract.fromProto(proto.getContract(), coreProtoResolver),
                proto.getContractHash().toByteArray().length == 0 ? null : proto.getContractHash().toByteArray(),
                proto.getDepositTxSerialized().toByteArray().length == 0 ? null : proto.getDepositTxSerialized().toByteArray(),
                proto.getPayoutTxSerialized().toByteArray().length == 0 ? null : proto.getPayoutTxSerialized().toByteArray(),
                proto.getDepositTxId().isEmpty() ? null : proto.getDepositTxId(),
                proto.getPayoutTxId().isEmpty() ? null : proto.getPayoutTxId(),
                proto.getContractAsJson(),
                proto.getMakerContractSignature().isEmpty() ? null : proto.getMakerContractSignature(),
                proto.getTakerContractSignature().isEmpty() ? null : proto.getTakerContractSignature(),
                PubKeyRing.fromProto(proto.getArbitratorPubKeyRing()),
                proto.getIsSupportTicket());

        dispute.disputeCommunicationMessages.addAll(proto.getDisputeCommunicationMessagesList().stream()
                .map(DisputeCommunicationMessage::fromProto)
                .collect(Collectors.toList()));

        dispute.openingDate = proto.getOpeningDate();
        dispute.isClosed = proto.getIsClosed();
        DisputeResult.fromProto(proto.getDisputeResult()).ifPresent(d -> dispute.disputeResult = d);
        dispute.disputePayoutTxId = proto.getDisputePayoutTxId().isEmpty() ? null : proto.getDisputePayoutTxId();
        return dispute;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void init() {
        observableList = FXCollections.observableArrayList(disputeCommunicationMessages);
        isClosedProperty = new SimpleBooleanProperty(isClosed);
        disputeResultProperty = new SimpleObjectProperty<>(disputeResult);
    }

    public void addDisputeMessage(DisputeCommunicationMessage disputeCommunicationMessage) {
        if (!disputeCommunicationMessages.contains(disputeCommunicationMessage)) {
            disputeCommunicationMessages.add(disputeCommunicationMessage);
            observableList.add(disputeCommunicationMessage);
            storage.queueUpForSave();
        } else {
            log.error("disputeDirectMessage already exists");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    // In case we get the object via the network storage is not set as its transient, so we need to set it.
    public void setStorage(Storage<DisputeList> storage) {
        this.storage = storage;
    }

    public void setIsClosed(boolean isClosed) {
        boolean changed = this.isClosed != isClosed;
        this.isClosed = isClosed;
        isClosedProperty.set(isClosed);
        if (changed)
            storage.queueUpForSave();
    }

    public void setDisputeResult(DisputeResult disputeResult) {
        boolean changed = this.disputeResult == null || !this.disputeResult.equals(disputeResult);
        this.disputeResult = disputeResult;
        disputeResultProperty.set(disputeResult);
        if (changed)
            storage.queueUpForSave();
    }

    public void setDisputePayoutTxId(String disputePayoutTxId) {
        boolean changed = this.disputePayoutTxId == null || !this.disputePayoutTxId.equals(disputePayoutTxId);
        this.disputePayoutTxId = disputePayoutTxId;
        if (changed)
            storage.queueUpForSave();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getShortTradeId() {
        return Utilities.getShortId(tradeId);
    }

    public ObservableList<DisputeCommunicationMessage> getDisputeCommunicationMessagesAsObservableList() {
        return observableList;
    }

    public ReadOnlyBooleanProperty isClosedProperty() {
        return isClosedProperty;
    }

    public ObjectProperty<DisputeResult> disputeResultProperty() {
        return disputeResultProperty;
    }

    public Date getTradeDate() {
        return new Date(tradeDate);
    }

    public Date getOpeningDate() {
        return new Date(openingDate);
    }

    @Override
    public String toString() {
        return "Dispute{" +
                "tradeId='" + tradeId + '\'' +
                ", id='" + id + '\'' +
                ", traderId=" + traderId +
                ", disputeOpenerIsBuyer=" + disputeOpenerIsBuyer +
                ", disputeOpenerIsMaker=" + disputeOpenerIsMaker +
                ", openingDate=" + openingDate +
                ", traderPubKeyRing=" + traderPubKeyRing +
                ", tradeDate=" + tradeDate +
                ", contract=" + contract +
                ", contractHash=" + Utilities.bytesAsHexString(contractHash) +
                ", depositTxSerialized=" + Utilities.bytesAsHexString(depositTxSerialized) +
                ", payoutTxSerialized not displayed for privacy reasons..." +
                ", depositTxId='" + depositTxId + '\'' +
                ", payoutTxId='" + payoutTxId + '\'' +
                ", contractAsJson='" + contractAsJson + '\'' +
                ", makerContractSignature='" + makerContractSignature + '\'' +
                ", takerContractSignature='" + takerContractSignature + '\'' +
                ", arbitratorPubKeyRing=" + arbitratorPubKeyRing +
                ", isSupportTicket=" + isSupportTicket +
                ", disputeCommunicationMessages=" + disputeCommunicationMessages +
                ", isClosed=" + isClosed +
                ", disputeResult=" + disputeResult +
                ", disputePayoutTxId='" + disputePayoutTxId + '\'' +
                ", disputeCommunicationMessagesAsObservableList=" + observableList +
                ", isClosedProperty=" + isClosedProperty +
                ", disputeResultProperty=" + disputeResultProperty +
                '}';
    }
}
