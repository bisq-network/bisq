package io.bisq.gui.main.funds.transactions;

import bisq.core.arbitration.DisputeManager;
import bisq.core.offer.OpenOffer;
import bisq.core.trade.Tradable;
import bisq.core.trade.Trade;
import org.bitcoinj.core.Transaction;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;

public class TransactionAwareTradableFactoryTest {
    @Test
    public void testCreateWhenNotOpenOfferOrTrade() {
        DisputeManager manager = mock(DisputeManager.class);

        TransactionAwareTradableFactory factory = new TransactionAwareTradableFactory(manager);

        Tradable delegate = mock(Tradable.class);
        assertFalse(delegate instanceof OpenOffer);
        assertFalse(delegate instanceof Trade);

        TransactionAwareTradable tradable = factory.create(delegate);

        assertFalse(tradable.isRelatedToTransaction(mock(Transaction.class)));
    }
}
