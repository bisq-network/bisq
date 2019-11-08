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

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.Tradable;
import bisq.core.trade.TradableList;
import bisq.core.trade.Trade;

import bisq.common.crypto.KeyRing;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import com.google.inject.Inject;

import com.google.common.collect.ImmutableList;

import javafx.collections.ObservableList;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ClosedTradableManager implements PersistedDataHost {
    private final Storage<TradableList<Tradable>> tradableListStorage;
    private TradableList<Tradable> closedTradables;
    private final KeyRing keyRing;
    private final PriceFeedService priceFeedService;
    private final BtcWalletService btcWalletService;

    @Inject
    public ClosedTradableManager(KeyRing keyRing,
                                 PriceFeedService priceFeedService,
                                 BtcWalletService btcWalletService,
                                 Storage<TradableList<Tradable>> storage) {
        this.keyRing = keyRing;
        this.priceFeedService = priceFeedService;
        this.btcWalletService = btcWalletService;
        tradableListStorage = storage;
        // The ClosedTrades object can become a few MB so we don't keep so many backups
        tradableListStorage.setNumMaxBackupFiles(3);

    }

    @Override
    public void readPersisted() {
        closedTradables = new TradableList<>(tradableListStorage, "ClosedTrades");
        closedTradables.forEach(tradable -> {
            tradable.getOffer().setPriceFeedService(priceFeedService);
            if (tradable instanceof Trade) {
                Trade trade = (Trade) tradable;
                trade.setTransientFields(tradableListStorage, btcWalletService);
            }
        });
    }

    public void add(Tradable tradable) {
        closedTradables.add(tradable);
    }

    public void remove(Tradable tradable) {
        closedTradables.remove(tradable);
    }

    public boolean wasMyOffer(Offer offer) {
        return offer.isMyOffer(keyRing);
    }

    public ObservableList<Tradable> getClosedTradables() {
        return closedTradables.getList();
    }

    public List<Trade> getClosedTrades() {
        return ImmutableList.copyOf(getClosedTradables().stream()
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
