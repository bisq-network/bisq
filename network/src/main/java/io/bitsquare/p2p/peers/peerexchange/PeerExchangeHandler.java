package io.bitsquare.p2p.peers.peerexchange;

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
import io.bitsquare.p2p.peers.PeerManager;
import io.bitsquare.p2p.peers.peerexchange.messages.GetPeersRequest;
import io.bitsquare.p2p.peers.peerexchange.messages.GetPeersResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Random;
import java.util.Timer;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

class PeerExchangeHandler implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(PeerExchangeHandler.class);

    private static final long TIME_OUT_SEC = 20;


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
    private final int nonce = new Random().nextInt();
    private Timer timeoutTimer;
    private Connection connection;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PeerExchangeHandler(NetworkNode networkNode, PeerManager peerManager, Listener listener) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        this.listener = listener;
    }

    public void cleanup() {
        if (connection != null)
            connection.removeMessageListener(this);

        if (timeoutTimer != null) {
            timeoutTimer.cancel();
            timeoutTimer = null;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void sendGetPeersRequest(NodeAddress nodeAddress) {
        Log.traceCall("nodeAddress=" + nodeAddress + " / this=" + this);
        checkNotNull(networkNode.getNodeAddress(), "PeerExchangeHandler.requestReportedPeers: My node address must " +
                "not be null at requestReportedPeers");
        GetPeersRequest getPeersRequest = new GetPeersRequest(networkNode.getNodeAddress(), nonce, peerManager.getConnectedPeersNonSeedNodes(nodeAddress));
        SettableFuture<Connection> future = networkNode.sendMessage(nodeAddress, getPeersRequest);
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(Connection connection) {
                PeerExchangeHandler.this.connection = connection;
                connection.addMessageListener(PeerExchangeHandler.this);
                log.trace("Send " + getPeersRequest + " to " + nodeAddress + " succeeded.");
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                String errorMessage = "Sending getPeersRequest to " + nodeAddress +
                        " failed. That is expected if the peer is offline.\n\tgetPeersRequest=" + getPeersRequest +
                        ".\n\tException=" + throwable.getMessage();
                log.info(errorMessage);
                handleFault(errorMessage, CloseConnectionReason.SEND_MSG_FAILURE, nodeAddress);
            }
        });

        checkArgument(timeoutTimer == null, "requestReportedPeers must not be called twice.");
        timeoutTimer = UserThread.runAfter(() -> {
                    String errorMessage = "A timeout occurred at sending getPeersRequest:" + getPeersRequest + " for nodeAddress:" + nodeAddress;
                    log.info(errorMessage + " / PeerExchangeHandler=" +
                            PeerExchangeHandler.this);
                    log.info("timeoutTimer called on " + this);
                    handleFault(errorMessage, CloseConnectionReason.SEND_MSG_TIMEOUT, nodeAddress);
                },
                TIME_OUT_SEC, TimeUnit.SECONDS);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof GetPeersResponse) {
            GetPeersResponse getPeersResponse = (GetPeersResponse) message;

            if (peerManager.isSeedNode(connection))
                connection.setPeerType(Connection.PeerType.SEED_NODE);

            // Check if the response is for our request
            if (getPeersResponse.requestNonce == nonce) {
                Log.traceCall(message.toString() + "\n\tconnection=" + connection);
                Log.traceCall("this=" + this);
                peerManager.addToReportedPeers(getPeersResponse.reportedPeers, connection);

                cleanup();
                listener.onComplete();
            } else {
                log.warn("Nonce not matching. That should never happen.\n\t" +
                                "We drop that message. nonce={} / requestNonce={}",
                        nonce, getPeersResponse.requestNonce);
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handleFault(String errorMessage, CloseConnectionReason sendMsgFailure, NodeAddress nodeAddress) {
        // TODO retry
        cleanup();
        if (connection == null)
            peerManager.shutDownConnection(nodeAddress, sendMsgFailure);
        else
            peerManager.shutDownConnection(connection, sendMsgFailure);

        listener.onFault(errorMessage, connection);
    }
}
