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

import bisq.core.trade.protocol.TradeMessage;

import bisq.network.p2p.DirectMessage;
import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public final class DelayedPayoutTxSignatureRequest extends TradeMessage implements DirectMessage {
    private final NodeAddress senderNodeAddress;
    private final byte[] delayedPayoutTx;
    private final byte[] delayedPayoutTxSellerSignature;

    public DelayedPayoutTxSignatureRequest(String uid,
                                           String tradeId,
                                           NodeAddress senderNodeAddress,
                                           byte[] delayedPayoutTx,
                                           byte[] delayedPayoutTxSellerSignature) {
        this(Version.getP2PMessageVersion(),
                uid,
                tradeId,
                senderNodeAddress,
                delayedPayoutTx,
                delayedPayoutTxSellerSignature);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private DelayedPayoutTxSignatureRequest(int messageVersion,
                                            String uid,
                                            String tradeId,
                                            NodeAddress senderNodeAddress,
                                            byte[] delayedPayoutTx,
                                            byte[] delayedPayoutTxSellerSignature) {
        super(messageVersion, tradeId, uid);
        this.senderNodeAddress = senderNodeAddress;
        this.delayedPayoutTx = delayedPayoutTx;
        this.delayedPayoutTxSellerSignature = delayedPayoutTxSellerSignature;
    }


    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setDelayedPayoutTxSignatureRequest(protobuf.DelayedPayoutTxSignatureRequest.newBuilder()
                        .setUid(uid)
                        .setTradeId(tradeId)
                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                        .setDelayedPayoutTx(ByteString.copyFrom(delayedPayoutTx))
                        .setDelayedPayoutTxSellerSignature(ByteString.copyFrom(delayedPayoutTxSellerSignature)))
                .build();
    }

    public static DelayedPayoutTxSignatureRequest fromProto(protobuf.DelayedPayoutTxSignatureRequest proto,
                                                            int messageVersion) {
        return new DelayedPayoutTxSignatureRequest(messageVersion,
                proto.getUid(),
                proto.getTradeId(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getDelayedPayoutTx().toByteArray(),
                proto.getDelayedPayoutTxSellerSignature().toByteArray());
    }

    @Override
    public String toString() {
        return "DelayedPayoutTxSignatureRequest{" +
                "\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     delayedPayoutTx=" + Utilities.bytesAsHexString(delayedPayoutTx) +
                ",\n     delayedPayoutTxSellerSignature=" + Utilities.bytesAsHexString(delayedPayoutTxSellerSignature) +
                "\n} " + super.toString();
    }
}
