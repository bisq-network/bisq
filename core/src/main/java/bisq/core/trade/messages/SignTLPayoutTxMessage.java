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

import io.bisq.generated.protobuffer.PB;

import lombok.EqualsAndHashCode;
import lombok.Value;

//TODO implement
@EqualsAndHashCode(callSuper = true)
@Value
public final class SignTLPayoutTxMessage extends TradeMessage implements MailboxMessage {
    private final NodeAddress senderNodeAddress;

    public SignTLPayoutTxMessage(String tradeId,
                                 NodeAddress senderNodeAddress,
                                 String uid) {
        this(tradeId,
                senderNodeAddress,
                uid,
                Version.getP2PMessageVersion());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private SignTLPayoutTxMessage(String tradeId,
                                  NodeAddress senderNodeAddress,
                                  String uid,
                                  int messageVersion) {
        super(messageVersion, tradeId, uid);
        this.senderNodeAddress = senderNodeAddress;
    }


    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setDepositTxPublishedMessage(PB.DepositTxPublishedMessage.newBuilder()
                        .setTradeId(tradeId)
                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                        .setUid(uid))
                .build();
    }

    public static SignTLPayoutTxMessage fromProto(PB.DepositTxPublishedMessage proto, int messageVersion) {
        return new SignTLPayoutTxMessage(proto.getTradeId(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getUid(),
                messageVersion);
    }


    @Override
    public String toString() {
        return "DepositTxPublishedMessage{" +
                ",\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     uid='" + uid + '\'' +
                "\n} " + super.toString();
    }
}
