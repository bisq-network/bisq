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
import bisq.network.utils.Utils;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.proto.network.NetworkProtoResolver;
import bisq.common.util.SingleThreadExecutorUtils;

import org.berndpruenster.netlayer.tor.HiddenServiceSocket;
import org.berndpruenster.netlayer.tor.Tor;
import org.berndpruenster.netlayer.tor.TorCtlException;
import org.berndpruenster.netlayer.tor.TorSocket;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;

import java.security.SecureRandom;

import java.net.Socket;

import java.io.IOException;

import java.util.Base64;
import java.util.concurrent.ExecutorService;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class TorNetworkNode extends NetworkNode {
    private static final long SHUT_DOWN_TIMEOUT = 2;

    private HiddenServiceSocket hiddenServiceSocket;
    private Timer shutDownTimeoutTimer;
    private Tor tor;
    private TorMode torMode;
    private boolean streamIsolation;
    private Socks5Proxy socksProxy;
    private boolean shutDownInProgress;
    private final ExecutorService executor;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TorNetworkNode(int servicePort,
                          NetworkProtoResolver networkProtoResolver,
                          boolean useStreamIsolation,
                          TorMode torMode,
                          @Nullable BanFilter banFilter,
                          int maxConnections) {
        super(servicePort, networkProtoResolver, banFilter, maxConnections);
        this.torMode = torMode;
        this.streamIsolation = useStreamIsolation;

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

        createTorAndHiddenService(Utils.findFreeSystemPort(), servicePort);
    }

    @Override
    protected Socket createSocket(NodeAddress peerNodeAddress) throws IOException {
        checkArgument(peerNodeAddress.getHostName().endsWith(".onion"), "PeerAddress is not an onion address");
        // If streamId is null stream isolation gets deactivated.
        // Hidden services use stream isolation by default, so we pass null.
        return new TorSocket(peerNodeAddress.getHostName(), peerNodeAddress.getPort(), null);
    }

    public Socks5Proxy getSocksProxy() {
        try {
            String stream = null;
            if (streamIsolation) {
                byte[] bytes = new byte[512]; // tor.getProxy creates a Sha256 hash
                new SecureRandom().nextBytes(bytes);
                stream = Base64.getEncoder().encodeToString(bytes);
            }

            if (socksProxy == null || streamIsolation) {
                tor = Tor.getDefault();
                socksProxy = tor != null ? tor.getProxy(stream) : null;
            }
            return socksProxy;
        } catch (Throwable t) {
            log.error("Error at getSocksProxy", t);
            return null;
        }
    }

    public void shutDown(@Nullable Runnable shutDownCompleteHandler) {
        log.info("TorNetworkNode shutdown started");
        if (shutDownInProgress) {
            log.warn("We got shutDown already called");
            return;
        }
        shutDownInProgress = true;

        shutDownTimeoutTimer = UserThread.runAfter(() -> {
            log.error("A timeout occurred at shutDown");
            if (shutDownCompleteHandler != null)
                shutDownCompleteHandler.run();

            executor.shutdownNow();
        }, SHUT_DOWN_TIMEOUT);

        super.shutDown(() -> {
            try {
                tor = Tor.getDefault();
                if (tor != null) {
                    tor.shutdown();
                    tor = null;
                    log.info("Tor shutdown completed");
                }
                executor.shutdownNow();
            } catch (Throwable e) {
                log.error("Shutdown torNetworkNode failed with exception", e);
            } finally {
                shutDownTimeoutTimer.stop();
                if (shutDownCompleteHandler != null)
                    shutDownCompleteHandler.run();
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Create tor and hidden service
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createTorAndHiddenService(int localPort, int servicePort) {
        executor.submit(() -> {
            try {
                Tor.setDefault(torMode.getTor());
                long ts = System.currentTimeMillis();
                hiddenServiceSocket = new HiddenServiceSocket(localPort, torMode.getHiddenServiceDirectory(), servicePort);
                nodeAddressProperty.set(new NodeAddress(hiddenServiceSocket.getServiceName() + ":" + hiddenServiceSocket.getHiddenServicePort()));
                UserThread.execute(() -> setupListeners.forEach(SetupListener::onTorNodeReady));
                hiddenServiceSocket.addReadyListener(socket -> {
                    log.info("\n################################################################\n" +
                                    "Tor hidden service published after {} ms. Socket={}\n" +
                                    "################################################################",
                            System.currentTimeMillis() - ts, socket);
                    UserThread.execute(() -> {
                        nodeAddressProperty.set(new NodeAddress(hiddenServiceSocket.getServiceName() + ":" + hiddenServiceSocket.getHiddenServicePort()));
                        startServer(socket);
                        setupListeners.forEach(SetupListener::onHiddenServicePublished);
                    });
                    return null;
                });
            } catch (TorCtlException e) {
                log.error("Starting tor node failed", e);
                if (e.getCause() instanceof IOException) {
                    UserThread.execute(() -> setupListeners.forEach(s -> s.onSetupFailed(new RuntimeException(e.getMessage()))));
                } else {
                    UserThread.execute(() -> setupListeners.forEach(SetupListener::onRequestCustomBridges));
                    log.warn("We shutdown as starting tor with the default bridges failed. We request user to add custom bridges.");
                    shutDown(null);
                }
            } catch (IOException e) {
                log.error("Could not connect to running Tor", e);
                UserThread.execute(() -> setupListeners.forEach(s -> s.onSetupFailed(new RuntimeException(e.getMessage()))));
            } catch (Throwable ignore) {
            }
            return null;
        });
    }
}
