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
public final class DelayedPayoutTxSignatureRequest extends TradeMessage implements DirectMessage {
    private final NodeAddress senderNodeAddress;
    private final byte[] delayedPayoutTx;

    public DelayedPayoutTxSignatureRequest(String uid,
                                           String tradeId,
                                           NodeAddress senderNodeAddress,
                                           byte[] delayedPayoutTx) {
        this(Version.getP2PMessageVersion(),
                uid,
                tradeId,
                senderNodeAddress,
                delayedPayoutTx);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private DelayedPayoutTxSignatureRequest(int messageVersion,
                                            String uid,
                                            String tradeId,
                                            NodeAddress senderNodeAddress,
                                            byte[] delayedPayoutTx) {
        super(messageVersion, tradeId, uid);
        this.senderNodeAddress = senderNodeAddress;
        this.delayedPayoutTx = delayedPayoutTx;
    }


    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setDelayedPayoutTxSignatureRequest(protobuf.DelayedPayoutTxSignatureRequest.newBuilder()
                        .setUid(uid)
                        .setTradeId(tradeId)
                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                        .setDelayedPayoutTx(ByteString.copyFrom(delayedPayoutTx)))
                .build();
    }

    public static DelayedPayoutTxSignatureRequest fromProto(protobuf.DelayedPayoutTxSignatureRequest proto, int messageVersion) {
        return new DelayedPayoutTxSignatureRequest(messageVersion,
                proto.getUid(),
                proto.getTradeId(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getDelayedPayoutTx().toByteArray());
    }

    @Override
    public String toString() {
        return "DelayedPayoutTxSignatureRequest{" +
                "\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     delayedPayoutTx=" + Utilities.bytesAsHexString(delayedPayoutTx) +
                "\n} " + super.toString();
    }
}
