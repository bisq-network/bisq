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
import bisq.core.trade.TakerTrade;
import bisq.core.trade.Tradable;
import bisq.core.trade.protocol.AtomicProcessModel;

import bisq.network.p2p.NodeAddress;

import bisq.common.proto.ProtoUtil;

import org.bitcoinj.core.Coin;

import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public final class AtomicTakerTrade extends AtomicTrade implements TakerTrade {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    public AtomicTakerTrade(String uid,
                            Offer offer,
                            Coin amount,
                            long price,
                            long takeOfferDate,
                            @Nullable NodeAddress peerNodeAddress,
                            long miningFeePerByte,
                            boolean isCurrencyForMakerFeeBtc,
                            boolean isCurrencyForTakerFeeBtc,
                            long makerFee,
                            long takerFee,
                            AtomicProcessModel atomicProcessModel,
                            @Nullable String errorMessage,
                            State state) {
        super(uid,
                offer,
                amount,
                price,
                takeOfferDate,
                peerNodeAddress,
                miningFeePerByte,
                isCurrencyForMakerFeeBtc,
                isCurrencyForTakerFeeBtc,
                makerFee,
                takerFee,
                atomicProcessModel,
                errorMessage,
                state);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.Tradable toProtoMessage() {
        return protobuf.Tradable.newBuilder()
                .setAtomicTakerTrade(protobuf.AtomicTakerTrade.newBuilder()
                        .setAtomicTrade((protobuf.AtomicTrade) super.toProtoMessage()))
                .build();
    }

    public static Tradable fromProto(protobuf.AtomicTakerTrade atomicTakerTradeProto,
                                     CoreProtoResolver coreProtoResolver) {
        var proto = atomicTakerTradeProto.getAtomicTrade();
        var uid = ProtoUtil.stringOrNullFromProto(proto.getUid());
        if (uid == null) {
            uid = UUID.randomUUID().toString();
        }
        return fromProto(new AtomicTakerTrade(
                        uid,
                        Offer.fromProto(proto.getOffer()),
                        Coin.valueOf(proto.getAmount()),
                        proto.getPrice(),
                        proto.getTakeOfferDate(),
                        proto.hasPeerNodeAddress() ? NodeAddress.fromProto(proto.getPeerNodeAddress()) : null,
                        proto.getMiningFeePerByte(),
                        proto.getIsCurrencyForMakerFeeBtc(),
                        proto.getIsCurrencyForTakerFeeBtc(),
                        proto.getMakerFee(),
                        proto.getTakerFee(),
                        AtomicProcessModel.fromProto(proto.getAtomicProcessModel(), coreProtoResolver),
                        proto.getErrorMessage(),
                        State.fromProto(proto.getState())),
                proto,
                coreProtoResolver);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////
//
//    @Override
//    protected void createTradeProtocol() {
//        tradeProtocol = new AtomicTakerProtocol(this);
//    }
//
//    @Override
//    public Coin getPayoutAmount() {
//        // There is no payout for atomic trades. Either the trade goes through and both parties get what the want,
//        // or the trade fails and no funds are transferred
//        return Coin.ZERO;
//    }
//
//    @Override
//    public void onTakeOffer() {
//        checkArgument(tradeProtocol instanceof AtomicTakerProtocol,
//                "tradeProtocol NOT instanceof AtomicTakerProtocol");
//        ((AtomicTakerProtocol) tradeProtocol).takeAvailableOffer();
//    }
}
