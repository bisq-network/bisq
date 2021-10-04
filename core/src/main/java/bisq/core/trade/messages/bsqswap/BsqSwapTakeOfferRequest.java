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

package bisq.core.trade.messages.bsqswap;

import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;
import bisq.common.crypto.PubKeyRing;

import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)
@Getter
public final class BsqSwapTakeOfferRequest extends TakeOfferRequest {

    public BsqSwapTakeOfferRequest(String tradeId,
                                   NodeAddress senderNodeAddress,
                                   PubKeyRing takerPubKeyRing,
                                   long tradeAmount,
                                   long txFeePerVbyte,
                                   long makerFee,
                                   long takerFee,
                                   long tradeDate) {
        this(Version.getP2PMessageVersion(),
                tradeId,
                UUID.randomUUID().toString(),
                senderNodeAddress,
                takerPubKeyRing,
                tradeAmount,
                txFeePerVbyte,
                makerFee,
                takerFee,
                tradeDate);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BsqSwapTakeOfferRequest(int messageVersion,
                                    String tradeId,
                                    String uid,
                                    NodeAddress senderNodeAddress,
                                    PubKeyRing takerPubKeyRing,
                                    long tradeAmount,
                                    long txFeePerVbyte,
                                    long makerFee,
                                    long takerFee,
                                    long tradeDate) {
        super(messageVersion, tradeId, uid, senderNodeAddress, takerPubKeyRing,
                tradeAmount, txFeePerVbyte, makerFee, takerFee, tradeDate);
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setBsqSwapTakeOfferRequest(protobuf.BsqSwapTakeOfferRequest.newBuilder()
                        .setUid(uid)
                        .setTradeId(tradeId)
                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                        .setTakerPubKeyRing(takerPubKeyRing.toProtoMessage())
                        .setTradeAmount(tradeAmount)
                        .setTxFeePerVbyte(txFeePerVbyte)
                        .setMakerFee(makerFee)
                        .setTakerFee(takerFee)
                        .setTradeDate(tradeDate))
                .build();
    }

    public static BsqSwapTakeOfferRequest fromProto(protobuf.BsqSwapTakeOfferRequest proto,
                                                    int messageVersion) {
        return new BsqSwapTakeOfferRequest(messageVersion,
                proto.getTradeId(),
                proto.getUid(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                PubKeyRing.fromProto(proto.getTakerPubKeyRing()),
                proto.getTradeAmount(),
                proto.getTxFeePerVbyte(),
                proto.getMakerFee(),
                proto.getTakerFee(),
                proto.getTradeDate());
    }
}
