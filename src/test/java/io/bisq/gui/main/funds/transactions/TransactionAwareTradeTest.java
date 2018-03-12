package io.bisq.gui.main.funds.transactions;

import bisq.core.arbitration.Dispute;
import bisq.core.arbitration.DisputeManager;
import bisq.core.trade.Trade;
import javafx.collections.FXCollections;
import org.bitcoinj.core.Transaction;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Collections;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Dispute.class)
@SuppressWarnings("ConstantConditions")
public class TransactionAwareTradeTest {
    private static final String XID = "123";

    private Transaction transaction;
    private DisputeManager manager;
    private Trade delegate;
    private TransactionAwareTradable trade;

    @Before
    public void setUp() {
        this.transaction = mock(Transaction.class);
        when(transaction.getHashAsString()).thenReturn(XID);

        this.delegate = mock(Trade.class, RETURNS_DEEP_STUBS);
        this.manager = mock(DisputeManager.class, RETURNS_DEEP_STUBS);
        this.trade = new TransactionAwareTrade(this.delegate, this.manager);
    }

    @Test
    public void testIsRelatedToTransactionWhenTakerOfferFeeTx() {
        when(delegate.getTakerFeeTxId()).thenReturn(XID);
        assertTrue(trade.isRelatedToTransaction(transaction));
    }

    @Test
    public void testIsRelatedToTransactionWhenPayoutTx() {
        when(delegate.getPayoutTx().getHashAsString()).thenReturn(XID);
        assertTrue(trade.isRelatedToTransaction(transaction));
    }

    @Test
    public void testIsRelatedToTransactionWhenDepositTx() {
        when(delegate.getDepositTx().getHashAsString()).thenReturn(XID);
        assertTrue(trade.isRelatedToTransaction(transaction));
    }

    @Test
    public void testIsRelatedToTransactionWhenOfferFeeTx() {
        when(delegate.getOffer().getOfferFeePaymentTxId()).thenReturn(XID);
        assertTrue(trade.isRelatedToTransaction(transaction));
    }

    @Test
    public void testIsRelatedToTransactionWhenDisputedPayoutTx() {
        final String tradeId = "7";

        Dispute dispute = mock(Dispute.class);
        when(dispute.getDisputePayoutTxId()).thenReturn(XID);
        when(dispute.getTradeId()).thenReturn(tradeId);

        when(manager.getDisputesAsObservableList())
                .thenReturn(FXCollections.observableArrayList(Collections.singleton(dispute)));

        when(delegate.getId()).thenReturn(tradeId);

        assertTrue(trade.isRelatedToTransaction(transaction));
    }
}
