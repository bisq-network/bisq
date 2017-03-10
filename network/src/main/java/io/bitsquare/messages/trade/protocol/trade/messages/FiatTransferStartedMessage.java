/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.messages.trade.protocol.trade.messages;

import io.bitsquare.app.Version;
import io.bitsquare.common.util.ProtoBufferUtils;
import io.bitsquare.common.wire.proto.Messages;
import io.bitsquare.messages.protocol.trade.TradeMessage;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.messaging.MailboxMessage;

import javax.annotation.concurrent.Immutable;
import java.util.UUID;

@Immutable
public final class FiatTransferStartedMessage extends TradeMessage implements MailboxMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public final String buyerPayoutAddress;
    private final NodeAddress senderNodeAddress;
    private final String uid;

    public FiatTransferStartedMessage(String tradeId, String buyerPayoutAddress,
                                      NodeAddress senderNodeAddress, String uid) {
        super(tradeId);
        this.buyerPayoutAddress = buyerPayoutAddress;
        this.senderNodeAddress = senderNodeAddress;
        this.uid = uid;
    }

    public FiatTransferStartedMessage(String tradeId, String buyerPayoutAddress, NodeAddress senderNodeAddress) {
        this(tradeId, buyerPayoutAddress, senderNodeAddress, UUID.randomUUID().toString());
    }

    @Override
    public NodeAddress getSenderNodeAddress() {
        return senderNodeAddress;
    }

    @Override
    public String getUID() {
        return uid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FiatTransferStartedMessage)) return false;
        if (!super.equals(o)) return false;

        FiatTransferStartedMessage that = (FiatTransferStartedMessage) o;

        if (buyerPayoutAddress != null ? !buyerPayoutAddress.equals(that.buyerPayoutAddress) : that.buyerPayoutAddress != null)
            return false;
        if (senderNodeAddress != null ? !senderNodeAddress.equals(that.senderNodeAddress) : that.senderNodeAddress != null)
            return false;
        return !(uid != null ? !uid.equals(that.uid) : that.uid != null);

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (buyerPayoutAddress != null ? buyerPayoutAddress.hashCode() : 0);
        result = 31 * result + (senderNodeAddress != null ? senderNodeAddress.hashCode() : 0);
        result = 31 * result + (uid != null ? uid.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "FiatTransferStartedMessage{" +
                "buyerPayoutAddress='" + buyerPayoutAddress + '\'' +
                ", senderNodeAddress=" + senderNodeAddress +
                ", uid='" + uid + '\'' +
                "} " + super.toString();
    }

    @Override
    public Messages.Envelope toProtoBuf() {
        Messages.Envelope.Builder baseEnvelope = ProtoBufferUtils.getBaseEnvelope();
        return baseEnvelope.setFiatTransferStartedMessage(baseEnvelope.getFiatTransferStartedMessageBuilder()
                .setMessageVersion(getMessageVersion())
                .setTradeId(tradeId)
                .setBuyerPayoutAddress(buyerPayoutAddress)
                .setSenderNodeAddress(senderNodeAddress.toProtoBuf())
                .setUid(uid)).build();
    }
}
