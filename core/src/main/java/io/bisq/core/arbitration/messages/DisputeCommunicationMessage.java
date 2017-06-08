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

package io.bisq.core.arbitration.messages;

import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.core.arbitration.Attachment;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@ToString
@Getter
public final class DisputeCommunicationMessage extends DisputeMessage {
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

    public DisputeCommunicationMessage(String tradeId,
                                       int traderId,
                                       boolean senderIsTrader,
                                       String message,
                                       @Nullable List<Attachment> attachments,
                                       NodeAddress senderNodeAddress,
                                       long date,
                                       boolean arrived,
                                       boolean storedInMailbox,
                                       String uid) {
        super(uid);
        this.tradeId = tradeId;
        this.traderId = traderId;
        this.senderIsTrader = senderIsTrader;
        this.message = message;
        Optional.ofNullable(attachments).ifPresent(e -> addAllAttachments(attachments));
        this.senderNodeAddress = senderNodeAddress;
        this.date = date;
        arrivedProperty = new SimpleBooleanProperty(arrived);
        storedInMailboxProperty = new SimpleBooleanProperty(storedInMailbox);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return NetworkEnvelope.getDefaultBuilder()
                .setDisputeCommunicationMessage(PB.DisputeCommunicationMessage.newBuilder()
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
                )
                .build();
    }

    public static DisputeCommunicationMessage fromProto(PB.DisputeCommunicationMessage proto) {
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
                proto.getUid());
        disputeCommunicationMessage.setSystemMessage(proto.getIsSystemMessage());
        return disputeCommunicationMessage;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addAllAttachments(List<Attachment> attachments) {
        this.attachments.addAll(attachments);
    }

    public void setArrived(@SuppressWarnings("SameParameterValue") boolean arrived) {
        this.arrivedProperty.set(arrived);
    }

    public void setStoredInMailbox(@SuppressWarnings("SameParameterValue") boolean storedInMailbox) {
        this.storedInMailboxProperty.set(storedInMailbox);
    }

    public ReadOnlyBooleanProperty arrivedProperty() {
        return arrivedProperty;
    }

    public ReadOnlyBooleanProperty storedInMailboxProperty() {
        return storedInMailboxProperty;
    }


}
