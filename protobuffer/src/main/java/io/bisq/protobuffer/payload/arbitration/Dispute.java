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

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
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
    private final boolean disputeOpenerIsOfferer;
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
    private final String offererContractSignature;
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
    transient private Storage<DisputeList<Dispute>> storage;
    transient private ObservableList<DisputeCommunicationMessage> observableList = FXCollections.observableArrayList(
            disputeCommunicationMessages);
    transient private BooleanProperty isClosedProperty = new SimpleBooleanProperty(isClosed);
    transient private ObjectProperty<DisputeResult> disputeResultProperty = new SimpleObjectProperty<>(disputeResult);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Dispute(Storage<DisputeList<Dispute>> storage,
                   String tradeId,
                   int traderId,
                   boolean disputeOpenerIsBuyer,
                   boolean disputeOpenerIsOfferer,
                   PubKeyRing traderPubKeyRing,
                   Date tradeDate,
                   Contract contract,
                   byte[] contractHash,
                   @Nullable byte[] depositTxSerialized,
                   @Nullable byte[] payoutTxSerialized,
                   @Nullable String depositTxId,
                   @Nullable String payoutTxId,
                   String contractAsJson,
                   @Nullable String offererContractSignature,
                   @Nullable String takerContractSignature,
                   PubKeyRing arbitratorPubKeyRing,
                   boolean isSupportTicket) {
        this(tradeId,
                traderId,
                disputeOpenerIsBuyer,
                disputeOpenerIsOfferer,
                traderPubKeyRing,
                tradeDate,
                contract,
                contractHash,
                depositTxSerialized,
                payoutTxSerialized,
                depositTxId,
                payoutTxId,
                contractAsJson,
                offererContractSignature,
                takerContractSignature,
                arbitratorPubKeyRing,
                isSupportTicket);
        this.storage = storage;
    }

    public Dispute(String tradeId,
                   int traderId,
                   boolean disputeOpenerIsBuyer,
                   boolean disputeOpenerIsOfferer,
                   PubKeyRing traderPubKeyRing,
                   Date tradeDate, 
                   Contract contract,
                   byte[] contractHash,
                   @Nullable byte[] depositTxSerialized,
                   @Nullable byte[] payoutTxSerialized,
                   @Nullable String depositTxId,
                   @Nullable String payoutTxId,
                   String contractAsJson,
                   @Nullable String offererContractSignature,
                   @Nullable String takerContractSignature,
                   PubKeyRing arbitratorPubKeyRing,
                   boolean isSupportTicket) {
        this.tradeId = tradeId;
        this.traderId = traderId;
        this.disputeOpenerIsBuyer = disputeOpenerIsBuyer;
        this.disputeOpenerIsOfferer = disputeOpenerIsOfferer;
        this.traderPubKeyRing = traderPubKeyRing;
        this.tradeDate = tradeDate.getTime();
        this.contract = contract;
        this.contractHash = contractHash;
        this.depositTxSerialized = depositTxSerialized;
        this.payoutTxSerialized = payoutTxSerialized;
        this.depositTxId = depositTxId;
        this.payoutTxId = payoutTxId;
        this.contractAsJson = contractAsJson;
        this.offererContractSignature = offererContractSignature;
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
    public void setStorage(Storage<DisputeList<Dispute>> storage) {
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

    public boolean isDisputeOpenerIsOfferer() {
        return disputeOpenerIsOfferer;
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
    public String getOffererContractSignature() {
        return offererContractSignature;
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

    @Override
    public String toString() {
        return "Dispute{" +
                "tradeId='" + tradeId + '\'' +
                ", id='" + id + '\'' +
                ", traderId=" + traderId +
                ", disputeOpenerIsBuyer=" + disputeOpenerIsBuyer +
                ", disputeOpenerIsOfferer=" + disputeOpenerIsOfferer +
                ", openingDate=" + openingDate +
                ", traderPubKeyRing=" + traderPubKeyRing +
                ", tradeDate=" + tradeDate +
                ", contract=" + contract +
                ", contractHash=" + Arrays.toString(contractHash) +
                ", depositTxSerialized=" + Arrays.toString(depositTxSerialized) +
                ", payoutTxSerialized=" + Arrays.toString(payoutTxSerialized) +
                ", depositTxId='" + depositTxId + '\'' +
                ", payoutTxId='" + payoutTxId + '\'' +
                ", contractAsJson='" + contractAsJson + '\'' +
                ", offererContractSignature='" + offererContractSignature + '\'' +
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
                .setDisputeOpenerIsOfferer(disputeOpenerIsOfferer)
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
        Optional.ofNullable(offererContractSignature).ifPresent(builder::setOffererContractSignature);
        Optional.ofNullable(disputeResult).ifPresent(result -> builder.setDisputeResult(disputeResult.toProto()));
        return builder.build();
    }
}
