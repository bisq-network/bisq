package io.bisq.gui.main.funds.transactions;

import com.google.common.collect.Sets;
import io.bisq.core.btc.wallet.BtcWalletService;
import org.bitcoinj.core.Transaction;
import org.junit.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class DisplayedTransactionsTest {
    @Test
    public void testUpdate() {
        Set<Transaction> transactions = Sets.newHashSet(mock(Transaction.class), mock(Transaction.class));

        BtcWalletService walletService = mock(BtcWalletService.class);
        when(walletService.getTransactions(false)).thenReturn(transactions);

        TransactionListItemFactory transactionListItemFactory = mock(TransactionListItemFactory.class,
                RETURNS_DEEP_STUBS);

        @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
        DisplayedTransactions testedEntity = new DisplayedTransactions(
                walletService,
                mock(TradableRepository.class),
                transactionListItemFactory,
                mock(TransactionAwareTradableFactory.class));

        testedEntity.update();

        assertEquals(transactions.size(), testedEntity.size());
    }

    @Test
    public void testUpdateWhenRepositoryIsEmpty(){
        BtcWalletService walletService = mock(BtcWalletService.class);
        when(walletService.getTransactions(false))
                .thenReturn(Collections.singleton(mock(Transaction.class)));

        TradableRepository tradableRepository = mock(TradableRepository.class);
        when(tradableRepository.getAll()).thenReturn(Collections.emptySet());

        TransactionListItemFactory transactionListItemFactory = mock(TransactionListItemFactory.class);

        @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
        DisplayedTransactions testedEntity = new DisplayedTransactions(
                walletService,
                tradableRepository,
                transactionListItemFactory,
                mock(TransactionAwareTradableFactory.class));

        testedEntity.update();

        assertEquals(1, testedEntity.size());
        verify(transactionListItemFactory).create(any(), nullable(TransactionAwareTradable.class));
    }
}
