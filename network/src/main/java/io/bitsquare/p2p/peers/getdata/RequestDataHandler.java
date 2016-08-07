package io.bitsquare.p2p.peers.getdata;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.app.Log;
import io.bitsquare.common.Timer;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.CloseConnectionReason;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.MessageListener;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.p2p.peers.PeerManager;
import io.bitsquare.p2p.peers.getdata.messages.GetDataRequest;
import io.bitsquare.p2p.peers.getdata.messages.GetDataResponse;
import io.bitsquare.p2p.peers.getdata.messages.GetUpdatedDataRequest;
import io.bitsquare.p2p.peers.getdata.messages.PreliminaryGetDataRequest;
import io.bitsquare.p2p.storage.P2PDataStorage;
import io.bitsquare.p2p.storage.payload.Priority1StoragePayload;
import io.bitsquare.p2p.storage.payload.Priority2StoragePayload;
import io.bitsquare.p2p.storage.payload.StoragePayload;
import io.bitsquare.p2p.storage.storageentry.ProtectedStorageEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

public class RequestDataHandler implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(RequestDataHandler.class);

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
    private final P2PDataStorage dataStorage;
    private final PeerManager peerManager;
    private final Listener listener;
    private Timer timeoutTimer;
    private final int nonce = new Random().nextInt();
    private boolean stopped;
    private Connection connection;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public RequestDataHandler(NetworkNode networkNode, P2PDataStorage dataStorage, PeerManager peerManager,
                              Listener listener) {
        this.networkNode = networkNode;
        this.dataStorage = dataStorage;
        this.peerManager = peerManager;
        this.listener = listener;
    }

    public void cancel() {
        cleanup();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void requestData(NodeAddress nodeAddress) {
        Log.traceCall("nodeAddress=" + nodeAddress);
        if (!stopped) {
            GetDataRequest getDataRequest;
            if (networkNode.getNodeAddress() == null)
                getDataRequest = new PreliminaryGetDataRequest(nonce);
            else
                getDataRequest = new GetUpdatedDataRequest(networkNode.getNodeAddress(), nonce);

            if (timeoutTimer == null) {
                timeoutTimer = UserThread.runAfter(() -> {  // setup before sending to avoid race conditions
                            if (!stopped) {
                                String errorMessage = "A timeout occurred at sending getDataRequest:" + getDataRequest +
                                        " on nodeAddress:" + nodeAddress;
                                log.debug(errorMessage + " / RequestDataHandler=" + RequestDataHandler.this);
                                handleFault(errorMessage, nodeAddress, CloseConnectionReason.SEND_MSG_TIMEOUT);
                            } else {
                                log.trace("We have stopped already. We ignore that timeoutTimer.run call. " +
                                        "Might be caused by an previous networkNode.sendMessage.onFailure.");
                            }
                        },
                        TIME_OUT_SEC);
            }

            log.debug("We send a {} to peer {}. ", getDataRequest.getClass().getSimpleName(), nodeAddress);
            SettableFuture<Connection> future = networkNode.sendMessage(nodeAddress, getDataRequest);
            Futures.addCallback(future, new FutureCallback<Connection>() {
                @Override
                public void onSuccess(Connection connection) {
                    if (!stopped) {
                        RequestDataHandler.this.connection = connection;
                        connection.addMessageListener(RequestDataHandler.this);
                        log.trace("Send " + getDataRequest + " to " + nodeAddress + " succeeded.");
                    } else {
                        log.trace("We have stopped already. We ignore that networkNode.sendMessage.onSuccess call." +
                                "Might be caused by an previous timeout.");
                    }
                }

                @Override
                public void onFailure(@NotNull Throwable throwable) {
                    if (!stopped) {
                        String errorMessage = "Sending getDataRequest to " + nodeAddress +
                                " failed. That is expected if the peer is offline.\n\t" +
                                "getDataRequest=" + getDataRequest + "." +
                                "\n\tException=" + throwable.getMessage();
                        log.debug(errorMessage);
                        handleFault(errorMessage, nodeAddress, CloseConnectionReason.SEND_MSG_FAILURE);
                    } else {
                        log.trace("We have stopped already. We ignore that networkNode.sendMessage.onFailure call. " +
                                "Might be caused by an previous timeout.");
                    }
                }
            });
        } else {
            log.warn("We have stopped already. We ignore that requestData call.");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof GetDataResponse) {
            Log.traceCall(message.toString() + "\n\tconnection=" + connection);
            if (!stopped) {
                GetDataResponse getDataResponse = (GetDataResponse) message;
                Map<String, Set<StoragePayload>> payloadByClassName = new HashMap<>();
                getDataResponse.dataSet.stream().forEach(e -> {
                    final StoragePayload storagePayload = e.getStoragePayload();
                    String className = storagePayload.getClass().getSimpleName();
                    if (!payloadByClassName.containsKey(className))
                        payloadByClassName.put(className, new HashSet<>());

                    payloadByClassName.get(className).add(storagePayload);
                });
                StringBuilder sb = new StringBuilder("Received data size: ").append(getDataResponse.dataSet.size()).append(", data items: ");
                payloadByClassName.entrySet().stream().forEach(e -> sb.append(e.getValue().size()).append(" items of ").append(e.getKey()).append("; "));
                log.info(sb.toString());

                if (getDataResponse.requestNonce == nonce) {
                    stopTimeoutTimer();
                    checkArgument(connection.getPeersNodeAddressOptional().isPresent(),
                            "RequestDataHandler.onMessage: connection.getPeersNodeAddressOptional() must be present " +
                                    "at that moment");

                    final NodeAddress sender = connection.getPeersNodeAddressOptional().get();

                    Set<ProtectedStorageEntry> newItems = getDataResponse.dataSet.stream()
                            .filter(e -> !dataStorage.mapContainsStoragePayload(e.getStoragePayload()))
                            .collect(Collectors.toSet());

                    log.debug("newItems.size() " + newItems.size());

                    // The non PriorityStoragePayload items we process directly
                    newItems.stream()
                            .filter(e -> !(e.getStoragePayload() instanceof Priority1StoragePayload) &&
                                    !(e.getStoragePayload() instanceof Priority2StoragePayload))
                            .forEach(protectedStorageEntry -> {
                                // We dont broadcast here as we are only connected to the seed node and would be pointless
                                dataStorage.add(protectedStorageEntry, sender, null, false, false);
                            });

                    // The Priority1StoragePayload items we process with a short delay (Offers)
                    final long[] counter = {0};
                    newItems.stream()
                            .filter(e -> e.getStoragePayload() instanceof Priority1StoragePayload)
                            .forEach(protectedStorageEntry -> {
                                // We process 10 items at a time and make a 100 ms delay between them
                                // One processing takes about 5-20 ms (sig check)
                                long delay = (counter[0] / 10) * 100 + 1;
                                counter[0]++;
                                // We don't want the UI get stuck when processing 100s of entries.
                                // The dataStorage.add call is a bit expensive as sig checks is done there
                                UserThread.runAfter(() -> {
                                            // We dont broadcast here as we are only connected to the seed node and would be pointless
                                            dataStorage.add(protectedStorageEntry, sender, null, false, false);
                                        },
                                        delay, TimeUnit.MILLISECONDS);
                            });

                    // The Priority2StoragePayload items we process with a longer delay (TradeStatistics)
                    final long[] counter2 = {0};
                    newItems.stream()
                            .filter(e -> e.getStoragePayload() instanceof Priority2StoragePayload)
                            .forEach(protectedStorageEntry -> {
                                long delay = (counter2[0] / 50) * 500 + 1;
                                counter2[0]++;
                                // We don't want the UI get stuck when processing 100s of entries.
                                // The dataStorage.add call is a bit expensive as sig checks is done there
                                UserThread.runAfter(() -> {
                                            // We dont broadcast here as we are only connected to the seed node and would be pointless
                                            dataStorage.add(protectedStorageEntry, sender, null, false, false);
                                        },
                                        delay, TimeUnit.MILLISECONDS);
                            });

                    cleanup();
                    listener.onComplete();
                } else {
                    log.debug("Nonce not matching. That can happen rarely if we get a response after a canceled " +
                                    "handshake (timeout causes connection close but peer might have sent a msg before " +
                                    "connection was closed).\n\t" +
                                    "We drop that message. nonce={} / requestNonce={}",
                            nonce, getDataResponse.requestNonce);
                }
            } else {
                log.warn("We have stopped already. We ignore that onDataRequest call.");
            }
        }
    }


    public void stop() {
        cleanup();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////


    private void handleFault(String errorMessage, NodeAddress nodeAddress, CloseConnectionReason closeConnectionReason) {
        cleanup();
        //peerManager.shutDownConnection(nodeAddress, closeConnectionReason);
        peerManager.handleConnectionFault(nodeAddress);
        listener.onFault(errorMessage, null);
    }

    private void cleanup() {
        Log.traceCall();
        stopped = true;
        if (connection != null)
            connection.removeMessageListener(this);
        stopTimeoutTimer();
    }

    private void stopTimeoutTimer() {
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }
}
