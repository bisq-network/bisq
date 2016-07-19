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

package io.bitsquare.gui.main.funds.deposit;

import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.btc.listeners.TxConfidenceListener;
import io.bitsquare.gui.components.indicator.TxConfidenceIndicator;
import io.bitsquare.gui.util.BSFormatter;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Tooltip;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DepositListItem {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final StringProperty balance = new SimpleStringProperty();
    private final WalletService walletService;
    private Coin balanceAsCoin;
    private final TxConfidenceIndicator txConfidenceIndicator;
    private final Tooltip tooltip;
    private String addressString;
    private String usage = "-";
    private TxConfidenceListener txConfidenceListener;
    private int numTxOutputs = 0;

    public DepositListItem(AddressEntry addressEntry, WalletService walletService, BSFormatter formatter) {
        this.walletService = walletService;

        addressString = addressEntry.getAddressString();

        // confidence
        txConfidenceIndicator = new TxConfidenceIndicator();
        txConfidenceIndicator.setId("funds-confidence");
        tooltip = new Tooltip("Not used yet");
        txConfidenceIndicator.setProgress(0);
        txConfidenceIndicator.setPrefHeight(30);
        txConfidenceIndicator.setPrefWidth(30);
        Tooltip.install(txConfidenceIndicator, tooltip);

        final Address address = addressEntry.getAddress();
        walletService.addBalanceListener(new BalanceListener(address) {
            @Override
            public void onBalanceChanged(Coin balanceAsCoin, Transaction tx) {
                DepositListItem.this.balanceAsCoin = balanceAsCoin;
                DepositListItem.this.balance.set(formatter.formatCoin(balanceAsCoin));
                updateConfidence(walletService.getConfidenceForTxId(tx.getHashAsString()));
                updateUsage(address);
            }
        });

        balanceAsCoin = walletService.getBalanceForAddress(address);
        balance.set(formatter.formatCoin(balanceAsCoin));

        updateUsage(address);

        TransactionConfidence transactionConfidence = walletService.getConfidenceForAddress(address);
        if (transactionConfidence != null) {
            updateConfidence(transactionConfidence);

            txConfidenceListener = new TxConfidenceListener(transactionConfidence.getTransactionHash().toString()) {
                @Override
                public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                    updateConfidence(confidence);
                }
            };
            walletService.addTxConfidenceListener(txConfidenceListener);
        }
    }

    private void updateUsage(Address address) {
        numTxOutputs = walletService.getNumTxOutputsForAddress(address);
        usage = numTxOutputs == 0 ? "Unused" : "Used in " + numTxOutputs + " transactions";
    }

    public void cleanup() {
        walletService.removeTxConfidenceListener(txConfidenceListener);
    }

    private void updateConfidence(TransactionConfidence confidence) {
        if (confidence != null) {
            switch (confidence.getConfidenceType()) {
                case UNKNOWN:
                    tooltip.setText("Unknown transaction status");
                    txConfidenceIndicator.setProgress(0);
                    break;
                case PENDING:
                    tooltip.setText("Seen by " + confidence.numBroadcastPeers() + " peer(s) / 0 confirmations");
                    txConfidenceIndicator.setProgress(-1.0);
                    break;
                case BUILDING:
                    tooltip.setText("Confirmed in " + confidence.getDepthInBlocks() + " block(s)");
                    txConfidenceIndicator.setProgress(Math.min(1, (double) confidence.getDepthInBlocks() / 6.0));
                    break;
                case DEAD:
                    tooltip.setText("Transaction is invalid.");
                    txConfidenceIndicator.setProgress(0);
                    break;
            }

            txConfidenceIndicator.setPrefSize(24, 24);
        }
    }

    public TxConfidenceIndicator getTxConfidenceIndicator() {
        return txConfidenceIndicator;
    }

    public String getAddressString() {
        return addressString;
    }

    public String getUsage() {
        return usage;
    }

    public final StringProperty balanceProperty() {
        return this.balance;
    }

    public String getBalance() {
        return balance.get();
    }

    public Coin getBalanceAsCoin() {
        return balanceAsCoin;
    }

    public int getNumTxOutputs() {
        return numTxOutputs;
    }
}
