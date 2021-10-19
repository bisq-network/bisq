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
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Value
@EqualsAndHashCode(callSuper = true)
public class MediatedPayoutTxSignatureMessage extends TradeMailboxMessage {
    private final byte[] txSignature;
    private final NodeAddress senderNodeAddress;

    public MediatedPayoutTxSignatureMessage(byte[] txSignature,
                                            String tradeId,
                                            NodeAddress senderNodeAddress,
                                            String uid) {
        this(txSignature,
                tradeId,
                senderNodeAddress,
                uid,
                Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private MediatedPayoutTxSignatureMessage(byte[] txSignature,
                                             String tradeId,
                                             NodeAddress senderNodeAddress,
                                             String uid,
                                             int messageVersion) {
        super(messageVersion, tradeId, uid);
        this.txSignature = txSignature;
        this.senderNodeAddress = senderNodeAddress;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setMediatedPayoutTxSignatureMessage(protobuf.MediatedPayoutTxSignatureMessage.newBuilder()
                        .setTxSignature(ByteString.copyFrom(txSignature))
                        .setTradeId(tradeId)
                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                        .setUid(uid))
                .build();
    }

    public static MediatedPayoutTxSignatureMessage fromProto(protobuf.MediatedPayoutTxSignatureMessage proto,
                                                             int messageVersion) {
        return new MediatedPayoutTxSignatureMessage(proto.getTxSignature().toByteArray(),
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
        return "MediatedPayoutSignatureMessage{" +
                "\n     txSignature=" + Utilities.bytesAsHexString(txSignature) +
                ",\n     tradeId='" + tradeId + '\'' +
                ",\n     senderNodeAddress=" + senderNodeAddress +
                "\n} " + super.toString();
    }
}
