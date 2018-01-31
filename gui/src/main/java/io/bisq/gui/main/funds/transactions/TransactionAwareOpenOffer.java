package io.bisq.gui.main.funds.transactions;

import io.bisq.core.offer.Offer;
import io.bisq.core.offer.OpenOffer;
import io.bisq.core.trade.Tradable;
import org.bitcoinj.core.Transaction;

class TransactionAwareOpenOffer implements TransactionAwareTradable {
    private final OpenOffer delegate;

    TransactionAwareOpenOffer(OpenOffer delegate) {
        this.delegate = delegate;
    }

    public boolean isRelatedToTransaction(Transaction transaction) {
        Offer offer = delegate.getOffer();
        String paymentTxId = offer.getOfferFeePaymentTxId();

        String txId = transaction.getHashAsString();

        return paymentTxId.equals(txId);
    }

    public Tradable asTradable() {
        return delegate;
    }
}
