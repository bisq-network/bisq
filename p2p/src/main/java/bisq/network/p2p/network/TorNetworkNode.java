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

import bisq.network.p2p.NodeAddress;
import bisq.network.tor.ExternalTorUnavailableException;
import bisq.network.tor.TorService;
import bisq.network.tor.bridges.BridgesUnavailableException;
import bisq.network.tor.controller.exceptions.TorBootstrapFailedException;
import bisq.network.tor.onionservice.OnionServiceKey;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.proto.network.NetworkProtoResolver;
import bisq.common.util.SingleThreadExecutorUtils;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import com.runjva.sourceforge.jsocks.protocol.UserPasswordAuthentication;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;

import java.security.SecureRandom;

import java.net.ServerSocket;
import java.net.Socket;

import java.io.IOException;

import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class TorNetworkNode extends NetworkNode {
    // The connections close within 1.5 s (NetworkNode.shutDown). The Tor library then stops Tor within about 6 s: it
    // waits 3 s for Tor to exit after SIGTERM, and 3 s after SIGKILL. A shorter wait lets the application exit while
    // the library still stops Tor.
    private static final long SHUT_DOWN_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(8);
    // Tor puts streams with different SOCKS credentials on different circuits. All peer connections use the same
    // credentials, as they all used netlayer's unauthenticated SOCKS connection before.
    private static final String PEER_CONNECTION_STREAM_ID = "bisq-p2p";

    private Timer shutDownTimeoutTimer;
    @Nullable
    private volatile TorService torService;
    private TorMode torMode;
    private boolean streamIsolation;
    private Socks5Proxy socksProxy;
    // Read by the start of Tor on the executor
    private volatile boolean shutDownInProgress;
    private final AtomicBoolean torShutDownStarted = new AtomicBoolean();
    private final AtomicBoolean shutDownCompleted = new AtomicBoolean();
    // The handlers of all shutDown calls. A call during the shutdown joins it (graceful-shutdown.md).
    private final Queue<Runnable> shutDownCompleteHandlers = new ConcurrentLinkedQueue<>();
    private final ExecutorService executor;
    private final long shutDownTimeoutMillis;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TorNetworkNode(int servicePort,
                          NetworkProtoResolver networkProtoResolver,
                          boolean useStreamIsolation,
                          TorMode torMode,
                          @Nullable BanFilter banFilter,
                          int maxConnections) {
        this(servicePort, networkProtoResolver, useStreamIsolation, torMode, banFilter, maxConnections,
                SHUT_DOWN_TIMEOUT_MILLIS);
    }

    @VisibleForTesting
    TorNetworkNode(int servicePort,
                   NetworkProtoResolver networkProtoResolver,
                   boolean useStreamIsolation,
                   TorMode torMode,
                   @Nullable BanFilter banFilter,
                   int maxConnections,
                   long shutDownTimeoutMillis) {
        super(servicePort, networkProtoResolver, banFilter, maxConnections);
        this.torMode = torMode;
        this.streamIsolation = useStreamIsolation;
        this.shutDownTimeoutMillis = shutDownTimeoutMillis;

        executor = SingleThreadExecutorUtils.getSingleThreadExecutor("StartTor");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void start(@Nullable SetupListener setupListener) {
        torMode.doRollingBackup();

        if (setupListener != null)
            addSetupListener(setupListener);

        createTorAndHiddenService(servicePort);
    }

    @Override
    protected Socket createSocket(NodeAddress peerNodeAddress) throws IOException {
        checkArgument(peerNodeAddress.getHostName().endsWith(".onion"), "PeerAddress is not an onion address");
        TorService currentTorService = torService;
        if (currentTorService == null) {
            throw new IOException("Tor is not running");
        }
        return currentTorService.getSocket(peerNodeAddress.getHostName(),
                peerNodeAddress.getPort(),
                PEER_CONNECTION_STREAM_ID,
                CREATE_SOCKET_TIMEOUT);
    }

    @Override
    public Socks5Proxy getSocksProxy() {
        try {
            TorService currentTorService = torService;
            if (currentTorService == null) {
                return null;
            }

            if (streamIsolation) {
                Socks5Proxy isolatedSocksProxy = currentTorService.getSocks5Proxy();
                // Tor isolates streams with different SOCKS credentials. A SOCKS username has at most 255 bytes.
                byte[] bytes = new byte[32];
                new SecureRandom().nextBytes(bytes);
                isolatedSocksProxy.setAuthenticationMethod(UserPasswordAuthentication.METHOD_ID,
                        new UserPasswordAuthentication(HexFormat.of().formatHex(bytes), ""));
                return isolatedSocksProxy;
            }

            if (socksProxy == null) {
                socksProxy = currentTorService.getSocks5Proxy();
            }
            return socksProxy;
        } catch (Throwable t) {
            log.error("Error at getSocksProxy", t);
            return null;
        }
    }

    public void shutDown(@Nullable Runnable shutDownCompleteHandler) {
        log.info("TorNetworkNode shutdown started");
        if (shutDownCompleteHandler != null) {
            shutDownCompleteHandlers.add(shutDownCompleteHandler);
        }
        if (shutDownInProgress) {
            log.info("The shutdown is already in progress. The handler runs when it completes.");
            if (shutDownCompleted.get()) {
                runShutDownCompleteHandlers();
            }
            return;
        }
        shutDownInProgress = true;

        shutDownTimeoutTimer = UserThread.runAfter(() -> {
            log.error("A timeout occurred at shutDown");
            completeShutDown();
        }, shutDownTimeoutMillis, TimeUnit.MILLISECONDS);

        super.shutDown(this::shutDownTor);
    }

    private void shutDownTor() {
        if (shutDownCompleted.get() || !torShutDownStarted.compareAndSet(false, true)) {
            return;
        }

        TorService currentTorService = torService;
        torService = null;
        if (currentTorService == null) {
            // A Tor which is still starting is stopped by the start, which checks shutDownInProgress.
            completeShutDown();
            return;
        }

        // TorService stops Tor on another thread and waits for the Tor process to exit. The UserThread stays
        // available for the bounded shutdown timeout and the persistence flush which follows it.
        currentTorService.shutdown().whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.error("Shutdown torNetworkNode failed with exception", throwable);
            } else {
                log.info("Tor shutdown completed");
            }
            UserThread.execute(this::completeShutDown);
        });
    }

    private void completeShutDown() {
        if (!shutDownCompleted.compareAndSet(false, true)) {
            return;
        }

        try {
            if (shutDownTimeoutTimer != null) {
                shutDownTimeoutTimer.stop();
            }
            executor.shutdownNow();
        } finally {
            runShutDownCompleteHandlers();
        }
    }

    // Runs each handler once, also the handler of a shutDown call which joins after the completion.
    private void runShutDownCompleteHandlers() {
        Runnable handler;
        while ((handler = shutDownCompleteHandlers.poll()) != null) {
            handler.run();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Create tor and hidden service
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createTorAndHiddenService(int servicePort) {
        executor.submit(() -> {
            TorService startedTorService = null;
            try {
                OnionServiceKey onionServiceKey = torMode.getOnionServiceKey();
                startedTorService = torMode.getTor();
                torService = startedTorService;
                // The shutdown stops the Tor it finds in torService. A shutdown which started before that has to be
                // checked here.
                if (shutDownInProgress) {
                    startedTorService.shutdown();
                    return null;
                }
                long ts = System.currentTimeMillis();
                NodeAddress nodeAddress = new NodeAddress(onionServiceKey.getOnionAddress() + ":" + servicePort);
                nodeAddressProperty.set(nodeAddress);
                UserThread.execute(() -> setupListeners.forEach(SetupListener::onTorNodeReady));
                ServerSocket serverSocket = startedTorService
                        .publishOnionServiceAndCreateServerSocket(servicePort, onionServiceKey)
                        .join();
                if (shutDownInProgress) {
                    closeQuietly(serverSocket);
                    startedTorService.shutdown();
                    return null;
                }
                log.info("\n################################################################\n" +
                                "Tor hidden service published after {} ms. Socket={}\n" +
                                "################################################################",
                        System.currentTimeMillis() - ts, serverSocket);
                UserThread.execute(() -> {
                    if (shutDownInProgress) {
                        closeQuietly(serverSocket);
                        return;
                    }
                    nodeAddressProperty.set(nodeAddress);
                    startServer(serverSocket);
                    setupListeners.forEach(SetupListener::onHiddenServicePublished);
                });
            } catch (Throwable throwable) {
                if (shutDownInProgress) {
                    log.info("Starting tor node stopped by the shutdown: {}", throwable.toString());
                    return null;
                }
                log.error("Starting tor node failed", throwable);
                // A node with Tor but without onion service could connect to peers but not be reached.
                if (startedTorService != null) {
                    torService = null;
                    startedTorService.shutdown();
                }
                String errorMessage = getErrorMessage(throwable);
                boolean canBridgesHelp = canBridgesHelp(throwable);
                UserThread.execute(() -> {
                    setupListeners.forEach(s -> s.onSetupFailed(new RuntimeException(errorMessage)));
                    if (canBridgesHelp) {
                        // As with netlayer: the network may block Tor, so the user can choose bridges.
                        setupListeners.forEach(SetupListener::onRequestCustomBridges);
                    }
                });
            }
            return null;
        });
    }

    private static void closeQuietly(ServerSocket serverSocket) {
        try {
            serverSocket.close();
        } catch (IOException e) {
            log.debug("Could not close the server socket", e);
        }
    }

    // Tor could not connect, or the chosen bridges cannot be used. Other bridges can help, but not with a running Tor,
    // which uses the bridges of its own configuration.
    static boolean canBridgesHelp(Throwable throwable) {
        List<Throwable> causalChain = Throwables.getCausalChain(throwable);
        return causalChain.stream().noneMatch(ExternalTorUnavailableException.class::isInstance) &&
                causalChain.stream().anyMatch(cause -> cause instanceof TorBootstrapFailedException ||
                        cause instanceof BridgesUnavailableException);
    }

    private static String getErrorMessage(Throwable throwable) {
        Throwable rootCause = Throwables.getRootCause(throwable);
        // Its message names the running Tor, the cause and what the user can do. The root cause alone, for example
        // "Connection refused", does not say that Bisq was configured to use a running Tor.
        Optional<ExternalTorUnavailableException> externalTorUnavailable = Throwables.getCausalChain(throwable).stream()
                .filter(ExternalTorUnavailableException.class::isInstance)
                .map(ExternalTorUnavailableException.class::cast)
                .findFirst();
        if (externalTorUnavailable.isPresent()) {
            return externalTorUnavailable.get().getMessage();
        }
        return rootCause.getMessage() != null ? rootCause.getMessage() : rootCause.getClass().getSimpleName();
    }
}
