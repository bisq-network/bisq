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

package bisq.core.trade.closed;

import bisq.core.offer.Offer;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.DumpDelayedPayoutTx;
import bisq.core.trade.Tradable;
import bisq.core.trade.TradableList;
import bisq.core.trade.Trade;

import bisq.common.crypto.KeyRing;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.persistable.PersistedDataHost;

import com.google.inject.Inject;

import com.google.common.collect.ImmutableList;

import javafx.collections.ObservableList;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ClosedTradableManager implements PersistedDataHost {
    private final PersistenceManager<TradableList<Tradable>> persistenceManager;
    private final TradableList<Tradable> closedTradables = new TradableList<>();
    private final KeyRing keyRing;
    private final PriceFeedService priceFeedService;
    private final DumpDelayedPayoutTx dumpDelayedPayoutTx;

    @Inject
    public ClosedTradableManager(KeyRing keyRing,
                                 PriceFeedService priceFeedService,
                                 PersistenceManager<TradableList<Tradable>> persistenceManager,
                                 DumpDelayedPayoutTx dumpDelayedPayoutTx) {
        this.keyRing = keyRing;
        this.priceFeedService = priceFeedService;
        this.dumpDelayedPayoutTx = dumpDelayedPayoutTx;
        this.persistenceManager = persistenceManager;

        this.persistenceManager.initialize(closedTradables, "ClosedTrades", PersistenceManager.Source.PRIVATE);
    }

    @Override
    public void readPersisted() {
        TradableList<Tradable> persisted = persistenceManager.getPersisted();
        if (persisted != null) {
            closedTradables.setAll(persisted.getList());
        }

        closedTradables.forEach(tradable -> tradable.getOffer().setPriceFeedService(priceFeedService));

        dumpDelayedPayoutTx.maybeDumpDelayedPayoutTxs(closedTradables, "delayed_payout_txs_closed");
    }

    public void add(Tradable tradable) {
        if (closedTradables.add(tradable)) {
            persistenceManager.requestPersistence();
        }
    }

    public void remove(Tradable tradable) {
        if (closedTradables.remove(tradable)) {
            persistenceManager.requestPersistence();
        }
    }

    public boolean wasMyOffer(Offer offer) {
        return offer.isMyOffer(keyRing);
    }

    public ObservableList<Tradable> getObservableList() {
        return closedTradables.getObservableList();
    }

    public List<Trade> getClosedTrades() {
        return ImmutableList.copyOf(getObservableList().stream()
                .filter(e -> e instanceof Trade)
                .map(e -> (Trade) e)
                .collect(Collectors.toList()));
    }

    public Optional<Tradable> getTradableById(String id) {
        return closedTradables.stream().filter(e -> e.getId().equals(id)).findFirst();
    }

    public Stream<Trade> getTradesStreamWithFundsLockedIn() {
        return getClosedTrades().stream()
                .filter(Trade::isFundsLockedIn);
    }
}
