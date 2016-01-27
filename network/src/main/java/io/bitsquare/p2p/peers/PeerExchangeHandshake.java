package io.bitsquare.p2p.peers;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.MessageListener;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.p2p.peers.messages.peers.GetPeersRequest;
import io.bitsquare.p2p.peers.messages.peers.GetPeersResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class PeerExchangeHandshake implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(PeerExchangeHandshake.class);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface Listener {
        void onComplete();

        void onFault(String errorMessage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final NetworkNode networkNode;
    private final PeerManager peerManager;
    private final Listener listener;
    private final long nonce = new Random().nextLong();
    private Timer timeoutTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PeerExchangeHandshake(NetworkNode networkNode, PeerManager peerManager, Listener listener) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        this.listener = listener;

        networkNode.addMessageListener(this);
    }

    public void shutDown() {
        Log.traceCall();
        networkNode.removeMessageListener(this);
        stopTimeoutTimer();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void requestReportedPeers(NodeAddress nodeAddress, List<NodeAddress> remainingNodeAddresses) {
        Log.traceCall("nodeAddress=" + nodeAddress);
        checkNotNull(networkNode.getNodeAddress(), "My node address must not be null at requestReportedPeers");
        checkArgument(timeoutTimer == null, "requestData must not be called twice.");

        timeoutTimer = UserThread.runAfter(() -> {
                    log.info("timeoutTimer called");
                    peerManager.shutDownConnection(nodeAddress);
                    shutDown();
                    listener.onFault("A timeout occurred");
                },
                20, TimeUnit.SECONDS);

        GetPeersRequest getPeersRequest = new GetPeersRequest(networkNode.getNodeAddress(), nonce,
                getReportedPeers(nodeAddress));
        SettableFuture<Connection> future = networkNode.sendMessage(nodeAddress,
                getPeersRequest);
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(Connection connection) {
                log.trace("Send " + getPeersRequest + " to " + nodeAddress + " succeeded.");
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                String errorMessage = "Sending getPeersRequest to " + nodeAddress +
                        " failed. That is expected if the peer is offline. getPeersRequest=" + getPeersRequest + "." +
                        "Exception: " + throwable.getMessage();
                log.info(errorMessage);

                peerManager.shutDownConnection(nodeAddress);
                shutDown();
                listener.onFault(errorMessage);
            }
        });
    }

    public void onGetPeersRequest(GetPeersRequest message, final Connection connection) {
        checkArgument(timeoutTimer == null, "requestData must not be called twice.");

        timeoutTimer = UserThread.runAfter(() -> {
                    log.info("timeoutTimer called");
                    peerManager.shutDownConnection(connection);
                    shutDown();
                    listener.onFault("A timeout occurred");
                },
                20, TimeUnit.SECONDS);

        GetPeersRequest getPeersRequest = message;
        HashSet<ReportedPeer> reportedPeers = getPeersRequest.reportedPeers;
        StringBuilder result = new StringBuilder("Received peers:");
        reportedPeers.stream().forEach(e -> result.append("\n").append(e));
        log.trace(result.toString());

        checkArgument(connection.getPeersNodeAddressOptional().isPresent(),
                "The peers address must have been already set at the moment");
        SettableFuture<Connection> future = networkNode.sendMessage(connection,
                new GetPeersResponse(getPeersRequest.nonce,
                        getReportedPeers(connection.getPeersNodeAddressOptional().get())));
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(Connection connection) {
                log.trace("GetPeersResponse sent successfully");
                shutDown();
                listener.onComplete();
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                String errorMessage = "Sending getPeersRequest to " + connection +
                        " failed. That is expected if the peer is offline. getPeersRequest=" + getPeersRequest + "." +
                        "Exception: " + throwable.getMessage();
                log.info(errorMessage);

                peerManager.shutDownConnection(connection);
                shutDown();
                listener.onFault(errorMessage);
            }
        });
        peerManager.addToReportedPeers(reportedPeers, connection);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof GetPeersResponse) {
            Log.traceCall(message.toString() + " / connection=" + connection);
            GetPeersResponse getPeersResponse = (GetPeersResponse) message;
            if (getPeersResponse.requestNonce == nonce) {
                stopTimeoutTimer();

                HashSet<ReportedPeer> reportedPeers = getPeersResponse.reportedPeers;
                StringBuilder result = new StringBuilder("Received peers:");
                reportedPeers.stream().forEach(e -> result.append("\n").append(e));
                log.trace(result.toString());
                peerManager.addToReportedPeers(reportedPeers, connection);

                shutDown();
                listener.onComplete();
            } else {
                log.debug("Nonce not matching. That happens if we get a response after a canceled handshake " +
                                "(timeout). We drop that message. nonce={} / requestNonce={}",
                        nonce, getPeersResponse.requestNonce);
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private HashSet<ReportedPeer> getReportedPeers(NodeAddress receiverNodeAddress) {
        return new HashSet<>(peerManager.getConnectedAndReportedPeers().stream()
                .filter(e -> !peerManager.isSeedNode(e) &&
                                !peerManager.isSelf(e) &&
                                !e.nodeAddress.equals(receiverNodeAddress)
                )
                .collect(Collectors.toSet()));
    }

    private void stopTimeoutTimer() {
        if (timeoutTimer != null) {
            timeoutTimer.cancel();
            timeoutTimer = null;
        }
    }
}
