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
import io.bisq.core.proto.CoreProtoResolver;
import io.bisq.generated.protobuffer.PB;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public final class TradableList<T extends Tradable> implements PersistableEnvelope {
    @Getter
    private List<T> list = new ArrayList<>();

    transient final private Storage<TradableList<T>> storage;
    transient private ObservableList<T> observableList;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TradableList(Storage<TradableList<T>> storage, String fileName) {
        this.storage = storage;

        TradableList<T> persisted = storage.initAndGetPersisted(this, fileName);
        if (persisted != null)
            list = persisted.getList();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TradableList(Storage<TradableList<T>> storage, List<T> list) {
        this.storage = storage;
        this.list.addAll(list);
    }

    @Override
    public Message toProtoMessage() {
        return PB.PersistableEnvelope.newBuilder().setTradableList(PB.TradableList.newBuilder()
                .addAllTradable(ProtoUtil.collectionToProto(list))).build();
    }

    @Nullable
    public static TradableList fromProto(PB.TradableList proto,
                                         CoreProtoResolver coreProtoResolver,
                                         Storage<TradableList<OpenOffer>> openOfferStorage,
                                         Storage<TradableList<BuyerAsMakerTrade>> buyerAsMakerTradeStorage,
                                         Storage<TradableList<BuyerAsTakerTrade>> buyerAsTakerTradeStorage,
                                         Storage<TradableList<SellerAsMakerTrade>> sellerAsMakerTradeStorage,
                                         Storage<TradableList<SellerAsTakerTrade>> sellerAsTakerTradeStorage,
                                         BtcWalletService btcWalletService) {
        log.debug("TradableList fromProto of {} ", proto);
        if (proto.getTradableList().size() == 0) {
            return new TradableList<>(openOfferStorage, new ArrayList<>());
        }

        List list = proto.getTradableList().stream()
                .map(tradable -> {
                    log.info(tradable.getClass().toString());
                    log.debug("tradable.getMessageCase(): {}", tradable.getMessageCase());
                    switch (tradable.getMessageCase()) {
                        case OPEN_OFFER:
                            return OpenOffer.fromProto(tradable.getOpenOffer());
                        case BUYER_AS_MAKER_TRADE:
                            return BuyerAsMakerTrade.fromProto(tradable.getBuyerAsMakerTrade(), buyerAsMakerTradeStorage, btcWalletService, coreProtoResolver);
                        case BUYER_AS_TAKER_TRADE:
                            return BuyerAsTakerTrade.fromProto(tradable.getBuyerAsTakerTrade(), buyerAsTakerTradeStorage, btcWalletService, coreProtoResolver);
                        case SELLER_AS_MAKER_TRADE:
                            return SellerAsMakerTrade.fromProto(tradable.getSellerAsMakerTrade(), sellerAsMakerTradeStorage, btcWalletService, coreProtoResolver);
                        case SELLER_AS_TAKER_TRADE:
                            return SellerAsTakerTrade.fromProto(tradable.getSellerAsTakerTrade(), sellerAsTakerTradeStorage, btcWalletService, coreProtoResolver);
                    }
                    return null;
                })
                .filter(e -> e != null)
                .collect(Collectors.toList());

        //TODO list.get(0) only works for offer/trade
        switch (list.get(0).getClass().getSimpleName()) {
            case "OpenOffer":
                return new TradableList<>(openOfferStorage, list);
            case "BuyerAsMakerTrade":
                return new TradableList<>(buyerAsMakerTradeStorage, list);
            case "BuyerAsTakerTrade":
                return new TradableList<>(buyerAsTakerTradeStorage, list);
            case "SellerAsMakerTrade":
                return new TradableList<>(sellerAsMakerTradeStorage, list);
            case "SellerAsTakerTrade":
                return new TradableList<>(sellerAsTakerTradeStorage, list);
        }

        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean add(T tradable) {
        boolean changed = list.add(tradable);
        getObservableList().add(tradable);
        if (changed)
            storage.queueUpForSave();
        return changed;
    }

    public boolean remove(T tradable) {
        boolean changed = this.list.remove(tradable);
        getObservableList().remove(tradable);
        if (changed)
            storage.queueUpForSave();
        return changed;
    }

    public Stream<T> stream() {
        return this.list.stream();
    }

    public void forEach(Consumer<? super T> action) {
        this.list.forEach(action);
    }


    public ObservableList<T> getObservableList() {
        if (observableList == null)
            observableList = FXCollections.observableArrayList(this.list);
        return observableList;
    }

    public int size() {
        return list.size();
    }

    public boolean contains(T thing) {
        return list.contains(thing);
    }
}
