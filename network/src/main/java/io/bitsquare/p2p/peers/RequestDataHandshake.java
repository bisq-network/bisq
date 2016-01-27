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
import io.bitsquare.p2p.peers.messages.data.DataRequest;
import io.bitsquare.p2p.peers.messages.data.DataResponse;
import io.bitsquare.p2p.peers.messages.data.PreliminaryDataRequest;
import io.bitsquare.p2p.peers.messages.data.UpdateDataRequest;
import io.bitsquare.p2p.storage.P2PDataStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Random;
import java.util.Timer;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;

public class RequestDataHandshake implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(RequestDataHandshake.class);

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
    private final P2PDataStorage dataStorage;
    private final PeerManager peerManager;
    private final Listener listener;
    private Timer timeoutTimer;
    private long requestNonce;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public RequestDataHandshake(NetworkNode networkNode, P2PDataStorage dataStorage, PeerManager peerManager,
                                Listener listener) {
        this.networkNode = networkNode;
        this.dataStorage = dataStorage;
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

    public void requestData(NodeAddress nodeAddress) {
        Log.traceCall("nodeAddress=" + nodeAddress);

        stopTimeoutTimer();

        checkArgument(timeoutTimer == null, "requestData must not be called twice.");
        timeoutTimer = UserThread.runAfter(() -> {
                    log.info("timeoutTimer called");
                    peerManager.shutDownConnection(nodeAddress);
                    shutDown();
                    listener.onFault("A timeout occurred");
                },
                10, TimeUnit.SECONDS);

        Message dataRequest;
        requestNonce = new Random().nextLong();
        if (networkNode.getNodeAddress() == null)
            dataRequest = new PreliminaryDataRequest(requestNonce);
        else
            dataRequest = new UpdateDataRequest(networkNode.getNodeAddress(), requestNonce);

        log.info("We send a {} to peer {}. ", dataRequest.getClass().getSimpleName(), nodeAddress);

        SettableFuture<Connection> future = networkNode.sendMessage(nodeAddress, dataRequest);
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(@Nullable Connection connection) {
                log.trace("Send DataRequest to " + nodeAddress + " succeeded.");
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                String errorMessage = "Sending " + dataRequest.getClass().getSimpleName() + " to " + nodeAddress +
                        " failed. That is expected if the peer is offline. " +
                        "Exception:" + throwable.getMessage();
                log.info(errorMessage);

                peerManager.shutDownConnection(nodeAddress);
                shutDown();
                listener.onFault(errorMessage);
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof DataRequest) {
            Log.traceCall(message.toString());
            DataRequest dataRequest = (DataRequest) message;
            DataResponse dataResponse = new DataResponse(new HashSet<>(dataStorage.getMap().values()), dataRequest.getNonce());
            SettableFuture<Connection> future = networkNode.sendMessage(connection, dataResponse);
            Futures.addCallback(future, new FutureCallback<Connection>() {
                @Override
                public void onSuccess(Connection connection) {
                    log.trace("Send DataResponse to {} succeeded. dataResponse={}",
                            connection.getPeersNodeAddressOptional(), dataResponse);
                    shutDown();
                    listener.onComplete();
                }

                @Override
                public void onFailure(@NotNull Throwable throwable) {
                    String errorMessage = "Send DataResponse to " + connection.getPeersNodeAddressOptional() + " failed. " +
                            "That is expected if the peer went offline. " +
                            "Exception:" + throwable.getMessage();
                    log.info(errorMessage);

                    peerManager.shutDownConnection(connection);
                    shutDown();
                    listener.onFault(errorMessage);
                }
            });
        } else if (message instanceof DataResponse) {
            DataResponse dataResponse = (DataResponse) message;
            if (dataResponse.requestNonce == requestNonce) {
                Log.traceCall(message.toString());

                stopTimeoutTimer();

                // connection.getPeersNodeAddressOptional() is not present at the first call
                log.debug("connection.getPeersNodeAddressOptional() " + connection.getPeersNodeAddressOptional());
                connection.getPeersNodeAddressOptional().ifPresent(peersNodeAddress -> {
                    ((DataResponse) message).dataSet.stream()
                            .forEach(e -> dataStorage.add(e, peersNodeAddress));
                });
                shutDown();
                listener.onComplete();
            }
        }
    }

    private void stopTimeoutTimer() {
        if (timeoutTimer != null) {
            timeoutTimer.cancel();
            timeoutTimer = null;
        }
    }
}
