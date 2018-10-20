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

package bisq.core.btc;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeManager;
import bisq.core.trade.closed.ClosedTradableManager;
import bisq.core.trade.failed.FailedTradesManager;

import bisq.common.util.Tuple2;

import javax.inject.Inject;

import java.util.Objects;
import java.util.stream.Stream;

public class BalanceUtil {
    private final TradeManager tradeManager;
    private final BtcWalletService btcWalletService;
    private final OpenOfferManager openOfferManager;
    private final ClosedTradableManager closedTradableManager;
    private final FailedTradesManager failedTradesManager;

    @Inject
    public BalanceUtil(TradeManager tradeManager, BtcWalletService btcWalletService, OpenOfferManager openOfferManager,
                       ClosedTradableManager closedTradableManager, FailedTradesManager failedTradesManager) {
        this.tradeManager = tradeManager;
        this.btcWalletService = btcWalletService;
        this.openOfferManager = openOfferManager;
        this.closedTradableManager = closedTradableManager;
        this.failedTradesManager = failedTradesManager;
    }

    public Stream<AddressEntry> getAddressEntriesForAvailableFunds() {
        return tradeManager.getAddressEntriesForAvailableBalanceStream();
    }

    public Stream<AddressEntry> getAddressEntriesForReservedFunds() {
        return getOpenOfferAndAddressEntriesForReservedFunds().map(tuple2 -> tuple2.second);
    }

    public Stream<Tuple2<OpenOffer, AddressEntry>> getOpenOfferAndAddressEntriesForReservedFunds() {
        return openOfferManager.getObservableList().stream()
                .map(openOffer -> btcWalletService.getAddressEntry(openOffer.getId(), AddressEntry.Context.RESERVED_FOR_TRADE)
                        .map(addressEntry -> new Tuple2<>(openOffer, addressEntry))
                        .orElse(null))
                .filter(Objects::nonNull);
    }

    public Stream<AddressEntry> getAddressEntriesForLockedFunds() {
        return getTradesAndAddressEntriesForLockedFunds().map(tuple2 -> tuple2.second);
    }

    public Stream<Tuple2<Trade, AddressEntry>> getTradesAndAddressEntriesForLockedFunds() {
        Stream<Trade> lockedTrades = Stream.concat(closedTradableManager.getLockedTradesStream(), failedTradesManager.getLockedTradesStream());
        lockedTrades = Stream.concat(lockedTrades, tradeManager.getLockedTradesStream());
        return lockedTrades
                .map(trade -> btcWalletService.getAddressEntry(trade.getId(), AddressEntry.Context.MULTI_SIG)
                        .map(addressEntry -> new Tuple2<>(trade, addressEntry))
                        .orElse(null))
                .filter(Objects::nonNull);
    }
}
