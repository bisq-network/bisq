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

package bisq.network.p2p.network;

import bisq.common.FrameRateTimer;
import bisq.common.UserThread;
import bisq.common.proto.network.NetworkProtoResolver;
import bisq.common.util.SingleThreadExecutorUtils;

import org.berndpruenster.netlayer.tor.Tor;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.Uninterruptibles;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import java.io.IOException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TorNetworkNodeShutdownTest {

    @Test
    void hiddenServiceCloseDoesNotBlockUserThreadOrRaceTorShutdown() throws Exception {
        ExecutorService userThreadExecutor = SingleThreadExecutorUtils.getSingleThreadExecutor(
                "TorNetworkNodeShutdownTest-UserThread");
        var shutdownCallReturned = new CountDownLatch(1);
        var torShutdownStarted = new CountDownLatch(1);
        var shutdownCompleted = new CountDownLatch(1);
        Tor tor = mock(Tor.class);
        doAnswer(invocation -> {
            torShutdownStarted.countDown();
            return null;
        }).when(tor).shutdown();
        Tor.setDefault(tor);

        UserThread.setExecutor(userThreadExecutor);
        UserThread.setTimerClass(FrameRateTimer.class);
        var networkNode = new TorNetworkNode(8080,
                mock(NetworkProtoResolver.class),
                false,
                mock(TorMode.class),
                null,
                2,
                "127.0.0.1");
        var serverSocket = new BlockingServerSocket();
        networkNode.startServer(serverSocket);

        try {
            assertTrue(serverSocket.acceptStarted.await(1, TimeUnit.SECONDS));

            UserThread.execute(() -> {
                networkNode.shutDown(shutdownCompleted::countDown);
                shutdownCallReturned.countDown();
            });

            assertTrue(serverSocket.closeStarted.await(1, TimeUnit.SECONDS));
            assertTrue(shutdownCallReturned.await(1, TimeUnit.SECONDS));
            verify(tor, never()).shutdown();

            serverSocket.allowClose.countDown();
            assertTrue(serverSocket.closeCompleted.await(1, TimeUnit.SECONDS));
            assertTrue(torShutdownStarted.await(1, TimeUnit.SECONDS));
            assertTrue(shutdownCompleted.await(1, TimeUnit.SECONDS));
            verify(tor).shutdown();
        } finally {
            serverSocket.allowClose.countDown();
            serverSocket.listenerClosed.countDown();
            Tor.setDefault(null);
            UserThread.setExecutor(Runnable::run);
            UserThread.setTimerClass(FrameRateTimer.class);
            userThreadExecutor.shutdownNow();
            shutDownTorExecutor(networkNode);
            shutDownExecutors(networkNode);
        }
    }

    @Test
    void shutdownTimeoutRemainsResponsiveWhenTorControlBlocks() throws Exception {
        ExecutorService userThreadExecutor = SingleThreadExecutorUtils.getSingleThreadExecutor(
                "TorNetworkNodeShutdownTest-UserThread");
        var torShutdownStarted = new CountDownLatch(1);
        var allowTorShutdown = new CountDownLatch(1);
        var torShutdownReturned = new CountDownLatch(1);
        var shutdownCallReturned = new CountDownLatch(1);
        var firstCompletion = new CountDownLatch(1);
        var duplicateCompletion = new CountDownLatch(1);
        var completionCount = new AtomicInteger();
        Tor tor = mock(Tor.class);
        doAnswer(invocation -> {
            torShutdownStarted.countDown();
            Uninterruptibles.awaitUninterruptibly(allowTorShutdown);
            torShutdownReturned.countDown();
            return null;
        }).when(tor).shutdown();
        Tor.setDefault(tor);

        UserThread.setExecutor(userThreadExecutor);
        UserThread.setTimerClass(FrameRateTimer.class);
        var networkNode = new TorNetworkNode(8080,
                mock(NetworkProtoResolver.class),
                false,
                mock(TorMode.class),
                null,
                2,
                "127.0.0.1");

        try {
            UserThread.execute(() -> {
                networkNode.shutDown(() -> {
                    if (completionCount.incrementAndGet() == 1) {
                        firstCompletion.countDown();
                    } else {
                        duplicateCompletion.countDown();
                    }
                });
                shutdownCallReturned.countDown();
            });

            assertTrue(torShutdownStarted.await(1, TimeUnit.SECONDS));
            assertTrue(shutdownCallReturned.await(1, TimeUnit.SECONDS));
            assertTrue(firstCompletion.await(4, TimeUnit.SECONDS));
            assertEquals(1, completionCount.get());

            allowTorShutdown.countDown();
            assertTrue(torShutdownReturned.await(1, TimeUnit.SECONDS));
            assertFalse(duplicateCompletion.await(500, TimeUnit.MILLISECONDS));
            assertEquals(1, completionCount.get());
        } finally {
            allowTorShutdown.countDown();
            Tor.setDefault(null);
            UserThread.setExecutor(Runnable::run);
            UserThread.setTimerClass(FrameRateTimer.class);
            userThreadExecutor.shutdownNow();
            shutDownTorExecutor(networkNode);
            shutDownExecutors(networkNode);
        }
    }

    private static void shutDownTorExecutor(TorNetworkNode networkNode) throws Exception {
        Field field = TorNetworkNode.class.getDeclaredField("executor");
        field.setAccessible(true);
        ((ExecutorService) field.get(networkNode)).shutdownNow();
    }

    private static void shutDownExecutors(NetworkNode networkNode) throws Exception {
        shutDownExecutor(networkNode, "connectionExecutor");
        shutDownExecutor(networkNode, "sendMessageExecutor");
    }

    private static void shutDownExecutor(NetworkNode networkNode, String fieldName) throws Exception {
        Field field = NetworkNode.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        ((ListeningExecutorService) field.get(networkNode)).shutdownNow();
    }

    private static final class BlockingServerSocket extends ServerSocket {
        private final CountDownLatch acceptStarted = new CountDownLatch(1);
        private final CountDownLatch listenerClosed = new CountDownLatch(1);
        private final CountDownLatch closeStarted = new CountDownLatch(1);
        private final CountDownLatch allowClose = new CountDownLatch(1);
        private final CountDownLatch closeCompleted = new CountDownLatch(1);

        private BlockingServerSocket() throws IOException {
        }

        @Override
        public Socket accept() throws IOException {
            acceptStarted.countDown();
            Uninterruptibles.awaitUninterruptibly(listenerClosed);
            throw new SocketException("listener closed");
        }

        @Override
        public void close() throws IOException {
            super.close();
            listenerClosed.countDown();
            closeStarted.countDown();
            Uninterruptibles.awaitUninterruptibly(allowClose);
            closeCompleted.countDown();
        }
    }
}
