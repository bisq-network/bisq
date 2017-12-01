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

package io.bisq.gui.main.funds.withdrawal;

import io.bisq.common.locale.Res;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.listeners.BalanceListener;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.gui.util.BSFormatter;
import javafx.scene.control.Label;
import lombok.Getter;
import lombok.Setter;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

class WithdrawalListItem {
    private final BalanceListener balanceListener;
    private final Label balanceLabel;
    private final AddressEntry addressEntry;
    private final BtcWalletService walletService;
    private final BSFormatter formatter;
    private Coin balance;
    private final String addressString;
    @Setter
    @Getter
    private boolean isSelected;

    public WithdrawalListItem(AddressEntry addressEntry, BtcWalletService walletService,
                              BSFormatter formatter) {
        this.addressEntry = addressEntry;
        this.walletService = walletService;
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
        balance = walletService.getBalanceForAddress(addressEntry.getAddress());
        if (balance != null)
            balanceLabel.setText(formatter.formatCoin(this.balance));
    }

    public final String getLabel() {
        if (addressEntry.isOpenOffer())
            return Res.getWithCol("shared.offerId") + " " + addressEntry.getShortOfferId();
        else if (addressEntry.isTrade())
            return Res.getWithCol("shared.tradeId") + " " + addressEntry.getShortOfferId();
        else if (addressEntry.getContext() == AddressEntry.Context.ARBITRATOR)
            return Res.get("funds.withdrawal.arbitrationFee");
        else
            return "-";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WithdrawalListItem)) return false;

        WithdrawalListItem that = (WithdrawalListItem) o;

        return addressEntry.equals(that.addressEntry);
    }

    @Override
    public int hashCode() {
        return addressEntry.hashCode();
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
