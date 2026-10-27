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

import bisq.network.tor.TorService;

import bisq.common.FrameRateTimer;
import bisq.common.UserThread;
import bisq.common.proto.network.NetworkProtoResolver;
import bisq.common.util.SingleThreadExecutorUtils;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.Uninterruptibles;

import java.net.InetAddress;
import java.net.ServerSocket;

import java.util.concurrent.CompletableFuture;
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
import static org.mockito.Mockito.verify;

class TorNetworkNodeShutdownTest {

    @Test
    void shutdownClosesTheServerSocketAndStopsTor() throws Exception {
        ExecutorService userThreadExecutor = SingleThreadExecutorUtils.getSingleThreadExecutor(
                "TorNetworkNodeShutdownTest-UserThread");
        var shutdownCompleted = new CountDownLatch(1);
        TorService torService = mock(TorService.class);
        doAnswer(invocation -> CompletableFuture.completedFuture(true)).when(torService).shutdown();

        UserThread.setExecutor(userThreadExecutor);
        UserThread.setTimerClass(FrameRateTimer.class);
        var networkNode = new TorNetworkNode(8080,
                mock(NetworkProtoResolver.class),
                false,
                mock(TorMode.class),
                null,
                2);
        setTorService(networkNode, torService);
        var serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        networkNode.startServer(serverSocket);

        try {
            UserThread.execute(() -> networkNode.shutDown(shutdownCompleted::countDown));

            assertTrue(shutdownCompleted.await(4, TimeUnit.SECONDS));
            assertTrue(serverSocket.isClosed());
            verify(torService).shutdown();
        } finally {
            serverSocket.close();
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
        TorService torService = mock(TorService.class);
        // TorService stops Tor on another thread
        doAnswer(invocation -> {
            torShutdownStarted.countDown();
            return CompletableFuture.supplyAsync(() -> {
                Uninterruptibles.awaitUninterruptibly(allowTorShutdown);
                torShutdownReturned.countDown();
                return true;
            });
        }).when(torService).shutdown();

        UserThread.setExecutor(userThreadExecutor);
        UserThread.setTimerClass(FrameRateTimer.class);
        var networkNode = new TorNetworkNode(8080,
                mock(NetworkProtoResolver.class),
                false,
                mock(TorMode.class),
                null,
                2,
                500);
        setTorService(networkNode, torService);

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
            UserThread.setExecutor(Runnable::run);
            UserThread.setTimerClass(FrameRateTimer.class);
            userThreadExecutor.shutdownNow();
            shutDownTorExecutor(networkNode);
            shutDownExecutors(networkNode);
        }
    }

    @Test
    void waitsForTheStopOfTorWhichTakesLongerThanTwoSeconds() throws Exception {
        // The Tor library waits up to 3 s for Tor to exit after SIGTERM before it kills Tor
        ExecutorService userThreadExecutor = SingleThreadExecutorUtils.getSingleThreadExecutor(
                "TorNetworkNodeShutdownTest-UserThread");
        var torShutdownReturned = new CountDownLatch(1);
        var shutdownCompleted = new CountDownLatch(1);
        TorService torService = mock(TorService.class);
        doAnswer(invocation -> CompletableFuture.supplyAsync(() -> {
            Uninterruptibles.sleepUninterruptibly(3, TimeUnit.SECONDS);
            torShutdownReturned.countDown();
            return true;
        })).when(torService).shutdown();

        UserThread.setExecutor(userThreadExecutor);
        UserThread.setTimerClass(FrameRateTimer.class);
        var networkNode = new TorNetworkNode(8080,
                mock(NetworkProtoResolver.class),
                false,
                mock(TorMode.class),
                null,
                2);
        setTorService(networkNode, torService);

        try {
            UserThread.execute(() -> networkNode.shutDown(shutdownCompleted::countDown));

            assertTrue(shutdownCompleted.await(6, TimeUnit.SECONDS));
            assertEquals(0, torShutdownReturned.getCount());
        } finally {
            UserThread.setExecutor(Runnable::run);
            UserThread.setTimerClass(FrameRateTimer.class);
            userThreadExecutor.shutdownNow();
            shutDownTorExecutor(networkNode);
            shutDownExecutors(networkNode);
        }
    }

    @Test
    void aSecondShutDownJoinsTheRunningShutdown() throws Exception {
        // For example the Tor network settings window stops the node first, and then the application shutdown stops
        // it again through P2PService. graceful-shutdown.md requires that the second call joins the first.
        ExecutorService userThreadExecutor = SingleThreadExecutorUtils.getSingleThreadExecutor(
                "TorNetworkNodeShutdownTest-UserThread");
        var allowTorShutdown = new CountDownLatch(1);
        var firstCompletions = new AtomicInteger();
        var secondCompletions = new AtomicInteger();
        var bothCompleted = new CountDownLatch(2);
        var lateCompleted = new CountDownLatch(1);
        TorService torService = mock(TorService.class);
        doAnswer(invocation -> CompletableFuture.supplyAsync(() -> {
            Uninterruptibles.awaitUninterruptibly(allowTorShutdown);
            return true;
        })).when(torService).shutdown();

        UserThread.setExecutor(userThreadExecutor);
        UserThread.setTimerClass(FrameRateTimer.class);
        var networkNode = new TorNetworkNode(8080,
                mock(NetworkProtoResolver.class),
                false,
                mock(TorMode.class),
                null,
                2);
        setTorService(networkNode, torService);

        try {
            UserThread.execute(() -> {
                networkNode.shutDown(() -> {
                    firstCompletions.incrementAndGet();
                    bothCompleted.countDown();
                });
                networkNode.shutDown(() -> {
                    secondCompletions.incrementAndGet();
                    bothCompleted.countDown();
                });
            });

            assertFalse(bothCompleted.await(200, TimeUnit.MILLISECONDS));
            allowTorShutdown.countDown();
            assertTrue(bothCompleted.await(4, TimeUnit.SECONDS));
            verify(torService).shutdown();

            UserThread.execute(() -> networkNode.shutDown(lateCompleted::countDown));
            assertTrue(lateCompleted.await(1, TimeUnit.SECONDS));
            assertEquals(1, firstCompletions.get());
            assertEquals(1, secondCompletions.get());
        } finally {
            allowTorShutdown.countDown();
            UserThread.setExecutor(Runnable::run);
            UserThread.setTimerClass(FrameRateTimer.class);
            userThreadExecutor.shutdownNow();
            shutDownTorExecutor(networkNode);
            shutDownExecutors(networkNode);
        }
    }

    private static void setTorService(TorNetworkNode networkNode, TorService torService) throws Exception {
        Field field = TorNetworkNode.class.getDeclaredField("torService");
        field.setAccessible(true);
        field.set(networkNode, torService);
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
}
