package io.bisq.network.p2p.peers.getdata;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.common.proto.network.NetworkPayload;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.network.CloseConnectionReason;
import io.bisq.network.p2p.network.Connection;
import io.bisq.network.p2p.network.MessageListener;
import io.bisq.network.p2p.network.NetworkNode;
import io.bisq.network.p2p.peers.PeerManager;
import io.bisq.network.p2p.peers.getdata.messages.GetDataRequest;
import io.bisq.network.p2p.peers.getdata.messages.GetDataResponse;
import io.bisq.network.p2p.peers.getdata.messages.GetUpdatedDataRequest;
import io.bisq.network.p2p.peers.getdata.messages.PreliminaryGetDataRequest;
import io.bisq.network.p2p.storage.P2PDataStorage;
import io.bisq.network.p2p.storage.payload.LazyProcessedPayload;
import io.bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import io.bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import io.bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
class RequestDataHandler implements MessageListener {
    private static final long TIMEOUT = 60;
    private NodeAddress peersNodeAddress;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface Listener {
        void onComplete();

        @SuppressWarnings("UnusedParameters")
        void onFault(String errorMessage, @SuppressWarnings("SameParameterValue") @Nullable Connection connection);
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public RequestDataHandler(NetworkNode networkNode,
                              P2PDataStorage dataStorage,
                              PeerManager peerManager,
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
            Set<byte[]> excludedKeys = dataStorage.getPersistableNetworkPayloadCollection().getMap().entrySet().stream()
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
                        TIMEOUT);
            }

            log.info("We send a {} to peer {}. ", getDataRequest.getClass().getSimpleName(), nodeAddress);
            networkNode.addMessageListener(this);
            SettableFuture<Connection> future = networkNode.sendMessage(nodeAddress, getDataRequest);
            Futures.addCallback(future, new FutureCallback<Connection>() {
                @Override
                public void onSuccess(Connection connection) {
                    if (!stopped) {
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
    public void onMessage(NetworkEnvelope networkEnvelop, Connection connection) {
        if (networkEnvelop instanceof GetDataResponse) {
            if (connection.getPeersNodeAddressOptional().isPresent() && connection.getPeersNodeAddressOptional().get().equals(peersNodeAddress)) {
                Log.traceCall(networkEnvelop.toString() + "\n\tconnection=" + connection);
                if (!stopped) {
                    GetDataResponse getDataResponse = (GetDataResponse) networkEnvelop;
                    Map<String, Set<NetworkPayload>> payloadByClassName = new HashMap<>();
                    final Set<ProtectedStorageEntry> dataSet = getDataResponse.getDataSet();
                    dataSet.stream().forEach(e -> {
                        final ProtectedStoragePayload protectedStoragePayload = e.getProtectedStoragePayload();
                        if (protectedStoragePayload == null) {
                            log.warn("StoragePayload was null: {}", networkEnvelop.toString());
                            return;
                        }

                        // For logging different data types
                        String className = protectedStoragePayload.getClass().getSimpleName();
                        if (!payloadByClassName.containsKey(className))
                            payloadByClassName.put(className, new HashSet<>());

                        payloadByClassName.get(className).add(protectedStoragePayload);
                    });


                    Set<PersistableNetworkPayload> persistableNetworkPayloadSet = getDataResponse.getPersistableNetworkPayloadSet();
                    if (persistableNetworkPayloadSet != null) {
                        persistableNetworkPayloadSet.stream().forEach(persistableNetworkPayload -> {
                            // For logging different data types
                            String className = persistableNetworkPayload.getClass().getSimpleName();
                            if (!payloadByClassName.containsKey(className))
                                payloadByClassName.put(className, new HashSet<>());

                            payloadByClassName.get(className).add(persistableNetworkPayload);
                        });
                    }

                    // Log different data types
                    StringBuilder sb = new StringBuilder();
                    sb.append("\n#################################################################\n");
                    sb.append("Connected to node: " + peersNodeAddress.getFullAddress() + "\n");
                    final int items = dataSet.size() +
                            (persistableNetworkPayloadSet != null ? persistableNetworkPayloadSet.size() : 0);
                    sb.append("Received ").append(items).append(" instances\n");
                    payloadByClassName.entrySet().stream().forEach(e -> sb.append(e.getKey())
                            .append(": ")
                            .append(e.getValue().size())
                            .append("\n"));
                    sb.append("#################################################################");
                    log.info(sb.toString());

                    if (getDataResponse.getRequestNonce() == nonce) {
                        stopTimeoutTimer();
                        checkArgument(connection.getPeersNodeAddressOptional().isPresent(),
                                "RequestDataHandler.onMessage: connection.getPeersNodeAddressOptional() must be present " +
                                        "at that moment");

                        final NodeAddress sender = connection.getPeersNodeAddressOptional().get();

                        List<NetworkPayload> processDelayedItems = new ArrayList<>();
                        dataSet.stream().forEach(e -> {
                            if (e.getProtectedStoragePayload() instanceof LazyProcessedPayload) {
                                processDelayedItems.add(e);
                            } else {
                                // We dont broadcast here (last param) as we are only connected to the seed node and would be pointless
                                dataStorage.addProtectedStorageEntry(e, sender, null, false, false);
                            }
                        });

                        if (persistableNetworkPayloadSet != null) {
                            persistableNetworkPayloadSet.stream().forEach(e -> {
                                if (e instanceof LazyProcessedPayload) {
                                    processDelayedItems.add(e);
                                } else {
                                    // We dont broadcast here as we are only connected to the seed node and would be pointless
                                    dataStorage.addPersistableNetworkPayload(e, sender, false, false, false, false);
                                }
                            });
                        }

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
                            List<NetworkPayload> subList = processDelayedItems.subList(startIndex, endIndex);
                            UserThread.runAfter(() -> subList.stream().forEach(item -> {
                                if (item instanceof ProtectedStorageEntry)
                                    dataStorage.addProtectedStorageEntry((ProtectedStorageEntry) item, sender, null, false, false);
                                else if (item instanceof PersistableNetworkPayload)
                                    dataStorage.addPersistableNetworkPayload((PersistableNetworkPayload) item, sender, false, false, false, false);
                            }), delay, TimeUnit.MILLISECONDS);
                        }

                        cleanup();
                        listener.onComplete();
                    } else {
                        log.debug("Nonce not matching. That can happen rarely if we get a response after a canceled " +
                                        "handshake (timeout causes connection close but peer might have sent a msg before " +
                                        "connection was closed).\n\t" +
                                        "We drop that message. nonce={} / requestNonce={}",
                                nonce, getDataResponse.getRequestNonce());
                    }
                } else {
                    log.warn("We have stopped already. We ignore that onDataRequest call.");
                }
            } else {
                log.debug("We got the message from another connection and ignore it on that handler. That is expected if we have several requests open.");
            }
        }
    }

    public void stop() {
        cleanup();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////


    @SuppressWarnings("UnusedParameters")
    private void handleFault(String errorMessage, NodeAddress nodeAddress, CloseConnectionReason closeConnectionReason) {
        cleanup();
        log.info(errorMessage);
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
