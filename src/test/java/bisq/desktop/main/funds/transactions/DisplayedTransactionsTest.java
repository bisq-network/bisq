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

import org.bitcoinj.core.Transaction;

import com.google.common.collect.Sets;

import java.util.Collections;
import java.util.Set;

import org.junit.Test;

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
    public void testUpdateWhenRepositoryIsEmpty() {
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
