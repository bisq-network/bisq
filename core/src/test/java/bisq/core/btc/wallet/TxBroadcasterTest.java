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

import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.nodes.LocalBitcoinNode;
import bisq.core.btc.wallet.http.MemPoolSpaceTxBroadcaster;

import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionBroadcast;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.wallet.Wallet;

import com.google.common.util.concurrent.SettableFuture;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TxBroadcasterTest {
    private static final int TIMEOUT_NOT_REACHED_IN_TEST = 300;

    @BeforeEach
    void setUp() {
        // Keeps the redundant broadcast to the mempool services out of the test.
        LocalBitcoinNode localBitcoinNode = mock(LocalBitcoinNode.class);
        when(localBitcoinNode.shouldBeUsed()).thenReturn(true);
        MemPoolSpaceTxBroadcaster.init(null, null, localBitcoinNode, false, false);
    }

    @Test
    void aSecondBroadcastOfTheSameTxDoesNotSuppressThePendingCallback() {
        Transaction tx = new Transaction(MainNetParams.get());
        Wallet wallet = mock(Wallet.class);
        SettableFuture<Transaction> broadcastFuture = SettableFuture.create();
        PeerGroup peerGroup = peerGroup(tx, broadcastFuture);
        RecordingCallback firstCallback = new RecordingCallback();
        RecordingCallback secondCallback = new RecordingCallback();

        TxBroadcaster.broadcastTx(wallet, peerGroup, tx, firstCallback, TIMEOUT_NOT_REACHED_IN_TEST);
        TxBroadcaster.broadcastTx(wallet, peerGroup, tx, secondCallback, TIMEOUT_NOT_REACHED_IN_TEST);

        // The misuse is reported to the second caller only, and the transaction is neither committed nor
        // broadcast a second time.
        assertNotNull(secondCallback.failure.get());
        assertNull(secondCallback.success.get());
        assertNull(firstCallback.failure.get());
        verify(wallet, times(1)).maybeCommitTx(tx);
        verify(peerGroup, times(1)).broadcastTransaction(tx);

        // The pending broadcast of the first caller is still able to complete.
        broadcastFuture.set(tx);
        assertSame(tx, firstCallback.success.get());
        assertNull(firstCallback.failure.get());
    }

    private static PeerGroup peerGroup(Transaction tx, SettableFuture<Transaction> broadcastFuture) {
        TransactionBroadcast broadcast = TransactionBroadcast.createMockBroadcast(tx, broadcastFuture);
        PeerGroup peerGroup = mock(PeerGroup.class);
        when(peerGroup.broadcastTransaction(tx)).thenReturn(broadcast);
        return peerGroup;
    }

    private static class RecordingCallback implements TxBroadcaster.Callback {
        private final AtomicReference<Transaction> success = new AtomicReference<>();
        private final AtomicReference<TxBroadcastException> failure = new AtomicReference<>();

        @Override
        public void onSuccess(Transaction transaction) {
            success.set(transaction);
        }

        @Override
        public void onFailure(TxBroadcastException exception) {
            failure.set(exception);
        }
    }
}
