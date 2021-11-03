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

package bisq.core.trade.bsq_swap;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.offer.Offer;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.model.TradableList;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;

import bisq.common.crypto.KeyRing;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.persistable.PersistedDataHost;

import org.bitcoinj.core.TransactionConfidence;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.google.common.collect.ImmutableList;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class BsqSwapTradeManager implements PersistedDataHost {
    private final BsqWalletService bsqWalletService;
    private final PersistenceManager<TradableList<BsqSwapTrade>> persistenceManager;
    private final TradableList<BsqSwapTrade> bsqSwapTrades = new TradableList<>();
    private final KeyRing keyRing;
    private final PriceFeedService priceFeedService;

    // Used for listening for notifications in the UI
    @Getter
    private final ObjectProperty<BsqSwapTrade> completedBsqSwapTrade = new SimpleObjectProperty<>();

    @Inject
    public BsqSwapTradeManager(KeyRing keyRing,
                               PriceFeedService priceFeedService,
                               BsqWalletService bsqWalletService,
                               PersistenceManager<TradableList<BsqSwapTrade>> persistenceManager) {
        this.keyRing = keyRing;
        this.priceFeedService = priceFeedService;
        this.bsqWalletService = bsqWalletService;
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
    }

    public void onTradeCompleted(BsqSwapTrade bsqSwapTrade) {
        if (findBsqSwapTradeById(bsqSwapTrade.getId()).isPresent()) {
            return;
        }

        if (bsqSwapTrades.add(bsqSwapTrade)) {
            requestPersistence();

            completedBsqSwapTrade.set(bsqSwapTrade);
        }
    }

    public void resetCompletedBsqSwapTrade() {
        completedBsqSwapTrade.set(null);
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

    public Stream<BsqSwapTrade> getUnconfirmedBsqSwapTrades() {
        return getObservableList().stream().filter(this::isUnconfirmed);
    }

    public Stream<BsqSwapTrade> getConfirmedBsqSwapTrades() {
        return getObservableList().stream().filter(this::isConfirmed);
    }

    private boolean isUnconfirmed(BsqSwapTrade bsqSwapTrade) {
        return matchesConfidence(bsqSwapTrade, TransactionConfidence.ConfidenceType.PENDING);
    }

    private boolean isConfirmed(BsqSwapTrade bsqSwapTrade) {
        return matchesConfidence(bsqSwapTrade, TransactionConfidence.ConfidenceType.BUILDING);
    }

    private boolean matchesConfidence(BsqSwapTrade bsqSwapTrade, TransactionConfidence.ConfidenceType confidenceTyp) {
        TransactionConfidence confidenceForTxId = bsqWalletService.getConfidenceForTxId(bsqSwapTrade.getTxId());
        return confidenceForTxId != null &&
                confidenceForTxId.getConfidenceType() == confidenceTyp;
    }

    private void requestPersistence() {
        persistenceManager.requestPersistence();
    }
}
