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
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.arbitration.ArbitrationManager;
import bisq.core.support.dispute.refund.RefundManager;
import bisq.core.trade.Trade;

import org.bitcoinj.core.Transaction;

import javafx.collections.FXCollections;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TransactionAwareTradeTest {
    private static final String XID = "123";

    private Transaction transaction;
    private ArbitrationManager arbitrationManager;
    private Trade delegate;
    private TransactionAwareTradable trade;
    private RefundManager refundManager;
    private BtcWalletService btcWalletService;

    @Before
    public void setUp() {
        this.transaction = mock(Transaction.class);
        when(transaction.getHashAsString()).thenReturn(XID);

        delegate = mock(Trade.class, RETURNS_DEEP_STUBS);
        arbitrationManager = mock(ArbitrationManager.class, RETURNS_DEEP_STUBS);
        refundManager = mock(RefundManager.class, RETURNS_DEEP_STUBS);
        btcWalletService = mock(BtcWalletService.class, RETURNS_DEEP_STUBS);
        trade = new TransactionAwareTrade(delegate, arbitrationManager, refundManager, btcWalletService, null);
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

        when(arbitrationManager.getDisputesAsObservableList())
                .thenReturn(FXCollections.observableArrayList(Collections.singleton(dispute)));

        when(delegate.getId()).thenReturn(tradeId);

        assertTrue(trade.isRelatedToTransaction(transaction));
    }
}
