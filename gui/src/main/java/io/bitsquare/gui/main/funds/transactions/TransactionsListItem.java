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

package io.bitsquare.gui.main.funds.transactions;

import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.listeners.TxConfidenceListener;
import io.bitsquare.gui.components.confidence.ConfidenceProgressIndicator;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.trade.Tradable;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.offer.OpenOffer;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Tooltip;
import org.bitcoinj.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class TransactionsListItem {

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final StringProperty date = new SimpleStringProperty();
    private final StringProperty amount = new SimpleStringProperty();
    private final String txId;
    private final WalletService walletService;
    private final ConfidenceProgressIndicator progressIndicator;
    private final Tooltip tooltip;
    private Tradable tradable;
    private String details;
    private String addressString;
    private String direction;
    private TxConfidenceListener txConfidenceListener;
    private boolean received;
    private boolean detailsAvailable;

    public TransactionsListItem(Transaction transaction, WalletService walletService, Optional<Tradable> tradableOptional, BSFormatter formatter) {
        txId = transaction.getHashAsString();
        this.walletService = walletService;

        Coin valueSentToMe = transaction.getValueSentToMe(walletService.getWallet());
        Coin valueSentFromMe = transaction.getValueSentFromMe(walletService.getWallet());
        Address address = null;
        if (valueSentToMe.isZero()) {
            amount.set("-" + formatter.formatCoin(valueSentFromMe));

            for (TransactionOutput transactionOutput : transaction.getOutputs()) {
                if (!transactionOutput.isMine(walletService.getWallet())) {
                    direction = "Sent to:";
                    received = false;
                    if (transactionOutput.getScriptPubKey().isSentToAddress()
                            || transactionOutput.getScriptPubKey().isPayToScriptHash()) {
                        address = transactionOutput.getScriptPubKey().getToAddress(walletService.getWallet().getParams());
                        addressString = address.toString();
                    }
                }
            }
        } else if (valueSentFromMe.isZero()) {
            amount.set(formatter.formatCoin(valueSentToMe));
            direction = "Received with:";
            received = true;

            for (TransactionOutput transactionOutput : transaction.getOutputs()) {
                if (transactionOutput.isMine(walletService.getWallet())) {
                    if (transactionOutput.getScriptPubKey().isSentToAddress() ||
                            transactionOutput.getScriptPubKey().isPayToScriptHash()) {
                        address = transactionOutput.getScriptPubKey().getToAddress(walletService.getWallet().getParams());
                        addressString = address.toString();
                    }
                }
            }
        } else {
            amount.set(formatter.formatCoin(valueSentToMe.subtract(valueSentFromMe)));
            boolean outgoing = false;
            for (TransactionOutput transactionOutput : transaction.getOutputs()) {
                if (!transactionOutput.isMine(walletService.getWallet())) {
                    outgoing = true;
                    if (transactionOutput.getScriptPubKey().isSentToAddress() ||
                            transactionOutput.getScriptPubKey().isPayToScriptHash()) {
                        address = transactionOutput.getScriptPubKey().getToAddress(walletService.getWallet().getParams());
                        addressString = address.toString();
                    }
                }
            }

            if (outgoing) {
                direction = "Sent to:";
                received = false;
            }
        }


        if (tradableOptional.isPresent()) {
            tradable = tradableOptional.get();
            detailsAvailable = true;
            if (tradable instanceof OpenOffer) {
                details = "Create offer fee: " + tradable.getShortId();
            } else if (tradable instanceof Trade) {
                Trade trade = (Trade) tradable;
                if (trade.getTakeOfferFeeTxId() != null && trade.getTakeOfferFeeTxId().equals(txId)) {
                    details = "Take offer fee: " + tradable.getShortId();
                } else if (trade.getOffer() != null &&
                        trade.getOffer().getOfferFeePaymentTxID() != null &&
                        trade.getOffer().getOfferFeePaymentTxID().equals(txId)) {
                    details = "Create offer fee: " + tradable.getShortId();
                } else if (trade.getDepositTx() != null &&
                        trade.getDepositTx().getHashAsString().equals(txId)) {
                    details = "MultiSig deposit: " + tradable.getShortId();
                } else if (trade.getPayoutTx() != null &&
                        trade.getPayoutTx().getHashAsString().equals(txId)) {
                    details = "MultiSig payout: " + tradable.getShortId();
                } else if (trade.getDisputeState() == Trade.DisputeState.DISPUTE_CLOSED) {
                    details = "Payout after dispute: " + tradable.getShortId();
                } else {
                    details = "Unknown reason: " + tradable.getShortId();
                }
            }
        } else {
            details = received ? "Funded to wallet" : "Withdrawn from wallet";
        }

        date.set(formatter.formatDateTime(transaction.getUpdateTime()));

        // confidence
        progressIndicator = new ConfidenceProgressIndicator();
        progressIndicator.setId("funds-confidence");
        tooltip = new Tooltip("Not used yet");
        progressIndicator.setProgress(0);
        progressIndicator.setPrefHeight(30);
        progressIndicator.setPrefWidth(30);
        Tooltip.install(progressIndicator, tooltip);

        if (address != null) {
            txConfidenceListener = walletService.addTxConfidenceListener(new TxConfidenceListener(txId) {
                @Override
                public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                    updateConfidence(confidence);
                }
            });

            updateConfidence(transaction.getConfidence());
        }
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

    public final StringProperty dateProperty() {
        return this.date;
    }

    public final StringProperty amountProperty() {
        return this.amount;
    }

    public String getAddressString() {
        return addressString;
    }

    public String getDirection() {
        return direction;
    }

    public String getTxId() {
        return txId;
    }

    public boolean getReceived() {
        return received;
    }

    public String getDetails() {
        return details;
    }

    public boolean getDetailsAvailable() {
        return detailsAvailable;
    }

    public Tradable getTradable() {
        return tradable;
    }
}

