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

import io.bisq.common.app.Version;
import io.bisq.common.persistence.Persistable;
import io.bisq.common.storage.Storage;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.offer.OpenOffer;
import io.bisq.generated.protobuffer.PB;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class TradableList<T extends Tradable> implements Persistable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private static final Logger log = LoggerFactory.getLogger(TradableList.class);

    transient final private Storage<TradableList<T>> storage;
    // Use getObservableList() also class locally, to be sure that object exists in case we use the object as deserialized form
    transient private ObservableList<T> observableList;

    @Getter
    private List<T> list = new ArrayList<>();

    // Superclass is ArrayList, which will be persisted

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TradableList(Storage<TradableList<T>> storage, String fileName) {
        this.storage = storage;

        TradableList<T> persisted = storage.initAndGetPersisted(this, fileName);
        if (persisted != null) {
            list.addAll(persisted.getList());
        }
        observableList = FXCollections.observableArrayList(list);
    }

    public TradableList(Storage<TradableList<T>> storage, List<T> list) {
        this.storage = storage;
        list.addAll(list);
        observableList = FXCollections.observableArrayList(list);
    }

    public boolean add(T tradable) {
        boolean changed = list.add(tradable);
        getObservableList().add(tradable);
        if (changed)
            storage.queueUpForSave();
        return changed;
    }

    public boolean remove(Object tradable) {
        boolean changed = list.remove(tradable);
        getObservableList().remove(tradable);
        if (changed)
            storage.queueUpForSave();
        return changed;
    }

    public Stream<T> stream() {
        return list.stream();
    }

    public void forEach(Consumer<? super T> action) {
        list.forEach(action);
    }


    public ObservableList<T> getObservableList() {
        if (observableList == null)
            observableList = FXCollections.observableArrayList(list);
        return observableList;
    }

    public int size() {
        return list.size();
    }

    public boolean contains(T thing) {
        return list.contains(thing);
    }

    public static TradableList fromProto(PB.TradableList proto, Storage<TradableList<OpenOffer>> openOfferStorage,
                                         Storage<TradableList<BuyerAsMakerTrade>> buyerAsMakerTradeStorage,
                                         Storage<TradableList<BuyerAsTakerTrade>> buyerAsTakerTradeStorage,
                                         Storage<TradableList<SellerAsMakerTrade>> sellerAsMakerTradeStorage,
                                         Storage<TradableList<SellerAsTakerTrade>> sellerAsTakerTradeStorage,
                                         BtcWalletService btcWalletService) {
        List collect = proto.getTradableList().stream().map(tradable -> {
            switch (tradable.getMessageCase()) {
                case OPEN_OFFER:
                    return OpenOffer.fromProto(tradable.getOpenOffer(), openOfferStorage);
                case BUYER_AS_MAKER_TRADE:
                    return BuyerAsMakerTrade.fromProto(tradable.getBuyerAsMakerTrade(), buyerAsMakerTradeStorage, btcWalletService);
                case BUYER_AS_TAKER_TRADE:
                    return BuyerAsTakerTrade.fromProto(tradable.getBuyerAsTakerTrade(), buyerAsTakerTradeStorage, btcWalletService);
                case SELLER_AS_MAKER_TRADE:
                    return SellerAsMakerTrade.fromProto(tradable.getSellerAsMakerTrade(), sellerAsMakerTradeStorage, btcWalletService);
                case SELLER_AS_TAKER_TRADE:
                    return SellerAsTakerTrade.fromProto(tradable.getSellerAsTakerTrade(), sellerAsTakerTradeStorage, btcWalletService);
            }
            return null;
        }).collect(Collectors.toList());
        switch (collect.get(0).getClass().getSimpleName()) {
            case "OpenOffer":
                return new TradableList<OpenOffer>(openOfferStorage, collect);
            case "BuyerAsMakerTrade":
                return new TradableList<BuyerAsMakerTrade>(buyerAsMakerTradeStorage, collect);
            case "BuyerAsTakerTrade":
                return new TradableList<BuyerAsTakerTrade>(buyerAsTakerTradeStorage, collect);
            case "SellerAsMakerTrade":
                return new TradableList<SellerAsMakerTrade>(sellerAsMakerTradeStorage, collect);
            case "SellerAsTakerTrade":
                return new TradableList<SellerAsTakerTrade>(sellerAsTakerTradeStorage, collect);
        }

        return null;
    }
}
