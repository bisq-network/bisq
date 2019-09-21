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

// It is the last message in the take offer phase. We use MailboxMessage instead of DirectMessage to add more tolerance
// in case of network issues and as the message does not trigger further protocol execution.
@EqualsAndHashCode(callSuper = true)
@Value
public final class DepositTxMessage extends TradeMessage implements DirectMessage {
    private final NodeAddress senderNodeAddress;
    private final byte[] depositTx;

    public DepositTxMessage(String uid,
                            String tradeId,
                            NodeAddress senderNodeAddress,
                            byte[] depositTx) {
        this(Version.getP2PMessageVersion(),
                uid,
                tradeId,
                senderNodeAddress,
                depositTx);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private DepositTxMessage(int messageVersion,
                             String uid,
                             String tradeId,
                             NodeAddress senderNodeAddress,
                             byte[] depositTx) {
        super(messageVersion, tradeId, uid);
        this.senderNodeAddress = senderNodeAddress;
        this.depositTx = depositTx;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setDepositTxMessage(protobuf.DepositTxMessage.newBuilder()
                        .setUid(uid)
                        .setTradeId(tradeId)
                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                        .setDepositTx(ByteString.copyFrom(depositTx)))
                .build();
    }

    public static DepositTxMessage fromProto(protobuf.DepositTxMessage proto, int messageVersion) {
        return new DepositTxMessage(messageVersion,
                proto.getUid(),
                proto.getTradeId(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getDepositTx().toByteArray());
    }

    @Override
    public String toString() {
        return "DepositTxMessage{" +
                "\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     depositTx=" + Utilities.bytesAsHexString(depositTx) +
                "\n} " + super.toString();
    }
}
