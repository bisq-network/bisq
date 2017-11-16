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

package io.bisq.core.trade;

import com.google.protobuf.Message;
import io.bisq.common.proto.ProtoUtil;
import io.bisq.common.proto.ProtobufferException;
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
    transient final private Storage<TradableList<T>> storage;
    @Getter
    private final ObservableList<T> list = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TradableList(Storage<TradableList<T>> storage, String fileName) {
        this.storage = storage;

        TradableList<T> persisted = storage.initAndGetPersisted(this, fileName, 50);
        if (persisted != null)
            list.addAll(persisted.getList());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private TradableList(Storage<TradableList<T>> storage, List<T> list) {
        this.storage = storage;
        this.list.addAll(list);
    }

    @Override
    public Message toProtoMessage() {
        ArrayList<T> clonedList = new ArrayList<>(this.list);
        return PB.PersistableEnvelope.newBuilder()
                .setTradableList(PB.TradableList.newBuilder()
                        .addAllTradable(ProtoUtil.collectionToProto(clonedList)))
                .build();
    }

    @Nullable
    public static TradableList fromProto(PB.TradableList proto,
                                         CoreProtoResolver coreProtoResolver,
                                         Storage<TradableList<Tradable>> storage,
                                         BtcWalletService btcWalletService) {
        log.debug("TradableList fromProto of {} ", proto);

        List<Tradable> list = proto.getTradableList().stream()
                .map(tradable -> {
                    switch (tradable.getMessageCase()) {
                        case OPEN_OFFER:
                            return OpenOffer.fromProto(tradable.getOpenOffer());
                        case BUYER_AS_MAKER_TRADE:
                            return BuyerAsMakerTrade.fromProto(tradable.getBuyerAsMakerTrade(), storage, btcWalletService, coreProtoResolver);
                        case BUYER_AS_TAKER_TRADE:
                            return BuyerAsTakerTrade.fromProto(tradable.getBuyerAsTakerTrade(), storage, btcWalletService, coreProtoResolver);
                        case SELLER_AS_MAKER_TRADE:
                            return SellerAsMakerTrade.fromProto(tradable.getSellerAsMakerTrade(), storage, btcWalletService, coreProtoResolver);
                        case SELLER_AS_TAKER_TRADE:
                            return SellerAsTakerTrade.fromProto(tradable.getSellerAsTakerTrade(), storage, btcWalletService, coreProtoResolver);
                        default:
                            log.error("Unknown messageCase. tradable.getMessageCase() = " + tradable.getMessageCase());
                            throw new ProtobufferException("Unknown messageCase. tradable.getMessageCase() = " + tradable.getMessageCase());
                    }
                })
                .collect(Collectors.toList());

        return new TradableList<>(storage, list);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean add(T tradable) {
        boolean changed = list.add(tradable);
        if (changed)
            storage.queueUpForSave();
        return changed;
    }

    public boolean remove(T tradable) {
        boolean changed = list.remove(tradable);
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

    public int size() {
        return list.size();
    }

    public boolean contains(T thing) {
        return list.contains(thing);
    }
}
