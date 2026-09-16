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

package bisq.network.p2p.peers;

import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.storage.messages.BroadcastMessage;

import bisq.common.Timer;
import bisq.common.UserThread;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;

import java.time.Duration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BroadcasterTest {
    @BeforeEach
    void setUp() {
        ManualTimer.clear();
        UserThread.setExecutor(Runnable::run);
        UserThread.setTimerClass(ManualTimer.class);
    }

    @AfterEach
    void tearDown() {
        UserThread.resetForTests();
        ManualTimer.clear();
    }

    @Test
    void keepsExecutorAvailableForShutdownBroadcasts() {
        NetworkNode networkNode = mock(NetworkNode.class);
        PeerManager peerManager = mock(PeerManager.class);
        Connection connection = mock(Connection.class);
        BroadcastMessage message = mock(BroadcastMessage.class);
        var executor = new AtomicReference<ListeningExecutorService>();
        var completionCount = new AtomicInteger();

        when(networkNode.getConfirmedConnections()).thenReturn(Set.of(connection));
        when(connection.getPeersNodeAddressOptional()).thenReturn(Optional.empty());
        when(connection.testCapability(message)).thenReturn(true);
        when(networkNode.sendMessage(same(connection), same(message), any(ListeningExecutorService.class)))
                .thenAnswer(invocation -> {
                    ListeningExecutorService broadcastExecutor = invocation.getArgument(2);
                    executor.set(broadcastExecutor);
                    assertFalse(broadcastExecutor.isShutdown());
                    SettableFuture<Connection> future = SettableFuture.create();
                    future.set(connection);
                    return future;
                });

        Broadcaster broadcaster = new Broadcaster(networkNode, peerManager, 1);
        broadcaster.broadcast(message, null);

        broadcaster.shutDown(completionCount::incrementAndGet);

        assertEquals(0, completionCount.get());
        ManualTimer.runNext();
        assertEquals(1, completionCount.get());
        assertTrue(executor.get().isShutdown());
    }

    @Test
    void earlierBroadcastCompletionDoesNotCancelShutdownBundle() {
        NetworkNode networkNode = mock(NetworkNode.class);
        PeerManager peerManager = mock(PeerManager.class);
        Connection connection = mock(Connection.class);
        BroadcastMessage earlierMessage = mock(BroadcastMessage.class);
        BroadcastMessage removalMessage = mock(BroadcastMessage.class);
        var completionCount = new AtomicInteger();
        SettableFuture<Connection> earlierSend = SettableFuture.create();
        SettableFuture<Connection> removalSend = SettableFuture.create();

        when(networkNode.getConfirmedConnections()).thenReturn(Set.of(connection));
        when(connection.getPeersNodeAddressOptional()).thenReturn(Optional.empty());
        when(connection.testCapability(any())).thenReturn(true);
        when(networkNode.sendMessage(same(connection), same(earlierMessage), any(ListeningExecutorService.class)))
                .thenReturn(earlierSend);
        when(networkNode.sendMessage(same(connection), same(removalMessage), any(ListeningExecutorService.class)))
                .thenAnswer(invocation -> {
                    assertFalse(invocation.<ListeningExecutorService>getArgument(2).isShutdown());
                    return removalSend;
                });

        Broadcaster broadcaster = new Broadcaster(networkNode, peerManager, 1);
        broadcaster.broadcast(earlierMessage, null);
        broadcaster.flush();
        ManualTimer.runNext();

        broadcaster.broadcast(removalMessage, null);
        broadcaster.shutDown(completionCount::incrementAndGet);
        earlierSend.set(connection);

        assertEquals(0, completionCount.get());
        ManualTimer.runNext();
        verify(networkNode).sendMessage(same(connection), same(removalMessage), any(ListeningExecutorService.class));
        removalSend.set(connection);
        assertEquals(1, completionCount.get());
    }

    @Test
    void completesShutdownOnceWhenAnActiveBroadcastIsCancelled() {
        NetworkNode networkNode = mock(NetworkNode.class);
        PeerManager peerManager = mock(PeerManager.class);
        Connection connection = mock(Connection.class);
        BroadcastMessage message = mock(BroadcastMessage.class);
        var completionCount = new AtomicInteger();

        when(networkNode.getConfirmedConnections()).thenReturn(Set.of(connection));

        Broadcaster broadcaster = new Broadcaster(networkNode, peerManager, 1);
        broadcaster.broadcast(message, null);
        // Hands the request to a BroadcastHandler whose sends are still pending, so the handler is
        // cancelled by the shutdown and reports completion re-entrantly from within it.
        broadcaster.flush();

        broadcaster.shutDown(completionCount::incrementAndGet);

        assertEquals(1, completionCount.get());
        verify(networkNode, never()).sendMessage(any(Connection.class),
                any(BroadcastMessage.class),
                any(ListeningExecutorService.class));
    }

    @Test
    void ignoresBroadcastRequestsAfterShutdownCompleted() {
        NetworkNode networkNode = mock(NetworkNode.class);
        PeerManager peerManager = mock(PeerManager.class);
        BroadcastMessage message = mock(BroadcastMessage.class);
        var completionCount = new AtomicInteger();
        when(networkNode.getConfirmedConnections()).thenReturn(Set.of());

        Broadcaster broadcaster = new Broadcaster(networkNode, peerManager, 1);
        broadcaster.shutDown(completionCount::incrementAndGet);
        assertEquals(1, completionCount.get());

        broadcaster.broadcast(message, null);

        // No bundle timer is scheduled, so nothing can hand the stopped executor to a new BroadcastHandler.
        assertThrows(NoSuchElementException.class, ManualTimer::runNext);
        verify(networkNode, never()).sendMessage(any(Connection.class),
                any(BroadcastMessage.class),
                any(ListeningExecutorService.class));
    }

    public static class ManualTimer implements Timer {
        private static final List<ManualTimer> timers = new ArrayList<>();

        private Duration delay;
        private Runnable runnable;
        private boolean stopped;

        @Override
        public Timer runLater(Duration delay, Runnable runnable) {
            this.delay = delay;
            this.runnable = runnable;
            timers.add(this);
            return this;
        }

        @Override
        public Timer runPeriodically(Duration interval, Runnable runnable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void stop() {
            stopped = true;
        }

        static void runNext() {
            ManualTimer timer = timers.stream()
                    .filter(candidate -> !candidate.stopped)
                    .min(Comparator.comparing(candidate -> candidate.delay))
                    .orElseThrow();
            timer.stopped = true;
            timer.runnable.run();
        }

        static void clear() {
            timers.clear();
        }
    }
}
