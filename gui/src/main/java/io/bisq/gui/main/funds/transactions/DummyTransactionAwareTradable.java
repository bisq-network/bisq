package io.bisq.gui.main.funds.transactions;

import io.bisq.core.trade.Tradable;
import org.bitcoinj.core.Transaction;

class DummyTransactionAwareTradable implements TransactionAwareTradable {
    private final Tradable delegate;

    DummyTransactionAwareTradable(Tradable delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean isRelatedToTransaction(Transaction transaction) {
        return false;
    }

    @Override
    public Tradable asTradable() {
        return delegate;
    }
}
