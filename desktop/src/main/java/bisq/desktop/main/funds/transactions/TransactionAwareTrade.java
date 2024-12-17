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
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;

import com.google.common.collect.ImmutableSet;

import javafx.collections.ObservableList;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.desktop.main.funds.transactions.TransactionAwareTradable.bucketIndex;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
class TransactionAwareTrade implements TransactionAwareTradable {
    private final TradeModel tradeModel;
    private final ArbitrationManager arbitrationManager;
    private final RefundManager refundManager;
    private final BtcWalletService btcWalletService;
    private final PubKeyRing pubKeyRing;

    // As Sha256Hash.toString() is expensive, cache the last result, which will usually be next one needed.
    private static Tuple2<Sha256Hash, String> lastTxIdTuple;
    // Similarly, cache the last computed set of tx receiver addresses, to speed up 'isRefundPayoutTx'.
    private static Tuple2<String, Set<String>> lastReceiverAddressStringsTuple;

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
        var txIdTuple = lastTxIdTuple;
        if (txIdTuple == null || !txIdTuple.first.equals(hash)) {
            lastTxIdTuple = txIdTuple = new Tuple2<>(hash, hash.toString());
        }
        String txId = txIdTuple.second;

        boolean tradeRelated = false;
        if (tradeModel instanceof Trade) {
            Trade trade = (Trade) tradeModel;
            boolean isTakerOfferFeeTx = txId.equals(trade.getTakerFeeTxId());
            boolean isOfferFeeTx = isOfferFeeTx(txId);
            boolean isDepositTx = isDepositTx(txId);
            boolean isPayoutTx = isPayoutTx(trade, txId);
            boolean isDisputedPayoutTx = isDisputedPayoutTx(txId);
            boolean isDelayedPayoutOrWarningTx = isDelayedPayoutOrWarningTx(transaction, txId);
            boolean isRedirectOrClaimTx = isRedirectOrClaimTx(transaction, txId);
            boolean isRefundPayoutTx = isRefundPayoutTx(trade, txId);
            tradeRelated = isTakerOfferFeeTx ||
                    isOfferFeeTx ||
                    isDepositTx ||
                    isPayoutTx ||
                    isDisputedPayoutTx ||
                    isDelayedPayoutOrWarningTx ||
                    isRedirectOrClaimTx ||
                    isRefundPayoutTx;
        }
        boolean isBsqSwapTrade = isBsqSwapTrade(txId);

