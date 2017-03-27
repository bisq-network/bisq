package io.bisq.network.p2p.peers.peerexchange;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.network.p2p.network.CloseConnectionReason;
import io.bisq.network.p2p.network.Connection;
import io.bisq.network.p2p.network.NetworkNode;
import io.bisq.network.p2p.peers.PeerManager;
import io.bisq.protobuffer.message.p2p.peers.peerexchange.GetPeersRequest;
import io.bisq.protobuffer.message.p2p.peers.peerexchange.GetPeersResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;

class GetPeersRequestHandler {
    private static final Logger log = LoggerFactory.getLogger(GetPeersRequestHandler.class);

    private static final long TIME_OUT_SEC = 40;


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
    private boolean stopped;


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
                peerManager.getConnectedNonSeedNodeReportedPeers(connection.getPeersNodeAddressOptional().get()));

        checkArgument(timeoutTimer == null, "onGetPeersRequest must not be called twice.");
        timeoutTimer = UserThread.runAfter(() -> {  // setup before sending to avoid race conditions
                    if (!stopped) {
                        String errorMessage = "A timeout occurred at sending getPeersResponse:" + getPeersResponse + " on connection:" + connection;
                        log.debug(errorMessage + " / PeerExchangeHandshake=" +
                                GetPeersRequestHandler.this);
                        log.debug("timeoutTimer called. this=" + this);
                        handleFault(errorMessage, CloseConnectionReason.SEND_MSG_TIMEOUT, connection);
                    } else {
                        log.trace("We have stopped already. We ignore that timeoutTimer.run call.");
                    }
                },
                TIME_OUT_SEC, TimeUnit.SECONDS);

        SettableFuture<Connection> future = networkNode.sendMessage(connection,
                getPeersResponse);
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(Connection connection) {
                if (!stopped) {
                    log.trace("GetPeersResponse sent successfully");
                    cleanup();
                    listener.onComplete();
                } else {
                    log.trace("We have stopped already. We ignore that networkNode.sendMessage.onSuccess call.");
                }
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                if (!stopped) {
                    String errorMessage = "Sending getPeersResponse to " + connection +
                            " failed. That is expected if the peer is offline. getPeersResponse=" + getPeersResponse + "." +
                            "Exception: " + throwable.getMessage();
                    log.debug(errorMessage);
                    handleFault(errorMessage, CloseConnectionReason.SEND_MSG_FAILURE, connection);
                } else {
                    log.trace("We have stopped already. We ignore that networkNode.sendMessage.onFailure call.");
                }
            }
        });

        peerManager.addToReportedPeers(getPeersRequest.reportedPeers, connection);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handleFault(String errorMessage, CloseConnectionReason closeConnectionReason, Connection connection) {
        cleanup();
        //peerManager.shutDownConnection(connection, closeConnectionReason);
        listener.onFault(errorMessage, connection);
    }

    private void cleanup() {
        stopped = true;
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }
}
