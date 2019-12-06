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

package bisq.desktop.main.funds.reserved;

import bisq.desktop.components.AutoTooltipLabel;

import bisq.core.btc.listeners.BalanceListener;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.OpenOffer;
import bisq.core.trade.Tradable;
import bisq.core.util.coin.CoinFormatter;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import javafx.scene.control.Label;

import java.util.Optional;

class ReservedListItem {
    private final BalanceListener balanceListener;
    private final Label balanceLabel;
    private final OpenOffer openOffer;
    private final AddressEntry addressEntry;
    private final BtcWalletService btcWalletService;
    private final CoinFormatter formatter;
    private final String addressString;
    private Coin balance;

    public ReservedListItem(OpenOffer openOffer, AddressEntry addressEntry, BtcWalletService btcWalletService, CoinFormatter formatter) {
        this.openOffer = openOffer;
        this.addressEntry = addressEntry;
        this.btcWalletService = btcWalletService;
        this.formatter = formatter;
        addressString = addressEntry.getAddressString();

        // balance
        balanceLabel = new AutoTooltipLabel();
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
        final Optional<AddressEntry> addressEntryOptional = btcWalletService.getAddressEntry(openOffer.getId(), AddressEntry.Context.RESERVED_FOR_TRADE);
        addressEntryOptional.ifPresent(addressEntry -> {
            balance = btcWalletService.getBalanceForAddress(addressEntry.getAddress());
            if (balance != null)
                balanceLabel.setText(formatter.formatCoin(balance));
        });
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

    public Tradable getOpenOffer() {
        return openOffer;
    }

}
