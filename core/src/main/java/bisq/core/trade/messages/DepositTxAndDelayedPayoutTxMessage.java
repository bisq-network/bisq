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

import bisq.network.p2p.MailboxMessage;
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
public final class DepositTxAndDelayedPayoutTxMessage extends TradeMessage implements MailboxMessage {
    private final NodeAddress senderNodeAddress;
    private final byte[] depositTx;
    private final byte[] delayedPayoutTx;

    public DepositTxAndDelayedPayoutTxMessage(String uid,
                                              String tradeId,
                                              NodeAddress senderNodeAddress,
                                              byte[] depositTx,
                                              byte[] delayedPayoutTx) {
        this(Version.getP2PMessageVersion(),
                uid,
                tradeId,
                senderNodeAddress,
                depositTx,
                delayedPayoutTx);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private DepositTxAndDelayedPayoutTxMessage(int messageVersion,
                                               String uid,
                                               String tradeId,
                                               NodeAddress senderNodeAddress,
                                               byte[] depositTx,
                                               byte[] delayedPayoutTx) {
        super(messageVersion, tradeId, uid);
        this.senderNodeAddress = senderNodeAddress;
        this.depositTx = depositTx;
        this.delayedPayoutTx = delayedPayoutTx;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setDepositTxAndDelayedPayoutTxMessage(protobuf.DepositTxAndDelayedPayoutTxMessage.newBuilder()
                        .setUid(uid)
                        .setTradeId(tradeId)
                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                        .setDepositTx(ByteString.copyFrom(depositTx))
                        .setDelayedPayoutTx(ByteString.copyFrom(delayedPayoutTx)))
                .build();
    }

    public static DepositTxAndDelayedPayoutTxMessage fromProto(protobuf.DepositTxAndDelayedPayoutTxMessage proto, int messageVersion) {
        return new DepositTxAndDelayedPayoutTxMessage(messageVersion,
                proto.getUid(),
                proto.getTradeId(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getDepositTx().toByteArray(),
                proto.getDelayedPayoutTx().toByteArray());
    }

    @Override
    public String toString() {
        return "DepositTxAndDelayedPayoutTxMessage{" +
                "\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     depositTx=" + Utilities.bytesAsHexString(depositTx) +
                ",\n     delayedPayoutTx=" + Utilities.bytesAsHexString(delayedPayoutTx) +
                "\n} " + super.toString();
    }
}
