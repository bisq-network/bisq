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

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.arbitration.ArbitrationManager;
import bisq.core.support.dispute.refund.RefundManager;
import bisq.core.trade.model.Tradable;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;

import bisq.common.crypto.PubKeyRing;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;

import javafx.collections.ObservableList;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
class TransactionAwareTrade implements TransactionAwareTradable {
    private final TradeModel tradeModel;
    private final ArbitrationManager arbitrationManager;
    private final RefundManager refundManager;
    private final BtcWalletService btcWalletService;
    private final PubKeyRing pubKeyRing;

    TransactionAwareTrade(TradeModel tradeModel,
                          ArbitrationManager arbitrationManager,
                          RefundManager refundManager,
                          BtcWalletService btcWalletService,
                          PubKeyRing pubKeyRing) {
        this.tradeModel = tradeModel;
        this.arbitrationManager = arbitrationManager;
        this.refundManager = refundManager;
        this.btcWalletService = btcWalletService;
        this.pubKeyRing = pubKeyRing;
    }

    @Override
    public boolean isRelatedToTransaction(Transaction transaction) {
        Sha256Hash hash = transaction.getTxId();
        String txId = hash.toString();

        boolean tradeRelated = false;
        if (tradeModel instanceof Trade) {
            Trade trade = (Trade) tradeModel;
            boolean isTakerOfferFeeTx = txId.equals(trade.getTakerFeeTxId());
            boolean isOfferFeeTx = isOfferFeeTx(txId);
            boolean isDepositTx = isDepositTx(hash);
            boolean isPayoutTx = isPayoutTx(hash);
            boolean isDisputedPayoutTx = isDisputedPayoutTx(txId);
            boolean isDelayedPayoutTx = transaction.getLockTime() != 0 && isDelayedPayoutTx(txId);
            boolean isRefundPayoutTx = isRefundPayoutTx(trade, txId);
            tradeRelated = isTakerOfferFeeTx ||
                    isOfferFeeTx ||
                    isDepositTx ||
                    isPayoutTx ||
                    isDisputedPayoutTx ||
                    isDelayedPayoutTx ||
                    isRefundPayoutTx;
        }
        boolean isBsqSwapTrade = isBsqSwapTrade(txId);

        return tradeRelated || isBsqSwapTrade;
    }

    private boolean isPayoutTx(Sha256Hash txId) {
        if (isBsqSwapTrade())
            return false;

        Trade trade = (Trade) tradeModel;
        return Optional.ofNullable(trade.getPayoutTx())
                .map(Transaction::getTxId)
                .map(hash -> hash.equals(txId))
                .orElse(false);
    }

    private boolean isDepositTx(Sha256Hash txId) {
        if (isBsqSwapTrade())
            return false;

        Trade trade = (Trade) tradeModel;
        return Optional.ofNullable(trade.getDepositTx())
                .map(Transaction::getTxId)
                .map(hash -> hash.equals(txId))
                .orElse(false);
    }

    private boolean isOfferFeeTx(String txId) {
        if (isBsqSwapTrade())
            return false;

        return Optional.ofNullable(tradeModel.getOffer())
                .map(Offer::getOfferFeePaymentTxId)
                .map(paymentTxId -> paymentTxId.equals(txId))
                .orElse(false);
    }

    private boolean isDisputedPayoutTx(String txId) {
        if (isBsqSwapTrade())
            return false;

        String delegateId = tradeModel.getId();
        ObservableList<Dispute> disputes = arbitrationManager.getDisputesAsObservableList();

        boolean isAnyDisputeRelatedToThis = arbitrationManager.getDisputedTradeIds().contains(tradeModel.getId());

        return isAnyDisputeRelatedToThis && disputes.stream()
                .anyMatch(dispute -> {
                    String disputePayoutTxId = dispute.getDisputePayoutTxId();
                    boolean isDisputePayoutTx = txId.equals(disputePayoutTxId);

                    String disputeTradeId = dispute.getTradeId();
                    boolean isDisputeRelatedToThis = delegateId.equals(disputeTradeId);

                    return isDisputePayoutTx && isDisputeRelatedToThis;
                });
    }

    boolean isDelayedPayoutTx(String txId) {
        if (isBsqSwapTrade())
            return false;

        Transaction transaction = btcWalletService.getTransaction(txId);
        if (transaction == null)
            return false;

        if (transaction.getLockTime() == 0)
            return false;

        if (transaction.getInputs() == null)
            return false;

        return transaction.getInputs().stream()
                .anyMatch(input -> {
                    TransactionOutput connectedOutput = input.getConnectedOutput();
                    if (connectedOutput == null) {
                        return false;
                    }
                    Transaction parentTransaction = connectedOutput.getParentTransaction();
                    if (parentTransaction == null) {
                        return false;
                    }
                    return isDepositTx(parentTransaction.getTxId());
                });
    }

    private boolean isRefundPayoutTx(Trade trade, String txId) {
        if (isBsqSwapTrade())
            return false;

        String tradeId = tradeModel.getId();
        ObservableList<Dispute> disputes = refundManager.getDisputesAsObservableList();

        boolean isAnyDisputeRelatedToThis = refundManager.getDisputedTradeIds().contains(tradeId);

        if (isAnyDisputeRelatedToThis) {
            Transaction tx = btcWalletService.getTransaction(txId);
            if (tx != null) {
                for (TransactionOutput txo : tx.getOutputs()) {
                    if (btcWalletService.isTransactionOutputMine(txo)) {
                        try {
                            Address receiverAddress = txo.getScriptPubKey().getToAddress(btcWalletService.getParams());
                            Contract contract = checkNotNull(trade.getContract());
                            String myPayoutAddressString = contract.isMyRoleBuyer(pubKeyRing) ?
                                    contract.getBuyerPayoutAddressString() :
                                    contract.getSellerPayoutAddressString();
                            if (receiverAddress != null && myPayoutAddressString.equals(receiverAddress.toString())) {
                                return true;
                            }
                        } catch (RuntimeException ignore) {
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean isBsqSwapTrade() {
        return tradeModel instanceof BsqSwapTrade;
    }

    private boolean isBsqSwapTrade(String txId) {
        if (isBsqSwapTrade()) {
            return (txId.equals(((BsqSwapTrade) tradeModel).getTxId()));
        }
        return false;
    }

    @Override
    public Tradable asTradable() {
        return tradeModel;
    }
}
