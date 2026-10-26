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

import bisq.network.tor.ExternalTorUnavailableException;
import bisq.network.tor.TorService;
import bisq.network.tor.bridges.BridgesUnavailableException;
import bisq.network.tor.controller.exceptions.HsDescUploadFailedException;
import bisq.network.tor.controller.exceptions.TorBootstrapFailedException;
import bisq.network.tor.onionservice.OnionServiceKey;
import bisq.network.tor.sandbox.SandboxUnavailableException;

import bisq.common.FrameRateTimer;
import bisq.common.UserThread;
import bisq.common.proto.network.NetworkProtoResolver;
import bisq.common.util.SingleThreadExecutorUtils;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.Uninterruptibles;

import java.net.InetAddress;
import java.net.ServerSocket;

import java.io.IOException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import java.lang.reflect.Field;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TorNetworkNodeStartTest {
    private static final OnionServiceKey ONION_SERVICE_KEY = new OnionServiceKey(
            "25njqamcweflpvkl73j4szahhihoc4xt3ktcgjnpaingr5yhkenl5sid.onion", "secret");

    private ExecutorService userThreadExecutor;

    @BeforeEach
    void setUp() {
        UserThread.resetForTests();
        userThreadExecutor = SingleThreadExecutorUtils.getSingleThreadExecutor("TorNetworkNodeStartTest-UserThread");
        UserThread.setExecutor(userThreadExecutor);
        UserThread.setTimerClass(FrameRateTimer.class);
    }

    @AfterEach
    void tearDown() {
        UserThread.resetForTests();
        userThreadExecutor.shutdownNow();
    }

    @Test
    void stopsTorWhenTheOnionServiceCannotBePublished() throws Exception {
        TorService torService = mock(TorService.class);
        when(torService.shutdown()).thenReturn(CompletableFuture.completedFuture(true));
        when(torService.publishOnionServiceAndCreateServerSocket(anyInt(), any())).thenThrow(new CompletionException(
                new HsDescUploadFailedException("Could not get onion address upload completed in 1 ms")));
        TorMode torMode = mock(TorMode.class);
        when(torMode.getOnionServiceKey()).thenReturn(ONION_SERVICE_KEY);
        when(torMode.getTor()).thenReturn(torService);
        var networkNode = new TorNetworkNode(8080, mock(NetworkProtoResolver.class), false, torMode, null, 2);
        var listener = new RecordingSetupListener();

        try {
            networkNode.start(listener);

            assertTrue(listener.failed.await(5, TimeUnit.SECONDS));
            assertEquals("Could not get onion address upload completed in 1 ms", listener.failure.get().getMessage());
            verify(torService).shutdown();
            assertNull(getTorService(networkNode));
            assertEquals(1, listener.published.getCount());
        } finally {
            shutDownExecutors(networkNode);
        }
    }

    @Test
    void reportsWhyTheRunningTorCannotBeUsed() throws Exception {
        String message = "Bisq is configured to use a Tor which runs outside Bisq, but it cannot use the Tor at " +
                "127.0.0.1:9051: Connection refused.";
        TorMode torMode = mock(TorMode.class);
        when(torMode.getOnionServiceKey()).thenReturn(ONION_SERVICE_KEY);
        when(torMode.getTor()).thenThrow(new CompletionException(new ExternalTorUnavailableException(message)));
        var networkNode = new TorNetworkNode(8080, mock(NetworkProtoResolver.class), false, torMode, null, 2);
        var listener = new RecordingSetupListener();

        try {
            networkNode.start(listener);

            assertTrue(listener.failed.await(5, TimeUnit.SECONDS));
            assertEquals(message, listener.failure.get().getMessage());
            // Bridges of the Tor network settings do not help a running Tor, which uses its own.
            waitForTheUserThread();
            assertEquals(1, listener.bridgesRequested.getCount());
        } finally {
            shutDownExecutors(networkNode);
        }
    }

    @Test
    void reportsWhyTorCannotRunInTheSandbox() throws Exception {
        var sandboxUnavailable = new SandboxUnavailableException("This build of Bisq contains no sandy binary.",
                NewTor.SANDBOX_OPT_OUT_HINT, new IOException("Missing resource"));
        TorMode torMode = mock(TorMode.class);
        when(torMode.getOnionServiceKey()).thenReturn(ONION_SERVICE_KEY);
        when(torMode.getTor()).thenThrow(new CompletionException(sandboxUnavailable));
        var networkNode = new TorNetworkNode(8080, mock(NetworkProtoResolver.class), false, torMode, null, 2);
        var listener = new RecordingSetupListener();

        try {
            networkNode.start(listener);

            assertTrue(listener.failed.await(5, TimeUnit.SECONDS));
            assertEquals(sandboxUnavailable.getMessage(), listener.failure.get().getMessage());
            assertTrue(listener.failure.get().getMessage().contains("--useTorSandbox=false"));
        } finally {
            shutDownExecutors(networkNode);
        }
    }

    @Test
    void asksForBridgesWhenTorCannotConnect() throws Exception {
        TorMode torMode = mock(TorMode.class);
        when(torMode.getOnionServiceKey()).thenReturn(ONION_SERVICE_KEY);
        when(torMode.getTor()).thenThrow(new CompletionException(
                new TorBootstrapFailedException("Could not bootstrap Tor in 240 seconds")));
        var networkNode = new TorNetworkNode(8080, mock(NetworkProtoResolver.class), false, torMode, null, 2);
        var listener = new RecordingSetupListener();

        try {
            networkNode.start(listener);

            assertTrue(listener.failed.await(5, TimeUnit.SECONDS));
            assertEquals("Could not bootstrap Tor in 240 seconds", listener.failure.get().getMessage());
            assertTrue(listener.bridgesRequested.await(5, TimeUnit.SECONDS));
        } finally {
            shutDownExecutors(networkNode);
        }
    }

    @Test
    void otherBridgesCanHelpWhenTheChosenBridgesCannotBeUsed() {
        assertTrue(TorNetworkNode.canBridgesHelp(new CompletionException(
                new BridgesUnavailableException("The program lyrebird of the pluggable transports obfs4 is missing."))));
        assertFalse(TorNetworkNode.canBridgesHelp(new CompletionException(new IllegalStateException("no"))));
    }

    @Test
    void stopsTheTorWhichStartsDuringTheShutdown() throws Exception {
        TorService torService = mock(TorService.class);
        when(torService.shutdown()).thenReturn(CompletableFuture.completedFuture(true));
        var getTorCalled = new CountDownLatch(1);
        var allowGetTor = new CountDownLatch(1);
        TorMode torMode = mock(TorMode.class);
        when(torMode.getOnionServiceKey()).thenReturn(ONION_SERVICE_KEY);
        when(torMode.getTor()).thenAnswer(invocation -> {
            getTorCalled.countDown();
            Uninterruptibles.awaitUninterruptibly(allowGetTor);
            return torService;
        });
        var networkNode = new TorNetworkNode(8080, mock(NetworkProtoResolver.class), false, torMode, null, 2);
        var listener = new RecordingSetupListener();
        var shutdownCompleted = new CountDownLatch(1);

        try {
            networkNode.start(listener);
            assertTrue(getTorCalled.await(5, TimeUnit.SECONDS));
            UserThread.execute(() -> networkNode.shutDown(shutdownCompleted::countDown));
            assertTrue(shutdownCompleted.await(5, TimeUnit.SECONDS));

            allowGetTor.countDown();

            verify(torService, timeout(5000)).shutdown();
            verify(torService, never()).publishOnionServiceAndCreateServerSocket(anyInt(), any());
            assertEquals(1, listener.failed.getCount());
        } finally {
            allowGetTor.countDown();
            shutDownExecutors(networkNode);
        }
    }

    @Test
    void doesNotStartTheServerWhenTheShutdownStartedDuringThePublication() throws Exception {
        TorService torService = mock(TorService.class);
        when(torService.shutdown()).thenReturn(CompletableFuture.completedFuture(true));
        var serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        var publishCalled = new CountDownLatch(1);
        var allowPublish = new CountDownLatch(1);
        when(torService.publishOnionServiceAndCreateServerSocket(anyInt(), any())).thenAnswer(invocation -> {
            publishCalled.countDown();
            Uninterruptibles.awaitUninterruptibly(allowPublish);
            return CompletableFuture.completedFuture(serverSocket);
        });
        TorMode torMode = mock(TorMode.class);
        when(torMode.getOnionServiceKey()).thenReturn(ONION_SERVICE_KEY);
        when(torMode.getTor()).thenReturn(torService);
        var networkNode = new TorNetworkNode(8080, mock(NetworkProtoResolver.class), false, torMode, null, 2);
        var listener = new RecordingSetupListener();
        var shutdownCompleted = new CountDownLatch(1);

        try {
            networkNode.start(listener);
            assertTrue(publishCalled.await(5, TimeUnit.SECONDS));
            UserThread.execute(() -> networkNode.shutDown(shutdownCompleted::countDown));
            assertTrue(shutdownCompleted.await(5, TimeUnit.SECONDS));

            allowPublish.countDown();

            // The shutdown and the start may both stop Tor; TorService ignores the second call.
            verify(torService, timeout(5000).atLeastOnce()).shutdown();
            assertTrue(waitUntilClosed(serverSocket));
            assertEquals(1, listener.published.getCount());
        } finally {
            allowPublish.countDown();
            serverSocket.close();
            shutDownExecutors(networkNode);
        }
    }

    private static void waitForTheUserThread() throws InterruptedException {
        var executed = new CountDownLatch(1);
        UserThread.execute(executed::countDown);
        assertTrue(executed.await(5, TimeUnit.SECONDS));
    }

    private static boolean waitUntilClosed(ServerSocket serverSocket) throws InterruptedException {
        for (int i = 0; i < 50 && !serverSocket.isClosed(); i++) {
            Thread.sleep(100);
        }
        return serverSocket.isClosed();
    }

    private static TorService getTorService(TorNetworkNode networkNode) throws Exception {
        Field field = TorNetworkNode.class.getDeclaredField("torService");
        field.setAccessible(true);
        return (TorService) field.get(networkNode);
    }

    private static void shutDownExecutors(TorNetworkNode networkNode) throws Exception {
        Field torExecutor = TorNetworkNode.class.getDeclaredField("executor");
        torExecutor.setAccessible(true);
        ((ExecutorService) torExecutor.get(networkNode)).shutdownNow();
        for (String fieldName : new String[]{"connectionExecutor", "sendMessageExecutor"}) {
            Field field = NetworkNode.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            ((ListeningExecutorService) field.get(networkNode)).shutdownNow();
        }
    }

    private static final class RecordingSetupListener implements SetupListener {
        private final CountDownLatch published = new CountDownLatch(1);
        private final CountDownLatch failed = new CountDownLatch(1);
        private final CountDownLatch bridgesRequested = new CountDownLatch(1);
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        @Override
        public void onTorNodeReady() {
        }

        @Override
        public void onHiddenServicePublished() {
            published.countDown();
        }

        @Override
        public void onSetupFailed(Throwable throwable) {
            failure.set(throwable);
            failed.countDown();
        }

        @Override
        public void onRequestCustomBridges() {
            bridgesRequested.countDown();
        }
    }
}
