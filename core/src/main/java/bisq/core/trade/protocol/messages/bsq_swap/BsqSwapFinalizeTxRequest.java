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

package bisq.core.trade.protocol.messages.bsq_swap;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.trade.protocol.messages.TradeMessage;

import bisq.network.p2p.DirectMessage;
import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;

import com.google.protobuf.ByteString;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)
@Getter
public final class BsqSwapFinalizeTxRequest extends TradeMessage implements DirectMessage {
    private final NodeAddress senderNodeAddress;
    private final byte[] tx;
    private final List<RawTransactionInput> btcInputs;

    public BsqSwapFinalizeTxRequest(String tradeId,
                                    NodeAddress senderNodeAddress,
                                    byte[] tx,
                                    List<RawTransactionInput> btcInputs) {
        this(Version.getP2PMessageVersion(),
                tradeId,
                UUID.randomUUID().toString(),
                senderNodeAddress,
                tx,
                btcInputs);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BsqSwapFinalizeTxRequest(int messageVersion,
                                     String tradeId,
                                     String uid,
                                     NodeAddress senderNodeAddress,
                                     byte[] tx,
                                     List<RawTransactionInput> btcInputs) {
        super(messageVersion, tradeId, uid);
        this.senderNodeAddress = senderNodeAddress;
        this.tx = tx;
        this.btcInputs = btcInputs;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setBsqSwapFinalizeTxRequest(protobuf.BsqSwapFinalizeTxRequest.newBuilder()
                        .setTradeId(tradeId)
                        .setUid(uid)
                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                        .setTx(ByteString.copyFrom(tx))
                        .addAllBtcInputs(btcInputs.stream().map(RawTransactionInput::toProtoMessage).collect(
                                Collectors.toList())))
                .build();
    }

    public static BsqSwapFinalizeTxRequest fromProto(protobuf.BsqSwapFinalizeTxRequest proto, int messageVersion) {
        return new BsqSwapFinalizeTxRequest(messageVersion,
                proto.getTradeId(),
                proto.getUid(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getTx().toByteArray(),
                proto.getBtcInputsList().stream()
                        .map(RawTransactionInput::fromProto)
                        .collect(Collectors.toList())
        );
    }
}
