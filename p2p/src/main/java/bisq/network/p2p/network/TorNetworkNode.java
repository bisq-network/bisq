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
import bisq.network.p2p.Utils;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.proto.network.NetworkProtoResolver;
import bisq.common.util.Utilities;

import org.berndpruenster.netlayer.tor.HiddenServiceSocket;
import org.berndpruenster.netlayer.tor.Tor;
import org.berndpruenster.netlayer.tor.TorCtlException;
import org.berndpruenster.netlayer.tor.TorSocket;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.monadic.MonadicBinding;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.security.SecureRandom;

import java.net.Socket;

import java.io.IOException;

import java.util.Base64;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

// Run in UserThread
public class TorNetworkNode extends NetworkNode {

    private static final Logger log = LoggerFactory.getLogger(TorNetworkNode.class);

    private static final int MAX_RESTART_ATTEMPTS = 5;
    private static final long SHUT_DOWN_TIMEOUT = 5;


    private HiddenServiceSocket hiddenServiceSocket;
    private Timer shutDownTimeoutTimer;
    private int restartCounter;
    @SuppressWarnings("FieldCanBeLocal")
    private MonadicBinding<Boolean> allShutDown;
    private Tor tor;

    private TorMode torMode;

    private boolean streamIsolation;

    private Socks5Proxy socksProxy;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TorNetworkNode(int servicePort, NetworkProtoResolver networkProtoResolver, boolean useStreamIsolation,
            TorMode torMode) {
        super(servicePort, networkProtoResolver);
        this.torMode = torMode;
        this.streamIsolation = useStreamIsolation;
        createExecutorService();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void start(@Nullable SetupListener setupListener) {
        torMode.doRollingBackup();

        if (setupListener != null)
            addSetupListener(setupListener);

        // Create the tor node (takes about 6 sec.)
        createTorAndHiddenService(Utils.findFreeSystemPort(), servicePort);
    }

    @Override
    protected Socket createSocket(NodeAddress peerNodeAddress) throws IOException {
        checkArgument(peerNodeAddress.getHostName().endsWith(".onion"), "PeerAddress is not an onion address");
        // If streamId is null stream isolation gets deactivated.
        // Hidden services use stream isolation by default so we pass null.
        return new TorSocket(peerNodeAddress.getHostName(), peerNodeAddress.getPort(), null);
    }

    // TODO handle failure more cleanly
    public Socks5Proxy getSocksProxy() {
        try {
            String stream = null;
            if (streamIsolation) {
                // create a random string
                byte[] bytes = new byte[512]; // note that getProxy does Sha256 that string anyways
                new SecureRandom().nextBytes(bytes);
                stream = Base64.getEncoder().encodeToString(bytes);
            }

            if (socksProxy == null || streamIsolation) {
                tor = Tor.getDefault();

                // ask for the connection
                socksProxy = tor != null ? tor.getProxy(stream) : null;
            }
            return socksProxy;
        } catch (TorCtlException e) {
            log.error("TorCtlException at getSocksProxy: " + e.toString());
            e.printStackTrace();
            return null;
        } catch (Throwable t) {
            log.error("Error at getSocksProxy: " + t.toString());
            return null;
        }
    }

    public void shutDown(@Nullable Runnable shutDownCompleteHandler) {
        BooleanProperty torNetworkNodeShutDown = torNetworkNodeShutDown();
        BooleanProperty networkNodeShutDown = networkNodeShutDown();
        BooleanProperty shutDownTimerTriggered = shutDownTimerTriggered();

        // Need to store allShutDown to not get garbage collected
        allShutDown = EasyBind.combine(torNetworkNodeShutDown, networkNodeShutDown, shutDownTimerTriggered, (a, b, c) -> (a && b) || c);
        allShutDown.subscribe((observable, oldValue, newValue) -> {
            if (newValue) {
                shutDownTimeoutTimer.stop();
                long ts = System.currentTimeMillis();
                log.debug("Shutdown executorService");
                try {
                    MoreExecutors.shutdownAndAwaitTermination(executorService, 500, TimeUnit.MILLISECONDS);
                    log.debug("Shutdown executorService done after " + (System.currentTimeMillis() - ts) + " ms.");
                    log.debug("Shutdown completed");
                } catch (Throwable t) {
                    log.error("Shutdown executorService failed with exception: " + t.getMessage());
                    t.printStackTrace();
                } finally {
                    try {
                        if (shutDownCompleteHandler != null)
                            shutDownCompleteHandler.run();
                    } catch (Throwable ignore) {
                    }
                }
            }
        });
    }

    private BooleanProperty torNetworkNodeShutDown() {
        final BooleanProperty done = new SimpleBooleanProperty();
        if (executorService != null) {
            executorService.submit(() -> {
                Utilities.setThreadName("torNetworkNodeShutDown");
                long ts = System.currentTimeMillis();
                log.debug("Shutdown torNetworkNode");
                try {
                    if (tor != null)
                        tor.shutdown();
                    log.debug("Shutdown torNetworkNode done after " + (System.currentTimeMillis() - ts) + " ms.");
                } catch (Throwable e) {
                    log.error("Shutdown torNetworkNode failed with exception: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    UserThread.execute(() -> done.set(true));
                }
            });
        } else {
            done.set(true);
        }
        return done;
    }

    private BooleanProperty networkNodeShutDown() {
        final BooleanProperty done = new SimpleBooleanProperty();
        super.shutDown(() -> done.set(true));
        return done;
    }

    private BooleanProperty shutDownTimerTriggered() {
        final BooleanProperty done = new SimpleBooleanProperty();
        shutDownTimeoutTimer = UserThread.runAfter(() -> {
            log.error("A timeout occurred at shutDown");
            done.set(true);
        }, SHUT_DOWN_TIMEOUT);
        return done;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // shutdown, restart
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void restartTor(String errorMessage) {
        log.info("Restarting Tor");
        restartCounter++;
        if (restartCounter <= MAX_RESTART_ATTEMPTS) {
            UserThread.execute(() -> {
                setupListeners.forEach(SetupListener::onRequestCustomBridges);
            });
            log.warn("We stop tor as starting tor with the default bridges failed. We request user to add custom bridges.");
            shutDown(null);
        } else {
            String msg = "We tried to restart Tor " + restartCounter +
                    " times, but it continued to fail with error message:\n" +
                    errorMessage + "\n\n" +
                    "Please check your internet connection and firewall and try to start again.";
            log.error(msg);
            throw new RuntimeException(msg);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // create tor
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createTorAndHiddenService(int localPort, int servicePort) {
        ListenableFuture<Void> future = executorService.submit(() -> {
            try {
                // get tor
                Tor.setDefault(torMode.getTor());

                // start hidden service
                long ts2 = new Date().getTime();
                hiddenServiceSocket = new HiddenServiceSocket(localPort, torMode.getHiddenServiceDirectory(), servicePort);
                nodeAddressProperty.set(new NodeAddress(hiddenServiceSocket.getServiceName() + ":" + hiddenServiceSocket.getHiddenServicePort()));
                UserThread.execute(() -> setupListeners.forEach(SetupListener::onTorNodeReady));
                hiddenServiceSocket.addReadyListener(socket -> {
                    try {
                        log.info("\n################################################################\n" +
                                        "Tor hidden service published after {} ms. Socked={}\n" +
                                        "################################################################",
                                (new Date().getTime() - ts2), socket); //takes usually 30-40 sec
                        new Thread() {
                            @Override
                            public void run() {
                                try {
                                    nodeAddressProperty.set(new NodeAddress(hiddenServiceSocket.getServiceName() + ":" + hiddenServiceSocket.getHiddenServicePort()));
                                    startServer(socket);
                                    UserThread.execute(() -> setupListeners.forEach(SetupListener::onHiddenServicePublished));
                                } catch (final Exception e1) {
                                    log.error(e1.toString());
                                    e1.printStackTrace();
                                }
                            }
                        }.start();
                    } catch (final Exception e) {
                        log.error(e.toString());
                        e.printStackTrace();
                    }
                    return null;
                });
                log.info("It will take some time for the HS to be reachable (~40 seconds). You will be notified about this");
            } catch (TorCtlException e) {
                String msg = e.getCause() != null ? e.getCause().toString() : e.toString();
                log.error("Tor node creation failed: {}", msg);
                if (e.getCause() instanceof IOException) {
                    // Since we cannot connect to Tor, we cannot do nothing.
                    // Furthermore, we have no hidden services started yet, so there is no graceful
                    // shutdown needed either
                    UserThread.execute(() -> setupListeners.forEach(s -> s.onSetupFailed(new RuntimeException(msg))));
                } else {
                    restartTor(e.getMessage());
                }
            } catch (IOException e) {
                log.error("Could not connect to running Tor: {}", e.getMessage());
                // Since we cannot connect to Tor, we cannot do nothing.
                // Furthermore, we have no hidden services started yet, so there is no graceful
                // shutdown needed either
                UserThread.execute(() -> setupListeners.forEach(s -> s.onSetupFailed(new RuntimeException(e.getMessage()))));
            } catch (Throwable ignore) {
            }

            return null;
        });
        Futures.addCallback(future, new FutureCallback<Void>() {
            public void onSuccess(Void ignore) {
            }

            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> log.error("Hidden service creation failed: " + throwable));
            }
        });
    }
}
