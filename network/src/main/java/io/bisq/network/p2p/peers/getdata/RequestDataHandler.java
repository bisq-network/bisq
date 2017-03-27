package io.bisq.network.p2p.peers.getdata;

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
import io.bisq.network.p2p.storage.P2PDataStorage;
import io.bisq.protobuffer.message.Message;
import io.bisq.protobuffer.message.p2p.peers.getdata.GetDataRequest;
import io.bisq.protobuffer.message.p2p.peers.getdata.GetDataResponse;
import io.bisq.protobuffer.message.p2p.peers.getdata.GetUpdatedDataRequest;
import io.bisq.protobuffer.message.p2p.peers.getdata.PreliminaryGetDataRequest;
import io.bisq.protobuffer.payload.LazyProcessedStoragePayload;
import io.bisq.protobuffer.payload.PersistedStoragePayload;
import io.bisq.protobuffer.payload.StoragePayload;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import io.bisq.protobuffer.payload.p2p.storage.ProtectedStorageEntry;
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

    private static final long TIME_OUT_SEC = 40;
    private NodeAddress peersNodeAddress;


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

    public void requestData(NodeAddress nodeAddress, boolean isPreliminaryDataRequest) {
        Log.traceCall("nodeAddress=" + nodeAddress);
        peersNodeAddress = nodeAddress;
        if (!stopped) {
            GetDataRequest getDataRequest;

            // We collect the keys of the PersistedStoragePayload items so we exclude them in our request.
            // PersistedStoragePayload items don't get removed, so we don't have an issue with the case that
            // an object gets removed in between PreliminaryGetDataRequest and the GetUpdatedDataRequest and we would 
            // miss that event if we do not load the full set or use some delta handling.
            Set<byte[]> excludedKeys = dataStorage.getMap().entrySet().stream()
                    .filter(e -> e.getValue().getStoragePayload() instanceof PersistedStoragePayload)
                    .map(e -> e.getKey().bytes)
                    .collect(Collectors.toSet());

            if (isPreliminaryDataRequest)
                getDataRequest = new PreliminaryGetDataRequest(nonce, excludedKeys);
            else
                getDataRequest = new GetUpdatedDataRequest(networkNode.getNodeAddress(), nonce, excludedKeys);

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
            networkNode.addMessageListener(this);
            SettableFuture<Connection> future = networkNode.sendMessage(nodeAddress, getDataRequest);
            Futures.addCallback(future, new FutureCallback<Connection>() {
                @Override
                public void onSuccess(Connection connection) {
                    if (!stopped) {
                        RequestDataHandler.this.connection = connection;
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
        if (connection.getPeersNodeAddressOptional().isPresent() && connection.getPeersNodeAddressOptional().get().equals(peersNodeAddress)) {
            if (message instanceof GetDataResponse) {
                Log.traceCall(message.toString() + "\n\tconnection=" + connection);
                if (!stopped) {
                    GetDataResponse getDataResponse = (GetDataResponse) message;
                    Map<String, Set<StoragePayload>> payloadByClassName = new HashMap<>();
                    final HashSet<ProtectedStorageEntry> dataSet = getDataResponse.dataSet;
                    dataSet.stream().forEach(e -> {
                        final StoragePayload storagePayload = e.getStoragePayload();
                        if (storagePayload == null) {
                            log.warn("StoragePayload was null: {}", message.toString());
                            return;
                        }
                        String className = storagePayload.getClass().getSimpleName();
                        if (!payloadByClassName.containsKey(className))
                            payloadByClassName.put(className, new HashSet<>());

                        payloadByClassName.get(className).add(storagePayload);
                    });
                    StringBuilder sb = new StringBuilder("Received data size: ").append(dataSet.size()).append(", data items: ");
                    payloadByClassName.entrySet().stream().forEach(e -> sb.append(e.getValue().size()).append(" items of ").append(e.getKey()).append("; "));
                    log.info(sb.toString());

                    if (getDataResponse.requestNonce == nonce) {
                        stopTimeoutTimer();
                        checkArgument(connection.getPeersNodeAddressOptional().isPresent(),
                                "RequestDataHandler.onMessage: connection.getPeersNodeAddressOptional() must be present " +
                                        "at that moment");

                        final NodeAddress sender = connection.getPeersNodeAddressOptional().get();

                        List<ProtectedStorageEntry> processDelayedItems = new ArrayList<>();
                        dataSet.stream().forEach(e -> {
                            if (e.getStoragePayload() instanceof LazyProcessedStoragePayload)
                                processDelayedItems.add(e);
                            else {
                                // We dont broadcast here (last param) as we are only connected to the seed node and would be pointless
                                dataStorage.add(e, sender, null, false, false);
                            }
                        });

                        // We process the LazyProcessedStoragePayload items (TradeStatistics) in batches with a delay in between.
                        // We want avoid that the UI get stuck when processing many entries.
                        // The dataStorage.add call is a bit expensive as sig checks is done there.

                        // Using a background thread might be an alternative but it would require much more effort and 
                        // it would also decrease user experience if the app gets under heavy load (like at startup with wallet sync).
                        // Beside that we mitigated the problem already as we will not get the whole TradeStatistics as we 
                        // pass the excludeKeys and we pack the latest data dump 
                        // into the resources, so a new user do not need to request all data.

                        // In future we will probably limit by date or load on demand from user intent to not get too much data.

                        // We split the list into sub lists with max 50 items and delay each batch with 200 ms.
                        int size = processDelayedItems.size();
                        int chunkSize = 50;
                        int chunks = 1 + size / chunkSize;
                        int startIndex = 0;
                        for (int i = 0; i < chunks && startIndex < size; i++, startIndex += chunkSize) {
                            long delay = (i + 1) * 200;
                            int endIndex = Math.min(size, startIndex + chunkSize);
                            List<ProtectedStorageEntry> subList = processDelayedItems.subList(startIndex, endIndex);
                            UserThread.runAfter(() -> {
                                subList.stream().forEach(protectedStorageEntry -> dataStorage.add(protectedStorageEntry, sender, null, false, false));
                            }, delay, TimeUnit.MILLISECONDS);
                        }

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
        } else {
            log.trace("We got a message from another connection and ignore it.");
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
        networkNode.removeMessageListener(this);
        stopTimeoutTimer();
    }

    private void stopTimeoutTimer() {
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }
}
