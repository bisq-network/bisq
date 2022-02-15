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

package bisq.core.trade.bisq_v1;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.model.TradableList;
import bisq.core.trade.model.bisq_v1.Trade;

import bisq.common.config.Config;
import bisq.common.crypto.KeyRing;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.persistable.PersistedDataHost;

import com.google.inject.Inject;

import javax.inject.Named;

import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Setter;

import static bisq.core.btc.model.AddressEntry.Context.AVAILABLE;

public class FailedTradesManager implements PersistedDataHost {
    private static final Logger log = LoggerFactory.getLogger(FailedTradesManager.class);
    private final TradableList<Trade> failedTrades = new TradableList<>();
    private final KeyRing keyRing;
    private final PriceFeedService priceFeedService;
    private final BtcWalletService btcWalletService;
    private final CleanupMailboxMessagesService cleanupMailboxMessagesService;
    private final PersistenceManager<TradableList<Trade>> persistenceManager;
    private final TradeUtil tradeUtil;
    private final DumpDelayedPayoutTx dumpDelayedPayoutTx;
    private final boolean allowFaultyDelayedTxs;

    @Setter
    private Predicate<Trade> unFailTradeCallback;

    @Inject
    public FailedTradesManager(KeyRing keyRing,
                               PriceFeedService priceFeedService,
                               BtcWalletService btcWalletService,
                               PersistenceManager<TradableList<Trade>> persistenceManager,
                               TradeUtil tradeUtil,
                               CleanupMailboxMessagesService cleanupMailboxMessagesService,
                               DumpDelayedPayoutTx dumpDelayedPayoutTx,
                               @Named(Config.ALLOW_FAULTY_DELAYED_TXS) boolean allowFaultyDelayedTxs) {
        this.keyRing = keyRing;
        this.priceFeedService = priceFeedService;
        this.btcWalletService = btcWalletService;
        this.cleanupMailboxMessagesService = cleanupMailboxMessagesService;
        this.dumpDelayedPayoutTx = dumpDelayedPayoutTx;
        this.persistenceManager = persistenceManager;
        this.tradeUtil = tradeUtil;
        this.allowFaultyDelayedTxs = allowFaultyDelayedTxs;

        this.persistenceManager.initialize(failedTrades, "FailedTrades", PersistenceManager.Source.PRIVATE);
    }

    @Override
    public void readPersisted(Runnable completeHandler) {
        persistenceManager.readPersisted(persisted -> {
                    failedTrades.setAll(persisted.getList());
                    failedTrades.stream()
                            .filter(trade -> trade.getOffer() != null)
                            .forEach(trade -> trade.getOffer().setPriceFeedService(priceFeedService));
                    dumpDelayedPayoutTx.maybeDumpDelayedPayoutTxs(failedTrades, "delayed_payout_txs_failed");
                    completeHandler.run();
                },
                completeHandler);
    }

    public void onAllServicesInitialized() {
        cleanupMailboxMessagesService.handleTrades(failedTrades.getList());
    }

    public void add(Trade trade) {
        if (failedTrades.add(trade)) {
            requestPersistence();
        }
    }

    public void removeTrade(Trade trade) {
        if (failedTrades.remove(trade)) {
            requestPersistence();
        }
    }

    public boolean wasMyOffer(Offer offer) {
        return offer.isMyOffer(keyRing);
    }

    public ObservableList<Trade> getObservableList() {
        return failedTrades.getObservableList();
    }

    public List<Trade> getTrades() {
        return getObservableList().stream().collect(Collectors.toList());
    }

    public Optional<Trade> getTradeById(String id) {
        return failedTrades.stream().filter(e -> e.getId().equals(id)).findFirst();
    }

    public Stream<Trade> getTradesStreamWithFundsLockedIn() {
        return failedTrades.stream()
                .filter(Trade::isFundsLockedIn);
    }

    public void unFailTrade(Trade trade) {
        if (unFailTradeCallback == null)
            return;

        if (unFailTradeCallback.test(trade)) {
            log.info("Unfailing trade {}", trade.getId());
            if (failedTrades.remove(trade)) {
                requestPersistence();
            }
        }
    }

    public String checkUnFail(Trade trade) {
        var addresses = tradeUtil.getTradeAddresses(trade);
        if (addresses == null) {
            return "Addresses not found";
        }
        Optional<List<String>> blockingTradeIds = getBlockingTradeIds(trade);
        return blockingTradeIds.map(strings -> String.join(",", strings)).orElse("");
    }

    public Optional<List<String>> getBlockingTradeIds(Trade trade) {
        var tradeAddresses = tradeUtil.getTradeAddresses(trade);
        if (tradeAddresses == null) {
            return Optional.empty();
        }

        Predicate<AddressEntry> isBeingUsedForOtherTrade = (addressEntry) -> {
            if (addressEntry.getContext() == AVAILABLE) {
                return false;
            }
            String address = addressEntry.getAddressString();
            return address != null
                    && (address.equals(tradeAddresses.first) || address.equals(tradeAddresses.second));
        };

        List<String> blockingTradeIds = new ArrayList<>();
        for (var addressEntry : btcWalletService.getAddressEntryListAsImmutableList()) {
            if (isBeingUsedForOtherTrade.test(addressEntry)) {
                var offerId = addressEntry.getOfferId();
                // TODO Be certain 'List<String> blockingTrades' should NOT be populated
                //  with the trade parameter's tradeId.  The 'var addressEntry' will
                //  always be found in the 'var tradeAddresses' tuple, so check
                //  offerId != trade.getId() to avoid the bug being fixed by the next if
                //  statement (if it was a bug).
                if (!Objects.equals(offerId, trade.getId()) && !blockingTradeIds.contains(offerId))
                    blockingTradeIds.add(offerId);
            }
        }
        return blockingTradeIds.isEmpty()
                ? Optional.empty()
                : Optional.of(blockingTradeIds);
    }

    public boolean hasDepositTx(Trade failedTrade) {
        if (failedTrade.getDepositTx() == null) {
            log.warn("Failed trade {} has no deposit tx.", failedTrade.getId());
            return false;
        } else {
            return true;
        }
    }

    public boolean hasDelayedPayoutTxBytes(Trade failedTrade) {
        if (failedTrade.getDelayedPayoutTxBytes() != null) {
            return true;
        } else {
            log.warn("Failed trade {} has no delayedPayoutTxBytes.", failedTrade.getId());
            return allowFaultyDelayedTxs;
        }
    }

    private void requestPersistence() {
        persistenceManager.requestPersistence();
    }
}
