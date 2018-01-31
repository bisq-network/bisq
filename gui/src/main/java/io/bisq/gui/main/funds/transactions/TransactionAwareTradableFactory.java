package io.bisq.gui.main.funds.transactions;

import io.bisq.core.arbitration.DisputeManager;
import io.bisq.core.offer.OpenOffer;
import io.bisq.core.trade.Tradable;
import io.bisq.core.trade.Trade;

import javax.inject.Inject;

public class TransactionAwareTradableFactory {
    private final DisputeManager disputeManager;

    @Inject
    TransactionAwareTradableFactory(DisputeManager disputeManager) {
        this.disputeManager = disputeManager;
    }

    TransactionAwareTradable create(Tradable delegate) {
        if (delegate instanceof OpenOffer) {
            return new TransactionAwareOpenOffer((OpenOffer) delegate);
        } else if (delegate instanceof Trade) {
            return new TransactionAwareTrade((Trade) delegate, disputeManager);
        } else {
            return new DummyTransactionAwareTradable(delegate);
        }
    }
}
