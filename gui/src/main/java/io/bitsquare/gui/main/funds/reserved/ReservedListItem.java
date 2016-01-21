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
import io.bitsquare.btc.listeners.AddressConfidenceListener;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.gui.components.confidence.ConfidenceProgressIndicator;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.trade.Tradable;
import io.bitsquare.trade.Trade;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.TransactionConfidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReservedListItem {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final BalanceListener balanceListener;

    private final Label balanceLabel;

    private final Tradable tradable;
    private final AddressEntry addressEntry;

    private final WalletService walletService;
    private final BSFormatter formatter;
    private final AddressConfidenceListener confidenceListener;

    private final ConfidenceProgressIndicator progressIndicator;

    private final Tooltip tooltip;
    private final String addressString;
    private Coin balance;

    public ReservedListItem(Tradable tradable, AddressEntry addressEntry, WalletService walletService, BSFormatter formatter) {
        this.tradable = tradable;
        this.addressEntry = addressEntry;
        this.walletService = walletService;
        this.formatter = formatter;
        addressString = addressEntry.getAddressString();

        // confidence
        progressIndicator = new ConfidenceProgressIndicator();
        progressIndicator.setId("funds-confidence");
        tooltip = new Tooltip("Not used yet");
        progressIndicator.setProgress(0);
        progressIndicator.setPrefSize(24, 24);
        Tooltip.install(progressIndicator, tooltip);

        confidenceListener = walletService.addAddressConfidenceListener(new AddressConfidenceListener(getAddress()) {
            @Override
            public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                updateConfidence(confidence);
            }
        });

        updateConfidence(walletService.getConfidenceForAddress(getAddress()));


        // balance
        balanceLabel = new Label();
        balanceListener = walletService.addBalanceListener(new BalanceListener(getAddress()) {
            @Override
            public void onBalanceChanged(Coin balance) {
                updateBalance(balance);
            }
        });

        updateBalance(walletService.getBalanceForAddress(getAddress()));
    }

    public void cleanup() {
        walletService.removeAddressConfidenceListener(confidenceListener);
        walletService.removeBalanceListener(balanceListener);
    }

    private void updateBalance(Coin balance) {
        this.balance = balance;
        if (balance != null) {
            if (tradable instanceof Trade) {
                Trade trade = (Trade) tradable;
                Trade.Phase phase = trade.getState().getPhase();
                switch (phase) {
                    case PREPARATION:
                    case TAKER_FEE_PAID:
                        balanceLabel.setText(formatter.formatCoinWithCode(balance) + " (locally reserved)");
                        break;
                    case DEPOSIT_REQUESTED:
                    case DEPOSIT_PAID:
                    case FIAT_SENT:
                    case FIAT_RECEIVED:
                        // We ignore the tx fee as it will be paid by both (once deposit, once payout)
                        Coin balanceInDeposit = FeePolicy.getSecurityDeposit();
                        // For the seller we add the trade amount
                        if (trade.getContract().getSellerNodeAddress().equals(getAddress()))
                            balanceInDeposit.add(trade.getTradeAmount());

                        balanceLabel.setText(formatter.formatCoinWithCode(balance) + " (in MS escrow)");
                        break;
                    case PAYOUT_PAID:
                        balanceLabel.setText(formatter.formatCoinWithCode(balance) + " (in local wallet)");
                        break;
                    case WITHDRAWN:
                        log.error("Invalid state at updateBalance (WITHDRAWN)");
                        balanceLabel.setText(formatter.formatCoinWithCode(balance) + " already withdrawn");
                        break;
                    case DISPUTE:
                        balanceLabel.setText(formatter.formatCoinWithCode(balance) + " open dispute/ticket");
                        break;
                    default:
                        log.warn("Not supported tradePhase: " + phase);
                }

            } else
                balanceLabel.setText(formatter.formatCoin(balance));
        }
    }

    private void updateConfidence(TransactionConfidence confidence) {
        if (confidence != null) {
            //log.debug("Type numBroadcastPeers getDepthInBlocks " + confidence.getConfidenceType() + " / " +
            // confidence.numBroadcastPeers() + " / " + confidence.getDepthInBlocks());
            switch (confidence.getConfidenceType()) {
                case UNKNOWN:
                    tooltip.setText("Unknown transaction status");
                    progressIndicator.setProgress(0);
                    break;
                case PENDING:
                    tooltip.setText("Seen by " + confidence.numBroadcastPeers() + " peer(s) / 0 confirmations");
                    progressIndicator.setProgress(-1.0);
                    break;
                case BUILDING:
                    tooltip.setText("Confirmed in " + confidence.getDepthInBlocks() + " block(s)");
                    progressIndicator.setProgress(Math.min(1, (double) confidence.getDepthInBlocks() / 6.0));
                    break;
                case DEAD:
                    tooltip.setText("Transaction is invalid.");
                    progressIndicator.setProgress(0);
                    break;
            }
        }
    }


    public final String getLabel() {
        switch (addressEntry.getContext()) {
            case TRADE:
                if (tradable instanceof Trade)
                    return "Trade ID: " + addressEntry.getShortOfferId();
                else
                    return "Offer ID: " + addressEntry.getShortOfferId();
            case ARBITRATOR:
                return "Arbitration deposit";
        }
        return "";
    }

    private Address getAddress() {
        return addressEntry.getAddress();
    }


    public AddressEntry getAddressEntry() {
        return addressEntry;
    }


    public ConfidenceProgressIndicator getProgressIndicator() {
        return progressIndicator;
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
