/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.funds.transactions;

import io.bisq.btc.listeners.TxConfidenceListener;
import io.bisq.btc.wallet.BtcWalletService;
import io.bisq.gui.components.indicator.TxConfidenceIndicator;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.GUIUtil;
import io.bisq.locale.Res;
import io.bisq.p2p.protocol.availability.Offer;
import io.bisq.trade.Tradable;
import io.bisq.trade.Trade;
import io.bisq.trade.offer.OpenOffer;
import javafx.scene.control.Tooltip;
import org.bitcoinj.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Date;
import java.util.Optional;

class TransactionsListItem {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private String dateString;
    private final Date date;
    private final String txId;
    private final BtcWalletService walletService;
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

    public TransactionsListItem(Transaction transaction, BtcWalletService walletService, Optional<Tradable> tradableOptional, BSFormatter formatter) {
        this.formatter = formatter;
        txId = transaction.getHashAsString();
        this.walletService = walletService;

        Coin valueSentToMe = walletService.getValueSentToMeForTransaction(transaction);
        Coin valueSentFromMe = walletService.getValueSentFromMeForTransaction(transaction);
        Address address;
        if (valueSentToMe.isZero()) {
            amountAsCoin = valueSentFromMe.multiply(-1);

            for (TransactionOutput transactionOutput : transaction.getOutputs()) {
                if (!walletService.isTransactionOutputMine(transactionOutput)) {
                    direction = Res.get("funds.tx.direction.sentTo");
                    received = false;
                    if (transactionOutput.getScriptPubKey().isSentToAddress()
                            || transactionOutput.getScriptPubKey().isPayToScriptHash()) {
                        address = transactionOutput.getScriptPubKey().getToAddress(walletService.getParams());
                        addressString = address.toString();
                    }
                }
            }
        } else if (valueSentFromMe.isZero()) {
            amountAsCoin = valueSentToMe;

            direction = Res.get("funds.tx.direction.receivedWith");
            received = true;

            for (TransactionOutput transactionOutput : transaction.getOutputs()) {
                if (!walletService.isTransactionOutputMine(transactionOutput)) {
                    if (transactionOutput.getScriptPubKey().isSentToAddress() ||
                            transactionOutput.getScriptPubKey().isPayToScriptHash()) {
                        address = transactionOutput.getScriptPubKey().getToAddress(walletService.getParams());
                        addressString = address.toString();
                    }
                }
            }
        } else {
            amountAsCoin = valueSentToMe.subtract(valueSentFromMe);
            boolean outgoing = false;
            for (TransactionOutput transactionOutput : transaction.getOutputs()) {
                if (!walletService.isTransactionOutputMine(transactionOutput)) {
                    outgoing = true;
                    if (transactionOutput.getScriptPubKey().isSentToAddress() ||
                            transactionOutput.getScriptPubKey().isPayToScriptHash()) {
                        address = transactionOutput.getScriptPubKey().getToAddress(walletService.getParams());
                        addressString = address.toString();
                    }
                }
            }

            if (outgoing) {
                direction = Res.get("funds.tx.direction.sentTo");
                received = false;
            }
        }

        // confidence
        txConfidenceIndicator = new TxConfidenceIndicator();
        txConfidenceIndicator.setId("funds-confidence");
        tooltip = new Tooltip(Res.get("shared.notUsedYet"));
        txConfidenceIndicator.setProgress(0);
        txConfidenceIndicator.setPrefHeight(30);
        txConfidenceIndicator.setPrefWidth(30);
        Tooltip.install(txConfidenceIndicator, tooltip);

        txConfidenceListener = new TxConfidenceListener(txId) {
            @Override
            public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                GUIUtil.updateConfidence(confidence, tooltip, txConfidenceIndicator);
                confirmations = confidence.getDepthInBlocks();
            }
        };
        walletService.addTxConfidenceListener(txConfidenceListener);
        TransactionConfidence confidence = transaction.getConfidence();
        GUIUtil.updateConfidence(confidence, tooltip, txConfidenceIndicator);
        confirmations = confidence.getDepthInBlocks();


        if (tradableOptional.isPresent()) {
            tradable = tradableOptional.get();
            detailsAvailable = true;
            String id = tradable.getShortId();
            if (tradable instanceof OpenOffer) {
                details = Res.get("funds.tx.createOfferFee", id);
            } else if (tradable instanceof Trade) {
                Trade trade = (Trade) tradable;
                if (trade.getTakeOfferFeeTxId() != null && trade.getTakeOfferFeeTxId().equals(txId)) {
                    details = Res.get("funds.tx.takeOfferFee", id);
                } else {
                    Offer offer = trade.getOffer();
                    String offerFeePaymentTxID = offer.getOfferFeePaymentTxID();
                    if (offer != null &&
                            offerFeePaymentTxID != null &&
                            offerFeePaymentTxID.equals(txId)) {
                        details = Res.get("funds.tx.createOfferFee", id);
                    } else if (trade.getDepositTx() != null &&
                            trade.getDepositTx().getHashAsString().equals(txId)) {
                        details = Res.get("funds.tx.multiSigDeposit", id);
                    } else if (trade.getPayoutTx() != null &&
                            trade.getPayoutTx().getHashAsString().equals(txId)) {
                        details = Res.get("funds.tx.multiSigPayout", id);
                    } else if (trade.getDisputeState() != Trade.DisputeState.NONE) {
                        if (valueSentToMe.isPositive()) {
                            details = Res.get("funds.tx.disputePayout", id);
                        } else {
                            details = Res.get("funds.tx.disputeLost", id);
                            txConfidenceIndicator.setVisible(false);
                        }
                    } else {
                        details = Res.get("funds.tx.unknown", id);
                    }
                }
            }
        } else {
            if (amountAsCoin.isZero())
                details = Res.get("funds.tx.noFundsFromDispute");
            else
                details = received ? Res.get("funds.tx.receivedFunds") : Res.get("funds.tx.withdrawnFromWallet");
        }

        date = transaction.getUpdateTime();
        dateString = formatter.formatDateTime(date);
    }


    public void cleanup() {
        walletService.removeTxConfidenceListener(txConfidenceListener);
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

