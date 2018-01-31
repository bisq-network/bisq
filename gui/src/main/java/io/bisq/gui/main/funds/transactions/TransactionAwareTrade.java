package io.bisq.gui.main.funds.transactions;

import io.bisq.core.arbitration.Dispute;
import io.bisq.core.arbitration.DisputeManager;
import io.bisq.core.offer.Offer;
import io.bisq.core.trade.Tradable;
import io.bisq.core.trade.Trade;
import javafx.collections.ObservableList;
import org.bitcoinj.core.Transaction;

import java.util.Optional;

class TransactionAwareTrade implements TransactionAwareTradable {
    private final Trade delegate;
    private final DisputeManager disputeManager;

    TransactionAwareTrade(Trade delegate, DisputeManager disputeManager) {
        this.delegate = delegate;
        this.disputeManager = disputeManager;
    }

    @Override
    public boolean isRelatedToTransaction(Transaction transaction) {
        String txId = transaction.getHashAsString();

        boolean isTakeOfferFeeTx = txId.equals(delegate.getTakerFeeTxId());
        boolean isOfferFeeTx = isOfferFeeTx(txId);
        boolean isDepositTx = isDepositTx(txId);
        boolean isPayoutTx = isPayoutTx(txId);
        boolean isDisputedPayoutTx = isDisputedPayoutTx(txId);

        return isTakeOfferFeeTx || isOfferFeeTx || isDepositTx || isPayoutTx || isDisputedPayoutTx;
    }

    private boolean isPayoutTx(String txId) {
        return Optional.ofNullable(delegate.getPayoutTx())
                    .map(Transaction::getHashAsString)
                    .map(hash -> hash.equals(txId))
                    .orElse(false);
    }

    private boolean isDepositTx(String txId) {
        return Optional.ofNullable(delegate.getDepositTx())
                    .map(Transaction::getHashAsString)
                    .map(hash -> hash.equals(txId))
                    .orElse(false);
    }

    private boolean isOfferFeeTx(String txId) {
        return Optional.ofNullable(delegate.getOffer())
                    .map(Offer::getOfferFeePaymentTxId)
                    .map(paymentTxId -> paymentTxId.equals(txId))
                    .orElse(false);
    }

    private boolean isDisputedPayoutTx(String txId) {
        String delegateId = delegate.getId();

        ObservableList<Dispute> disputes = disputeManager.getDisputesAsObservableList();
        return disputes.stream()
                .anyMatch(dispute -> {
                    String disputePayoutTxId = dispute.getDisputePayoutTxId();
                    boolean isDisputePayoutTx = txId.equals(disputePayoutTxId);

                    String disputeTradeId = dispute.getTradeId();
                    boolean isDisputeRelatedToThis = delegateId.equals(disputeTradeId);

                    return isDisputePayoutTx && isDisputeRelatedToThis;
                });
    }

    @Override
    public Tradable asTradable() {
        return delegate;
    }
}
