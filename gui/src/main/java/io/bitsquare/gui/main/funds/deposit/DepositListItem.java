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
import io.bitsquare.gui.components.confidence.ConfidenceProgressIndicator;
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
    private BSFormatter formatter;
    private final ConfidenceProgressIndicator progressIndicator;
    private final Tooltip tooltip;


    private String balanceString;
    private String addressString;
    private String status = "Unused";
    private TxConfidenceListener txConfidenceListener;

    // public DepositListItem(AddressEntry addressEntry, Transaction transaction, WalletService walletService, Optional<Tradable> tradableOptional, BSFormatter formatter) {
    public DepositListItem(AddressEntry addressEntry, WalletService walletService, BSFormatter formatter) {
        this.walletService = walletService;
        this.formatter = formatter;

        addressString = addressEntry.getAddressString();

        // confidence
        progressIndicator = new ConfidenceProgressIndicator();
        progressIndicator.setId("funds-confidence");
        tooltip = new Tooltip("Not used yet");
        progressIndicator.setProgress(0);
        progressIndicator.setPrefHeight(30);
        progressIndicator.setPrefWidth(30);
        Tooltip.install(progressIndicator, tooltip);

        final Address address = addressEntry.getAddress();
        walletService.addBalanceListener(new BalanceListener(address) {
            @Override
            public void onBalanceChanged(Coin balanceAsCoin, Transaction tx) {
                DepositListItem.this.balance.set(formatter.formatCoin(balanceAsCoin));
                updateConfidence(walletService.getConfidenceForTxId(tx.getHashAsString()));
                if (balanceAsCoin.isPositive())
                    status = "Funded";
            }
        });

        Coin balanceAsCoin = walletService.getBalanceForAddress(address);
        balance.set(formatter.formatCoin(balanceAsCoin));
        if (balanceAsCoin.isPositive())
            status = "Funded";

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

    public void setStatus(String status) {
        this.status = status;
    }

    public void cleanup() {
        walletService.removeTxConfidenceListener(txConfidenceListener);
    }

    private void updateConfidence(TransactionConfidence confidence) {
        if (confidence != null) {
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

            progressIndicator.setPrefSize(24, 24);
        }
    }

    public ConfidenceProgressIndicator getProgressIndicator() {
        return progressIndicator;
    }

    public String getAddressString() {
        return addressString;
    }

    public String getStatus() {
        return status;
    }

    public final StringProperty balanceProperty() {
        return this.balance;
    }

    public String getBalance() {
        return balance.get();
    }

}
