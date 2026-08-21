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

package bisq.desktop.main.funds.withdrawal;

import bisq.desktop.util.filtering.FilterableListItem;
import bisq.desktop.components.AutoTooltipLabel;

import bisq.core.btc.listeners.BalanceListener;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.locale.Res;
import bisq.core.util.coin.CoinFormatter;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import org.apache.commons.lang3.StringUtils;

import javafx.scene.control.Label;

import lombok.Getter;
import lombok.Setter;

class WithdrawalListItem implements FilterableListItem {
    private final BalanceListener balanceListener;
    private final Label balanceLabel;
    private final AddressEntry addressEntry;
    private final BtcWalletService walletService;
    private final CoinFormatter formatter;
    private Coin balance;
    private final String addressString;
    @Setter
    @Getter
    private boolean isSelected;

    public WithdrawalListItem(AddressEntry addressEntry, BtcWalletService walletService,
                              CoinFormatter formatter) {
        this.addressEntry = addressEntry;
        this.walletService = walletService;
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

    public String getBalanceAsString() {
        return formatter.formatCoin(balance);
    }

    public String getAddressString() {
        return addressString;
    }

    @Override
    public boolean match(String filterString) {
        if (filterString.isEmpty()) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getBalanceAsString(), filterString)) {
            return true;
        }
        return StringUtils.containsIgnoreCase(getAddressString(), filterString);

    }
}
