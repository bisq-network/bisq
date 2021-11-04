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

package bisq.core.trade.model;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.OpenOffer;
import bisq.core.proto.CoreProtoResolver;
import bisq.core.trade.model.bisq_v1.BuyerAsMakerTrade;
import bisq.core.trade.model.bisq_v1.BuyerAsTakerTrade;
import bisq.core.trade.model.bisq_v1.SellerAsMakerTrade;
import bisq.core.trade.model.bisq_v1.SellerAsTakerTrade;
import bisq.core.trade.model.bsq_swap.BsqSwapBuyerAsMakerTrade;
import bisq.core.trade.model.bsq_swap.BsqSwapBuyerAsTakerTrade;
import bisq.core.trade.model.bsq_swap.BsqSwapSellerAsMakerTrade;
import bisq.core.trade.model.bsq_swap.BsqSwapSellerAsTakerTrade;

import bisq.common.proto.ProtoUtil;
import bisq.common.proto.ProtobufferRuntimeException;
import bisq.common.proto.persistable.PersistableListAsObservable;

import com.google.protobuf.Message;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class TradableList<T extends Tradable> extends PersistableListAsObservable<T> {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TradableList() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected TradableList(Collection<T> collection) {
        super(collection);
    }

    @Override
    public Message toProtoMessage() {
        return protobuf.PersistableEnvelope.newBuilder()
                .setTradableList(protobuf.TradableList.newBuilder()
                        .addAllTradable(ProtoUtil.collectionToProto(getList(), protobuf.Tradable.class)))
                .build();
    }

    public static TradableList<Tradable> fromProto(protobuf.TradableList proto,
                                                   CoreProtoResolver coreProtoResolver,
                                                   BtcWalletService btcWalletService) {
        List<Tradable> list = proto.getTradableList().stream()
                .map(tradable -> {
                    switch (tradable.getMessageCase()) {
                        case OPEN_OFFER:
                            return OpenOffer.fromProto(tradable.getOpenOffer());
                        case BUYER_AS_MAKER_TRADE:
                            return BuyerAsMakerTrade.fromProto(tradable.getBuyerAsMakerTrade(), btcWalletService, coreProtoResolver);
                        case BUYER_AS_TAKER_TRADE:
                            return BuyerAsTakerTrade.fromProto(tradable.getBuyerAsTakerTrade(), btcWalletService, coreProtoResolver);
                        case SELLER_AS_MAKER_TRADE:
                            return SellerAsMakerTrade.fromProto(tradable.getSellerAsMakerTrade(), btcWalletService, coreProtoResolver);
                        case SELLER_AS_TAKER_TRADE:
                            return SellerAsTakerTrade.fromProto(tradable.getSellerAsTakerTrade(), btcWalletService, coreProtoResolver);
                        case BSQ_SWAP_BUYER_AS_MAKER_TRADE:
                            return BsqSwapBuyerAsMakerTrade.fromProto(tradable.getBsqSwapBuyerAsMakerTrade());
                        case BSQ_SWAP_BUYER_AS_TAKER_TRADE:
                            return BsqSwapBuyerAsTakerTrade.fromProto(tradable.getBsqSwapBuyerAsTakerTrade());
                        case BSQ_SWAP_SELLER_AS_MAKER_TRADE:
                            return BsqSwapSellerAsMakerTrade.fromProto(tradable.getBsqSwapSellerAsMakerTrade());
                        case BSQ_SWAP_SELLER_AS_TAKER_TRADE:
                            return BsqSwapSellerAsTakerTrade.fromProto(tradable.getBsqSwapSellerAsTakerTrade());
                        default:
                            log.error("Unknown messageCase. tradable.getMessageCase() = " + tradable.getMessageCase());
                            throw new ProtobufferRuntimeException("Unknown messageCase. tradable.getMessageCase() = " +
                                    tradable.getMessageCase());
                    }
                })
                .collect(Collectors.toList());

        return new TradableList<>(list);
    }

    @Override
    public String toString() {
        return "TradableList{" +
                ",\n     list=" + getList() +
                "\n}";
    }
}
