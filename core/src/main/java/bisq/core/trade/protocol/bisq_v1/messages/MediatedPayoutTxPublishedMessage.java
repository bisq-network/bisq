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
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public final class MediatedPayoutTxPublishedMessage extends TradeMailboxMessage {
    private final byte[] payoutTx;
    private final NodeAddress senderNodeAddress;

    public MediatedPayoutTxPublishedMessage(String tradeId,
                                            byte[] payoutTx,
                                            NodeAddress senderNodeAddress,
                                            String uid) {
        this(tradeId,
                payoutTx,
                senderNodeAddress,
                uid,
                Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private MediatedPayoutTxPublishedMessage(String tradeId,
                                             byte[] payoutTx,
                                             NodeAddress senderNodeAddress,
                                             String uid,
                                             int messageVersion) {
        super(messageVersion, tradeId, uid);
        this.payoutTx = payoutTx;
        this.senderNodeAddress = senderNodeAddress;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setMediatedPayoutTxPublishedMessage(protobuf.MediatedPayoutTxPublishedMessage.newBuilder()
                        .setTradeId(tradeId)
                        .setPayoutTx(ByteString.copyFrom(payoutTx))
                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                        .setUid(uid))
                .build();
    }

    public static NetworkEnvelope fromProto(protobuf.MediatedPayoutTxPublishedMessage proto, int messageVersion) {
        return new MediatedPayoutTxPublishedMessage(proto.getTradeId(),
                proto.getPayoutTx().toByteArray(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getUid(),
                messageVersion);
    }

    @Override
    public String toString() {
        return "MediatedPayoutTxPublishedMessage{" +
                "\n     payoutTx=" + Utilities.bytesAsHexString(payoutTx) +
                ",\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     uid='" + uid + '\'' +
                "\n} " + super.toString();
    }
}
