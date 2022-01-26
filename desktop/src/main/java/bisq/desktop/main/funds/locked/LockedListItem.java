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

package bisq.desktop.main.funds.locked;

import bisq.desktop.util.filtering.FilterableListItem;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.filtering.FilteringUtils;

import bisq.core.btc.listeners.BalanceListener;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletService;
import bisq.core.locale.Res;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.util.coin.CoinFormatter;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import org.apache.commons.lang3.StringUtils;

import javafx.scene.control.Label;

import lombok.Getter;

import javax.annotation.Nullable;

class LockedListItem implements FilterableListItem {
    private final BalanceListener balanceListener;
    private final BtcWalletService btcWalletService;
    private final CoinFormatter formatter;

    @Getter
    private final Label balanceLabel;
    @Getter
    private final Trade trade;
    @Getter
    private final AddressEntry addressEntry;
    @Getter
    private final String addressString;
    @Nullable
    private final Address address;
    @Getter
    private Coin balance;
    @Getter
    private String balanceString;

    public LockedListItem(Trade trade,
                          AddressEntry addressEntry,
                          BtcWalletService btcWalletService,
                          CoinFormatter formatter) {
        this.trade = trade;
        this.addressEntry = addressEntry;
        this.btcWalletService = btcWalletService;
        this.formatter = formatter;

        if (trade.getDepositTx() != null && !trade.getDepositTx().getOutputs().isEmpty()) {
            address = WalletService.getAddressFromOutput(trade.getDepositTx().getOutput(0));
            addressString = address != null ? address.toString() : "";
        } else {
            address = null;
            addressString = "";
        }
        balanceLabel = new AutoTooltipLabel();
        balanceListener = new BalanceListener(address) {
            @Override
            public void onBalanceChanged(Coin balance, Transaction tx) {
                updateBalance();
            }
        };
        btcWalletService.addBalanceListener(balanceListener);
        updateBalance();
    }

    LockedListItem() {
        this.trade = null;
        this.addressEntry = null;
        this.btcWalletService = null;
        this.formatter = null;
        addressString = null;
        address = null;
        balanceLabel = null;
        balanceListener = null;
    }

    public void cleanup() {
        btcWalletService.removeBalanceListener(balanceListener);
    }

    private void updateBalance() {
        balance = addressEntry.getCoinLockedInMultiSigAsCoin();
        balanceString = formatter.formatCoin(this.balance);
        balanceLabel.setText(balanceString);
    }

    public String getDetails() {
        return trade != null ?
                Res.get("funds.locked.locked", trade.getShortId()) :
                Res.get("shared.noDetailsAvailable");
    }

    public String getDateString() {
        return trade != null ?
                DisplayUtils.formatDateTime(trade.getDate()) :
                Res.get("shared.noDateAvailable");
    }

    @Override
    public boolean match(String filterString) {
        if (filterString.isEmpty())
            return true;

        if (StringUtils.containsIgnoreCase(getDetails(), filterString)) {
            return true;
        }

        if (StringUtils.containsIgnoreCase(getDateString(), filterString)) {
            return true;
        }

        if (StringUtils.containsIgnoreCase(getAddressString(), filterString)) {
            return true;
        }

        if (StringUtils.containsIgnoreCase(getBalanceString(), filterString)) {
            return true;
        }

        return FilteringUtils.match(getTrade(), filterString);
    }
}
