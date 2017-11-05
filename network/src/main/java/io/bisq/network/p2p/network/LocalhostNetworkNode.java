package io.bisq.network.p2p.network;

import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.common.proto.network.NetworkProtoResolver;
import io.bisq.network.p2p.NodeAddress;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

// Run in UserThread
public class LocalhostNetworkNode extends NetworkNode {
    private static final Logger log = LoggerFactory.getLogger(LocalhostNetworkNode.class);

    private static int simulateTorDelayTorNode = 500;
    private static int simulateTorDelayHiddenService = 500;

    public static void setSimulateTorDelayTorNode(int simulateTorDelayTorNode) {
        LocalhostNetworkNode.simulateTorDelayTorNode = simulateTorDelayTorNode;
    }

    public static void setSimulateTorDelayHiddenService(int simulateTorDelayHiddenService) {
        LocalhostNetworkNode.simulateTorDelayHiddenService = simulateTorDelayHiddenService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public LocalhostNetworkNode(int port, NetworkProtoResolver networkProtoResolver) {
        super(port, networkProtoResolver);
    }

    @Override
    public void start(@Nullable SetupListener setupListener) {
        if (setupListener != null)
            addSetupListener(setupListener);

        createExecutorService();

        // simulate tor connection delay
        UserThread.runAfter(() -> {
            Log.traceCall("torNode created");
            setupListeners.stream().forEach(SetupListener::onTorNodeReady);

            // simulate tor HS publishing delay
            UserThread.runAfter(() -> {
                Log.traceCall("hiddenService created");
                try {
                    startServer(new ServerSocket(servicePort));
                } catch (IOException e) {
                    e.printStackTrace();
                    log.error("Exception at startServer: " + e.getMessage());
                }

                nodeAddressProperty.set(new NodeAddress("localhost", servicePort));
                setupListeners.stream().forEach(SetupListener::onHiddenServicePublished);
            }, simulateTorDelayTorNode, TimeUnit.MILLISECONDS);
        }, simulateTorDelayHiddenService, TimeUnit.MILLISECONDS);
    }

    // Called from NetworkNode thread
    @Override
    protected Socket createSocket(NodeAddress peerNodeAddress) throws IOException {
        return new Socket(peerNodeAddress.getHostName(), peerNodeAddress.getPort());
    }
}
