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

package bisq.core.btc.setup;

import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.Wallet;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;

import java.nio.file.Path;

import java.io.File;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletConfigShutDownTest {
    private static final long MAX_SHUT_DOWN_MILLIS = (WalletConfig.PEER_GROUP_STOP_TIMEOUT_SEC + 3) * 1000;

    @TempDir
    Path tempDir;

    private WalletConfig walletConfig;
    private PeerGroup peerGroup;
    private SPVBlockStore store;
    private Wallet btcWallet;
    private Wallet bsqWallet;

    @BeforeEach
    void setUp() {
        walletConfig = new WalletConfig(RegTestParams.get(), tempDir.toFile(), "bisq");
        peerGroup = mock(PeerGroup.class);
        store = mock(SPVBlockStore.class);
        btcWallet = mock(Wallet.class);
        bsqWallet = mock(Wallet.class);

        walletConfig.vPeerGroup = peerGroup;
        walletConfig.vStore = store;
        walletConfig.vBtcWallet = btcWallet;
        walletConfig.vBsqWallet = bsqWallet;
        walletConfig.vBtcWalletFile = new File(tempDir.toFile(), "bisq_BTC.wallet");
        walletConfig.vBsqWalletFile = new File(tempDir.toFile(), "bisq_BSQ.wallet");
    }

    @Test
    void stopsPeerGroupBeforeSavingWalletsAndClosingStore() throws Exception {
        List<String> calls = new ArrayList<>();
        when(peerGroup.isRunning()).thenReturn(true);
        when(peerGroup.stopAsync()).thenAnswer(invocation -> {
            calls.add("stop PeerGroup");
            return Futures.immediateFuture(null);
        });
        doAnswer(invocation -> calls.add("save BTC wallet")).when(btcWallet).saveToFile(any(File.class));
        doAnswer(invocation -> calls.add("save BSQ wallet")).when(bsqWallet).saveToFile(any(File.class));
        doAnswer(invocation -> calls.add("close SPV store")).when(store).close();

        walletConfig.shutDown();

        assertEquals(List.of("stop PeerGroup", "save BTC wallet", "save BSQ wallet", "close SPV store"), calls);
        verify(peerGroup, never()).stop();
    }

    @Test
    void stopsPeerGroupCompletelyBeforeSavingWalletsIfStopWithoutTimeoutIsSet() throws Exception {
        List<String> calls = new ArrayList<>();
        when(peerGroup.isRunning()).thenReturn(true);
        doAnswer(invocation -> calls.add("stop PeerGroup")).when(peerGroup).stop();
        doAnswer(invocation -> calls.add("save BTC wallet")).when(btcWallet).saveToFile(any(File.class));
        doAnswer(invocation -> calls.add("save BSQ wallet")).when(bsqWallet).saveToFile(any(File.class));
        doAnswer(invocation -> calls.add("close SPV store")).when(store).close();

        walletConfig.setStopPeerGroupWithoutTimeout(true);
        walletConfig.shutDown();

        assertEquals(List.of("stop PeerGroup", "save BTC wallet", "save BSQ wallet", "close SPV store"), calls);
        verify(peerGroup, never()).stopAsync();
    }

    @Test
    void savesWalletsAndClosesStoreIfPeerGroupStopTimesOut() throws Exception {
        when(peerGroup.isRunning()).thenReturn(true);
        when(peerGroup.stopAsync()).thenReturn(SettableFuture.create());

        long start = System.currentTimeMillis();
        walletConfig.shutDown();
        long duration = System.currentTimeMillis() - start;

        // The stop never completes, so shutDown must return after its own bounded wait.
        assertTrue(duration < MAX_SHUT_DOWN_MILLIS, "shutDown took " + duration + " ms");
        InOrder inOrder = inOrder(btcWallet, bsqWallet, store);
        inOrder.verify(btcWallet).saveToFile(any(File.class));
        inOrder.verify(bsqWallet).saveToFile(any(File.class));
        inOrder.verify(store).close();
    }

    @Test
    void savesWalletsAndClosesStoreIfPeerGroupNotRunning() throws Exception {
        when(peerGroup.isRunning()).thenReturn(false);

        walletConfig.shutDown();

        verify(peerGroup, never()).stopAsync();
        verify(btcWallet).saveToFile(any(File.class));
        verify(bsqWallet).saveToFile(any(File.class));
        verify(store).close();
    }

    @Test
    void savesWalletsAndClosesStoreIfPeerGroupIsMissing() throws Exception {
        walletConfig.vPeerGroup = null;

        walletConfig.shutDown();

        InOrder inOrder = inOrder(btcWallet, bsqWallet, store);
        inOrder.verify(btcWallet).saveToFile(any(File.class));
        inOrder.verify(bsqWallet).saveToFile(any(File.class));
        inOrder.verify(store).close();
    }

    @Test
    void savesWalletsAndClosesStoreIfIsRunningThrows() throws Exception {
        when(peerGroup.isRunning()).thenThrow(new IllegalStateException("isRunning failed"));

        walletConfig.shutDown();

        InOrder inOrder = inOrder(btcWallet, bsqWallet, store);
        inOrder.verify(btcWallet).saveToFile(any(File.class));
        inOrder.verify(bsqWallet).saveToFile(any(File.class));
        inOrder.verify(store).close();
    }

    @Test
    void savesWalletsAndClosesStoreIfPeerGroupStopFails() throws Exception {
        when(peerGroup.isRunning()).thenReturn(true);
        when(peerGroup.stopAsync()).thenReturn(Futures.immediateFailedFuture(new RuntimeException("stop failed")));

        walletConfig.shutDown();

        InOrder inOrder = inOrder(btcWallet, bsqWallet, store);
        inOrder.verify(btcWallet).saveToFile(any(File.class));
        inOrder.verify(bsqWallet).saveToFile(any(File.class));
        inOrder.verify(store).close();
    }

    @Test
    void savesWalletsAndClosesStoreIfInterruptedAndKeepsInterruptFlag() throws Exception {
        when(peerGroup.isRunning()).thenReturn(true);
        when(peerGroup.stopAsync()).thenReturn(SettableFuture.create());

        Thread.currentThread().interrupt();
        try {
            walletConfig.shutDown();
            assertTrue(Thread.currentThread().isInterrupted(), "interrupt flag must be restored");
        } finally {
            Thread.interrupted();
        }

        InOrder inOrder = inOrder(btcWallet, bsqWallet, store);
        inOrder.verify(btcWallet).saveToFile(any(File.class));
        inOrder.verify(bsqWallet).saveToFile(any(File.class));
        inOrder.verify(store).close();
    }

    @Test
    void savesWalletsOnlyAfterPeerGroupStopCompleted() throws Exception {
        List<String> calls = Collections.synchronizedList(new ArrayList<>());
        Thread[] completer = new Thread[1];
        when(peerGroup.isRunning()).thenReturn(true);
        when(peerGroup.stopAsync()).thenAnswer(invocation -> {
            calls.add("stop PeerGroup");
            SettableFuture<Void> future = SettableFuture.create();
            completer[0] = new Thread(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignore) {
                }
                calls.add("PeerGroup stop completed");
                future.set(null);
            });
            completer[0].start();
            return future;
        });
        doAnswer(invocation -> calls.add("save BTC wallet")).when(btcWallet).saveToFile(any(File.class));
        doAnswer(invocation -> calls.add("save BSQ wallet")).when(bsqWallet).saveToFile(any(File.class));
        doAnswer(invocation -> calls.add("close SPV store")).when(store).close();

        walletConfig.shutDown();
        completer[0].join();

        assertEquals(List.of("stop PeerGroup", "PeerGroup stop completed", "save BTC wallet", "save BSQ wallet",
                "close SPV store"), calls);
    }

    @Test
    void savesWalletsAndClosesStoreIfStopAsyncThrows() throws Exception {
        when(peerGroup.isRunning()).thenReturn(true);
        when(peerGroup.stopAsync()).thenThrow(new IllegalStateException("not running"));

        walletConfig.shutDown();

        InOrder inOrder = inOrder(btcWallet, bsqWallet, store);
        inOrder.verify(btcWallet).saveToFile(any(File.class));
        inOrder.verify(bsqWallet).saveToFile(any(File.class));
        inOrder.verify(store).close();
    }
}
