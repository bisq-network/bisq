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

package io.bisq.protobuffer.message.arbitration;

import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.message.Message;
import io.bisq.protobuffer.payload.arbitration.Attachment;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class DisputeCommunicationMessage extends DisputeMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    private static final Logger log = LoggerFactory.getLogger(DisputeCommunicationMessage.class);

    private final long date;
    private final String tradeId;

    private final int traderId;
    private final boolean senderIsTrader;
    private final String message;
    private final ArrayList<Attachment> attachments = new ArrayList<>();
    private boolean arrived;
    private boolean storedInMailbox;
    private boolean isSystemMessage;
    private final NodeAddress myNodeAddress;

    transient private BooleanProperty arrivedProperty = new SimpleBooleanProperty();
    transient private BooleanProperty storedInMailboxProperty = new SimpleBooleanProperty();

    public DisputeCommunicationMessage(String tradeId, int traderId, boolean senderIsTrader, String message,
                                       @Nullable List<Attachment> attachments, NodeAddress myNodeAddress, long date,
                                       boolean arrived, boolean storedInMailbox) {
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

    public DisputeCommunicationMessage(String tradeId, int traderId, boolean senderIsTrader, String message,
                                       NodeAddress myNodeAddress) {
        this(tradeId, traderId, senderIsTrader, message, null, myNodeAddress, new Date().getTime(),
                false, false);
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

    public Date getDate() {
        return new Date(date);
    }

    public boolean isSenderIsTrader() {
        return senderIsTrader;
    }

    public String getMessage() {
        return message;
    }

    public int getTraderId() {
        return traderId;
    }

    public BooleanProperty arrivedProperty() {
        return arrivedProperty;
    }

    public BooleanProperty storedInMailboxProperty() {
        return storedInMailboxProperty;
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }

    public String getTradeId() {
        return tradeId;
    }

    public boolean isSystemMessage() {
        return isSystemMessage;
    }

    public void setIsSystemMessage(boolean isSystemMessage) {
        this.isSystemMessage = isSystemMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DisputeCommunicationMessage)) return false;

        DisputeCommunicationMessage that = (DisputeCommunicationMessage) o;

        if (date != that.date) return false;
        if (traderId != that.traderId) return false;
        if (senderIsTrader != that.senderIsTrader) return false;
        if (arrived != that.arrived) return false;
        if (storedInMailbox != that.storedInMailbox) return false;
        if (isSystemMessage != that.isSystemMessage) return false;
        if (tradeId != null ? !tradeId.equals(that.tradeId) : that.tradeId != null) return false;
        if (message != null ? !message.equals(that.message) : that.message != null) return false;
        if (attachments != null ? !attachments.equals(that.attachments) : that.attachments != null) return false;
        return !(myNodeAddress != null ? !myNodeAddress.equals(that.myNodeAddress) : that.myNodeAddress != null);

    }

    @Override
    public int hashCode() {
        int result = (int) (date ^ (date >>> 32));
        result = 31 * result + (tradeId != null ? tradeId.hashCode() : 0);
        result = 31 * result + traderId;
        result = 31 * result + (senderIsTrader ? 1 : 0);
        result = 31 * result + (message != null ? message.hashCode() : 0);
        result = 31 * result + (attachments != null ? attachments.hashCode() : 0);
        result = 31 * result + (arrived ? 1 : 0);
        result = 31 * result + (storedInMailbox ? 1 : 0);
        result = 31 * result + (isSystemMessage ? 1 : 0);
        result = 31 * result + (myNodeAddress != null ? myNodeAddress.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DisputeDirectMessage{" +
                "date=" + date +
                ", tradeId='" + tradeId + '\'' +
                ", traderId='" + traderId + '\'' +
                ", senderIsTrader=" + senderIsTrader +
                ", message='" + message + '\'' +
                ", attachments=" + attachments +
                '}';
    }

    @Override
    public PB.Envelope toProto() {
        PB.Envelope.Builder baseEnvelope = Message.getBaseEnvelope();
        return baseEnvelope.setDisputeCommunicationMessage(PB.DisputeCommunicationMessage.newBuilder()
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
