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
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.Utils;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
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
import java.util.Date;
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

    private HiddenServiceSocket hiddenServiceSocket;
    private final File torDir;
    private final BridgeAddressProvider bridgeAddressProvider;
    private Timer shutDownTimeoutTimer;
    private int restartCounter;
    @SuppressWarnings("FieldCanBeLocal")
    private MonadicBinding<Boolean> allShutDown;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TorNetworkNode(int servicePort, File torDir, NetworkProtoResolver networkProtoResolver, BridgeAddressProvider bridgeAddressProvider) {
        super(servicePort, networkProtoResolver);
        this.torDir = torDir;
        this.bridgeAddressProvider = bridgeAddressProvider;
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
        createTorAndHiddenService(torDir, Utils.findFreeSystemPort(), servicePort, bridgeAddressProvider.getBridgeAddresses());
    }

    @Override
    protected Socket createSocket(NodeAddress peerNodeAddress) throws IOException {
        checkArgument(peerNodeAddress.getHostName().endsWith(".onion"), "PeerAddress is not an onion address");
        return new TorSocket(peerNodeAddress.getHostName(), peerNodeAddress.getPort(), UUID.randomUUID().toString()); // each socket uses a random Tor stream id
    }

    // TODO handle failure more cleanly
    public Socks5Proxy getSocksProxy() {
        try {
            final Tor tor = Tor.getDefault();
            return tor != null ? tor.getProxy() : null;
        } catch (TorCtlException e) {
            log.error("Error at getSocksProxy: " + e.toString());
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
                    final Tor tor = Tor.getDefault();
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
        }, SHUT_DOWN_TIMEOUT_SEC);
        return done;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // shutdown, restart
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void restartTor(String errorMessage) {
        Log.traceCall();
        log.info("Restarting Tor");
        restartCounter++;
        if (restartCounter <= MAX_RESTART_ATTEMPTS) {
            UserThread.execute(() -> {
                setupListeners.stream().forEach(SetupListener::onRequestCustomBridges);
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

    private void createTorAndHiddenService(File torDir, int localPort, int servicePort, @Nullable List<String> bridgeEntries) {
        Log.traceCall();
        if (bridgeEntries != null)
            log.info("Using bridges: {}", bridgeEntries.stream().collect(Collectors.joining(",")));

        ListenableFuture<Void> future = executorService.submit(() -> {
            try {
                long ts1 = new Date().getTime();
                log.info("Starting tor");
                Tor.setDefault(new NativeTor(torDir, bridgeEntries));
                log.info("Tor started after {} ms. Start publishing hidden service.", (new Date().getTime() - ts1)); // takes usually a few seconds

                UserThread.execute(() -> setupListeners.stream().forEach(SetupListener::onTorNodeReady));

                long ts2 = new Date().getTime();
                hiddenServiceSocket = new HiddenServiceSocket(localPort, "", servicePort);
                hiddenServiceSocket.addReadyListener(socket -> {
                    try {
                        log.info("Tor hidden service published after {} ms. Socked={}", (new Date().getTime() - ts2), socket); //takes usually 30-40 sec
                        new Thread() {
                            @Override
                            public void run() {
                                try {
                                    Log.traceCall("hiddenService created");
                                    nodeAddressProperty.set(new NodeAddress(hiddenServiceSocket.getServiceName() + ":" + hiddenServiceSocket.getHiddenServicePort()));
                                    startServer(socket);
                                    UserThread.execute(() -> setupListeners.stream().forEach(SetupListener::onHiddenServicePublished));
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
                log.error("Tor node creation failed: " + (e.getCause() != null ? e.getCause().toString() : e.toString()));
                restartTor(e.getMessage());
            }

            return null;
        });
        Futures.addCallback(future, new FutureCallback<Void>() {
            public void onSuccess(Void ignore) {
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
