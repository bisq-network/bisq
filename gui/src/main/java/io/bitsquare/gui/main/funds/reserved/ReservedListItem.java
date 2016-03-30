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

package io.bitsquare.gui.main.funds.reserved;

import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.trade.Tradable;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.offer.OpenOffer;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Label;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReservedListItem {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final StringProperty date = new SimpleStringProperty();
    private final BalanceListener balanceListener;
    private final Label balanceLabel;
    private String fundsInfo;
    private final Tradable tradable;
    private final AddressEntry addressEntry;
    private final WalletService walletService;
    private final BSFormatter formatter;
    private final String addressString;
    private Coin balance;

    public ReservedListItem(Tradable tradable, AddressEntry addressEntry, WalletService walletService, BSFormatter formatter) {
        this.tradable = tradable;
        this.addressEntry = addressEntry;
        this.walletService = walletService;
        this.formatter = formatter;
        addressString = addressEntry.getAddressString();

        date.set(formatter.formatDateTime(tradable.getDate()));

        // balance
        balanceLabel = new Label();
        balanceListener = new BalanceListener(getAddress()) {
            @Override
            public void onBalanceChanged(Coin balance, Transaction tx) {
                updateBalance(balance);
            }
        };
        walletService.addBalanceListener(balanceListener);
        updateBalance(walletService.getBalanceForAddress(getAddress()));
    }

    public void cleanup() {
        walletService.removeBalanceListener(balanceListener);
    }

    private void updateBalance(Coin balance) {
        this.balance = balance;
        if (balance != null) {
            balanceLabel.setText(formatter.formatCoin(balance));

            if (tradable instanceof Trade) {
                Trade trade = (Trade) tradable;
                Trade.Phase phase = trade.getState().getPhase();
                switch (phase) {
                    case PREPARATION:
                    case TAKER_FEE_PAID:
                        fundsInfo = "Reserved in local wallet";
                        break;
                    case DEPOSIT_REQUESTED:
                    case DEPOSIT_PAID:
                    case FIAT_SENT:
                    case FIAT_RECEIVED:
                        fundsInfo = "Locked in MultiSig";
                        // We ignore the tx fee as it will be paid by both (once deposit, once payout)
                        Coin balanceInDeposit = FeePolicy.getSecurityDeposit().add(FeePolicy.getFeePerKb());
                        // For the seller we add the trade amount
                        if (trade.getContract() != null &&
                                trade.getTradeAmount() != null &&
                                trade.getContract().getSellerPayoutAddressString().equals(addressString))
                            balanceInDeposit = balanceInDeposit.add(trade.getTradeAmount());

                        balanceLabel.setText(formatter.formatCoin(balanceInDeposit));
                        break;
                    case PAYOUT_PAID:
                        fundsInfo = "Received in local wallet";
                        break;
                    case WITHDRAWN:
                        log.error("Invalid state at updateBalance (WITHDRAWN)");
                        break;
                    case DISPUTE:
                        log.error("Invalid state at updateBalance (DISPUTE)");
                        break;
                    default:
                        log.warn("Not supported tradePhase: " + phase);
                }
            } else if (tradable instanceof OpenOffer) {
                fundsInfo = "Reserved in local wallet";
            }
        }
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

    public String getFundsInfo() {
        return fundsInfo;
    }

    public Tradable getTradable() {
        return tradable;
    }

}
