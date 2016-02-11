package io.bitsquare.p2p.peers;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.CloseConnectionReason;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.MessageListener;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.p2p.peers.messages.peers.GetPeersRequest;
import io.bitsquare.p2p.peers.messages.peers.GetPeersResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Random;
import java.util.Timer;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class MaintenanceHandshake implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(MaintenanceHandshake.class);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface Listener {
        void onComplete();

        void onFault(String errorMessage, @Nullable Connection connection);
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

    public MaintenanceHandshake(NetworkNode networkNode, PeerManager peerManager, Listener listener) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        this.listener = listener;

        networkNode.addMessageListener(this);
    }

    public void shutDown() {
        networkNode.removeMessageListener(this);
        stopTimeoutTimer();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void requestReportedPeers(NodeAddress nodeAddress) {
        Log.traceCall("nodeAddress=" + nodeAddress + " / this=" + this);
        checkNotNull(networkNode.getNodeAddress(), "PeerExchangeHandshake.requestReportedPeers: My node address must " +
                "not be null at requestReportedPeers");
        GetPeersRequest getPeersRequest = new GetPeersRequest(networkNode.getNodeAddress(), nonce, getConnectedPeers(nodeAddress));
        SettableFuture<Connection> future = networkNode.sendMessage(nodeAddress, getPeersRequest);
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(Connection connection) {
                log.trace("Send " + getPeersRequest + " to " + nodeAddress + " succeeded.");
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                String errorMessage = "Sending getPeersRequest to " + nodeAddress +
                        " failed. That is expected if the peer is offline.\n\tgetPeersRequest=" + getPeersRequest +
                        ".\n\tException=" + throwable.getMessage();
                log.info(errorMessage);

                peerManager.shutDownConnection(nodeAddress, CloseConnectionReason.SEND_MSG_FAILURE);
                shutDown();
                listener.onFault(errorMessage, null);
            }
        });

        checkArgument(timeoutTimer == null, "requestReportedPeers must not be called twice.");
        timeoutTimer = UserThread.runAfter(() -> {
                    String errorMessage = "A timeout occurred at sending getPeersRequest:" + getPeersRequest + " for nodeAddress:" + nodeAddress;
                    log.info(errorMessage + " / PeerExchangeHandshake=" +
                            MaintenanceHandshake.this);

                    log.info("timeoutTimer called on " + this);
                    peerManager.shutDownConnection(nodeAddress, CloseConnectionReason.SEND_MSG_TIMEOUT);
                    shutDown();
                    listener.onFault(errorMessage, null);
                },
                20, TimeUnit.SECONDS);
    }

    public void onGetPeersRequest(GetPeersRequest getPeersRequest, final Connection connection) {
        Log.traceCall("getPeersRequest=" + getPeersRequest + "\n\tconnection=" + connection + "\n\tthis=" + this);

        HashSet<ReportedPeer> reportedPeers = getPeersRequest.reportedPeers;
        
       /* StringBuilder result = new StringBuilder("Received peers:");
        reportedPeers.stream().forEach(e -> result.append("\n\t").append(e));
        log.trace(result.toString());*/
        log.trace("reportedPeers.size=" + reportedPeers.size());

        checkArgument(connection.getPeersNodeAddressOptional().isPresent(),
                "The peers address must have been already set at the moment");
        GetPeersResponse getPeersResponse = new GetPeersResponse(getPeersRequest.nonce,
                getConnectedPeers(connection.getPeersNodeAddressOptional().get()));
        SettableFuture<Connection> future = networkNode.sendMessage(connection,
                getPeersResponse);
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

                peerManager.shutDownConnection(connection, CloseConnectionReason.SEND_MSG_FAILURE);
                shutDown();
                listener.onFault(errorMessage, connection);
            }
        });

        checkArgument(timeoutTimer == null, "onGetPeersRequest must not be called twice.");
        timeoutTimer = UserThread.runAfter(() -> {
                    String errorMessage = "A timeout occurred at sending getPeersResponse:" + getPeersResponse + " on connection:" + connection;
                    log.info(errorMessage + " / PeerExchangeHandshake=" +
                            MaintenanceHandshake.this);

                    log.info("timeoutTimer called. this=" + this);
                    peerManager.shutDownConnection(connection, CloseConnectionReason.SEND_MSG_TIMEOUT);
                    shutDown();
                    listener.onFault(errorMessage, connection);
                },
                20, TimeUnit.SECONDS);

        peerManager.addToReportedPeers(reportedPeers, connection);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof GetPeersResponse) {
            Log.traceCall(message.toString() + "\n\tconnection=" + connection);
            Log.traceCall("this=" + this);
            GetPeersResponse getPeersResponse = (GetPeersResponse) message;
            if (getPeersResponse.requestNonce == nonce) {
                stopTimeoutTimer();

                HashSet<ReportedPeer> reportedPeers = getPeersResponse.reportedPeers;
                StringBuilder result = new StringBuilder("Received peers:");
                reportedPeers.stream().forEach(e -> result.append("\n\t").append(e));
                log.trace(result.toString());
                peerManager.addToReportedPeers(reportedPeers, connection);

                shutDown();
                listener.onComplete();
            } else {
                log.debug("Nonce not matching. That can happen rarely if we get a response after a canceled handshake " +
                                "(timeout causes connection close but peer might have sent a msg before connection " +
                                "was closed).\n\tWe drop that message. nonce={} / requestNonce={}",
                        nonce, getPeersResponse.requestNonce);
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private HashSet<ReportedPeer> getConnectedPeers(NodeAddress receiverNodeAddress) {
        return new HashSet<>(peerManager.getConnectedPeers().stream()
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
