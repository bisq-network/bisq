package io.bitsquare.p2p.peers.peerexchange;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.network.CloseConnectionReason;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.p2p.peers.PeerManager;
import io.bitsquare.p2p.peers.peerexchange.messages.GetPeersRequest;
import io.bitsquare.p2p.peers.peerexchange.messages.GetPeersResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;

class GetPeersRequestHandler {
    private static final Logger log = LoggerFactory.getLogger(GetPeersRequestHandler.class);

    private static final long TIME_OUT_SEC = 20;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface Listener {
        void onComplete();

        void onFault(String errorMessage, Connection connection);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final NetworkNode networkNode;
    private final PeerManager peerManager;
    private final Listener listener;
    private Timer timeoutTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public GetPeersRequestHandler(NetworkNode networkNode, PeerManager peerManager, Listener listener) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        this.listener = listener;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void handle(GetPeersRequest getPeersRequest, final Connection connection) {
        Log.traceCall("getPeersRequest=" + getPeersRequest + "\n\tconnection=" + connection + "\n\tthis=" + this);

        checkArgument(connection.getPeersNodeAddressOptional().isPresent(),
                "The peers address must have been already set at the moment");
        GetPeersResponse getPeersResponse = new GetPeersResponse(getPeersRequest.nonce,
                peerManager.getConnectedPeersNonSeedNodes(connection.getPeersNodeAddressOptional().get()));
        SettableFuture<Connection> future = networkNode.sendMessage(connection,
                getPeersResponse);
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(Connection connection) {
                log.trace("GetPeersResponse sent successfully");
                cleanup();
                listener.onComplete();
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                String errorMessage = "Sending getPeersResponse to " + connection +
                        " failed. That is expected if the peer is offline. getPeersResponse=" + getPeersResponse + "." +
                        "Exception: " + throwable.getMessage();
                log.info(errorMessage);
                handleFault(errorMessage, CloseConnectionReason.SEND_MSG_FAILURE, connection);
            }
        });

        checkArgument(timeoutTimer == null, "onGetPeersRequest must not be called twice.");
        timeoutTimer = UserThread.runAfter(() -> {
                    String errorMessage = "A timeout occurred at sending getPeersResponse:" + getPeersResponse + " on connection:" + connection;
                    log.info(errorMessage + " / PeerExchangeHandshake=" +
                            GetPeersRequestHandler.this);
                    log.info("timeoutTimer called. this=" + this);
                    handleFault(errorMessage, CloseConnectionReason.SEND_MSG_TIMEOUT, connection);
                },
                TIME_OUT_SEC, TimeUnit.SECONDS);

        peerManager.addToReportedPeers(getPeersRequest.reportedPeers, connection);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handleFault(String errorMessage, CloseConnectionReason sendMsgFailure, Connection connection) {
        // TODO retry
        cleanup();
        peerManager.shutDownConnection(connection, sendMsgFailure);
        listener.onFault(errorMessage, connection);
    }

    private void cleanup() {
        if (timeoutTimer != null) {
            timeoutTimer.cancel();
            timeoutTimer = null;
        }
    }
}
