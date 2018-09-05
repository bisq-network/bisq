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

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;

import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public final class DepositTxPublishedMessage extends TradeMessage implements MailboxMessage {
    private final byte[] depositTx;
    private final NodeAddress senderNodeAddress;

    public DepositTxPublishedMessage(String tradeId,
                                     byte[] depositTx,
                                     NodeAddress senderNodeAddress,
                                     String uid) {
        this(tradeId,
                depositTx,
                senderNodeAddress,
                uid,
                Version.getP2PMessageVersion());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private DepositTxPublishedMessage(String tradeId,
                                      byte[] depositTx,
                                      NodeAddress senderNodeAddress,
                                      String uid,
                                      int messageVersion) {
        super(messageVersion, tradeId, uid);
        this.depositTx = depositTx;
        this.senderNodeAddress = senderNodeAddress;
    }


    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setDepositTxPublishedMessage(PB.DepositTxPublishedMessage.newBuilder()
                        .setTradeId(tradeId)
                        .setDepositTx(ByteString.copyFrom(depositTx))
                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                        .setUid(uid))
                .build();
    }

    public static DepositTxPublishedMessage fromProto(PB.DepositTxPublishedMessage proto, int messageVersion) {
        return new DepositTxPublishedMessage(proto.getTradeId(),
                proto.getDepositTx().toByteArray(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getUid(),
                messageVersion);
    }


    @Override
    public String toString() {
        return "DepositTxPublishedMessage{" +
                "\n     depositTx=" + Utilities.bytesAsHexString(depositTx) +
                ",\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     uid='" + uid + '\'' +
                "\n} " + super.toString();
    }
}
