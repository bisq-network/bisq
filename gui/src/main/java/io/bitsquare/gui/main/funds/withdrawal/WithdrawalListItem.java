/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main.funds.withdrawal;

import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.trade.TradableHelper;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.closed.ClosedTradableManager;
import io.bitsquare.trade.failed.FailedTradesManager;
import io.bitsquare.trade.offer.OpenOfferManager;
import javafx.scene.control.Label;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

public class WithdrawalListItem {
    private final BalanceListener balanceListener;
    private final Label balanceLabel;
    private final AddressEntry addressEntry;
    private final WalletService walletService;
    private final OpenOfferManager openOfferManager;
    private final TradeManager tradeManager;
    private final ClosedTradableManager closedTradableManager;
    private final FailedTradesManager failedTradesManager;
    private final BSFormatter formatter;
    private Coin balance;
    private final String addressString;

    public WithdrawalListItem(AddressEntry addressEntry, WalletService walletService,
                              OpenOfferManager openOfferManager, TradeManager tradeManager,
                              ClosedTradableManager closedTradableManager,
                              FailedTradesManager failedTradesManager,
                              BSFormatter formatter) {
        this.addressEntry = addressEntry;
        this.walletService = walletService;
        this.openOfferManager = openOfferManager;
        this.tradeManager = tradeManager;
        this.closedTradableManager = closedTradableManager;
        this.failedTradesManager = failedTradesManager;
        this.formatter = formatter;
        addressString = addressEntry.getAddressString();

        // balance
        balanceLabel = new Label();
        balanceListener = new BalanceListener(getAddress()) {
            @Override
            public void onBalanceChanged(Coin balance, Transaction tx) {
                updateBalance();
            }
        };
        walletService.addBalanceListener(balanceListener);

        updateBalance();
    }

    public void cleanup() {
        walletService.removeBalanceListener(balanceListener);
    }

    private void updateBalance() {
        balance = TradableHelper.getAvailableBalance(addressEntry,
                walletService,
                openOfferManager,
                tradeManager,
                closedTradableManager,
                failedTradesManager);

        if (balance != null)
            balanceLabel.setText(formatter.formatCoin(this.balance));
    }

    public final String getLabel() {
        switch (addressEntry.getContext()) {
            case TRADE:
                return addressEntry.getShortOfferId();
            case ARBITRATOR:
                return "Arbitration fee";
        }
        return "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WithdrawalListItem)) return false;

        WithdrawalListItem that = (WithdrawalListItem) o;

        return !(addressEntry != null ? !addressEntry.equals(that.addressEntry) : that.addressEntry != null);

    }

    @Override
    public int hashCode() {
        return addressEntry != null ? addressEntry.hashCode() : 0;
    }

    private Address getAddress() {
        return addressEntry.getAddress();
    }

    public AddressEntry getAddressEntry() {
        return addressEntry;
    }

    public Label getBalanceLabel() {
        return balanceLabel;
    }

    public Coin getBalance() {
        return balance;
    }

    public String getAddressString() {
        return addressString;
    }
}
