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

package bisq.core.trade.protocol.bisq_v1.messages;

import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;

import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public final class PeerPublishedDelayedPayoutTxMessage extends TradeMailboxMessage {
    private final NodeAddress senderNodeAddress;

    public PeerPublishedDelayedPayoutTxMessage(String uid,
                                               String tradeId,
                                               NodeAddress senderNodeAddress) {
        this(Version.getP2PMessageVersion(),
                uid,
                tradeId,
                senderNodeAddress);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private PeerPublishedDelayedPayoutTxMessage(int messageVersion,
                                                String uid,
                                                String tradeId,
                                                NodeAddress senderNodeAddress) {
        super(messageVersion, tradeId, uid);
        this.senderNodeAddress = senderNodeAddress;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        final protobuf.PeerPublishedDelayedPayoutTxMessage.Builder builder = protobuf.PeerPublishedDelayedPayoutTxMessage.newBuilder();
        builder.setUid(uid)
                .setTradeId(tradeId)
                .setSenderNodeAddress(senderNodeAddress.toProtoMessage());
        return getNetworkEnvelopeBuilder().setPeerPublishedDelayedPayoutTxMessage(builder).build();
    }

    public static PeerPublishedDelayedPayoutTxMessage fromProto(protobuf.PeerPublishedDelayedPayoutTxMessage proto, int messageVersion) {
        return new PeerPublishedDelayedPayoutTxMessage(messageVersion,
                proto.getUid(),
                proto.getTradeId(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()));
    }

    @Override
    public String toString() {
        return "PeerPublishedDelayedPayoutTxMessage{" +
                "\n     senderNodeAddress=" + senderNodeAddress +
                "\n} " + super.toString();
    }
}
