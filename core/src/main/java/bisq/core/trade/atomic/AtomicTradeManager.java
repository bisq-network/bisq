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
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.TradableList;
import bisq.core.trade.closed.CleanupMailboxMessages;

import bisq.common.crypto.KeyRing;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.persistable.PersistedDataHost;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.google.common.collect.ImmutableList;

import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class AtomicTradeManager implements PersistedDataHost {
    private final PersistenceManager<TradableList<AtomicTrade>> persistenceManager;
    private final TradableList<AtomicTrade> atomicTrades = new TradableList<>();
    private final KeyRing keyRing;
    private final PriceFeedService priceFeedService;
    private final CleanupMailboxMessages cleanupMailboxMessages;

    @Inject
    public AtomicTradeManager(KeyRing keyRing,
                              PriceFeedService priceFeedService,
                              PersistenceManager<TradableList<AtomicTrade>> persistenceManager,
                              CleanupMailboxMessages cleanupMailboxMessages) {
        this.keyRing = keyRing;
        this.priceFeedService = priceFeedService;
        this.cleanupMailboxMessages = cleanupMailboxMessages;
        this.persistenceManager = persistenceManager;

        this.persistenceManager.initialize(atomicTrades, "AtomicTrades", PersistenceManager.Source.PRIVATE);
    }

    @Override
    public void readPersisted(Runnable completeHandler) {
        persistenceManager.readPersisted(persisted -> {
                    atomicTrades.setAll(persisted.getList());
                    atomicTrades.stream()
                            .filter(atomicTrade -> atomicTrade.getOffer() != null)
                            .forEach(atomicTrade -> atomicTrade.getOffer().setPriceFeedService(priceFeedService));
                    completeHandler.run();
                },
                completeHandler);
    }

    public void onAllServicesInitialized() {
        // TODO(sq): Cleanup TradeModel
//        cleanupMailboxMessages.handleTrades(getAtomicTrades());
    }

    public void add(AtomicTrade atomicTrade) {
        if (atomicTrades.add(atomicTrade)) {
            requestPersistence();
        }
    }

    public void remove(AtomicTrade atomicTrade) {
        if (atomicTrades.remove(atomicTrade)) {
            requestPersistence();
        }
    }

    public boolean wasMyOffer(Offer offer) {
        return offer.isMyOffer(keyRing);
    }

    public ObservableList<AtomicTrade> getObservableList() {
        return atomicTrades.getObservableList();
    }

    public List<AtomicTrade> getAtomicTrades() {
        return ImmutableList.copyOf(new ArrayList<>(getObservableList()));
    }

    public Optional<AtomicTrade> getAtomicTradeById(String id) {
        return atomicTrades.stream().filter(e -> e.getId().equals(id)).findFirst();
    }

    private void requestPersistence() {
        persistenceManager.requestPersistence();
    }
}
