package io.bitsquare.p2p.network;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyContext;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyManager;
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
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class LocalhostNetworkNode extends NetworkNode {
    private static final Logger log = LoggerFactory.getLogger(LocalhostNetworkNode.class);

    private static int simulateTorDelayTorNode = 0;
    private static int simulateTorDelayHiddenService = 0;
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

        executorService = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());

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


                setupListeners.stream().forEach(e -> e.onHiddenServiceReady());
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
        Callable<TorNode<JavaOnionProxyManager, JavaOnionProxyContext>> task = () -> {
            long ts = System.currentTimeMillis();
            log.trace("[simulation] Create TorNode");
            if (simulateTorDelayTorNode > 0) Thread.sleep(simulateTorDelayTorNode);
            log.trace("\n\n##### TorNode created [simulation]. Took " + (System.currentTimeMillis() - ts) + " ms\n\n");
            return null;
        };
        ListenableFuture<TorNode<JavaOnionProxyManager, JavaOnionProxyContext>> future = executorService.submit(task);
        Futures.addCallback(future, new FutureCallback<TorNode<JavaOnionProxyManager, JavaOnionProxyContext>>() {
            public void onSuccess(TorNode<JavaOnionProxyManager, JavaOnionProxyContext> torNode) {
                resultHandler.accept(torNode);
            }

            public void onFailure(Throwable throwable) {
                log.error("[simulation] TorNode creation failed");
            }
        });
    }

    private void createHiddenService(final Consumer<HiddenServiceDescriptor> resultHandler) {
        Callable<HiddenServiceDescriptor> task = () -> {
            long ts = System.currentTimeMillis();
            log.debug("[simulation] Create hidden service");
            if (simulateTorDelayHiddenService > 0) Thread.sleep(simulateTorDelayHiddenService);
            log.debug("\n\n##### Hidden service created [simulation]. Took " + (System.currentTimeMillis() - ts) + " ms\n\n");
            return null;
        };
        ListenableFuture<HiddenServiceDescriptor> future = executorService.submit(task);
        Futures.addCallback(future, new FutureCallback<HiddenServiceDescriptor>() {
            public void onSuccess(HiddenServiceDescriptor hiddenServiceDescriptor) {
                resultHandler.accept(hiddenServiceDescriptor);
            }

            public void onFailure(Throwable throwable) {
                log.error("[simulation] Hidden service creation failed");
            }
        });
    }

}
