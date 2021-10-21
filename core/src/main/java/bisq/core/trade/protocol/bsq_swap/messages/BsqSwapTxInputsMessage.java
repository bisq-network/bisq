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

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.trade.protocol.TradeMessage;

import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)
@Getter
public final class BsqSwapTxInputsMessage extends TradeMessage implements TxInputsMessage {
    private final NodeAddress senderNodeAddress;
    private final List<RawTransactionInput> bsqInputs;
    private final long bsqChange;
    private final String buyersBtcPayoutAddress;
    private final String buyersBsqChangeAddress;

    public BsqSwapTxInputsMessage(String tradeId,
                                  NodeAddress senderNodeAddress,
                                  List<RawTransactionInput> bsqInputs,
                                  long bsqChange,
                                  String buyersBtcPayoutAddress,
                                  String buyersBsqChangeAddress) {
        this(Version.getP2PMessageVersion(),
                tradeId,
                UUID.randomUUID().toString(),
                senderNodeAddress,
                bsqInputs,
                bsqChange,
                buyersBtcPayoutAddress,
                buyersBsqChangeAddress);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BsqSwapTxInputsMessage(int messageVersion,
                                   String tradeId,
                                   String uid,
                                   NodeAddress senderNodeAddress,
                                   List<RawTransactionInput> bsqInputs,
                                   long bsqChange,
                                   String buyersBtcPayoutAddress,
                                   String buyersBsqChangeAddress) {
        super(messageVersion, tradeId, uid);
        this.senderNodeAddress = senderNodeAddress;
        this.bsqInputs = bsqInputs;
        this.bsqChange = bsqChange;
        this.buyersBtcPayoutAddress = buyersBtcPayoutAddress;
        this.buyersBsqChangeAddress = buyersBsqChangeAddress;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setBsqSwapTxInputsMessage(protobuf.BsqSwapTxInputsMessage.newBuilder()
                        .setUid(uid)
                        .setTradeId(tradeId)
                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                        .addAllBsqInputs(bsqInputs.stream().map(RawTransactionInput::toProtoMessage).collect(
                                Collectors.toList()))
                        .setBsqChange(bsqChange)
                        .setBuyersBtcPayoutAddress(buyersBtcPayoutAddress)
                        .setBuyersBsqChangeAddress(buyersBsqChangeAddress))
                .build();
    }

    public static BsqSwapTxInputsMessage fromProto(protobuf.BsqSwapTxInputsMessage proto,
                                                   int messageVersion) {
        return new BsqSwapTxInputsMessage(messageVersion,
                proto.getTradeId(),
                proto.getUid(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getBsqInputsList().stream()
                        .map(RawTransactionInput::fromProto)
                        .collect(Collectors.toList()),
                proto.getBsqChange(),
                proto.getBuyersBtcPayoutAddress(),
                proto.getBuyersBsqChangeAddress());
    }
}
