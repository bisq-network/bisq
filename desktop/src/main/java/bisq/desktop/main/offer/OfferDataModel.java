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

package bisq.desktop.main.offer;

import bisq.desktop.common.model.ActivatableDataModel;

import bisq.core.btc.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;

import org.bitcoinj.core.Coin;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

import lombok.Getter;

/**
 * Domain for that UI element.
 * Note that the create offer domain has a deeper scope in the application domain (TradeManager).
 * That model is just responsible for the domain specific parts displayed needed in that UI element.
 */
public abstract class OfferDataModel extends ActivatableDataModel {
    protected final BtcWalletService btcWalletService;

    @Getter
    protected final BooleanProperty isBtcWalletFunded = new SimpleBooleanProperty();
    @Getter
    protected final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();
    @Getter
    protected final ObjectProperty<Coin> balance = new SimpleObjectProperty<>();
    @Getter
    protected final ObjectProperty<Coin> missingCoin = new SimpleObjectProperty<>(Coin.ZERO);
    @Getter
    protected final BooleanProperty showWalletFundedNotification = new SimpleBooleanProperty();
    @Getter
    protected Coin totalAvailableBalance;
    protected AddressEntry addressEntry;
    protected boolean useSavingsWallet;

    public OfferDataModel(BtcWalletService btcWalletService) {
        this.btcWalletService = btcWalletService;
    }

    protected void updateBalance() {
        Coin tradeWalletBalance = btcWalletService.getBalanceForAddress(addressEntry.getAddress());
        if (useSavingsWallet) {
            Coin savingWalletBalance = btcWalletService.getSavingWalletBalance();
            totalAvailableBalance = savingWalletBalance.add(tradeWalletBalance);
            if (totalToPayAsCoin.get() != null) {
                if (totalAvailableBalance.compareTo(totalToPayAsCoin.get()) > 0)
                    balance.set(totalToPayAsCoin.get());
                else
                    balance.set(totalAvailableBalance);
            }
        } else {
            balance.set(tradeWalletBalance);
        }
        if (totalToPayAsCoin.get() != null) {
            missingCoin.set(totalToPayAsCoin.get().subtract(balance.get()));
            if (missingCoin.get().isNegative())
                missingCoin.set(Coin.ZERO);
        }

        isBtcWalletFunded.set(isBalanceSufficient(balance.get()));
        if (totalToPayAsCoin.get() != null && isBtcWalletFunded.get() && !showWalletFundedNotification.get()) {
            showWalletFundedNotification.set(true);
        }
    }

    private boolean isBalanceSufficient(Coin balance) {
        return totalToPayAsCoin.get() != null && balance.compareTo(totalToPayAsCoin.get()) >= 0;
    }
}
