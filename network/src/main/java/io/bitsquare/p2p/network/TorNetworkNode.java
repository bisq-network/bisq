package io.bitsquare.p2p.network;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyContext;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyManager;
import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.Utils;
import io.nucleo.net.HiddenServiceDescriptor;
import io.nucleo.net.JavaTorNode;
import io.nucleo.net.TorNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.Timer;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;

// Run in UserThread
public class TorNetworkNode extends NetworkNode {
    private static final Logger log = LoggerFactory.getLogger(TorNetworkNode.class);

    private static final int MAX_RESTART_ATTEMPTS = 3;
    private static final int WAIT_BEFORE_RESTART = 2000;
    private static final long SHUT_DOWN_TIMEOUT = 5000;

    private final File torDir;
    private TorNode torNetworkNode;
    private HiddenServiceDescriptor hiddenServiceDescriptor;
    private Timer shutDownTimeoutTimer;
    private int restartCounter;
    private Runnable shutDownCompleteHandler;
    private boolean torShutDownComplete, networkNodeShutDownDoneComplete;


    // /////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    // /////////////////////////////////////////////////////////////////////////////////////////

    public TorNetworkNode(int servicePort, File torDir) {
        super(servicePort);
        Log.traceCall();
        this.torDir = torDir;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void start(@Nullable SetupListener setupListener) {
        Log.traceCall();
        if (setupListener != null)
            addSetupListener(setupListener);

        createExecutorService();

        // Create the tor node (takes about 6 sec.)
        createTorNode(torDir, torNode -> {
            Log.traceCall("torNode created");
            TorNetworkNode.this.torNetworkNode = torNode;

            setupListeners.stream().forEach(e -> e.onTorNodeReady());

            // Create Hidden Service (takes about 40 sec.)
            createHiddenService(torNode,
                    Utils.findFreeSystemPort(),
                    servicePort,
                    hiddenServiceDescriptor -> {
                        Log.traceCall("hiddenService created");
                        TorNetworkNode.this.hiddenServiceDescriptor = hiddenServiceDescriptor;

                        startServer(hiddenServiceDescriptor.getServerSocket());
                        setupListeners.stream().forEach(e -> e.onHiddenServicePublished());
                    });
        });
    }

    @Override
    @Nullable
    public NodeAddress getNodeAddress() {
        if (hiddenServiceDescriptor != null)
            return new NodeAddress(hiddenServiceDescriptor.getFullAddress());
        else
            return null;
    }

    @Override
    protected Socket createSocket(NodeAddress peerNodeAddress) throws IOException {
        Log.traceCall();
        checkArgument(peerNodeAddress.hostName.endsWith(".onion"), "PeerAddress is not an onion address");

        return torNetworkNode.connectToHiddenService(peerNodeAddress.hostName, peerNodeAddress.port);
    }

    //TODO simplify
    public void shutDown(Runnable shutDownCompleteHandler) {
        Log.traceCall();
        this.shutDownCompleteHandler = shutDownCompleteHandler;

        shutDownTimeoutTimer = UserThread.runAfter(() -> {
            log.error("A timeout occurred at shutDown");
            shutDownExecutorService();
        }, SHUT_DOWN_TIMEOUT, TimeUnit.MILLISECONDS);

        if (executorService != null) {
            executorService.submit(() -> {
                UserThread.execute(() -> {
                    // We want to stay in UserThread
                    super.shutDown(() -> {
                        networkNodeShutDownDoneComplete = true;
                        if (torShutDownComplete)
                            shutDownExecutorService();
                    });
                });
            });
        } else {
            log.error("executorService must not be null at shutDown");
        }
        executorService.submit(() -> {
            Utilities.setThreadName("NetworkNode:torNodeShutdown");
            try {
                long ts = System.currentTimeMillis();
                log.info("Shutdown torNode");
                // Might take a bit so we use a thread
                if (torNetworkNode != null)
                    torNetworkNode.shutdown();
                log.info("Shutdown torNode done after " + (System.currentTimeMillis() - ts) + " ms.");
                UserThread.execute(() -> {
                    torShutDownComplete = true;
                    if (networkNodeShutDownDoneComplete)
                        shutDownExecutorService();
                });
            } catch (Throwable e) {
                UserThread.execute(() -> {
                    log.error("Shutdown torNode failed with exception: " + e.getMessage());
                    e.printStackTrace();
                    // We want to switch to UserThread
                    shutDownExecutorService();
                });
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // shutdown, restart
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void shutDownExecutorService() {
        Log.traceCall();
        shutDownTimeoutTimer.cancel();
        new Thread(() -> {
            Utilities.setThreadName("NetworkNode:shutDownExecutorService");
            try {
                long ts = System.currentTimeMillis();
                log.debug("Shutdown executorService");
                MoreExecutors.shutdownAndAwaitTermination(executorService, 500, TimeUnit.MILLISECONDS);
                log.debug("Shutdown executorService done after " + (System.currentTimeMillis() - ts) + " ms.");
                log.info("Shutdown completed");
                shutDownCompleteHandler.run();
            } catch (Throwable t) {
                log.error("Shutdown executorService failed with exception: " + t.getMessage());
                t.printStackTrace();
                shutDownCompleteHandler.run();
            }
        }).start();
    }

    private void restartTor() {
        Log.traceCall();
        restartCounter++;
        if (restartCounter <= MAX_RESTART_ATTEMPTS) {
            shutDown(() -> UserThread.runAfter(() -> {
                log.warn("We restart tor as starting tor failed.");
                start(null);
            }, WAIT_BEFORE_RESTART, TimeUnit.MILLISECONDS));
        } else {
            String msg = "We tried to restart tor " + restartCounter
                    + " times, but we failed to get tor running. We give up now.";
            log.error(msg);
            // TODO display better error msg
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
            try {
                long ts = System.currentTimeMillis();
                if (torDir.mkdirs())
                    log.trace("Created directory for tor");

                log.info("TorDir = " + torDir.getAbsolutePath());
                log.trace("Create TorNode");
                TorNode<JavaOnionProxyManager, JavaOnionProxyContext> torNode = new JavaTorNode(torDir);
                log.info("\n\n############################################################\n" +
                        "TorNode created:" +
                        "\nTook " + (System.currentTimeMillis() - ts) + " ms"
                        + "\n############################################################\n");
                return torNode;
            } catch (Throwable t) {
                throw t;
            }
        });
        Futures.addCallback(future, new FutureCallback<TorNode<JavaOnionProxyManager, JavaOnionProxyContext>>() {
            public void onSuccess(TorNode<JavaOnionProxyManager, JavaOnionProxyContext> torNode) {
                UserThread.execute(() -> resultHandler.accept(torNode));
            }

            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> {
                    log.error("TorNode creation failed with exception: " + throwable.getMessage());
                    restartTor();
                });
            }
        });
    }

    private void createHiddenService(TorNode torNode, int localPort, int servicePort,
                                     Consumer<HiddenServiceDescriptor> resultHandler) {
        Log.traceCall();
        ListenableFuture<Object> future = executorService.submit(() -> {
            Utilities.setThreadName("TorNetworkNode:CreateHiddenService");
            try {
                long ts = System.currentTimeMillis();
                log.debug("Create hidden service");
                HiddenServiceDescriptor hiddenServiceDescriptor = torNode.createHiddenService(localPort, servicePort);

                torNode.addHiddenServiceReadyListener(hiddenServiceDescriptor, descriptor -> {
                    log.info("\n\n############################################################\n" +
                            "Hidden service published:" +
                            "\nAddress=" + descriptor.getFullAddress() +
                            "\nTook " + (System.currentTimeMillis() - ts) + " ms"
                            + "\n############################################################\n");

                    UserThread.execute(() -> resultHandler.accept(hiddenServiceDescriptor));
                });

                return null;
            } catch (Throwable t) {
                throw t;
            }
        });
        Futures.addCallback(future, new FutureCallback<Object>() {
            public void onSuccess(Object hiddenServiceDescriptor) {
                log.debug("HiddenServiceDescriptor created. Wait for publishing.");
            }

            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> {
                    log.error("Hidden service creation failed");
                    restartTor();
                });
            }
        });
    }
}
