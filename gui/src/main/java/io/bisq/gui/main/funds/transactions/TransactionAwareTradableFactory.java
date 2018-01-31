package io.bisq.gui.main.funds.transactions;

import io.bisq.core.arbitration.DisputeManager;
import io.bisq.core.trade.Tradable;

import javax.inject.Inject;

public class TransactionAwareTradableFactory {
    private final DisputeManager disputeManager;

    @Inject
    public TransactionAwareTradableFactory(DisputeManager disputeManager) {
        this.disputeManager = disputeManager;
    }

    TransactionAwareTradable create(Tradable delegate) {
        return new TransactionAwareTradable(disputeManager, delegate);
    }
}
