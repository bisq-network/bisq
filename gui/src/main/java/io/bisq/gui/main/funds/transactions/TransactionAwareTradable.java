package io.bisq.gui.main.funds.transactions;

import io.bisq.core.arbitration.DisputeManager;
import io.bisq.core.offer.OpenOffer;
import io.bisq.core.trade.Tradable;
import io.bisq.core.trade.Trade;
import org.bitcoinj.core.Transaction;

class TransactionAwareTradable {
    private final DisputeManager disputeManager;
    private final Tradable delegate;

    TransactionAwareTradable(DisputeManager disputeManager, Tradable delegate) {
        this.disputeManager = disputeManager;
        this.delegate = delegate;
    }

    boolean isRelatedToTransaction(Transaction transaction) {
        String txId = transaction.getHashAsString();
        if (delegate instanceof OpenOffer)
            return delegate.getOffer().getOfferFeePaymentTxId().equals(txId);
        else if (delegate instanceof Trade) {
            Trade trade = (Trade) delegate;
            boolean isTakeOfferFeeTx = txId.equals(trade.getTakerFeeTxId());
            boolean isOfferFeeTx = trade.getOffer() != null &&
                    txId.equals(trade.getOffer().getOfferFeePaymentTxId());
            boolean isDepositTx = trade.getDepositTx() != null &&
                    trade.getDepositTx().getHashAsString().equals(txId);
            boolean isPayoutTx = trade.getPayoutTx() != null &&
                    trade.getPayoutTx().getHashAsString().equals(txId);

            boolean isDisputedPayoutTx = disputeManager.getDisputesAsObservableList().stream()
                    .anyMatch(dispute -> txId.equals(dispute.getDisputePayoutTxId()) &&
                            delegate.getId().equals(dispute.getTradeId()));

            return isTakeOfferFeeTx || isOfferFeeTx || isDepositTx || isPayoutTx || isDisputedPayoutTx;
        } else {
            return false;
        }
    }

    Tradable asTradable() {
        return delegate;
    }
}
