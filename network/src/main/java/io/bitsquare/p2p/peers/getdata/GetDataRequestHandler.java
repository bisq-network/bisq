package io.bitsquare.p2p.peers.getdata;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.app.Log;
import io.bitsquare.common.Timer;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.network.CloseConnectionReason;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.p2p.peers.PeerManager;
import io.bitsquare.p2p.peers.getdata.messages.GetDataRequest;
import io.bitsquare.p2p.peers.getdata.messages.GetDataResponse;
import io.bitsquare.p2p.storage.P2PDataStorage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;

public class GetDataRequestHandler {
    private static final Logger log = LoggerFactory.getLogger(GetDataRequestHandler.class);


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
    private P2PDataStorage dataStorage;
    private final Listener listener;
    private Timer timeoutTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public GetDataRequestHandler(NetworkNode networkNode, PeerManager peerManager, P2PDataStorage dataStorage, Listener listener) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        this.dataStorage = dataStorage;
        this.listener = listener;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void handle(GetDataRequest getDataRequest, final Connection connection) {
        Log.traceCall(getDataRequest + "\n\tconnection=" + connection);
        GetDataResponse getDataResponse = new GetDataResponse(new HashSet<>(dataStorage.getMap().values()),
                getDataRequest.getNonce());
        SettableFuture<Connection> future = networkNode.sendMessage(connection, getDataResponse);
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(Connection connection) {
                log.trace("Send DataResponse to {} succeeded. getDataResponse={}",
                        connection.getPeersNodeAddressOptional(), getDataResponse);
                cleanup();
                listener.onComplete();
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                String errorMessage = "Sending getDataRequest to " + connection +
                        " failed. That is expected if the peer is offline. getDataResponse=" + getDataResponse + "." +
                        "Exception: " + throwable.getMessage();
                handleFault(errorMessage, CloseConnectionReason.SEND_MSG_FAILURE, connection);
            }
        });

        checkArgument(timeoutTimer == null, "requestData must not be called twice.");
        timeoutTimer = UserThread.runAfter(() -> {
                    String errorMessage = "A timeout occurred for getDataResponse:" + getDataResponse +
                            " on connection:" + connection;
                    handleFault(errorMessage, CloseConnectionReason.SEND_MSG_TIMEOUT, connection);
                },
                TIME_OUT_SEC, TimeUnit.SECONDS);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handleFault(String errorMessage, CloseConnectionReason closeConnectionReason, Connection connection) {
        log.info(errorMessage);
        //peerManager.shutDownConnection(connection, closeConnectionReason);
        cleanup();
        listener.onFault(errorMessage, connection);
    }

    private void cleanup() {
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }
}
