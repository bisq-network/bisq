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

package bisq.core.support.messages;

import bisq.core.locale.Res;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Attachment;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeResult;

import bisq.network.p2p.NodeAddress;

import bisq.common.UserThread;
import bisq.common.app.Version;
import bisq.common.util.Utilities;

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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import java.lang.ref.WeakReference;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/* Message for direct communication between two nodes. Originally built for trader to
 * arbitrator communication as no other direct communication was allowed. Arbitrator is
 * considered as the server and trader as the client in arbitration chats
 *
 * For trader to trader communication the maker is considered to be the server
 * and the taker is considered as the client.
 * */
@EqualsAndHashCode(callSuper = true) // listener is transient and therefore excluded anyway
@Getter
@Slf4j
public final class ChatMessage extends SupportMessage {
    public static final long TTL = TimeUnit.DAYS.toMillis(7);

    public interface Listener {
        void onMessageStateChanged();
    }

    private final String tradeId;
    private final int traderId;
    // This is only used for the server client relationship
    // If senderIsTrader == true then the sender is the client
    private final boolean senderIsTrader;
    private final String message;
    private final ArrayList<Attachment> attachments = new ArrayList<>();
    private final NodeAddress senderNodeAddress;
    private final long date;
    @Setter
    private boolean isSystemMessage;

    // Added in v1.1.6. for trader chat to store if message was shown in popup
    @Setter
    private boolean wasDisplayed;

    //todo move to base class
    private final BooleanProperty arrivedProperty;
    private final BooleanProperty storedInMailboxProperty;
    private final BooleanProperty acknowledgedProperty;
    private final StringProperty sendMessageErrorProperty;
    private final StringProperty ackErrorProperty;

    transient private WeakReference<Listener> listener;

    public ChatMessage(SupportType supportType,
                       String tradeId,
                       int traderId,
                       boolean senderIsTrader,
                       String message,
                       NodeAddress senderNodeAddress) {
        this(supportType,
                tradeId,
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
                null,
                false);
    }

    public ChatMessage(SupportType supportType,
                       String tradeId,
                       int traderId,
                       boolean senderIsTrader,
                       String message,
                       NodeAddress senderNodeAddress,
                       ArrayList<Attachment> attachments) {
        this(supportType,
                tradeId,
                traderId,
                senderIsTrader,
                message,
                attachments,
                senderNodeAddress,
                new Date().getTime(),
                false,
                false,
                UUID.randomUUID().toString(),
                Version.getP2PMessageVersion(),
                false,
                null,
                null,
                false);
    }

    public ChatMessage(SupportType supportType,
                       String tradeId,
                       int traderId,
                       boolean senderIsTrader,
                       String message,
                       NodeAddress senderNodeAddress,
                       long date) {
        this(supportType,
                tradeId,
                traderId,
                senderIsTrader,
                message,
                null,
                senderNodeAddress,
                date,
                false,
                false,
                UUID.randomUUID().toString(),
                Version.getP2PMessageVersion(),
                false,
                null,
                null,
                false);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private ChatMessage(SupportType supportType,
                        String tradeId,
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
                        @Nullable String ackError,
                        boolean wasDisplayed) {
        super(messageVersion, uid, supportType);
        this.tradeId = tradeId;
        this.traderId = traderId;
        this.senderIsTrader = senderIsTrader;
        this.message = message;
        this.wasDisplayed = wasDisplayed;
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

    // We cannot rename protobuf definition because it would break backward compatibility
    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        protobuf.ChatMessage.Builder builder = protobuf.ChatMessage.newBuilder()
                .setType(SupportType.toProtoMessage(supportType))
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
                .setAcknowledged(acknowledgedProperty.get())
                .setWasDisplayed(wasDisplayed);
        Optional.ofNullable(sendMessageErrorProperty.get()).ifPresent(builder::setSendMessageError);
        Optional.ofNullable(ackErrorProperty.get()).ifPresent(builder::setAckError);
        return getNetworkEnvelopeBuilder()
                .setChatMessage(builder)
                .build();
    }

    // The protobuf definition ChatMessage cannot be changed as it would break backward compatibility.
    public static ChatMessage fromProto(protobuf.ChatMessage proto,
                                        int messageVersion) {
        // If we get a msg from an old client type will be ordinal 0 which is the dispute entry and as we only added
        // the trade case it is the desired behaviour.
        final ChatMessage chatMessage = new ChatMessage(
                SupportType.fromProto(proto.getType()),
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
                proto.getAckError().isEmpty() ? null : proto.getAckError(),
                proto.getWasDisplayed());
        chatMessage.setSystemMessage(proto.getIsSystemMessage());
        return chatMessage;
    }

    public static ChatMessage fromPayloadProto(protobuf.ChatMessage proto) {
        // We have the case that an envelope got wrapped into a payload.
        // We don't check the message version here as it was checked in the carrier envelope already (in connection class)
        // Payloads don't have a message version and are also used for persistence
        // We set the value to -1 to indicate it is set but irrelevant
        return fromProto(proto, -1);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addAllAttachments(List<Attachment> attachments) {
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

    // each chat message notifies the user if an ACK is not received in time
    public void startAckTimer() {
        UserThread.runAfter(() -> {
            if (!this.getAcknowledgedProperty().get() && !this.getStoredInMailboxProperty().get()) {
                this.setArrived(false);
                this.setAckError(Res.get("support.errorTimeout"));
            }
        }, 60, TimeUnit.SECONDS);
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

    public boolean isResultMessage(Dispute dispute) {
        DisputeResult disputeResult = dispute.getDisputeResultProperty().get();
        if (disputeResult == null) {
            return false;
        }

        ChatMessage resultChatMessage = disputeResult.getChatMessage();
        return resultChatMessage != null && resultChatMessage.getUid().equals(uid);
    }

    @Override
    public long getTTL() {
        return TTL;
    }

    private void notifyChangeListener() {
        if (listener != null) {
            Listener listener = this.listener.get();
            if (listener != null) {
                listener.onMessageStateChanged();
            }
        }
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "\n     tradeId='" + tradeId + '\'' +
                ",\n     traderId=" + traderId +
                ",\n     senderIsTrader=" + senderIsTrader +
                ",\n     message='" + message + '\'' +
                ",\n     attachments=" + attachments +
                ",\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     date=" + date +
                ",\n     isSystemMessage=" + isSystemMessage +
                ",\n     wasDisplayed=" + wasDisplayed +
                ",\n     arrivedProperty=" + arrivedProperty +
                ",\n     storedInMailboxProperty=" + storedInMailboxProperty +
                ",\n     acknowledgedProperty=" + acknowledgedProperty +
                ",\n     sendMessageErrorProperty=" + sendMessageErrorProperty +
                ",\n     ackErrorProperty=" + ackErrorProperty +
                "\n} " + super.toString();
    }
}
