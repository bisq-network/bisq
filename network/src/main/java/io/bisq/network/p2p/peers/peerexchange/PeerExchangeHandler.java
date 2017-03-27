package io.bisq.network.p2p.peers.peerexchange;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.network.p2p.network.CloseConnectionReason;
import io.bisq.network.p2p.network.Connection;
import io.bisq.network.p2p.network.MessageListener;
import io.bisq.network.p2p.network.NetworkNode;
import io.bisq.network.p2p.peers.PeerManager;
import io.bisq.protobuffer.message.Message;
import io.bisq.protobuffer.message.p2p.peers.peerexchange.GetPeersRequest;
import io.bisq.protobuffer.message.p2p.peers.peerexchange.GetPeersResponse;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Random;
import java.util.concurrent.TimeUnit;

class PeerExchangeHandler implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(PeerExchangeHandler.class);

    private static final long TIME_OUT_SEC = 40;
    private static final int DELAY_MS = 1000;


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
    private boolean stopped;
    private Timer delayTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PeerExchangeHandler(NetworkNode networkNode, PeerManager peerManager, Listener listener) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        this.listener = listener;
    }

    public void cancel() {
        Log.traceCall();
        cleanup();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void sendGetPeersRequestAfterRandomDelay(NodeAddress nodeAddress) {
        delayTimer = UserThread.runAfterRandomDelay(() -> sendGetPeersRequest(nodeAddress), 1, DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void sendGetPeersRequest(NodeAddress nodeAddress) {
        Log.traceCall("nodeAddress=" + nodeAddress + " / this=" + this);
        if (!stopped) {
            if (networkNode.getNodeAddress() != null) {
                GetPeersRequest getPeersRequest = new GetPeersRequest(networkNode.getNodeAddress(), nonce, peerManager.getConnectedNonSeedNodeReportedPeers(nodeAddress));

                if (timeoutTimer == null) {
                    timeoutTimer = UserThread.runAfter(() -> {  // setup before sending to avoid race conditions
                                if (!stopped) {
                                    String errorMessage = "A timeout occurred at sending getPeersRequest:" + getPeersRequest + " for nodeAddress:" + nodeAddress;
                                    log.debug(errorMessage + " / PeerExchangeHandler=" +
                                            PeerExchangeHandler.this);
                                    log.debug("timeoutTimer called on " + this);
                                    handleFault(errorMessage, CloseConnectionReason.SEND_MSG_TIMEOUT, nodeAddress);
                                } else {
                                    log.trace("We have stopped that handler already. We ignore that timeoutTimer.run call.");
                                }
                            },
                            TIME_OUT_SEC, TimeUnit.SECONDS);
                }

                SettableFuture<Connection> future = networkNode.sendMessage(nodeAddress, getPeersRequest);
                Futures.addCallback(future, new FutureCallback<Connection>() {
                    @Override
                    public void onSuccess(Connection connection) {
                        if (!stopped) {
                            //TODO
                            /*if (!connection.getPeersNodeAddressOptional().isPresent()) {
                                connection.setPeersNodeAddress(nodeAddress);
                                log.warn("sendGetPeersRequest: !connection.getPeersNodeAddressOptional().isPresent()");
                            }*/

                            PeerExchangeHandler.this.connection = connection;
                            connection.addMessageListener(PeerExchangeHandler.this);
                            log.trace("Send " + getPeersRequest + " to " + nodeAddress + " succeeded.");
                        } else {
                            log.trace("We have stopped that handler already. We ignore that sendGetPeersRequest.onSuccess call.");
                        }
                    }

                    @Override
                    public void onFailure(@NotNull Throwable throwable) {
                        if (!stopped) {
                            String errorMessage = "Sending getPeersRequest to " + nodeAddress +
                                    " failed. That is expected if the peer is offline.\n\tgetPeersRequest=" + getPeersRequest +
                                    ".\n\tException=" + throwable.getMessage();
                            log.debug(errorMessage);
                            handleFault(errorMessage, CloseConnectionReason.SEND_MSG_FAILURE, nodeAddress);
                        } else {
                            log.trace("We have stopped that handler already. We ignore that sendGetPeersRequest.onFailure call.");
                        }
                    }
                });
            } else {
                log.debug("My node address is still null at sendGetPeersRequest. We ignore that call.");
            }
        } else {
            log.trace("We have stopped that handler already. We ignore that sendGetPeersRequest call.");
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof GetPeersResponse) {
            if (!stopped) {
                Log.traceCall(message.toString() + "\n\tconnection=" + connection);
                GetPeersResponse getPeersResponse = (GetPeersResponse) message;
                if (peerManager.isSeedNode(connection))
                    connection.setPeerType(Connection.PeerType.SEED_NODE);

                // Check if the response is for our request
                if (getPeersResponse.requestNonce == nonce) {
                    peerManager.addToReportedPeers(getPeersResponse.reportedPeers, connection);
                    cleanup();
                    listener.onComplete();
                } else {
                    log.warn("Nonce not matching. That should never happen.\n\t" +
                                    "We drop that message. nonce={} / requestNonce={}",
                            nonce, getPeersResponse.requestNonce);
                }
            } else {
                log.trace("We have stopped that handler already. We ignore that onMessage call.");
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handleFault(String errorMessage, CloseConnectionReason sendMsgFailure, NodeAddress nodeAddress) {
        Log.traceCall();
        cleanup();
       /* if (connection == null)
            peerManager.shutDownConnection(nodeAddress, sendMsgFailure);
        else
            peerManager.shutDownConnection(connection, sendMsgFailure);*/

        peerManager.handleConnectionFault(nodeAddress, connection);
        listener.onFault(errorMessage, connection);
    }

    private void cleanup() {
        Log.traceCall();
        stopped = true;
        if (connection != null)
            connection.removeMessageListener(this);

        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }

        if (delayTimer != null) {
            delayTimer.stop();
            delayTimer = null;
        }
    }

}
