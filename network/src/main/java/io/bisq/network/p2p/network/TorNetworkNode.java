package io.bisq.network.p2p.network;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyContext;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyManager;
import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.common.util.Utilities;
import io.bisq.network.p2p.Utils;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import io.nucleo.net.HiddenServiceDescriptor;
import io.nucleo.net.JavaTorNode;
import io.nucleo.net.TorNode;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.monadic.MonadicBinding;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;

// Run in UserThread
public class TorNetworkNode extends NetworkNode {
    private static final Logger log = LoggerFactory.getLogger(TorNetworkNode.class);

    private static final int MAX_RESTART_ATTEMPTS = 5;
    private static final long SHUT_DOWN_TIMEOUT_SEC = 5;

    private final File torDir;
    private TorNode torNetworkNode;
    private HiddenServiceDescriptor hiddenServiceDescriptor;
    private Timer shutDownTimeoutTimer;
    private int restartCounter;
    private MonadicBinding<Boolean> allShutDown;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TorNetworkNode(int servicePort, File torDir) {
        super(servicePort);
        this.torDir = torDir;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void start(@Nullable SetupListener setupListener) {
        if (setupListener != null)
            addSetupListener(setupListener);

        createExecutorService();

        // Create the tor node (takes about 6 sec.)
        createTorNode(torDir,
                torNode -> {
                    Log.traceCall("torNode created");
                    TorNetworkNode.this.torNetworkNode = torNode;

                    setupListeners.stream().forEach(SetupListener::onTorNodeReady);

                    // Create Hidden Service (takes about 40 sec.)
                    createHiddenService(torNode,
                            Utils.findFreeSystemPort(),
                            servicePort,
                            hiddenServiceDescriptor -> {
                                Log.traceCall("hiddenService created");
                                TorNetworkNode.this.hiddenServiceDescriptor = hiddenServiceDescriptor;
                                nodeAddressProperty.set(new NodeAddress(hiddenServiceDescriptor.getFullAddress()));
                                startServer(hiddenServiceDescriptor.getServerSocket());
                                setupListeners.stream().forEach(SetupListener::onHiddenServicePublished);
                            });
                });
    }

    @Override
    protected Socket createSocket(NodeAddress peerNodeAddress) throws IOException {
        checkArgument(peerNodeAddress.hostName.endsWith(".onion"), "PeerAddress is not an onion address");

        return torNetworkNode.connectToHiddenService(peerNodeAddress.hostName, peerNodeAddress.port);
    }

    public Socks5Proxy getSocksProxy() {
        return torNetworkNode != null ? torNetworkNode.getSocksProxy() : null;
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
        executorService.submit(() -> {
            Utilities.setThreadName("torNetworkNodeShutDown");
            long ts = System.currentTimeMillis();
            log.debug("Shutdown torNetworkNode");
            try {
                if (torNetworkNode != null)
                    torNetworkNode.shutdown();
                log.debug("Shutdown torNetworkNode done after " + (System.currentTimeMillis() - ts) + " ms.");
            } catch (Throwable e) {
                log.error("Shutdown torNetworkNode failed with exception: " + e.getMessage());
                e.printStackTrace();
            } finally {
                UserThread.execute(() -> done.set(true));
            }
        });
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
        restartCounter++;
        if (restartCounter > MAX_RESTART_ATTEMPTS) {
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

    private void createTorNode(final File torDir, final Consumer<TorNode> resultHandler) {
        Log.traceCall();
        ListenableFuture<TorNode<JavaOnionProxyManager, JavaOnionProxyContext>> future = executorService.submit(() -> {
            Utilities.setThreadName("TorNetworkNode:CreateTorNode");
            long ts = System.currentTimeMillis();
            if (torDir.mkdirs())
                log.trace("Created directory for tor at {}", torDir.getAbsolutePath());
            TorNode<JavaOnionProxyManager, JavaOnionProxyContext> torNode = new JavaTorNode(torDir);
            log.debug("\n\n############################################################\n" +
                    "TorNode created:" +
                    "\nTook " + (System.currentTimeMillis() - ts) + " ms"
                    + "\n############################################################\n");
            return torNode;
        });
        Futures.addCallback(future, new FutureCallback<TorNode<JavaOnionProxyManager, JavaOnionProxyContext>>() {
            public void onSuccess(TorNode<JavaOnionProxyManager, JavaOnionProxyContext> torNode) {
                UserThread.execute(() -> resultHandler.accept(torNode));
            }

            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> {
                    log.error("TorNode creation failed with exception: " + throwable.getMessage());
                    restartTor(throwable.getMessage());
                });
            }
        });
    }

    private void createHiddenService(TorNode torNode, int localPort, int servicePort,
                                     Consumer<HiddenServiceDescriptor> resultHandler) {
        Log.traceCall();
        ListenableFuture<Object> future = executorService.submit(() -> {
            Utilities.setThreadName("TorNetworkNode:CreateHiddenService");
            {
                long ts = System.currentTimeMillis();
                HiddenServiceDescriptor hiddenServiceDescriptor = torNode.createHiddenService(localPort, servicePort);
                torNode.addHiddenServiceReadyListener(hiddenServiceDescriptor, descriptor -> {
                    log.debug("\n\n############################################################\n" +
                            "Hidden service published:" +
                            "\nAddress=" + descriptor.getFullAddress() +
                            "\nTook " + (System.currentTimeMillis() - ts) + " ms"
                            + "\n############################################################\n");

                    UserThread.execute(() -> resultHandler.accept(hiddenServiceDescriptor));
                });
                return null;
            }
        });
        Futures.addCallback(future, new FutureCallback<Object>() {
            public void onSuccess(Object hiddenServiceDescriptor) {
                log.debug("HiddenServiceDescriptor created. Wait for publishing.");
            }

            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> {
                    log.error("Hidden service creation failed");
                    restartTor(throwable.getMessage());
                });
            }
        });
    }
}
