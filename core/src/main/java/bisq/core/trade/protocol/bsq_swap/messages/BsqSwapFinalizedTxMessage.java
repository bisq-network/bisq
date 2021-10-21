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

package bisq.core.trade.protocol.bsq_swap.messages;

import bisq.core.trade.protocol.TradeMessage;

import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;

import com.google.protobuf.ByteString;

import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)
@Getter
public final class BsqSwapFinalizedTxMessage extends TradeMessage {
    private final NodeAddress senderNodeAddress;
    private final byte[] tx;


    public BsqSwapFinalizedTxMessage(String tradeId,
                                     NodeAddress senderNodeAddress,
                                     byte[] tx) {
        this(Version.getP2PMessageVersion(),
                tradeId,
                UUID.randomUUID().toString(),
                senderNodeAddress,
                tx);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BsqSwapFinalizedTxMessage(int messageVersion,
                                      String tradeId,
                                      String uid,
                                      NodeAddress senderNodeAddress,
                                      byte[] tx) {
        super(messageVersion, tradeId, uid);
        this.senderNodeAddress = senderNodeAddress;
        this.tx = tx;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setBsqSwapFinalizedTxMessage(protobuf.BsqSwapFinalizedTxMessage.newBuilder()
                        .setTradeId(tradeId)
                        .setUid(uid)
                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                        .setTx(ByteString.copyFrom(tx)))
                .build();
    }

    public static BsqSwapFinalizedTxMessage fromProto(protobuf.BsqSwapFinalizedTxMessage proto, int messageVersion) {
        return new BsqSwapFinalizedTxMessage(messageVersion,
                proto.getTradeId(),
                proto.getUid(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getTx().toByteArray()
        );
    }
}
