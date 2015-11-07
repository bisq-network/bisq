package io.bitsquare.p2p.network;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyContext;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyManager;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.Utils;
import io.nucleo.net.HiddenServiceDescriptor;
import io.nucleo.net.TorNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.Random;
import java.util.Timer;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;

public class TorNetworkNode extends NetworkNode {
    private static final Logger log = LoggerFactory.getLogger(TorNetworkNode.class);
    private static final Random random = new Random();

    private static final long TIMEOUT = 5000;
    private static final int MAX_ERRORS_BEFORE_RESTART = 3;
    private static final int MAX_RESTART_ATTEMPTS = 3;
    private static final int WAIT_BEFORE_RESTART = 2000;
    private static final long SHUT_DOWN_TIMEOUT = 5000;

    private final File torDir;
    private TorNode torNode;
    private HiddenServiceDescriptor hiddenServiceDescriptor;
    private Timer shutDownTimeoutTimer;
    private long nonce;
    private int errorCounter;
    private int restartCounter;
    private Runnable shutDownCompleteHandler;
    private boolean torShutDownComplete, networkNodeShutDownDoneComplete;


    // /////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    // /////////////////////////////////////////////////////////////////////////////////////////

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

        createExecutor();

        // Create the tor node (takes about 6 sec.)
        createTorNode(torDir, torNode -> {
            TorNetworkNode.this.torNode = torNode;

            setupListeners.stream().forEach(e -> UserThread.execute(() -> e.onTorNodeReady()));

            // Create Hidden Service (takes about 40 sec.)
            createHiddenService(torNode, Utils.findFreeSystemPort(), port, hiddenServiceDescriptor -> {
                TorNetworkNode.this.hiddenServiceDescriptor = hiddenServiceDescriptor;

                startServer(hiddenServiceDescriptor.getServerSocket());
                UserThread.runAfter(() -> setupListeners.stream().forEach(e -> e.onHiddenServicePublished()),
                        500, TimeUnit.MILLISECONDS);
            });
        });
    }

    @Override
    @Nullable
    public Address getAddress() {
        if (hiddenServiceDescriptor != null)
            return new Address(hiddenServiceDescriptor.getFullAddress());
        else
            return null;
    }

    public void shutDown(Runnable shutDownCompleteHandler) {
        log.info("Shutdown TorNetworkNode");
        this.shutDownCompleteHandler = shutDownCompleteHandler;

        shutDownTimeoutTimer = UserThread.runAfter(() -> {
            log.error("A timeout occurred at shutDown");
            shutDownExecutorService();
        }, SHUT_DOWN_TIMEOUT, TimeUnit.MILLISECONDS);

        if (executorService != null) {
            executorService.submit(() -> super.shutDown(() -> {
                        networkNodeShutDownDoneComplete = true;
                        if (torShutDownComplete)
                            shutDownExecutorService();
                    }
            ));
        } else {
            log.error("executorService must not be null at shutDown");
        }
        ListenableFuture<?> future2 = executorService.submit(() -> {
            try {
                long ts = System.currentTimeMillis();
                log.info("Shutdown torNode");
                if (torNode != null)
                    torNode.shutdown();
                log.info("Shutdown torNode done after " + (System.currentTimeMillis() - ts) + " ms.");
            } catch (Throwable e) {
                e.printStackTrace();
                log.error("Shutdown torNode failed with exception: " + e.getMessage());
                shutDownExecutorService();
            }
        });
        Futures.addCallback(future2, new FutureCallback<Object>() {
            @Override
            public void onSuccess(Object o) {
                torShutDownComplete = true;
                if (networkNodeShutDownDoneComplete)
                    shutDownExecutorService();
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                throwable.printStackTrace();
                log.error("Shutdown torNode failed with exception: " + throwable.getMessage());
                shutDownExecutorService();
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // shutdown, restart
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void shutDownExecutorService() {
        shutDownTimeoutTimer.cancel();
        new Thread(() -> {
            Thread.currentThread().setName("NetworkNode:shutDownExecutorService-" + new Random().nextInt(1000));
            try {
                long ts = System.currentTimeMillis();
                log.info("Shutdown executorService");
                MoreExecutors.shutdownAndAwaitTermination(executorService, 500, TimeUnit.MILLISECONDS);
                log.info("Shutdown executorService done after " + (System.currentTimeMillis() - ts) + " ms.");

                log.info("Shutdown completed");
                UserThread.execute(() -> shutDownCompleteHandler.run());
            } catch (Throwable t) {
                t.printStackTrace();
                log.error("Shutdown executorService failed with exception: " + t.getMessage());
                UserThread.execute(() -> shutDownCompleteHandler.run());
            }
        }).start();
    }

    private void restartTor() {
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
        ListenableFuture<TorNode<JavaOnionProxyManager, JavaOnionProxyContext>> future = executorService.submit(() -> {
            Thread.currentThread().setName("NetworkNode:CreateTorNode-" + new Random().nextInt(1000));
            try {
                long ts = System.currentTimeMillis();
                if (torDir.mkdirs())
                    log.trace("Created directory for tor");

                log.info("TorDir = " + torDir.getAbsolutePath());
                log.trace("Create TorNode");
                TorNode<JavaOnionProxyManager, JavaOnionProxyContext> torNode1 = new TorNode<JavaOnionProxyManager, JavaOnionProxyContext>(
                        torDir) {
                };
                log.info("\n\n############################################################\n" +
                        "TorNode created:" +
                        "\nTook " + (System.currentTimeMillis() - ts) + " ms"
                        + "\n############################################################\n");
                return torNode1;
            } catch (Throwable t) {
                throw t;
            }
        });
        Futures.addCallback(future, new FutureCallback<TorNode<JavaOnionProxyManager, JavaOnionProxyContext>>() {
            public void onSuccess(TorNode<JavaOnionProxyManager, JavaOnionProxyContext> torNode) {
                resultHandler.accept(torNode);
            }

            public void onFailure(@NotNull Throwable throwable) {
                log.error("TorNode creation failed with exception: " + throwable.getMessage());
                restartTor();
            }
        });
    }

    private void createHiddenService(TorNode torNode, int localPort, int servicePort,
                                     Consumer<HiddenServiceDescriptor> resultHandler) {
        ListenableFuture<HiddenServiceDescriptor> future = executorService.submit(() -> {
            Thread.currentThread().setName("NetworkNode:CreateHiddenService-" + new Random().nextInt(1000));
            try {
                long ts = System.currentTimeMillis();
                log.debug("Create hidden service");
                HiddenServiceDescriptor hiddenServiceDescriptor = torNode.createHiddenService(localPort, servicePort);
                log.info("\n\n############################################################\n" +
                        "Hidden service created:" +
                        "\nAddress=" + hiddenServiceDescriptor.getFullAddress() +
                        "\nTook " + (System.currentTimeMillis() - ts) + " ms"
                        + "\n############################################################\n");

                return hiddenServiceDescriptor;
            } catch (Throwable t) {
                throw t;
            }
        });
        Futures.addCallback(future, new FutureCallback<HiddenServiceDescriptor>() {
            public void onSuccess(HiddenServiceDescriptor hiddenServiceDescriptor) {
                resultHandler.accept(hiddenServiceDescriptor);
            }

            public void onFailure(@NotNull Throwable throwable) {
                log.error("Hidden service creation failed");
                restartTor();
            }
        });
    }


    @Override
    protected Socket getSocket(Address peerAddress) throws IOException {
        checkArgument(peerAddress.hostName.endsWith(".onion"), "PeerAddress is not an onion address");

        return torNode.connectToHiddenService(peerAddress.hostName, peerAddress.port);
    }


}
