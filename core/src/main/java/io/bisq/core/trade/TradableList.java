/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.trade;

import com.google.protobuf.Message;
import io.bisq.common.proto.ProtoUtil;
import io.bisq.common.proto.persistable.PersistableEnvelope;
import io.bisq.common.storage.Storage;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.offer.OpenOffer;
import io.bisq.core.proto.persistable.CorePersistenceProtoResolver;
import io.bisq.generated.protobuffer.PB;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public final class TradableList<T extends Tradable> implements PersistableEnvelope {
    @Getter
    private List<T> tradableList = new ArrayList<>();

    transient final private Storage<TradableList<T>> storage;
    transient private ObservableList<T> observableList = FXCollections.observableArrayList(tradableList);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TradableList(Storage<TradableList<T>> storage, String fileName) {
        this.storage = storage;

        TradableList<T> persisted = storage.initAndGetPersisted(this, fileName);
        if (persisted != null)
            tradableList = persisted.getTradableList();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TradableList(Storage<TradableList<T>> storage, List<T> tradableList) {
        this.storage = storage;
        this.tradableList.addAll(tradableList);
    }

    @Override
    public Message toProtoMessage() {
        return PB.PersistableEnvelope.newBuilder().setTradableList(PB.TradableList.newBuilder()
                .addAllTradable(ProtoUtil.collectionToProto(tradableList))).build();
    }

    public static TradableList fromProto(PB.TradableList proto,
                                         CorePersistenceProtoResolver corePersistenceProtoResolver,
                                         Storage<TradableList<OpenOffer>> openOfferStorage,
                                         Storage<TradableList<BuyerAsMakerTrade>> buyerAsMakerTradeStorage,
                                         Storage<TradableList<BuyerAsTakerTrade>> buyerAsTakerTradeStorage,
                                         Storage<TradableList<SellerAsMakerTrade>> sellerAsMakerTradeStorage,
                                         Storage<TradableList<SellerAsTakerTrade>> sellerAsTakerTradeStorage,
                                         BtcWalletService btcWalletService) {
        log.debug("TradableList fromProto of {} ", proto);
        List list = proto.getTradableList().stream().peek(tradable -> log.info(tradable.getClass().toString())).map(tradable -> {
            log.debug("tradable.getMessageCase(): {}", tradable.getMessageCase());
            switch (tradable.getMessageCase()) {
                case OPEN_OFFER:
                    return OpenOffer.fromProto(tradable.getOpenOffer());
                case BUYER_AS_MAKER_TRADE:
                    return BuyerAsMakerTrade.fromProto(tradable.getBuyerAsMakerTrade(), buyerAsMakerTradeStorage, btcWalletService);
                case BUYER_AS_TAKER_TRADE:
                    Tradable tradable1 = BuyerAsTakerTrade.fromProto(tradable.getBuyerAsTakerTrade(), buyerAsTakerTradeStorage, btcWalletService);
                    return tradable1;
                case SELLER_AS_MAKER_TRADE:
                    return SellerAsMakerTrade.fromProto(tradable.getSellerAsMakerTrade(), sellerAsMakerTradeStorage, btcWalletService);
                case SELLER_AS_TAKER_TRADE:
                    return SellerAsTakerTrade.fromProto(tradable.getSellerAsTakerTrade(), sellerAsTakerTradeStorage, btcWalletService);
            }
            return null;
        }).collect(Collectors.toList());

        switch (list.get(0).getClass().getSimpleName()) {
            case "OpenOffer":
                return new TradableList<OpenOffer>(openOfferStorage, list);
            case "BuyerAsMakerTrade":
                return new TradableList<BuyerAsMakerTrade>(buyerAsMakerTradeStorage, list);
            case "BuyerAsTakerTrade":
                return new TradableList<BuyerAsTakerTrade>(buyerAsTakerTradeStorage, list);
            case "SellerAsMakerTrade":
                return new TradableList<SellerAsMakerTrade>(sellerAsMakerTradeStorage, list);
            case "SellerAsTakerTrade":
                return new TradableList<SellerAsTakerTrade>(sellerAsTakerTradeStorage, list);
        }

        return null;
    }

   /* public static TradableList fromProto(PB.TradeList proto,
                                         CorePersistenceProtoResolver corePersistenceProtoResolver,
                                         Storage<TradableList<BuyerAsMakerTrade>> buyerAsMakerTradeStorage,
                                         Storage<TradableList<BuyerAsTakerTrade>> buyerAsTakerTradeStorage,
                                         Storage<TradableList<SellerAsMakerTrade>> sellerAsMakerTradeStorage,
                                         Storage<TradableList<SellerAsTakerTrade>> sellerAsTakerTradeStorage,
                                         BtcWalletService btcWalletService) {
        log.error("fromProto " + proto);
        List tradableList = proto.getTradeList().stream().map(trade -> {
            // corePersistenceProtoResolver.fromProto(trade, st)
            
           
            switch (trade.getMessageCase()) {
                case OPEN_OFFER:
                    return OpenOffer.fromProto(trade.getOpenOffer());
                case BUYER_AS_MAKER_TRADE:
                    return BuyerAsMakerTrade.fromProto(trade.getBuyerAsMakerTrade(), buyerAsMakerTradeStorage, btcWalletService);
                case BUYER_AS_TAKER_TRADE:
                    return BuyerAsTakerTrade.fromProto(trade.getBuyerAsTakerTrade(), buyerAsTakerTradeStorage, btcWalletService);
                case SELLER_AS_MAKER_TRADE:
                    return SellerAsMakerTrade.fromProto(trade.getSellerAsMakerTrade(), sellerAsMakerTradeStorage, btcWalletService);
                case SELLER_AS_TAKER_TRADE:
                    return SellerAsTakerTrade.fromProto(trade.getSellerAsTakerTrade(), sellerAsTakerTradeStorage, btcWalletService);
            }
            return null;
        }).collect(Collectors.toList());

        switch (tradableList.get(0).getClass().getSimpleName()) {
            case "OpenOffer":
                return new TradableList<OpenOffer>(openOfferStorage, tradableList);
            case "BuyerAsMakerTrade":
                return new TradableList<BuyerAsMakerTrade>(buyerAsMakerTradeStorage, tradableList);
            case "BuyerAsTakerTrade":
                return new TradableList<BuyerAsTakerTrade>(buyerAsTakerTradeStorage, tradableList);
            case "SellerAsMakerTrade":
                return new TradableList<SellerAsMakerTrade>(sellerAsMakerTradeStorage, tradableList);
            case "SellerAsTakerTrade":
                return new TradableList<SellerAsTakerTrade>(sellerAsTakerTradeStorage, tradableList);
        }

        return null;
    }*/


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean add(T tradable) {
        boolean changed = tradableList.add(tradable);
        getObservableList().add(tradable);
        if (changed)
            storage.queueUpForSave();
        return changed;
    }

    public boolean remove(T tradable) {
        boolean changed = this.tradableList.remove(tradable);
        getObservableList().remove(tradable);
        if (changed)
            storage.queueUpForSave();
        return changed;
    }

    public Stream<T> stream() {
        return this.tradableList.stream();
    }

    public void forEach(Consumer<? super T> action) {
        this.tradableList.forEach(action);
    }


    public ObservableList<T> getObservableList() {
        if (observableList == null)
            observableList = FXCollections.observableArrayList(this.tradableList);
        return observableList;
    }

    public int size() {
        return tradableList.size();
    }

    public boolean contains(T thing) {
        return tradableList.contains(thing);
    }
}
