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

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.network.Msg;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public final class PeerPublishedPayoutTxMsg extends DisputeMsg {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public final byte[] transaction;
    public final String tradeId;
    private final NodeAddress myNodeAddress;

    public PeerPublishedPayoutTxMsg(byte[] transaction, String tradeId, NodeAddress myNodeAddress, String uid) {
        super(uid);
        this.transaction = transaction;
        this.tradeId = tradeId;
        this.myNodeAddress = myNodeAddress;
    }

    @Override
    public NodeAddress getSenderNodeAddress() {
        return myNodeAddress;
    }

    @Override
    public PB.Msg toProtoMsg() {
        PB.Msg.Builder msgBuilder = Msg.getMsgBuilder();
        return msgBuilder.setPeerPublishedPayoutTxMessage(PB.PeerPublishedPayoutTxMessage.newBuilder()
                .setTransaction(ByteString.copyFrom(transaction))
                .setTradeId(tradeId)
                .setMyNodeAddress(myNodeAddress.toProtoMessage())).build();
    }

    // transaction not displayed for privacy reasons...
    @Override
    public String toString() {
        return "PeerPublishedPayoutTxMessage{" +
                "transaction not displayed for privacy reasons..." +
                ", tradeId='" + tradeId + '\'' +
                ", myNodeAddress=" + myNodeAddress +
                "} " + super.toString();
    }
}
