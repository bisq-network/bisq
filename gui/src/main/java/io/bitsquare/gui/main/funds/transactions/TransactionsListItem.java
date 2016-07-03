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
import io.bitsquare.gui.components.indicator.TxConfidenceIndicator;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.trade.Tradable;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.offer.OpenOffer;
import javafx.scene.control.Tooltip;
import org.bitcoinj.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Date;
import java.util.Optional;

public class TransactionsListItem {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private String dateString;
    private final Date date;
    private final String txId;
    private final WalletService walletService;
    private final TxConfidenceIndicator txConfidenceIndicator;
    private final Tooltip tooltip;
    @Nullable
    private Tradable tradable;
    private String details;
    private String addressString = "";
    private String direction;
    private TxConfidenceListener txConfidenceListener;
    private boolean received;
    private boolean detailsAvailable;
    private Coin amountAsCoin = Coin.ZERO;
    private BSFormatter formatter;
    private int confirmations = 0;

    public TransactionsListItem() {
        date = null;
        walletService = null;
        txConfidenceIndicator = null;
        tooltip = null;
        txId = null;
    }

    public TransactionsListItem(Transaction transaction, WalletService walletService, Optional<Tradable> tradableOptional, BSFormatter formatter) {
        this.formatter = formatter;
        txId = transaction.getHashAsString();
        this.walletService = walletService;

        Coin valueSentToMe = transaction.getValueSentToMe(walletService.getWallet());
        Coin valueSentFromMe = transaction.getValueSentFromMe(walletService.getWallet());
        Address address = null;
        if (valueSentToMe.isZero()) {
            amountAsCoin = valueSentFromMe.multiply(-1);

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
            amountAsCoin = valueSentToMe;

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
            amountAsCoin = valueSentToMe.subtract(valueSentFromMe);
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

        // confidence
        txConfidenceIndicator = new TxConfidenceIndicator();
        txConfidenceIndicator.setId("funds-confidence");
        tooltip = new Tooltip("Not used yet");
        txConfidenceIndicator.setProgress(0);
        txConfidenceIndicator.setPrefHeight(30);
        txConfidenceIndicator.setPrefWidth(30);
        Tooltip.install(txConfidenceIndicator, tooltip);

        txConfidenceListener = new TxConfidenceListener(txId) {
            @Override
            public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                updateConfidence(confidence);
            }
        };
        walletService.addTxConfidenceListener(txConfidenceListener);
        updateConfidence(transaction.getConfidence());


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
                } else if (trade.getDisputeState() != Trade.DisputeState.NONE) {
                    if (valueSentToMe.isPositive()) {
                        details = "Dispute payout: " + tradable.getShortId();
                    } else {
                        details = "Lost dispute case: " + tradable.getShortId();
                        txConfidenceIndicator.setVisible(false);
                    }
                } else {
                    details = "Unknown reason: " + tradable.getShortId();
                }
            }
        } else {
            if (amountAsCoin.isZero())
                details = "No refund from dispute";
            else
                details = received ? "Received funds" : "Withdrawn from wallet";
        }

        date = transaction.getUpdateTime();
        dateString = formatter.formatDateTime(date);
    }


    public void cleanup() {
        walletService.removeTxConfidenceListener(txConfidenceListener);
    }

    private void updateConfidence(TransactionConfidence confidence) {
        confirmations = confidence.getDepthInBlocks();
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
                    txConfidenceIndicator.setStyle(" -fx-progress-color: -bs-error-red;");
                    txConfidenceIndicator.setProgress(-1);
                    break;
            }

            txConfidenceIndicator.setPrefSize(24, 24);
        }
    }

    public TxConfidenceIndicator getTxConfidenceIndicator() {
        return txConfidenceIndicator;
    }

    public final String getDateString() {
        return dateString;
    }


    public String getAmount() {
        return formatter.formatCoin(amountAsCoin);
    }

    public Coin getAmountAsCoin() {
        return amountAsCoin;
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

    public Date getDate() {
        return date;
    }

    @Nullable
    public Tradable getTradable() {
        return tradable;
    }

    public String getNumConfirmations() {
        return String.valueOf(confirmations);
    }
}

