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

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.message.Message;
import io.bisq.protobuffer.payload.p2p.NodeAddress;

import java.util.Arrays;

public final class PeerPublishedPayoutTxMessage extends DisputeMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public final byte[] transaction;
    public final String tradeId;
    private final NodeAddress myNodeAddress;

    public PeerPublishedPayoutTxMessage(byte[] transaction, String tradeId, NodeAddress myNodeAddress) {
        super();
        this.transaction = transaction;
        this.tradeId = tradeId;
        this.myNodeAddress = myNodeAddress;
    }

    public PeerPublishedPayoutTxMessage(byte[] transaction, String tradeId, NodeAddress myNodeAddress, String uid) {
        super(uid);
        this.transaction = transaction;
        this.tradeId = tradeId;
        this.myNodeAddress = myNodeAddress;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PeerPublishedPayoutTxMessage)) return false;

        PeerPublishedPayoutTxMessage that = (PeerPublishedPayoutTxMessage) o;

        if (!Arrays.equals(transaction, that.transaction)) return false;
        if (tradeId != null ? !tradeId.equals(that.tradeId) : that.tradeId != null) return false;
        return !(myNodeAddress != null ? !myNodeAddress.equals(that.myNodeAddress) : that.myNodeAddress != null);

    }

    @Override
    public int hashCode() {
        int result = transaction != null ? Arrays.hashCode(transaction) : 0;
        result = 31 * result + (tradeId != null ? tradeId.hashCode() : 0);
        result = 31 * result + (myNodeAddress != null ? myNodeAddress.hashCode() : 0);
        return result;
    }

    @Override
    public NodeAddress getSenderNodeAddress() {
        return myNodeAddress;
    }

    @Override
    public PB.Envelope toProto() {
        PB.Envelope.Builder baseEnvelope = Message.getBaseEnvelope();
        return baseEnvelope.setPeerPublishedPayoutTxMessage(PB.PeerPublishedPayoutTxMessage.newBuilder()
                .setTransaction(ByteString.copyFrom(transaction))
                .setTradeId(tradeId)
                .setMyNodeAddress(myNodeAddress.toProto())).build();
    }
}
