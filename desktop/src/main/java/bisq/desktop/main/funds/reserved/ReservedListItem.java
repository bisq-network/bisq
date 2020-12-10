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
import bisq.desktop.util.DisplayUtils;

import bisq.core.btc.listeners.BalanceListener;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.locale.Res;
import bisq.core.offer.OpenOffer;
import bisq.core.util.coin.CoinFormatter;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import javafx.scene.control.Label;

import java.util.Optional;

import lombok.Getter;

class ReservedListItem {
    private final BalanceListener balanceListener;
    private final BtcWalletService btcWalletService;
    private final CoinFormatter formatter;

    @Getter
    private final Label balanceLabel;
    @Getter
    private final OpenOffer openOffer;
    @Getter
    private final AddressEntry addressEntry;
    @Getter
    private final String addressString;
    @Getter
    private final Address address;
    @Getter
    private Coin balance;
    @Getter
    private String balanceString;

    public ReservedListItem(OpenOffer openOffer,
                            AddressEntry addressEntry,
                            BtcWalletService btcWalletService,
                            CoinFormatter formatter) {
        this.openOffer = openOffer;
        this.addressEntry = addressEntry;
        this.btcWalletService = btcWalletService;
        this.formatter = formatter;
        addressString = addressEntry.getAddressString();
        address = addressEntry.getAddress();
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

    ReservedListItem() {
        this.openOffer = null;
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
        Optional<AddressEntry> addressEntryOptional = btcWalletService.getAddressEntry(openOffer.getId(),
                AddressEntry.Context.RESERVED_FOR_TRADE);
        addressEntryOptional.ifPresent(addressEntry -> {
            balance = btcWalletService.getBalanceForAddress(addressEntry.getAddress());
            if (balance != null) {
                balanceString = formatter.formatCoin(balance);
                balanceLabel.setText(balanceString);
            }
        });
    }

    public String getDateAsString() {
        return DisplayUtils.formatDateTime(openOffer.getDate());
    }

    public String getDetails() {
        return openOffer != null ?
                Res.get("funds.reserved.reserved", openOffer.getShortId()) :
                Res.get("shared.noDetailsAvailable");
    }
}
