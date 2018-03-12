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

package bisq.desktop.main.funds.transactions;

import bisq.desktop.components.indicator.TxConfidenceIndicator;
import bisq.desktop.util.BSFormatter;
import bisq.desktop.util.GUIUtil;

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.listeners.TxConfidenceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletService;
import bisq.core.dao.blockchain.BsqBlockChain;
import bisq.core.dao.blockchain.vo.TxType;
import bisq.core.offer.Offer;
import bisq.core.offer.OpenOffer;
import bisq.core.trade.Tradable;
import bisq.core.trade.Trade;

import bisq.common.locale.Res;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;

import javafx.scene.control.Tooltip;

import java.util.Date;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
class TransactionsListItem {
    private String dateString;
    private final Date date;
    private final String txId;
    private final BtcWalletService btcWalletService;
    private final TxConfidenceIndicator txConfidenceIndicator;
    private final Tooltip tooltip;
    @Nullable
    private Tradable tradable;
    private String details = "";
    private String addressString = "";
    private String direction = "";
    private TxConfidenceListener txConfidenceListener;
    private boolean received;
    private boolean detailsAvailable;
    private Coin amountAsCoin = Coin.ZERO;
    private BSFormatter formatter;
    private int confirmations = 0;

    public TransactionsListItem() {
        date = null;
        btcWalletService = null;
        txConfidenceIndicator = null;
        tooltip = null;
        txId = null;
    }

    public TransactionsListItem(Transaction transaction,
                                BtcWalletService btcWalletService,
                                BsqWalletService bsqWalletService,
                                Optional<Tradable> tradableOptional,
                                BsqBlockChain bsqBlockChain,
                                BSFormatter formatter) {
        this.formatter = formatter;
        txId = transaction.getHashAsString();
        this.btcWalletService = btcWalletService;

        Coin valueSentToMe = btcWalletService.getValueSentToMeForTransaction(transaction);
        Coin valueSentFromMe = btcWalletService.getValueSentFromMeForTransaction(transaction);

        // TODO check and refactor
        boolean txFeeForBsqPayment = false;
        if (valueSentToMe.isZero()) {
            amountAsCoin = valueSentFromMe.multiply(-1);
            for (TransactionOutput output : transaction.getOutputs()) {
                if (!btcWalletService.isTransactionOutputMine(output)) {
                    received = false;
                    if (WalletService.isOutputScriptConvertibleToAddress(output)) {
                        addressString = WalletService.getAddressStringFromOutput(output);
                        if (BisqEnvironment.isBaseCurrencySupportingBsq() && bsqWalletService.isTransactionOutputMine(output)) {
                            txFeeForBsqPayment = true;
                        } else {
                            direction = Res.get("funds.tx.direction.sentTo");
                        }
                        break;
                    }
                }
            }
        } else if (valueSentFromMe.isZero()) {
            amountAsCoin = valueSentToMe;
            direction = Res.get("funds.tx.direction.receivedWith");
            received = true;
            for (TransactionOutput output : transaction.getOutputs()) {
                if (btcWalletService.isTransactionOutputMine(output) &&
                        WalletService.isOutputScriptConvertibleToAddress(output)) {
                    addressString = WalletService.getAddressStringFromOutput(output);
                    break;
                }
            }
        } else {
            amountAsCoin = valueSentToMe.subtract(valueSentFromMe);
            boolean outgoing = false;
            for (TransactionOutput output : transaction.getOutputs()) {
                if (!btcWalletService.isTransactionOutputMine(output)) {
                    if (WalletService.isOutputScriptConvertibleToAddress(output)) {
                        addressString = WalletService.getAddressStringFromOutput(output);
                        if (BisqEnvironment.isBaseCurrencySupportingBsq() && bsqWalletService.isTransactionOutputMine(output)) {
                            outgoing = false;
                            txFeeForBsqPayment = true;

                            //
                            final Optional<TxType> txTypeOptional = bsqBlockChain.getTxType(txId);
                            if (txTypeOptional.isPresent() && txTypeOptional.get().equals(TxType.COMPENSATION_REQUEST))
                                details = Res.get("funds.tx.compRequest");
                        } else {
                            outgoing = true;
                        }
                        break;
                    }
                }
            }

            if (outgoing) {
                direction = Res.get("funds.tx.direction.sentTo");
                received = false;
            }
        }

        if (txFeeForBsqPayment) {
            // direction = Res.get("funds.tx.txFeePaymentForBsqTx");
            direction = Res.get("funds.tx.direction.sentTo");
            //addressString = "";
        }

        // confidence
        txConfidenceIndicator = new TxConfidenceIndicator();
        txConfidenceIndicator.setId("funds-confidence");
        tooltip = new Tooltip(Res.get("shared.notUsedYet"));
        txConfidenceIndicator.setProgress(0);
        txConfidenceIndicator.setPrefHeight(30);
        txConfidenceIndicator.setPrefWidth(30);
        txConfidenceIndicator.setTooltip(tooltip);

        txConfidenceListener = new TxConfidenceListener(txId) {
            @Override
            public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                GUIUtil.updateConfidence(confidence, tooltip, txConfidenceIndicator);
                confirmations = confidence.getDepthInBlocks();
            }
        };
        btcWalletService.addTxConfidenceListener(txConfidenceListener);
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
                if (trade.getTakerFeeTxId() != null && trade.getTakerFeeTxId().equals(txId)) {
                    details = Res.get("funds.tx.takeOfferFee", id);
                } else {
                    Offer offer = trade.getOffer();
                    String offerFeePaymentTxID = offer.getOfferFeePaymentTxId();
                    if (offerFeePaymentTxID != null && offerFeePaymentTxID.equals(txId)) {
                        details = Res.get("funds.tx.createOfferFee", id);
                    } else if (trade.getDepositTx() != null &&
                            trade.getDepositTx().getHashAsString().equals(txId)) {
                        details = Res.get("funds.tx.multiSigDeposit", id);
                    } else if (trade.getPayoutTx() != null &&
                            trade.getPayoutTx().getHashAsString().equals(txId)) {
                        details = Res.get("funds.tx.multiSigPayout", id);
                    } else if (trade.getDisputeState() != Trade.DisputeState.NO_DISPUTE) {
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
            else if (!txFeeForBsqPayment)
                details = received ? Res.get("funds.tx.receivedFunds") : Res.get("funds.tx.withdrawnFromWallet");
            else if (details.isEmpty())
                details = Res.get("funds.tx.txFeePaymentForBsqTx");
        }

        date = transaction.getUpdateTime();
        dateString = formatter.formatDateTime(date);
    }

    public void cleanup() {
        btcWalletService.removeTxConfidenceListener(txConfidenceListener);
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

