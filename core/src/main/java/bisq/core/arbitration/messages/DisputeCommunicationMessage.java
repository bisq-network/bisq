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

package bisq.core.arbitration.messages;

import bisq.core.arbitration.Attachment;

import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;
import bisq.common.util.Utilities;

import io.bisq.generated.protobuffer.PB;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import java.lang.ref.WeakReference;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@EqualsAndHashCode(callSuper = true) // listener is transient and therefore excluded anyway
@Getter
@Slf4j
public final class DisputeCommunicationMessage extends DisputeMessage {

    public interface Listener {
        void onMessageStateChanged();
    }

    private final String tradeId;
    private final int traderId;
    private final boolean senderIsTrader;
    private final String message;
    private final ArrayList<Attachment> attachments = new ArrayList<>();
    private final NodeAddress senderNodeAddress;
    private final long date;
    @Setter
    private boolean isSystemMessage;

    private final BooleanProperty arrivedProperty;
    private final BooleanProperty storedInMailboxProperty;
    private final BooleanProperty acknowledgedProperty;
    private final StringProperty sendMessageErrorProperty;
    private final StringProperty ackErrorProperty;

    transient private WeakReference<Listener> listener;

    public DisputeCommunicationMessage(String tradeId,
                                       int traderId,
                                       boolean senderIsTrader,
                                       String message,
                                       NodeAddress senderNodeAddress) {
        this(tradeId,
                traderId,
                senderIsTrader,
                message,
                null,
                senderNodeAddress,
                new Date().getTime(),
                false,
                false,
                UUID.randomUUID().toString(),
                Version.getP2PMessageVersion(),
                false,
                null,
                null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private DisputeCommunicationMessage(String tradeId,
                                        int traderId,
                                        boolean senderIsTrader,
                                        String message,
                                        @Nullable List<Attachment> attachments,
                                        NodeAddress senderNodeAddress,
                                        long date,
                                        boolean arrived,
                                        boolean storedInMailbox,
                                        String uid,
                                        int messageVersion,
                                        boolean acknowledged,
                                        @Nullable String sendMessageError,
                                        @Nullable String ackError) {
        super(messageVersion, uid);
        this.tradeId = tradeId;
        this.traderId = traderId;
        this.senderIsTrader = senderIsTrader;
        this.message = message;
        Optional.ofNullable(attachments).ifPresent(e -> addAllAttachments(attachments));
        this.senderNodeAddress = senderNodeAddress;
        this.date = date;
        arrivedProperty = new SimpleBooleanProperty(arrived);
        storedInMailboxProperty = new SimpleBooleanProperty(storedInMailbox);
        acknowledgedProperty = new SimpleBooleanProperty(acknowledged);
        sendMessageErrorProperty = new SimpleStringProperty(sendMessageError);
        ackErrorProperty = new SimpleStringProperty(ackError);
        notifyChangeListener();
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        PB.DisputeCommunicationMessage.Builder builder = PB.DisputeCommunicationMessage.newBuilder()
                .setTradeId(tradeId)
                .setTraderId(traderId)
                .setSenderIsTrader(senderIsTrader)
                .setMessage(message)
                .addAllAttachments(attachments.stream().map(Attachment::toProtoMessage).collect(Collectors.toList()))
                .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                .setDate(date)
                .setArrived(arrivedProperty.get())
                .setStoredInMailbox(storedInMailboxProperty.get())
                .setIsSystemMessage(isSystemMessage)
                .setUid(uid)
                .setAcknowledged(acknowledgedProperty.get());
        Optional.ofNullable(sendMessageErrorProperty.get()).ifPresent(builder::setSendMessageError);
        Optional.ofNullable(ackErrorProperty.get()).ifPresent(builder::setAckError);
        return getNetworkEnvelopeBuilder()
                .setDisputeCommunicationMessage(builder)
                .build();
    }

    public static DisputeCommunicationMessage fromProto(PB.DisputeCommunicationMessage proto, int messageVersion) {
        final DisputeCommunicationMessage disputeCommunicationMessage = new DisputeCommunicationMessage(
                proto.getTradeId(),
                proto.getTraderId(),
                proto.getSenderIsTrader(),
                proto.getMessage(),
                new ArrayList<>(proto.getAttachmentsList().stream().map(Attachment::fromProto).collect(Collectors.toList())),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getDate(),
                proto.getArrived(),
                proto.getStoredInMailbox(),
                proto.getUid(),
                messageVersion,
                proto.getAcknowledged(),
                proto.getSendMessageError().isEmpty() ? null : proto.getSendMessageError(),
                proto.getAckError().isEmpty() ? null : proto.getAckError());
        disputeCommunicationMessage.setSystemMessage(proto.getIsSystemMessage());
        return disputeCommunicationMessage;
    }

    public static DisputeCommunicationMessage fromPayloadProto(PB.DisputeCommunicationMessage proto) {
        // We have the case that an envelope got wrapped into a payload.
        // We don't check the message version here as it was checked in the carrier envelope already (in connection class)
        // Payloads don't have a message version and are also used for persistence
        // We set the value to -1 to indicate it is set but irrelevant
        return fromProto(proto, -1);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addAllAttachments(List<Attachment> attachments) {
        this.attachments.addAll(attachments);
    }

    public void setArrived(@SuppressWarnings("SameParameterValue") boolean arrived) {
        this.arrivedProperty.set(arrived);
        notifyChangeListener();
    }

    public ReadOnlyBooleanProperty arrivedProperty() {
        return arrivedProperty;
    }


    public void setStoredInMailbox(@SuppressWarnings("SameParameterValue") boolean storedInMailbox) {
        this.storedInMailboxProperty.set(storedInMailbox);
        notifyChangeListener();
    }

    public ReadOnlyBooleanProperty storedInMailboxProperty() {
        return storedInMailboxProperty;
    }

    public void setAcknowledged(boolean acknowledged) {
        this.acknowledgedProperty.set(acknowledged);
        notifyChangeListener();
    }

    public ReadOnlyBooleanProperty acknowledgedProperty() {
        return acknowledgedProperty;
    }

    public void setSendMessageError(String sendMessageError) {
        this.sendMessageErrorProperty.set(sendMessageError);
        notifyChangeListener();
    }

    public ReadOnlyStringProperty sendMessageErrorProperty() {
        return sendMessageErrorProperty;
    }

    public void setAckError(String ackError) {
        this.ackErrorProperty.set(ackError);
        notifyChangeListener();
    }

    public ReadOnlyStringProperty ackErrorProperty() {
        return ackErrorProperty;
    }

    @Override
    public String getTradeId() {
        return tradeId;
    }

    public String getShortId() {
        return Utilities.getShortId(tradeId);
    }

    public void addWeakMessageStateListener(Listener listener) {
        this.listener = new WeakReference<>(listener);
    }

    private void notifyChangeListener() {
        if (listener != null && listener.get() != null)
            listener.get().onMessageStateChanged();
    }

    @Override
    public String toString() {
        return "DisputeCommunicationMessage{" +
                "\n     tradeId='" + tradeId + '\'' +
                ",\n     traderId=" + traderId +
                ",\n     senderIsTrader=" + senderIsTrader +
                ",\n     message='" + message + '\'' +
                ",\n     attachments=" + attachments +
                ",\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     date=" + date +
                ",\n     isSystemMessage=" + isSystemMessage +
                ",\n     arrivedProperty=" + arrivedProperty +
                ",\n     storedInMailboxProperty=" + storedInMailboxProperty +
                ",\n     DisputeCommunicationMessage.uid='" + uid + '\'' +
                ",\n     messageVersion=" + messageVersion +
                ",\n     acknowledgedProperty=" + acknowledgedProperty +
                ",\n     sendMessageErrorProperty=" + sendMessageErrorProperty +
                ",\n     ackErrorProperty=" + ackErrorProperty +
                "\n} " + super.toString();
    }
}
