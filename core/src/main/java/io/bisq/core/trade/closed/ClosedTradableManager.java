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

package io.bisq.core.trade.closed;

import com.google.inject.Inject;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.proto.persistable.PersistedDataHost;
import io.bisq.common.proto.persistable.PersistenceProtoResolver;
import io.bisq.common.storage.Storage;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.offer.Offer;
import io.bisq.core.provider.price.PriceFeedService;
import io.bisq.core.trade.Tradable;
import io.bisq.core.trade.TradableList;
import io.bisq.core.trade.Trade;
import javafx.collections.ObservableList;

import javax.inject.Named;
import java.io.File;
import java.util.Optional;

public class ClosedTradableManager implements PersistedDataHost {
    private final Storage<TradableList<Tradable>> tradableListStorage;
    private TradableList<Tradable> closedTrades;
    private final KeyRing keyRing;
    private PriceFeedService priceFeedService;
    private BtcWalletService btcWalletService;

    @Inject
    public ClosedTradableManager(KeyRing keyRing, PriceFeedService priceFeedService,
                                 PersistenceProtoResolver persistenceProtoResolver,
                                 BtcWalletService btcWalletService,
                                 @Named(Storage.STORAGE_DIR) File storageDir) {
        this.keyRing = keyRing;
        this.priceFeedService = priceFeedService;
        this.btcWalletService = btcWalletService;
        tradableListStorage = new Storage<>(storageDir, persistenceProtoResolver);
        // The ClosedTrades object can become a few MB so we don't keep so many backups
        tradableListStorage.setNumMaxBackupFiles(3);

    }

    @Override
    public void readPersisted() {
        closedTrades = new TradableList<>(tradableListStorage, "ClosedTrades");
        closedTrades.readPersisted();
        closedTrades.forEach(tradable -> {
            tradable.getOffer().setPriceFeedService(priceFeedService);
            if (tradable instanceof Trade) {
                Trade trade = (Trade) tradable;
                trade.setTransientFields(tradableListStorage, btcWalletService);
            }
        });
    }

    public void add(Tradable tradable) {
        closedTrades.add(tradable);
    }

    public boolean wasMyOffer(Offer offer) {
        return offer.isMyOffer(keyRing);
    }

    public ObservableList<Tradable> getClosedTrades() {
        return closedTrades.getList();
    }

    public Optional<Tradable> getTradableById(String id) {
        return closedTrades.stream().filter(e -> e.getId().equals(id)).findFirst();
    }

}
