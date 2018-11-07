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

package bisq.core.arbitration.messages;

import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;
import bisq.common.util.Utilities;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public final class PeerPublishedDisputePayoutTxMessage extends DisputeMessage {
    private final byte[] transaction;
    private final String tradeId;
    private final NodeAddress senderNodeAddress;

    public PeerPublishedDisputePayoutTxMessage(byte[] transaction,
                                               String tradeId,
                                               NodeAddress senderNodeAddress,
                                               String uid) {
        this(transaction,
                tradeId,
                senderNodeAddress,
                uid,
                Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private PeerPublishedDisputePayoutTxMessage(byte[] transaction,
                                                String tradeId,
                                                NodeAddress senderNodeAddress,
                                                String uid,
                                                int messageVersion) {
        super(messageVersion, uid);
        this.transaction = transaction;
        this.tradeId = tradeId;
        this.senderNodeAddress = senderNodeAddress;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setPeerPublishedDisputePayoutTxMessage(PB.PeerPublishedDisputePayoutTxMessage.newBuilder()
                        .setTransaction(ByteString.copyFrom(transaction))
                        .setTradeId(tradeId)
                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                        .setUid(uid))
                .build();
    }

    public static PeerPublishedDisputePayoutTxMessage fromProto(PB.PeerPublishedDisputePayoutTxMessage proto, int messageVersion) {
        return new PeerPublishedDisputePayoutTxMessage(proto.getTransaction().toByteArray(),
                proto.getTradeId(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getUid(),
                messageVersion);
    }

    @Override
    public String getTradeId() {
        return tradeId;
    }

    @Override
    public String toString() {
        return "PeerPublishedDisputePayoutTxMessage{" +
                "\n     transaction=" + Utilities.bytesAsHexString(transaction) +
                ",\n     tradeId='" + tradeId + '\'' +
                ",\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     PeerPublishedDisputePayoutTxMessage.uid='" + uid + '\'' +
                ",\n     messageVersion=" + messageVersion +
                "\n} " + super.toString();
    }
}
