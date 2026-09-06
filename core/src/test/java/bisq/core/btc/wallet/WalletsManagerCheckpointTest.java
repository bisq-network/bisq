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

package bisq.core.btc.wallet;

import bisq.core.dao.state.model.blockchain.TxType;

import org.bitcoinj.core.Transaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class WalletsManagerCheckpointTest {
    private final BtcWalletService btcWalletService = mock(BtcWalletService.class);
    private final BsqWalletService bsqWalletService = mock(BsqWalletService.class);
    private final WalletsManager walletsManager = new WalletsManager(btcWalletService, null, bsqWalletService, null);

    @Test
    void checkpointFailureBlocksPreparedTransactionBeforeEitherWalletCommits() {
        IllegalStateException failure = new IllegalStateException("DAO checkpoint verification failed");
        doThrow(failure).when(bsqWalletService).assertCheckpointNotFailed();
        Transaction transaction = mock(Transaction.class);
        TxBroadcaster.Callback callback = mock(TxBroadcaster.Callback.class);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> walletsManager.publishAndCommitBsqTx(transaction, TxType.TRANSFER_BSQ, callback)));

        verifyNoInteractions(btcWalletService, transaction, callback);
        inOrder(bsqWalletService).verify(bsqWalletService).assertCheckpointNotFailed();
        verifyNoMoreInteractions(bsqWalletService);
    }

    @Test
    void healthyStatePreservesBothCommitsAndBroadcastOrder() {
        Transaction transaction = mock(Transaction.class);
        Transaction clonedTransaction = mock(Transaction.class);
        TxBroadcaster.Callback callback = mock(TxBroadcaster.Callback.class);
        when(btcWalletService.getClonedTransaction(transaction)).thenReturn(clonedTransaction);

        walletsManager.publishAndCommitBsqTx(transaction, TxType.TRANSFER_BSQ, callback);

        var order = inOrder(bsqWalletService, btcWalletService);
        order.verify(bsqWalletService).assertCheckpointNotFailed();
        order.verify(btcWalletService).getClonedTransaction(transaction);
        order.verify(btcWalletService).commitTx(clonedTransaction);
        order.verify(bsqWalletService).commitTx(transaction, TxType.TRANSFER_BSQ);
        order.verify(bsqWalletService).broadcastTx(transaction, callback, 1);
    }
}
