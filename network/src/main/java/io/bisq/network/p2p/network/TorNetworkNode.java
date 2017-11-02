package io.bisq.network.p2p.network;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.common.proto.network.NetworkProtoResolver;
import io.bisq.common.storage.FileUtil;
import io.bisq.common.util.Utilities;
import io.bisq.network.BridgeProvider;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.Utils;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.Setter;
import org.berndpruenster.netlayer.tor.*;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.monadic.MonadicBinding;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

// Run in UserThread
public class TorNetworkNode extends NetworkNode {
    private static final Logger log = LoggerFactory.getLogger(TorNetworkNode.class);

    private static final int MAX_RESTART_ATTEMPTS = 5;
    private static final long SHUT_DOWN_TIMEOUT_SEC = 5;
    private static final int WAIT_BEFORE_RESTART = 2000;

    private HiddenServiceSocket hiddenServiceSocket;
    private final File torDir;
    private Timer shutDownTimeoutTimer;
    private int restartCounter;
    @SuppressWarnings("FieldCanBeLocal")
    private MonadicBinding<Boolean> allShutDown;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TorNetworkNode(int servicePort, File torDir, NetworkProtoResolver networkProtoResolver) {
        super(servicePort, networkProtoResolver);
        this.torDir = torDir;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void start(@Nullable SetupListener setupListener) {
        FileUtil.rollingBackup(new File(Paths.get(torDir.getAbsolutePath(), "hiddenservice").toString()), "private_key", 20);

        if (setupListener != null)
            addSetupListener(setupListener);

        createExecutorService();

        // Create the tor node (takes about 6 sec.)
        createTorAndHiddenService(torDir, Utils.findFreeSystemPort(), servicePort, BridgeProvider.getBridges());
    }

    @Override
    protected Socket createSocket(NodeAddress peerNodeAddress) throws IOException {
        checkArgument(peerNodeAddress.getHostName().endsWith(".onion"), "PeerAddress is not an onion address");
        return new TorSocket(peerNodeAddress.getHostName(), peerNodeAddress.getPort(), UUID.randomUUID().toString()); // each socket uses a random Tor stream id
    }

    // TODO handle failure more cleanly
    public Socks5Proxy getSocksProxy() {
        try {
            return Tor.getDefault() != null ? Tor.getDefault().getProxy() : null;
        } catch (TorCtlException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void shutDown(@Nullable Runnable shutDownCompleteHandler) {
        Log.traceCall();
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
                    if (shutDownCompleteHandler != null)
                        shutDownCompleteHandler.run();
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
                    if (Tor.getDefault() != null)
                        Tor.getDefault().shutdown();
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
        }, SHUT_DOWN_TIMEOUT_SEC);
        return done;
    }


///////////////////////////////////////////////////////////////////////////////////////////
// shutdown, restart
///////////////////////////////////////////////////////////////////////////////////////////

    private void restartTor(String errorMessage) {
        Log.traceCall();
        log.warn("Restarting Tor");
        restartCounter++;
        if (restartCounter <= MAX_RESTART_ATTEMPTS) {
            // If we failed we try with custom bridges
            if (restartCounter == 1) {
                setupListeners.stream().forEach(e -> e.onRequestCustomBridges(() -> {
                    log.warn("Tor restart after custom bridges.");
                    start(null);
                }));
                log.warn("We stop tor as starting tor with the default bridges failed. We request user to add custom bridges.");
                shutDown(null);
            } else {
                shutDown(() -> UserThread.runAfter(() -> {
                    log.warn("We restart tor using custom bridges.");
                    log.warn("Bridges: " + BridgeProvider.getBridges());
                    start(null);
                }, WAIT_BEFORE_RESTART, TimeUnit.MILLISECONDS));
            }
        } else {
            String msg = "We tried to restart Tor " + restartCounter +
                    " times, but it continued to fail with error message:\n" +
                    errorMessage + "\n\n" +
                    "Please check your internet connection and firewall and try to start again.";
            log.error(msg);
            throw new RuntimeException(msg);
        }
        //start();
    }

///////////////////////////////////////////////////////////////////////////////////////////
// create tor
///////////////////////////////////////////////////////////////////////////////////////////

    private void createTorAndHiddenService(final File torDir, int localPort, int servicePort, List<String> bridgeLines) {
        Log.traceCall();
        log.debug("Using bridges: {}", bridgeLines.stream().collect(Collectors.joining(",")));
        if(restartCounter == 0) {
            log.error("Doing fake restart to get to the bridges");
            restartTor("error message here...");
            return;
        }
        ListenableFuture<Object> future = (ListenableFuture<Object>) executorService.submit(() -> {
            try {
                Tor.setDefault(new NativeTor(torDir, bridgeLines));
            } catch (TorCtlException e) {
                log.error("Tor node creation failed", e);
                restartTor(e.getMessage());
                return;
            }
            UserThread.execute(() -> setupListeners.stream().forEach(SetupListener::onTorNodeReady));

            hiddenServiceSocket = new HiddenServiceSocket(localPort, "hiddenservicedir_changeme", servicePort);
            hiddenServiceSocket.addReadyListener(socket -> {
                Socket con;
                try {
                    log.info("Hidden Service " + socket + " is ready");
                    new Thread() {
                        @Override
                        public void run() {
                            try {
                                Log.traceCall("hiddenService created");
                                nodeAddressProperty.set(new NodeAddress(hiddenServiceSocket.getServiceName() + ":" + hiddenServiceSocket.getHiddenServicePort()));
                                startServer(socket);
                                UserThread.execute(() -> setupListeners.stream().forEach(SetupListener::onHiddenServicePublished));
                            } catch (final Exception e1) {
                                e1.printStackTrace();
                            }
                        }
                    }.start();
                } catch (final Exception e) {
                    e.printStackTrace();
                }
                return null;
            });
            log.info("It will take some time for the HS to be reachable (~40 seconds). You will be notified about this");
        });
        Futures.addCallback(future, new FutureCallback<Object>() {
            public void onSuccess(Object hiddenServiceDescriptor) {
                log.debug("HiddenServiceDescriptor created. Wait for publishing.");
            }

            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> {
                    log.error("Hidden service creation failed", throwable);
                    restartTor(throwable.getMessage());
                });
            }
        });
    }
}