        return tradeRelated || isBsqSwapTrade;
    }

    private boolean isPayoutTx(Trade trade, String txId) {
        return txId.equals(trade.getPayoutTxId());
    }

    private boolean isDepositTx(String txId) {
        if (isBsqSwapTrade())
            return false;

        Trade trade = (Trade) tradeModel;
        return txId.equals(trade.getDepositTxId());
    }

    private boolean isOfferFeeTx(String txId) {
        Offer offer = tradeModel.getOffer();
        return offer != null && txId.equals(offer.getOfferFeePaymentTxId());
    }

    private boolean isDisputedPayoutTx(String txId) {
        String delegateId = tradeModel.getId();
        ObservableList<Dispute> disputes = arbitrationManager.getDisputesAsObservableList();

        boolean isAnyDisputeRelatedToThis = arbitrationManager.getDisputedTradeIds().contains(delegateId);

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
        return isDelayedPayoutOrWarningTx(txId) && !((Trade) tradeModel).hasV5Protocol();
    }

    boolean isWarningTx(String txId) {
        return isDelayedPayoutOrWarningTx(txId) && ((Trade) tradeModel).hasV5Protocol();
    }

    private boolean isDelayedPayoutOrWarningTx(String txId) {
        if (isBsqSwapTrade()) {
            return false;
        }
        Transaction transaction = btcWalletService.getTransaction(txId);
        return transaction != null && isDelayedPayoutOrWarningTx(transaction, null);
    }

    private boolean isDelayedPayoutOrWarningTx(Transaction transaction, @Nullable String txId) {
        if (transaction.getLockTime() == 0 || transaction.getInputs().size() != 1) {
            return false;
        }
        if (!TransactionAwareTradable.isPossibleEscrowSpend(transaction.getInput(0))) {
            return false;
        }
        return firstParent(this::isDepositTx, transaction, txId);
    }

    boolean isRedirectTx(String txId) {
        if (isBsqSwapTrade()) {
            return false;
        }
        Transaction transaction = btcWalletService.getTransaction(txId);
        return transaction != null && !transaction.hasRelativeLockTime() && isRedirectOrClaimTx(transaction, null);
    }

    boolean isClaimTx(String txId) {
        if (isBsqSwapTrade()) {
            return false;
        }
        Transaction transaction = btcWalletService.getTransaction(txId);
        return transaction != null && transaction.hasRelativeLockTime() && isRedirectOrClaimTx(transaction, null);
    }

    private boolean isRedirectOrClaimTx(Transaction transaction, @Nullable String txId) {
        if (transaction.getInputs().size() != 1) {
            return false;
        }
        if (!TransactionAwareTradable.isPossibleRedirectOrClaimTx(transaction)) {
            return false;
        }
        return firstParent(this::isWarningTx, transaction, txId);
    }

    private boolean firstParent(Predicate<String> parentPredicate, Transaction transaction, @Nullable String txId) {
        Transaction walletTransaction = txId != null ? btcWalletService.getTransaction(txId) : transaction;
        if (walletTransaction == null) {
            return false;
        }
        TransactionOutput connectedOutput = walletTransaction.getInput(0).getConnectedOutput();
        if (connectedOutput == null) {
            return false;
        }
        Transaction parentTransaction = connectedOutput.getParentTransaction();
        if (parentTransaction == null) {
            return false;
        }
        return parentPredicate.test(parentTransaction.getTxId().toString());
    }

    private boolean isRefundPayoutTx(Trade trade, String txId) {
        String tradeId = tradeModel.getId();
        boolean isAnyDisputeRelatedToThis = refundManager.getDisputedTradeIds().contains(tradeId);

        if (isAnyDisputeRelatedToThis) {
            try {
                Contract contract = checkNotNull(trade.getContract());
                String myPayoutAddressString = contract.isMyRoleBuyer(pubKeyRing) ?
                        contract.getBuyerPayoutAddressString() :
                        contract.getSellerPayoutAddressString();

                return getReceiverAddressStrings(txId).contains(myPayoutAddressString);
            } catch (RuntimeException ignore) {
            }
        }
        return false;
    }

    private Set<String> getReceiverAddressStrings(String txId) {
        var tuple = lastReceiverAddressStringsTuple;
        if (tuple == null || !tuple.first.equals(txId)) {
            lastReceiverAddressStringsTuple = tuple = computeReceiverAddressStringsTuple(txId);
        }
        return tuple != null ? tuple.second : ImmutableSet.of();
    }

    private Tuple2<String, Set<String>> computeReceiverAddressStringsTuple(String txId) {
        Transaction tx = btcWalletService.getTransaction(txId);
        if (tx == null) {
            // Clear cache if the tx isn't found, as theoretically it could be added to the wallet later.
            return null;
        }
        Set<String> addressStrings = tx.getOutputs().stream()
                .filter(btcWalletService::isTransactionOutputMine)
                .map(txo -> txo.getScriptPubKey().getToAddress(btcWalletService.getParams()))
                .map(Address::toString)
                .collect(ImmutableSet.toImmutableSet());

        return new Tuple2<>(txId, addressStrings);
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

    @Override
    public IntStream getRelatedTransactionFilter() {
        if (tradeModel instanceof Trade && !arbitrationManager.getDisputedTradeIds().contains(tradeModel.getId()) &&
                !refundManager.getDisputedTradeIds().contains(tradeModel.getId())) {
            Trade trade = (Trade) tradeModel;
            String takerFeeTxId = trade.getTakerFeeTxId();
            String offerFeeTxId = trade.getOffer() != null ? trade.getOffer().getOfferFeePaymentTxId() : null;
            String depositTxId = trade.getDepositTxId();
            String payoutTxId = trade.getPayoutTxId();
            return IntStream.of(DELAYED_PAYOUT_TX_BUCKET_INDEX, bucketIndex(takerFeeTxId), bucketIndex(offerFeeTxId),
                            bucketIndex(depositTxId), bucketIndex(payoutTxId))
                    .filter(i -> i >= 0);
        } else if (tradeModel instanceof BsqSwapTrade) {
            BsqSwapTrade trade = (BsqSwapTrade) tradeModel;
            String swapTxId = trade.getTxId();
            return IntStream.of(bucketIndex(swapTxId))
                    .filter(i -> i >= 0);
        } else {
            // We are involved in a dispute (rare) - don't do any initial tx filtering.
            return IntStream.range(0, TX_FILTER_SIZE);
        }
    }
}
