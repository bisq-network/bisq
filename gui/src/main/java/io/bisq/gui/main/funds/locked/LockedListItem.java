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

package io.bisq.gui.main.funds.locked;

import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.listeners.BalanceListener;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.btc.wallet.WalletService;
import io.bisq.core.trade.Tradable;
import io.bisq.core.trade.Trade;
import io.bisq.gui.util.BSFormatter;
import javafx.scene.control.Label;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import javax.annotation.Nullable;

class LockedListItem {
    private final BalanceListener balanceListener;
    private final Label balanceLabel;
    private final Trade trade;
    private final AddressEntry addressEntry;
    private final BtcWalletService btcWalletService;
    private final BSFormatter formatter;
    private final String addressString;
    @Nullable
    private final Address address;
    private Coin balance;

    public LockedListItem(Trade trade, AddressEntry addressEntry, BtcWalletService btcWalletService, BSFormatter formatter) {
        this.trade = trade;
        this.addressEntry = addressEntry;
        this.btcWalletService = btcWalletService;
        this.formatter = formatter;

        if (trade.getDepositTx() != null && !trade.getDepositTx().getOutputs().isEmpty()) {
            address = WalletService.getAddressFromOutput(trade.getDepositTx().getOutput(0));
            addressString = address.toBase58();
        } else {
            address = null;
            addressString = "";
        }

        // balance
        balanceLabel = new Label();
        balanceListener = new BalanceListener(getAddress()) {
            @Override
            public void onBalanceChanged(Coin balance, Transaction tx) {
                updateBalance();
            }
        };
        btcWalletService.addBalanceListener(balanceListener);
        updateBalance();
    }

    public void cleanup() {
        btcWalletService.removeBalanceListener(balanceListener);
    }

    private void updateBalance() {
        balance = addressEntry.getCoinLockedInMultiSig();
        balanceLabel.setText(formatter.formatCoin(this.balance));
    }

    @Nullable
    private Address getAddress() {
        return address;
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

    public Tradable getTrade() {
        return trade;
    }

}
