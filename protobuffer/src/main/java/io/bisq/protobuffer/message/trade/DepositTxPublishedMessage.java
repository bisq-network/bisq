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

package io.bisq.protobuffer.message.trade;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.message.Message;
import io.bisq.protobuffer.message.p2p.MailboxMessage;
import io.bisq.protobuffer.payload.p2p.NodeAddress;

import javax.annotation.concurrent.Immutable;
import java.util.Arrays;
import java.util.UUID;

@Immutable
public final class DepositTxPublishedMessage extends TradeMessage implements MailboxMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public final byte[] depositTx;
    private final NodeAddress senderNodeAddress;
    private final String uid;

    public DepositTxPublishedMessage(String tradeId, byte[] depositTx, NodeAddress senderNodeAddress, String uid) {
        super(tradeId);
        this.depositTx = depositTx;
        this.senderNodeAddress = senderNodeAddress;
        this.uid = uid;
    }

    public DepositTxPublishedMessage(String tradeId, byte[] depositTx, NodeAddress senderNodeAddress) {
        this(tradeId, depositTx, senderNodeAddress, UUID.randomUUID().toString());
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
        if (!(o instanceof DepositTxPublishedMessage)) return false;
        if (!super.equals(o)) return false;

        DepositTxPublishedMessage that = (DepositTxPublishedMessage) o;

        if (!Arrays.equals(depositTx, that.depositTx)) return false;
        if (senderNodeAddress != null ? !senderNodeAddress.equals(that.senderNodeAddress) : that.senderNodeAddress != null)
            return false;
        return !(uid != null ? !uid.equals(that.uid) : that.uid != null);

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (depositTx != null ? Arrays.hashCode(depositTx) : 0);
        result = 31 * result + (senderNodeAddress != null ? senderNodeAddress.hashCode() : 0);
        result = 31 * result + (uid != null ? uid.hashCode() : 0);
        return result;
    }

    @Override
    public PB.Envelope toProto() {
        PB.Envelope.Builder baseEnvelope = Message.getBaseEnvelope();
        return baseEnvelope.setDepositTxPublishedMessage(PB.DepositTxPublishedMessage.newBuilder()
                .setMessageVersion(getMessageVersion())
                .setTradeId(tradeId)
                .setDepositTx(ByteString.copyFrom(depositTx))
                .setSenderNodeAddress(senderNodeAddress.toProto())
                .setUid(uid)).build();
    }
}
