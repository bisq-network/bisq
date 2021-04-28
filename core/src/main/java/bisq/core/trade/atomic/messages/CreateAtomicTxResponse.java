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

package bisq.core.trade.atomic.messages;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.trade.messages.TradeMessage;

import bisq.network.p2p.DirectMessage;
import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public class CreateAtomicTxResponse extends TradeMessage implements DirectMessage {
    NodeAddress senderNodeAddress;
    byte[] atomicTx;
    long makerBsqOutputValue;
    String makerBsqOutputAddress;
    long makerBtcOutputValue;
    String makerBtcOutputAddress;
    List<RawTransactionInput> makerBsqInputs;
    List<RawTransactionInput> makerBtcInputs;

    public CreateAtomicTxResponse(String uid,
                                  String tradeId,
                                  NodeAddress senderNodeAddress,
                                  byte[] atomicTx,
                                  long makerBsqOutputValue,
                                  String makerBsqOutputAddress,
                                  long makerBtcOutputValue,
                                  String makerBtcOutputAddress,
                                  List<RawTransactionInput> makerBsqInputs,
                                  List<RawTransactionInput> makerBtcInputs) {
        this(Version.getP2PMessageVersion(),
                uid,
                tradeId,
                senderNodeAddress,
                atomicTx,
                makerBsqOutputValue,
                makerBsqOutputAddress,
                makerBtcOutputValue,
                makerBtcOutputAddress,
                makerBsqInputs,
                makerBtcInputs);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private CreateAtomicTxResponse(int messageVersion,
                                   String uid,
                                   String tradeId,
                                   NodeAddress senderNodeAddress,
                                   byte[] atomicTx,
                                   long makerBsqOutputValue,
                                   String makerBsqOutputAddress,
                                   long makerBtcOutputValue,
                                   String makerBtcOutputAddress,
                                   List<RawTransactionInput> makerBsqInputs,
                                   List<RawTransactionInput> makerBtcInputs) {
        super(messageVersion, tradeId, uid);
        this.senderNodeAddress = senderNodeAddress;
        this.atomicTx = atomicTx;
        this.makerBsqOutputValue = makerBsqOutputValue;
        this.makerBsqOutputAddress = makerBsqOutputAddress;
        this.makerBtcOutputValue = makerBtcOutputValue;
        this.makerBtcOutputAddress = makerBtcOutputAddress;
        this.makerBsqInputs = makerBsqInputs;
        this.makerBtcInputs = makerBtcInputs;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setCreateAtomicTxResponse(protobuf.CreateAtomicTxResponse.newBuilder()
                        .setUid(uid)
                        .setTradeId(tradeId)
                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                        .setAtomicTx(ByteString.copyFrom(atomicTx))
                        .setMakerBsqOutputValue(makerBsqOutputValue)
                        .setMakerBsqOutputAddress(makerBsqOutputAddress)
                        .setMakerBtcOutputValue(makerBtcOutputValue)
                        .setMakerBtcOutputAddress(makerBtcOutputAddress)
                        .addAllMakerBsqInputs(makerBsqInputs.stream().map(RawTransactionInput::toProtoMessage).collect(
                                Collectors.toList()))
                        .addAllMakerBtcInputs(makerBtcInputs.stream().map(RawTransactionInput::toProtoMessage).collect(
                                Collectors.toList()))
                ).build();
    }

    public static CreateAtomicTxResponse fromProto(protobuf.CreateAtomicTxResponse proto, int messageVersion) {
        return new CreateAtomicTxResponse(messageVersion,
                proto.getUid(),
                proto.getTradeId(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getAtomicTx().toByteArray(),
                proto.getMakerBsqOutputValue(),
                proto.getMakerBsqOutputAddress(),
                proto.getMakerBtcOutputValue(),
                proto.getMakerBtcOutputAddress(),
                proto.getMakerBsqInputsList().stream()
                        .map(RawTransactionInput::fromProto)
                        .collect(Collectors.toList()),
                proto.getMakerBtcInputsList().stream()
                        .map(RawTransactionInput::fromProto)
                        .collect(Collectors.toList())
        );
    }

    @Override
    public String toString() {
        return "CreateAtomicTxResponse{" +
                "\n     senderNodeAddress=" + senderNodeAddress +
                "\n     atomicTx=" + Utilities.bytesAsHexString(atomicTx) +
                "\n     makerBsqOutputValue=" + makerBsqOutputValue +
                "\n     makerBsqOutputAddress=" + makerBsqOutputAddress +
                "\n     makerBtcOutputValue=" + makerBtcOutputValue +
                "\n     makerBtcOutputAddress=" + makerBtcOutputAddress +
                "\n     makerBsqInputs=" + makerBsqInputs +
                "\n     makerBtcInputs=" + makerBtcInputs +
                "\n} " + super.toString();
    }
}
