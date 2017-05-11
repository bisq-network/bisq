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

import io.bisq.common.app.Version;
import io.bisq.common.network.Msg;
import io.bisq.core.arbitration.Attachment;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@ToString
@Getter
@Slf4j
public final class DisputeCommunicationMsg extends DisputeMsg {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private final long date;
    private final String tradeId;
    private final int traderId;
    private final boolean senderIsTrader;
    private final String message;
    private final NodeAddress myNodeAddress;
    @Nullable
    private final ArrayList<Attachment> attachments = new ArrayList<>();
    private boolean arrived;
    private boolean storedInMailbox;
    @Setter
    private boolean isSystemMessage;

    // domain
    transient private BooleanProperty arrivedProperty = new SimpleBooleanProperty();
    transient private BooleanProperty storedInMailboxProperty = new SimpleBooleanProperty();

    public DisputeCommunicationMsg(String tradeId,
                                   int traderId,
                                   boolean senderIsTrader,
                                   String message,
                                   @Nullable List<Attachment> attachments,
                                   NodeAddress myNodeAddress,
                                   long date,
                                   boolean arrived,
                                   boolean storedInMailbox,
                                   String uid) {
        super(uid);
        this.tradeId = tradeId;
        this.traderId = traderId;
        this.senderIsTrader = senderIsTrader;
        this.message = message;
        this.myNodeAddress = myNodeAddress;
        this.date = date;
        this.arrived = arrived;
        this.storedInMailbox = storedInMailbox;
        Optional.ofNullable(attachments).ifPresent(e -> addAllAttachments(attachments));
        updateBooleanProperties();
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            updateBooleanProperties();
        } catch (Throwable t) {
            log.warn("Cannot be deserialized." + t.getMessage());
        }
    }

    private void updateBooleanProperties() {
        arrivedProperty = new SimpleBooleanProperty(arrived);
        storedInMailboxProperty = new SimpleBooleanProperty(storedInMailbox);
    }

    @Override
    public NodeAddress getSenderNodeAddress() {
        return myNodeAddress;
    }

    public void addAttachment(Attachment attachment) {
        attachments.add(attachment);
    }

    public void addAllAttachments(List<Attachment> attachments) {
        this.attachments.addAll(attachments);
    }

    public void setArrived(boolean arrived) {
        this.arrived = arrived;
        this.arrivedProperty.set(arrived);
    }

    public void setStoredInMailbox(boolean storedInMailbox) {
        this.storedInMailbox = storedInMailbox;
        this.storedInMailboxProperty.set(storedInMailbox);
    }

    public BooleanProperty arrivedProperty() {
        return arrivedProperty;
    }

    public BooleanProperty storedInMailboxProperty() {
        return storedInMailboxProperty;
    }

    @Override
    public PB.Msg toEnvelopeProto() {
        PB.Msg.Builder msgBuilder = Msg.getMsgBuilder();
        return msgBuilder.setDisputeCommunicationMessage(PB.DisputeCommunicationMessage.newBuilder()
                .setDate(date)
                .setTradeId(tradeId)
                .setTraderId(traderId)
                .setSenderIsTrader(senderIsTrader)
                .setMessage(message)
                .addAllAttachments(attachments.stream().map(attachment -> attachment.toProto()).collect(Collectors.toList()))
                .setArrived(arrived)
                .setStoredInMailbox(storedInMailbox)
                .setIsSystemMessage(isSystemMessage)
                .setMyNodeAddress(myNodeAddress.toProto())).build();
    }
}
