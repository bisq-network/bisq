package io.bitsquare.p2p.network;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyContext;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyManager;
import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.p2p.Address;
import io.nucleo.net.HiddenServiceDescriptor;
import io.nucleo.net.TorNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

// Run in UserThread
public class LocalhostNetworkNode extends NetworkNode {
    private static final Logger log = LoggerFactory.getLogger(LocalhostNetworkNode.class);

    private static volatile int simulateTorDelayTorNode = 2 * 100;
    private static volatile int simulateTorDelayHiddenService = 2 * 100;
    private Address address;

    public static void setSimulateTorDelayTorNode(int simulateTorDelayTorNode) {
        LocalhostNetworkNode.simulateTorDelayTorNode = simulateTorDelayTorNode;
    }

    public static void setSimulateTorDelayHiddenService(int simulateTorDelayHiddenService) {
        LocalhostNetworkNode.simulateTorDelayHiddenService = simulateTorDelayHiddenService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public LocalhostNetworkNode(int port) {
        super(port);
        Log.traceCall();
    }

    @Override
    public void start(@Nullable SetupListener setupListener) {
        Log.traceCall();
        if (setupListener != null) addSetupListener(setupListener);

        createExecutorService();

        //Tor delay simulation
        createTorNode(torNode -> {
            Log.traceCall("torNode created");
            setupListeners.stream().forEach(e -> e.onTorNodeReady());

            // Create Hidden Service (takes about 40 sec.)
            createHiddenService(hiddenServiceDescriptor -> {
                Log.traceCall("hiddenService created");
                try {
                    startServer(new ServerSocket(servicePort));
                } catch (IOException e) {
                    e.printStackTrace();
                    log.error("Exception at startServer: " + e.getMessage());
                }

                address = new Address("localhost", servicePort);

                setupListeners.stream().forEach(e -> e.onHiddenServicePublished());
            });
        });
    }


    @Override
    @Nullable
    public Address getAddress() {
        Log.traceCall();
        return address;
    }

    // Called from NetworkNode thread
    @Override
    protected Socket createSocket(Address peerAddress) throws IOException {
        Log.traceCall();
        return new Socket(peerAddress.hostName, peerAddress.port);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Tor delay simulation
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createTorNode(final Consumer<TorNode> resultHandler) {
        Log.traceCall();
        ListenableFuture<TorNode<JavaOnionProxyManager, JavaOnionProxyContext>> future = executorService.submit(() -> {
            Utilities.setThreadName("NetworkNode:CreateTorNode");
            try {
                long ts = System.currentTimeMillis();
                if (simulateTorDelayTorNode > 0)
                    Uninterruptibles.sleepUninterruptibly(simulateTorDelayTorNode, TimeUnit.MILLISECONDS);

                log.info("\n\n############################################################\n" +
                        "TorNode created [simulation]:" +
                        "\nTook " + (System.currentTimeMillis() - ts) + " ms"
                        + "\n############################################################\n");
                // as we are simulating we return null
                return null;
            } catch (Throwable t) {
                throw t;
            }
        });
        Futures.addCallback(future, new FutureCallback<TorNode<JavaOnionProxyManager, JavaOnionProxyContext>>() {
            public void onSuccess(TorNode<JavaOnionProxyManager, JavaOnionProxyContext> torNode) {
                UserThread.execute(() -> {
                    // as we are simulating we return null
                    resultHandler.accept(null);
                });
            }

            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> {
                    log.error("[simulation] TorNode creation failed. " + throwable.getMessage());
                    throwable.printStackTrace();
                });
            }
        });
    }

    private void createHiddenService(final Consumer<HiddenServiceDescriptor> resultHandler) {
        Log.traceCall();
        ListenableFuture<HiddenServiceDescriptor> future = executorService.submit(() -> {
            Utilities.setThreadName("NetworkNode:CreateHiddenService");
            try {
                long ts = System.currentTimeMillis();
                if (simulateTorDelayHiddenService > 0)
                    Uninterruptibles.sleepUninterruptibly(simulateTorDelayHiddenService, TimeUnit.MILLISECONDS);

                log.info("\n\n############################################################\n" +
                        "Hidden service published [simulation]:" +
                        "\nTook " + (System.currentTimeMillis() - ts) + " ms"
                        + "\n############################################################\n");
                // as we are simulating we return null
                return null;
            } catch (Throwable t) {
                throw t;
            }
        });
        Futures.addCallback(future, new FutureCallback<HiddenServiceDescriptor>() {
            public void onSuccess(HiddenServiceDescriptor hiddenServiceDescriptor) {
                UserThread.execute(() -> {
                    // as we are simulating we return null
                    resultHandler.accept(null);
                });
            }

            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> {
                    log.error("[simulation] Hidden service creation failed. " + throwable.getMessage());
                    throwable.printStackTrace();
                });
            }
        });
    }
}
