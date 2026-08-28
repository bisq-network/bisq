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
import bisq.core.testutil.ManualTimer;

import bisq.common.FrameRateTimer;
import bisq.common.UserThread;

import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionBroadcast;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.wallet.Wallet;

import com.google.common.util.concurrent.SettableFuture;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TxBroadcasterTest {
    private static final int TIMEOUT_NOT_REACHED_IN_TEST = 300;

    @BeforeEach
    void setUp() {
        ManualTimer.clear();
        UserThread.setTimerClass(ManualTimer.class);
        // Keeps the redundant broadcast to the mempool services out of the test.
        LocalBitcoinNode localBitcoinNode = mock(LocalBitcoinNode.class);
        when(localBitcoinNode.shouldBeUsed()).thenReturn(true);
        MemPoolSpaceTxBroadcaster.init(null, null, localBitcoinNode, false, false);
    }

    @AfterEach
    void tearDown() {
        ManualTimer.firePendingTimers();
        UserThread.setTimerClass(FrameRateTimer.class);
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

    @Test
    void aLateResultFromAnExpiredBroadcastDoesNotConsumeANewerRequest() {
        Transaction tx = new Transaction(MainNetParams.get());
        Wallet wallet = mock(Wallet.class);
        SettableFuture<Transaction> firstFuture = SettableFuture.create();
        SettableFuture<Transaction> secondFuture = SettableFuture.create();
        TransactionBroadcast firstBroadcast = TransactionBroadcast.createMockBroadcast(tx, firstFuture);
        TransactionBroadcast secondBroadcast = TransactionBroadcast.createMockBroadcast(tx, secondFuture);
        PeerGroup peerGroup = mock(PeerGroup.class);
        when(peerGroup.broadcastTransaction(tx)).thenReturn(firstBroadcast, secondBroadcast);
        RecordingCallback firstCallback = new RecordingCallback();
        RecordingCallback secondCallback = new RecordingCallback();

        TxBroadcaster.broadcastTx(wallet, peerGroup, tx, firstCallback, TIMEOUT_NOT_REACHED_IN_TEST);
        ManualTimer firstTimer = ManualTimer.latest();
        firstTimer.fire();
        assertSame(tx, firstCallback.success.get());
        assertEquals(1, firstCallback.successCount.get());

        TxBroadcaster.broadcastTx(wallet, peerGroup, tx, secondCallback, TIMEOUT_NOT_REACHED_IN_TEST);
        ManualTimer secondTimer = ManualTimer.latest();
        firstFuture.set(tx);

        assertFalse(secondTimer.isStopped());
        assertNull(secondCallback.success.get());
        assertNull(secondCallback.failure.get());
        assertEquals(1, firstCallback.successCount.get());

        secondFuture.set(tx);

        assertTrue(secondTimer.isStopped());
        assertSame(tx, secondCallback.success.get());
        assertEquals(1, secondCallback.successCount.get());
        assertNull(secondCallback.failure.get());
    }

    @Test
    void aSynchronousWalletCommitFailureRemovesThePendingRequest() {
        Transaction tx = new Transaction(MainNetParams.get());
        Wallet wallet = mock(Wallet.class);
        IllegalStateException setupFailure = new IllegalStateException("commit failed");
        when(wallet.maybeCommitTx(tx)).thenThrow(setupFailure);
        RecordingCallback callback = new RecordingCallback();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> TxBroadcaster.broadcastTx(wallet,
                        mock(PeerGroup.class),
                        tx,
                        callback,
                        TIMEOUT_NOT_REACHED_IN_TEST));

        assertSame(setupFailure, thrown);
        assertSynchronousFailureWasCleanedUp(tx, callback);
    }

    @Test
    void aSynchronousPeerGroupFailureRemovesThePendingRequest() {
        Transaction tx = new Transaction(MainNetParams.get());
        Wallet wallet = mock(Wallet.class);
        PeerGroup peerGroup = mock(PeerGroup.class);
        IllegalStateException setupFailure = new IllegalStateException("broadcast failed");
        when(peerGroup.broadcastTransaction(tx)).thenThrow(setupFailure);
        RecordingCallback callback = new RecordingCallback();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> TxBroadcaster.broadcastTx(wallet,
                        peerGroup,
                        tx,
                        callback,
                        TIMEOUT_NOT_REACHED_IN_TEST));

        assertSame(setupFailure, thrown);
        assertSynchronousFailureWasCleanedUp(tx, callback);
    }

    private static void assertSynchronousFailureWasCleanedUp(Transaction tx, RecordingCallback failedCallback) {
        ManualTimer failedTimer = ManualTimer.latest();
        assertTrue(failedTimer.isStopped());
        failedTimer.fire();
        assertEquals(0, failedCallback.successCount.get());
        assertEquals(0, failedCallback.failureCount.get());

        Wallet retryWallet = mock(Wallet.class);
        SettableFuture<Transaction> retryFuture = SettableFuture.create();
        RecordingCallback retryCallback = new RecordingCallback();
        TxBroadcaster.broadcastTx(retryWallet,
                peerGroup(tx, retryFuture),
                tx,
                retryCallback,
                TIMEOUT_NOT_REACHED_IN_TEST);
        retryFuture.set(tx);

        assertSame(tx, retryCallback.success.get());
        assertNull(retryCallback.failure.get());
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
        private final AtomicInteger successCount = new AtomicInteger();
        private final AtomicInteger failureCount = new AtomicInteger();

        @Override
        public void onSuccess(Transaction transaction) {
            success.set(transaction);
            successCount.incrementAndGet();
        }

        @Override
        public void onFailure(TxBroadcastException exception) {
            failure.set(exception);
            failureCount.incrementAndGet();
        }
    }

}
