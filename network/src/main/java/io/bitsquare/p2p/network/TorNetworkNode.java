package io.bitsquare.p2p.network;

import com.google.common.util.concurrent.*;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyContext;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyManager;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.Utils;
import io.bitsquare.p2p.network.messages.SelfTestMessage;
import io.nucleo.net.HiddenServiceDescriptor;
import io.nucleo.net.TorNode;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class TorNetworkNode extends NetworkNode {
    private static final Logger log = LoggerFactory.getLogger(TorNetworkNode.class);
    private static final Random random = new Random();

    private static final long TIMEOUT = 5000;
    private static final long SELF_TEST_INTERVAL = 10 * 60 * 1000;
    private static final int MAX_ERRORS_BEFORE_RESTART = 3;
    private static final int MAX_RESTART_ATTEMPTS = 3;
    private static final int WAIT_BEFORE_RESTART = 2000;
    private static final long SHUT_DOWN_TIMEOUT = 5000;

    private final File torDir;
    private TorNode torNode;
    private HiddenServiceDescriptor hiddenServiceDescriptor;
    private Timer shutDownTimeoutTimer, selfTestTimer, selfTestTimeoutTimer;
    private TimerTask selfTestTimeoutTask, selfTestTask;
    private AtomicBoolean selfTestRunning = new AtomicBoolean(false);
    private int nonce;
    private int errorCounter;
    private int restartCounter;
    private Runnable shutDownCompleteHandler;
    private boolean torShutDownComplete, networkNodeShutDownDoneComplete;


    // /////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    // /////////////////////////////////////////////////////////////////////////////////////////

    public TorNetworkNode(int port, File torDir) {
        super(port);

        this.torDir = torDir;

        selfTestTimeoutTask = new TimerTask() {
            @Override
            public void run() {
                log.error("A timeout occurred at self test");
                stopSelfTestTimer();
                selfTestFailed();
            }
        };

        selfTestTask = new TimerTask() {
            @Override
            public void run() {
                stopTimeoutTimer();
                if (selfTestRunning.get()) {
                    log.debug("running self test");
                    selfTestTimeoutTimer = new Timer();
                    selfTestTimeoutTimer.schedule(selfTestTimeoutTask, TIMEOUT);
                    // might be interrupted by timeout task
                    if (selfTestRunning.get()) {
                        nonce = random.nextInt();
                        log.trace("send msg with nonce " + nonce);

                        try {
                            SettableFuture<Connection> future = sendMessage(new Address(hiddenServiceDescriptor.getFullAddress()), new SelfTestMessage(nonce));
                            Futures.addCallback(future, new FutureCallback<Connection>() {
                                @Override
                                public void onSuccess(Connection connection) {
                                    log.trace("Sending self test message succeeded");
                                }

                                @Override
                                public void onFailure(Throwable throwable) {
                                    log.error("Error at sending self test message. Exception = " + throwable);
                                    stopTimeoutTimer();
                                    throwable.printStackTrace();
                                    selfTestFailed();
                                }
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        };

        addMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message, Connection connection) {
                if (message instanceof SelfTestMessage) {
                    if (((SelfTestMessage) message).nonce == nonce) {
                        runSelfTest();
                    } else {
                        log.error("Nonce not matching our challenge. That should never happen.");
                        selfTestFailed();
                    }
                }
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void start(@Nullable SetupListener setupListener) {
        if (setupListener != null) addSetupListener(setupListener);

        // executorService might have been shutdown before a restart, so we create a new one
        executorService = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());

        // Create the tor node (takes about 6 sec.)
        createTorNode(torDir, torNode -> {
            TorNetworkNode.this.torNode = torNode;

            setupListeners.stream().forEach(e -> e.onTorNodeReady());

            // Create Hidden Service (takes about 40 sec.)
            createHiddenService(torNode, port, hiddenServiceDescriptor -> {
                TorNetworkNode.this.hiddenServiceDescriptor = hiddenServiceDescriptor;

                startServer(hiddenServiceDescriptor.getServerSocket());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }

                setupListeners.stream().forEach(e -> e.onHiddenServiceReady());

                // we are ready. so we start our periodic self test if our HS is available
                // startSelfTest();
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
        checkNotNull(executorService, "executorService must not be null");

        selfTestRunning.set(false);
        stopSelfTestTimer();

        shutDownTimeoutTimer = new Timer();
        shutDownTimeoutTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                log.error("A timeout occurred at shutDown");
                shutDownExecutorService();
            }
        }, SHUT_DOWN_TIMEOUT);

        executorService.submit(() -> super.shutDown(() -> {
            networkNodeShutDownDoneComplete = true;
            if (torShutDownComplete)
                shutDownExecutorService();
        }));

        ListenableFuture<?> future2 = executorService.submit(() -> {
            long ts = System.currentTimeMillis();
            log.info("Shutdown torNode");
            try {
                if (torNode != null)
                    torNode.shutdown();
                log.info("Shutdown torNode done after " + (System.currentTimeMillis() - ts) + " ms.");
            } catch (IOException e) {
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
            public void onFailure(Throwable throwable) {
                throwable.printStackTrace();
                log.error("Shutdown torNode failed with exception: " + throwable.getMessage());
                shutDownExecutorService();
            }
        });
    }

    // /////////////////////////////////////////////////////////////////////////////////////////
    // shutdown, restart
    // /////////////////////////////////////////////////////////////////////////////////////////

    private void shutDownExecutorService() {
        shutDownTimeoutTimer.cancel();
        ListenableFuture<?> future = executorService.submit(() -> {
            long ts = System.currentTimeMillis();
            log.info("Shutdown executorService");
            Utils.shutDownExecutorService(executorService);
            log.info("Shutdown executorService done after " + (System.currentTimeMillis() - ts) + " ms.");
        });
        Futures.addCallback(future, new FutureCallback<Object>() {
            @Override
            public void onSuccess(Object o) {
                log.info("Shutdown completed");
                new Thread(shutDownCompleteHandler).start();
            }

            @Override
            public void onFailure(Throwable throwable) {
                throwable.printStackTrace();
                log.error("Shutdown executorService failed with exception: " + throwable.getMessage());
                new Thread(shutDownCompleteHandler).start();
            }
        });
    }

    private void restartTor() {
        restartCounter++;
        if (restartCounter <= MAX_RESTART_ATTEMPTS) {
            shutDown(() -> {
                try {
                    Thread.sleep(WAIT_BEFORE_RESTART);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt();
                }
                log.warn("We restart tor as too many self tests failed.");
                start(null);
            });
        } else {
            log.error("We tried to restart tor " + restartCounter
                    + " times, but we failed to get tor running. We give up now.");
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // create tor
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createTorNode(final File torDir, final Consumer<TorNode> resultHandler) {
        Callable<TorNode<JavaOnionProxyManager, JavaOnionProxyContext>> task = () -> {
            long ts = System.currentTimeMillis();
            if (torDir.mkdirs())
                log.trace("Created directory for tor");

            log.trace("Create TorNode");
            TorNode<JavaOnionProxyManager, JavaOnionProxyContext> torNode1 = new TorNode<JavaOnionProxyManager, JavaOnionProxyContext>(
                    torDir) {
            };
            log.trace("\n\n##### TorNode created. Took " + (System.currentTimeMillis() - ts) + " ms\n\n");
            return torNode1;
        };
        ListenableFuture<TorNode<JavaOnionProxyManager, JavaOnionProxyContext>> future = executorService.submit(task);
        Futures.addCallback(future, new FutureCallback<TorNode<JavaOnionProxyManager, JavaOnionProxyContext>>() {
            public void onSuccess(TorNode<JavaOnionProxyManager, JavaOnionProxyContext> torNode) {
                resultHandler.accept(torNode);
            }

            public void onFailure(Throwable throwable) {
                log.error("TorNode creation failed");
                restartTor();
            }
        });
    }

    private void createHiddenService(final TorNode torNode, final int port,
                                     final Consumer<HiddenServiceDescriptor> resultHandler) {
        Callable<HiddenServiceDescriptor> task = () -> {
            long ts = System.currentTimeMillis();
            log.debug("Create hidden service");
            HiddenServiceDescriptor hiddenServiceDescriptor = torNode.createHiddenService(port);
            log.debug("\n\n##### Hidden service created. Address = " + hiddenServiceDescriptor.getFullAddress() + ". Took " + (System.currentTimeMillis() - ts) + " ms\n\n");

            return hiddenServiceDescriptor;
        };
        ListenableFuture<HiddenServiceDescriptor> future = executorService.submit(task);
        Futures.addCallback(future, new FutureCallback<HiddenServiceDescriptor>() {
            public void onSuccess(HiddenServiceDescriptor hiddenServiceDescriptor) {
                resultHandler.accept(hiddenServiceDescriptor);
            }

            public void onFailure(Throwable throwable) {
                log.error("Hidden service creation failed");
                restartTor();
            }
        });
    }


    // /////////////////////////////////////////////////////////////////////////////////////////
    // Self test
    // /////////////////////////////////////////////////////////////////////////////////////////

    private void startSelfTest() {
        selfTestRunning.set(true);
        //addListener(messageListener);
        runSelfTest();
    }

    private void runSelfTest() {
        stopSelfTestTimer();
        selfTestTimer = new Timer();
        selfTestTimer.schedule(selfTestTask, SELF_TEST_INTERVAL);
    }

    private void stopSelfTestTimer() {
        stopTimeoutTimer();
        if (selfTestTimer != null)
            selfTestTimer.cancel();
    }

    private void stopTimeoutTimer() {
        if (selfTestTimeoutTimer != null)
            selfTestTimeoutTimer.cancel();
    }

    private void selfTestFailed() {
        errorCounter++;
        log.warn("Self test failed. Already " + errorCounter + " failure(s). Max. errors before restart: "
                + MAX_ERRORS_BEFORE_RESTART);
        if (errorCounter >= MAX_ERRORS_BEFORE_RESTART)
            restartTor();
        else
            runSelfTest();
    }

    @Override
    protected Socket getSocket(Address peerAddress) throws IOException {
        checkArgument(peerAddress.hostName.endsWith(".onion"), "PeerAddress is not an onion address");

        return torNode.connectToHiddenService(peerAddress.hostName, peerAddress.port);
    }


}
