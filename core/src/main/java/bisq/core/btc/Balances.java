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

import bisq.core.btc.listeners.BalanceListener;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeManager;
import bisq.core.trade.closed.ClosedTradableManager;
import bisq.core.trade.failed.FailedTradesManager;

import bisq.common.UserThread;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import javafx.collections.ListChangeListener;

import java.util.Objects;
import java.util.stream.Stream;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Balances {
    private final TradeManager tradeManager;
    private final BtcWalletService btcWalletService;
    private final OpenOfferManager openOfferManager;
    private final ClosedTradableManager closedTradableManager;
    private final FailedTradesManager failedTradesManager;

    @Getter
    private final ObjectProperty<Coin> availableBalance = new SimpleObjectProperty<>();
    @Getter
    private final ObjectProperty<Coin> reservedBalance = new SimpleObjectProperty<>();
    @Getter
    private final ObjectProperty<Coin> lockedBalance = new SimpleObjectProperty<>();

    @Inject
    public Balances(TradeManager tradeManager,
                    BtcWalletService btcWalletService,
                    OpenOfferManager openOfferManager,
                    ClosedTradableManager closedTradableManager,
                    FailedTradesManager failedTradesManager) {
        this.tradeManager = tradeManager;
        this.btcWalletService = btcWalletService;
        this.openOfferManager = openOfferManager;
        this.closedTradableManager = closedTradableManager;
        this.failedTradesManager = failedTradesManager;
    }

    public void onAllServicesInitialized() {
        openOfferManager.getObservableList().addListener((ListChangeListener<OpenOffer>) c -> updateBalance());
        tradeManager.getTradableList().addListener((ListChangeListener<Trade>) change -> updateBalance());
        btcWalletService.addBalanceListener(new BalanceListener() {
            @Override
            public void onBalanceChanged(Coin balance, Transaction tx) {
                updateBalance();
            }
        });

        updateBalance();
    }

    private void updateBalance() {
        // Need to delay a bit to get the balances correct
        UserThread.execute(() -> {
            updateAvailableBalance();
            updateReservedBalance();
            updateLockedBalance();
        });
    }

    private void updateAvailableBalance() {
        long sum = tradeManager.getAddressEntriesForAvailableBalanceStream()
                .mapToLong(addressEntry -> btcWalletService.getBalanceForAddress(addressEntry.getAddress()).value)
                .sum();
        availableBalance.set(Coin.valueOf(sum));
    }

    private void updateReservedBalance() {
        long sum = openOfferManager.getObservableList().stream()
                .map(openOffer -> btcWalletService.getAddressEntry(openOffer.getId(), AddressEntry.Context.RESERVED_FOR_TRADE)
                        .orElse(null))
                .filter(Objects::nonNull)
                .mapToLong(addressEntry -> btcWalletService.getBalanceForAddress(addressEntry.getAddress()).value)
                .sum();
        reservedBalance.set(Coin.valueOf(sum));
    }

    private void updateLockedBalance() {
        Stream<Trade> lockedTrades = Stream.concat(closedTradableManager.getLockedTradesStream(), failedTradesManager.getLockedTradesStream());
        lockedTrades = Stream.concat(lockedTrades, tradeManager.getLockedTradesStream());
        long sum = lockedTrades.map(trade -> btcWalletService.getAddressEntry(trade.getId(), AddressEntry.Context.MULTI_SIG)
                .orElse(null))
                .filter(Objects::nonNull)
                .mapToLong(addressEntry -> addressEntry.getCoinLockedInMultiSig().getValue())
                .sum();
        lockedBalance.set(Coin.valueOf(sum));
    }
}
