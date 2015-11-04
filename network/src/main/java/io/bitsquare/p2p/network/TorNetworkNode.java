package io.bitsquare.p2p.network;

import com.google.common.util.concurrent.*;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyContext;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyManager;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.Utils;
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
import java.util.concurrent.*;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

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

    public TorNetworkNode(int port, File torDir) {
        super(port);

        this.torDir = torDir;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void start(@Nullable SetupListener setupListener) {
        if (setupListener != null)
            addSetupListener(setupListener);

        // executorService might have been shutdown before a restart, so we create a new one
        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("NetworkNode-" + port)
                .setDaemon(true)
                .build();
        executorService = MoreExecutors.listeningDecorator(new ThreadPoolExecutor(5, 50, 10L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(50), threadFactory));

        // Create the tor node (takes about 6 sec.)
        createTorNode(torDir, torNode -> {
            TorNetworkNode.this.torNode = torNode;

            setupListeners.stream().forEach(e -> UserThread.execute(() -> e.onTorNodeReady()));

            // Create Hidden Service (takes about 40 sec.)
            createHiddenService(torNode, port, hiddenServiceDescriptor -> {
                TorNetworkNode.this.hiddenServiceDescriptor = hiddenServiceDescriptor;

                startServer(hiddenServiceDescriptor.getServerSocket());
                Uninterruptibles.sleepUninterruptibly(500, TimeUnit.MILLISECONDS);

                setupListeners.stream().forEach(e -> UserThread.execute(() -> e.onHiddenServiceReady()));
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

        shutDownTimeoutTimer = new Timer();
        shutDownTimeoutTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Thread.currentThread().setName("ShutDownTimeoutTimer-" + new Random().nextInt(1000));
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

    ///////////////////////////////////////////////////////////////////////////////////////////
    // shutdown, restart
    ///////////////////////////////////////////////////////////////////////////////////////////

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
                UserThread.execute(() -> shutDownCompleteHandler.run());
            }

            @Override
            public void onFailure(Throwable throwable) {
                throwable.printStackTrace();
                log.error("Shutdown executorService failed with exception: " + throwable.getMessage());
                UserThread.execute(() -> shutDownCompleteHandler.run());
            }
        });
    }

    private void restartTor() {
        restartCounter++;
        if (restartCounter <= MAX_RESTART_ATTEMPTS) {
            shutDown(() -> {
                Uninterruptibles.sleepUninterruptibly(WAIT_BEFORE_RESTART, TimeUnit.MILLISECONDS);
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
            Thread.currentThread().setName("CreateTorNode-" + new Random().nextInt(1000));
            long ts = System.currentTimeMillis();
            if (torDir.mkdirs())
                log.trace("Created directory for tor");

            log.trace("Create TorNode");
            TorNode<JavaOnionProxyManager, JavaOnionProxyContext> torNode1 = new TorNode<JavaOnionProxyManager, JavaOnionProxyContext>(
                    torDir) {
            };
            log.info("\n\n############################################################\n" +
                    "TorNode created:" +
                    "\nTook " + (System.currentTimeMillis() - ts) + " ms"
                    + "\n############################################################\n");
            return torNode1;
        };
        ListenableFuture<TorNode<JavaOnionProxyManager, JavaOnionProxyContext>> future = executorService.submit(task);
        Futures.addCallback(future, new FutureCallback<TorNode<JavaOnionProxyManager, JavaOnionProxyContext>>() {
            public void onSuccess(TorNode<JavaOnionProxyManager, JavaOnionProxyContext> torNode) {
                resultHandler.accept(torNode);
            }

            public void onFailure(Throwable throwable) {
                log.error("TorNode creation failed with exception: " + throwable.getMessage());
                restartTor();
            }
        });
    }

    private void createHiddenService(final TorNode torNode, final int port,
                                     final Consumer<HiddenServiceDescriptor> resultHandler) {
        Callable<HiddenServiceDescriptor> task = () -> {
            Thread.currentThread().setName("CreateHiddenService-" + new Random().nextInt(1000));
            long ts = System.currentTimeMillis();
            log.debug("Create hidden service");
            HiddenServiceDescriptor hiddenServiceDescriptor = torNode.createHiddenService(port);
            log.info("\n\n############################################################\n" +
                    "Hidden service created:" +
                    "\nAddress=" + hiddenServiceDescriptor.getFullAddress() +
                    "\nTook " + (System.currentTimeMillis() - ts) + " ms"
                    + "\n############################################################\n");

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


    @Override
    protected Socket getSocket(Address peerAddress) throws IOException {
        checkArgument(peerAddress.hostName.endsWith(".onion"), "PeerAddress is not an onion address");

        return torNode.connectToHiddenService(peerAddress.hostName, peerAddress.port);
    }


}
