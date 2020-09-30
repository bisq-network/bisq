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

package bisq.core.trade;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.OpenOffer;
import bisq.core.proto.CoreProtoResolver;

import bisq.common.proto.ProtoUtil;
import bisq.common.proto.ProtobufferRuntimeException;
import bisq.common.proto.persistable.PersistableList;

import com.google.protobuf.Message;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class TradableList<T extends Tradable> extends PersistableList<T> {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TradableList() {
    }

    protected List<T> createList() {
        return FXCollections.observableArrayList();
    }

    public ObservableList<T> getObservableList() {
        return (ObservableList<T>) getList();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private TradableList(List<T> list) {
        super(list);
    }

    @Override
    public Message toProtoMessage() {
        ArrayList<T> clonedList = new ArrayList<>(getList());
        return protobuf.PersistableEnvelope.newBuilder()
                .setTradableList(protobuf.TradableList.newBuilder()
                        .addAllTradable(ProtoUtil.collectionToProto(clonedList, protobuf.Tradable.class)))
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
                        default:
                            log.error("Unknown messageCase. tradable.getMessageCase() = " + tradable.getMessageCase());
                            throw new ProtobufferRuntimeException("Unknown messageCase. tradable.getMessageCase() = " +
                                    tradable.getMessageCase());
                    }
                })
                .collect(Collectors.toList());

        return new TradableList<>(list);
    }
}
