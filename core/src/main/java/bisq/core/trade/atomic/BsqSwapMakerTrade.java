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

package bisq.core.trade.atomic;

import bisq.core.offer.Offer;
import bisq.core.proto.CoreProtoResolver;
import bisq.core.trade.Tradable;
import bisq.core.trade.atomic.protocol.BsqSwapProtocolModel;

import bisq.network.p2p.NodeAddress;

import bisq.common.proto.ProtoUtil;

import org.bitcoinj.core.Coin;

import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;


@Slf4j
public final class BsqSwapMakerTrade extends BsqSwapTrade {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BsqSwapMakerTrade(String uid,
                             Offer offer,
                             Coin amount,
                             long price,
                             long takeOfferDate,
                             @Nullable NodeAddress peerNodeAddress,
                             long miningFeePerByte,
                             long makerFee,
                             long takerFee,
                             BsqSwapProtocolModel bsqSwapProtocolModel,
                             @Nullable String errorMessage,
                             State state) {
        super(uid,
                offer,
                amount,
                price,
                takeOfferDate,
                peerNodeAddress,
                miningFeePerByte,
                makerFee,
                takerFee,
                bsqSwapProtocolModel,
                errorMessage,
                state);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.Tradable toProtoMessage() {
        return protobuf.Tradable.newBuilder()
                .setBsqSwapMakerTrade(protobuf.BsqSwapMakerTrade.newBuilder()
                        .setBsqSwapTrade((protobuf.BsqSwapTrade) super.toProtoMessage()))
                .build();
    }

    public static Tradable fromProto(protobuf.BsqSwapMakerTrade atomicMakerTradeProto,
                                     CoreProtoResolver coreProtoResolver) {
        var proto = atomicMakerTradeProto.getBsqSwapTrade();
        var uid = ProtoUtil.stringOrNullFromProto(proto.getUid());
        if (uid == null) {
            uid = UUID.randomUUID().toString();
        }
        var atomicTrade = fromProto(new BsqSwapMakerTrade(
                uid,
                Offer.fromProto(proto.getOffer()),
                Coin.valueOf(proto.getAmount()),
                proto.getPrice(),
                proto.getTakeOfferDate(),
                proto.hasPeerNodeAddress() ? NodeAddress.fromProto(proto.getPeerNodeAddress()) : null,
                proto.getMiningFeePerByte(),
                proto.getMakerFee(),
                proto.getTakerFee(),
                BsqSwapProtocolModel.fromProto(proto.getBsqSwapProtocolModel(), coreProtoResolver),
                proto.getErrorMessage(),
                State.fromProto(proto.getState())));
        atomicTrade.setTxId(proto.getTxId());
        return atomicTrade;
    }
}
