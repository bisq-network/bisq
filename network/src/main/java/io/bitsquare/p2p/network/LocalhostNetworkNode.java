package io.bitsquare.p2p.network;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyContext;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyManager;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.Address;
import io.nucleo.net.HiddenServiceDescriptor;
import io.nucleo.net.TorNode;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class LocalhostNetworkNode extends NetworkNode {
    private static final Logger log = LoggerFactory.getLogger(LocalhostNetworkNode.class);

    private static int simulateTorDelayTorNode = 1 * 100;
    private static int simulateTorDelayHiddenService = 2 * 100;
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
    }

    @Override
    public void start(@Nullable SetupListener setupListener) {
        if (setupListener != null) addSetupListener(setupListener);

        createExecutor();

        //Tor delay simulation
        createTorNode(torNode -> {
            setupListeners.stream().forEach(e -> e.onTorNodeReady());

            // Create Hidden Service (takes about 40 sec.)
            createHiddenService(hiddenServiceDescriptor -> {
                try {
                    startServer(new ServerSocket(port));
                } catch (BindException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                address = new Address("localhost", port);


                setupListeners.stream().forEach(e -> e.onHiddenServicePublished());
            });
        });
    }


    @Override
    @Nullable
    public Address getAddress() {
        return address;
    }

    @Override
    protected Socket getSocket(Address peerAddress) throws IOException {
        return new Socket(peerAddress.hostName, peerAddress.port);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Tor delay simulation
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createTorNode(final Consumer<TorNode> resultHandler) {
        ListenableFuture<TorNode<JavaOnionProxyManager, JavaOnionProxyContext>> future = executorService.submit(() -> {
            Thread.currentThread().setName("NetworkNode:CreateTorNode-" + new Random().nextInt(1000));
            try {
                long ts = System.currentTimeMillis();
                if (simulateTorDelayTorNode > 0)
                    Uninterruptibles.sleepUninterruptibly(simulateTorDelayTorNode, TimeUnit.MILLISECONDS);

                log.info("\n\n############################################################\n" +
                        "TorNode created [simulation]:" +
                        "\nTook " + (System.currentTimeMillis() - ts) + " ms"
                        + "\n############################################################\n");
                return null;
            } catch (Throwable t) {
                throw t;
            }
        });
        Futures.addCallback(future, new FutureCallback<TorNode<JavaOnionProxyManager, JavaOnionProxyContext>>() {
            public void onSuccess(TorNode<JavaOnionProxyManager, JavaOnionProxyContext> torNode) {
                UserThread.execute(() -> resultHandler.accept(torNode));
            }

            public void onFailure(Throwable throwable) {
                log.error("[simulation] TorNode creation failed");
            }
        });
    }

    private void createHiddenService(final Consumer<HiddenServiceDescriptor> resultHandler) {
        ListenableFuture<HiddenServiceDescriptor> future = executorService.submit(() -> {
            Thread.currentThread().setName("NetworkNode:CreateHiddenService-" + new Random().nextInt(1000));
            try {
                long ts = System.currentTimeMillis();
                if (simulateTorDelayHiddenService > 0)
                    Uninterruptibles.sleepUninterruptibly(simulateTorDelayHiddenService, TimeUnit.MILLISECONDS);

                log.info("\n\n############################################################\n" +
                        "Hidden service created [simulation]:" +
                        "\nTook " + (System.currentTimeMillis() - ts) + " ms"
                        + "\n############################################################\n");
                return null;
            } catch (Throwable t) {
                throw t;
            }
        });
        Futures.addCallback(future, new FutureCallback<HiddenServiceDescriptor>() {
            public void onSuccess(HiddenServiceDescriptor hiddenServiceDescriptor) {
                UserThread.execute(() -> resultHandler.accept(hiddenServiceDescriptor));
            }

            public void onFailure(Throwable throwable) {
                log.error("[simulation] Hidden service creation failed");
            }
        });
    }

}
