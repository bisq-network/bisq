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

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.OpenOfferManager;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeManager;
import bisq.core.trade.closed.ClosedTradableManager;
import bisq.core.trade.failed.FailedTradesManager;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import lombok.Getter;

public class BalanceModel {
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
    public BalanceModel(TradeManager tradeManager, BtcWalletService btcWalletService, OpenOfferManager openOfferManager,
                        ClosedTradableManager closedTradableManager, FailedTradesManager failedTradesManager) {
        this.tradeManager = tradeManager;
        this.btcWalletService = btcWalletService;
        this.openOfferManager = openOfferManager;
        this.closedTradableManager = closedTradableManager;
        this.failedTradesManager = failedTradesManager;
    }

    public void updateBalance() {
        //TODO check if still needed
      /*  // Without delaying to the next cycle it does not update.
        // Seems order of events we are listening on causes that...
        UserThread.execute(() -> {
            updateAvailableBalance();
            updateReservedBalance();
            updateLockedBalance();
        });*/
        updateAvailableBalance();
        updateReservedBalance();
        updateLockedBalance();
        // TODO add lockingBalance
    }

    private void updateAvailableBalance() {
        Coin totalAvailableBalance = Coin.valueOf(tradeManager.getAddressEntriesForAvailableBalanceStream()
                .mapToLong(addressEntry -> btcWalletService.getBalanceForAddress(addressEntry.getAddress()).getValue())
                .sum());
        availableBalance.set(totalAvailableBalance);
    }

    private void updateReservedBalance() {
        Coin sum = Coin.valueOf(openOfferManager.getObservableList().stream()
                .map(openOffer -> {
                    final Optional<AddressEntry> addressEntryOptional = btcWalletService.getAddressEntry(openOffer.getId(), AddressEntry.Context.RESERVED_FOR_TRADE);
                    if (addressEntryOptional.isPresent()) {
                        Address address = addressEntryOptional.get().getAddress();
                        return btcWalletService.getBalanceForAddress(address);
                    } else {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .mapToLong(Coin::getValue)
                .sum());

        reservedBalance.set(sum);
    }

    private void updateLockedBalance() {
        Stream<Trade> lockedTrades = Stream.concat(closedTradableManager.getLockedTradesStream(), failedTradesManager.getLockedTradesStream());
        lockedTrades = Stream.concat(lockedTrades, tradeManager.getLockedTradesStream());
        Coin sum = Coin.valueOf(lockedTrades
                .mapToLong(trade -> {
                    final Optional<AddressEntry> addressEntryOptional = btcWalletService.getAddressEntry(trade.getId(), AddressEntry.Context.MULTI_SIG);
                    return addressEntryOptional.map(addressEntry -> addressEntry.getCoinLockedInMultiSig().getValue()).orElse(0L);
                })
                .sum());
        lockedBalance.set(sum);
    }
}
