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

package bisq.core.trade.messages;

import bisq.network.p2p.DirectMessage;
import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public final class DelayedPayoutTxSignatureResponse extends TradeMessage implements DirectMessage {
    private final NodeAddress senderNodeAddress;
    private final byte[] delayedPayoutTxSignature;

    public DelayedPayoutTxSignatureResponse(String uid,
                                            String tradeId,
                                            NodeAddress senderNodeAddress,
                                            byte[] delayedPayoutTxSignature) {
        this(Version.getP2PMessageVersion(),
                uid,
                tradeId,
                senderNodeAddress,
                delayedPayoutTxSignature);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private DelayedPayoutTxSignatureResponse(int messageVersion,
                                             String uid,
                                             String tradeId,
                                             NodeAddress senderNodeAddress,
                                             byte[] delayedPayoutTxSignature) {
        super(messageVersion, tradeId, uid);
        this.senderNodeAddress = senderNodeAddress;
        this.delayedPayoutTxSignature = delayedPayoutTxSignature;
    }


    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setDelayedPayoutTxSignatureResponse(protobuf.DelayedPayoutTxSignatureResponse.newBuilder()
                        .setUid(uid)
                        .setTradeId(tradeId)
                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                        .setDelayedPayoutTxSignature(ByteString.copyFrom(delayedPayoutTxSignature))
                )
                .build();
    }

    public static DelayedPayoutTxSignatureResponse fromProto(protobuf.DelayedPayoutTxSignatureResponse proto, int messageVersion) {
        return new DelayedPayoutTxSignatureResponse(messageVersion,
                proto.getUid(),
                proto.getTradeId(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getDelayedPayoutTxSignature().toByteArray());
    }

    @Override
    public String toString() {
        return "DelayedPayoutTxSignatureResponse{" +
                "\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     delayedPayoutTxSignature=" + Utilities.bytesAsHexString(delayedPayoutTxSignature) +
                "\n} " + super.toString();
    }
}
