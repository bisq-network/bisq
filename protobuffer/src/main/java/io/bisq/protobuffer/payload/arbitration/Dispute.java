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

package io.bisq.protobuffer.payload.arbitration;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.storage.Storage;
import io.bisq.common.util.Utilities;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.message.arbitration.DisputeCommunicationMessage;
import io.bisq.protobuffer.payload.Payload;
import io.bisq.protobuffer.payload.crypto.PubKeyRing;
import io.bisq.protobuffer.payload.trade.Contract;
import io.bisq.protobuffer.persistence.arbitration.DisputeList;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@EqualsAndHashCode
public final class Dispute implements Payload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Payload
    private final String tradeId;
    private final String id;
    private final int traderId;
    private final boolean disputeOpenerIsBuyer;
    private final boolean disputeOpenerIsMaker;
    private final long openingDate;
    private final PubKeyRing traderPubKeyRing;
    private final long tradeDate;
    private final Contract contract;
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


    // Domain
    transient private Storage<DisputeList> storage;
    transient private ObservableList<DisputeCommunicationMessage> observableList = FXCollections.observableArrayList(
            disputeCommunicationMessages);
    transient private BooleanProperty isClosedProperty = new SimpleBooleanProperty(isClosed);
    transient private ObjectProperty<DisputeResult> disputeResultProperty = new SimpleObjectProperty<>(disputeResult);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Dispute(Storage<DisputeList> storage,
                   String tradeId,
                   int traderId,
                   boolean disputeOpenerIsBuyer,
                   boolean disputeOpenerIsMaker,
                   PubKeyRing traderPubKeyRing,
                   Date tradeDate,
                   Contract contract,
                   byte[] contractHash,
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
        this.storage = storage;
    }

    public Dispute(String tradeId,
                   int traderId,
                   boolean disputeOpenerIsBuyer,
                   boolean disputeOpenerIsMaker,
                   PubKeyRing traderPubKeyRing,
                   Date tradeDate,
                   Contract contract,
                   byte[] contractHash,
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
        this.tradeDate = tradeDate.getTime();
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
        this.openingDate = new Date().getTime();

        id = tradeId + "_" + traderId;
        fillInTransients();
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            fillInTransients();
        } catch (Throwable t) {
            log.warn("Cannot be deserialized." + t.getMessage());
        }
    }

    private void fillInTransients() {
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

    public String getId() {
        return id;
    }

    public String getTradeId() {
        return tradeId;
    }

    public String getShortTradeId() {
        return Utilities.getShortId(tradeId);
    }

    public int getTraderId() {
        return traderId;
    }

    public boolean isDisputeOpenerIsBuyer() {
        return disputeOpenerIsBuyer;
    }

    public boolean isDisputeOpenerIsMaker() {
        return disputeOpenerIsMaker;
    }

    public Date getOpeningDate() {
        return new Date(openingDate);
    }

    public PubKeyRing getTraderPubKeyRing() {
        return traderPubKeyRing;
    }

    public Contract getContract() {
        return contract;
    }

    @Nullable
    public byte[] getDepositTxSerialized() {
        return depositTxSerialized;
    }

    @Nullable
    public byte[] getPayoutTxSerialized() {
        return payoutTxSerialized;
    }

    @Nullable
    public String getDepositTxId() {
        return depositTxId;
    }

    @Nullable
    public String getPayoutTxId() {
        return payoutTxId;
    }

    public String getContractAsJson() {
        return contractAsJson;
    }

    @Nullable
    public String getMakerContractSignature() {
        return makerContractSignature;
    }

    @Nullable
    public String getTakerContractSignature() {
        return takerContractSignature;
    }

    public ObservableList<DisputeCommunicationMessage> getDisputeCommunicationMessagesAsObservableList() {
        return observableList;
    }

    public boolean isClosed() {
        return isClosedProperty.get();
    }

    public ReadOnlyBooleanProperty isClosedProperty() {
        return isClosedProperty;
    }

    public PubKeyRing getArbitratorPubKeyRing() {
        return arbitratorPubKeyRing;
    }

    public ObjectProperty<DisputeResult> disputeResultProperty() {
        return disputeResultProperty;
    }

    public boolean isSupportTicket() {
        return isSupportTicket;
    }

    public byte[] getContractHash() {
        return contractHash;
    }

    public Date getTradeDate() {
        return new Date(tradeDate);
    }

    @org.jetbrains.annotations.Nullable
    public String getDisputePayoutTxId() {
        return disputePayoutTxId;
    }

    //payoutTxSerialized not displayed for privacy reasons...
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
                ", contractHash=" + Hex.toHexString(contractHash) +
                ", depositTxSerialized=" + Hex.toHexString(depositTxSerialized) +
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

    @Override
    public PB.Dispute toProto() {
        PB.Dispute.Builder builder = PB.Dispute.newBuilder()
                .setTradeId(tradeId)
                .setId(id)
                .setTraderId(traderId)
                .setDisputeOpenerIsBuyer(disputeOpenerIsBuyer)
                .setDisputeOpenerIsMaker(disputeOpenerIsMaker)
                .setOpeningDate(openingDate)
                .setTraderPubKeyRing(traderPubKeyRing.toProto())
                .setTradeDate(tradeDate)
                .setContract(contract.toProto())
                .setContractHash(ByteString.copyFrom(contractHash))
                .setContractAsJson(contractAsJson)
                .setArbitratorPubKeyRing(arbitratorPubKeyRing.toProto())
                .setIsSupportTicket(isSupportTicket)
                .addAllDisputeCommunicationMessages(disputeCommunicationMessages.stream().map(
                        disputeCommunicationMessage -> disputeCommunicationMessage.toProto().getDisputeCommunicationMessage()).collect(Collectors.toList()))
                .setIsClosed(isClosed);

        Optional.ofNullable(depositTxSerialized).ifPresent(tx -> builder.setDepositTxSerialized(ByteString.copyFrom(tx)));
        Optional.ofNullable(payoutTxSerialized).ifPresent(tx -> builder.setPayoutTxSerialized(ByteString.copyFrom(tx)));
        Optional.ofNullable(depositTxId).ifPresent(builder::setDepositTxId);
        Optional.ofNullable(payoutTxId).ifPresent(builder::setPayoutTxId);
        Optional.ofNullable(disputePayoutTxId).ifPresent(builder::setDisputePayoutTxId);
        Optional.ofNullable(takerContractSignature).ifPresent(builder::setTakerContractSignature);
        Optional.ofNullable(makerContractSignature).ifPresent(builder::setMakerContractSignature);
        Optional.ofNullable(disputeResult).ifPresent(result -> builder.setDisputeResult(disputeResult.toProto()));
        return builder.build();
    }
}
