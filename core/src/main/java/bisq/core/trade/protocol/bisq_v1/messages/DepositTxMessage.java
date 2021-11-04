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

// It is the last message in the take offer phase. We use MailboxMessage instead of DirectMessage to add more tolerance
// in case of network issues and as the message does not trigger further protocol execution.
@EqualsAndHashCode(callSuper = true)
@Value
public final class DepositTxMessage extends TradeMessage implements DirectMessage {
    private final NodeAddress senderNodeAddress;
    private final byte[] depositTxWithoutWitnesses;

    public DepositTxMessage(String uid,
                            String tradeId,
                            NodeAddress senderNodeAddress,
                            byte[] depositTxWithoutWitnesses) {
        this(Version.getP2PMessageVersion(),
                uid,
                tradeId,
                senderNodeAddress,
                depositTxWithoutWitnesses);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private DepositTxMessage(int messageVersion,
                             String uid,
                             String tradeId,
                             NodeAddress senderNodeAddress,
                             byte[] depositTxWithoutWitnesses) {
        super(messageVersion, tradeId, uid);
        this.senderNodeAddress = senderNodeAddress;
        this.depositTxWithoutWitnesses = depositTxWithoutWitnesses;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setDepositTxMessage(protobuf.DepositTxMessage.newBuilder()
                        .setUid(uid)
                        .setTradeId(tradeId)
                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                        .setDepositTxWithoutWitnesses(ByteString.copyFrom(depositTxWithoutWitnesses)))
                .build();
    }

    public static DepositTxMessage fromProto(protobuf.DepositTxMessage proto, int messageVersion) {
        return new DepositTxMessage(messageVersion,
                proto.getUid(),
                proto.getTradeId(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getDepositTxWithoutWitnesses().toByteArray());
    }

    @Override
    public String toString() {
        return "DepositTxMessage{" +
                "\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     depositTxWithoutWitnesses=" + Utilities.bytesAsHexString(depositTxWithoutWitnesses) +
                "\n} " + super.toString();
    }
}
