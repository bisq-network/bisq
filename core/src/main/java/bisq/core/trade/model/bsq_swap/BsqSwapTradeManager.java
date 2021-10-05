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

package bisq.core.trade.model.bsq_swap;

import bisq.core.offer.Offer;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.model.TradableList;

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
public class BsqSwapTradeManager implements PersistedDataHost {
    private final PersistenceManager<TradableList<BsqSwapTrade>> persistenceManager;
    private final TradableList<BsqSwapTrade> bsqSwapTrades = new TradableList<>();
    private final KeyRing keyRing;
    private final PriceFeedService priceFeedService;

    @Inject
    public BsqSwapTradeManager(KeyRing keyRing,
                               PriceFeedService priceFeedService,
                               PersistenceManager<TradableList<BsqSwapTrade>> persistenceManager) {
        this.keyRing = keyRing;
        this.priceFeedService = priceFeedService;
        this.persistenceManager = persistenceManager;

        this.persistenceManager.initialize(bsqSwapTrades, "BsqSwapTrades", PersistenceManager.Source.PRIVATE);
    }

    @Override
    public void readPersisted(Runnable completeHandler) {
        persistenceManager.readPersisted(persisted -> {
                    bsqSwapTrades.setAll(persisted.getList());
                    bsqSwapTrades.stream()
                            .filter(bsqSwapTrade -> bsqSwapTrade.getOffer() != null)
                            .forEach(bsqSwapTrade -> bsqSwapTrade.getOffer().setPriceFeedService(priceFeedService));
                    completeHandler.run();
                },
                completeHandler);
    }

    public void onAllServicesInitialized() {
        // TODO(sq): Cleanup TradeModel
    }

    public void onTradeCompleted(BsqSwapTrade bsqSwapTrade) {
        if (bsqSwapTrades.add(bsqSwapTrade)) {
            requestPersistence();
        }
    }

    public boolean wasMyOffer(Offer offer) {
        return offer.isMyOffer(keyRing);
    }

    public ObservableList<BsqSwapTrade> getObservableList() {
        return bsqSwapTrades.getObservableList();
    }

    public List<BsqSwapTrade> getBsqSwapTrades() {
        return ImmutableList.copyOf(new ArrayList<>(getObservableList()));
    }

    public Optional<BsqSwapTrade> findBsqSwapTradeById(String id) {
        return bsqSwapTrades.stream().filter(e -> e.getId().equals(id)).findFirst();
    }

    private void requestPersistence() {
        persistenceManager.requestPersistence();
    }
}
