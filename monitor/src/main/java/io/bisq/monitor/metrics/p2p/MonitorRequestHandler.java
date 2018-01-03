package io.bisq.monitor.metrics.p2p;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.app.DevEnv;
import io.bisq.common.app.Log;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.common.proto.network.NetworkPayload;
import io.bisq.monitor.metrics.Metrics;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.network.CloseConnectionReason;
import io.bisq.network.p2p.network.Connection;
import io.bisq.network.p2p.network.MessageListener;
import io.bisq.network.p2p.network.NetworkNode;
import io.bisq.network.p2p.peers.getdata.messages.GetDataRequest;
import io.bisq.network.p2p.peers.getdata.messages.GetDataResponse;
import io.bisq.network.p2p.peers.getdata.messages.PreliminaryGetDataRequest;
import io.bisq.network.p2p.storage.P2PDataStorage;
import io.bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import io.bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import io.bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
class MonitorRequestHandler implements MessageListener {
    private static final long TIMEOUT = 120;
    private NodeAddress peersNodeAddress;
    private long requestTs;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface Listener {
        void onComplete();

        @SuppressWarnings("UnusedParameters")
        void onFault(String errorMessage, NodeAddress nodeAddress);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final NetworkNode networkNode;
    private P2PDataStorage dataStorage;
    private Metrics metrics;
    private final Listener listener;
    private Timer timeoutTimer;
    private final int nonce = new Random().nextInt();
    private boolean stopped;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public MonitorRequestHandler(NetworkNode networkNode, P2PDataStorage dataStorage, Metrics metrics, Listener listener) {
        this.networkNode = networkNode;
        this.dataStorage = dataStorage;
        this.metrics = metrics;
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
        peersNodeAddress = nodeAddress;
        requestTs = new Date().getTime();
        if (!stopped) {
            Set<byte[]> excludedKeys = dataStorage.getPersistableNetworkPayloadCollection().getMap().entrySet().stream()
                    .map(e -> e.getKey().bytes)
                    .collect(Collectors.toSet());

            GetDataRequest getDataRequest = new PreliminaryGetDataRequest(nonce, excludedKeys);

            if (timeoutTimer != null) {
                log.warn("timeoutTimer was already set. That must not happen.");
                timeoutTimer.stop();

                if (DevEnv.DEV_MODE)
                    throw new RuntimeException("timeoutTimer was already set. That must not happen.");
            }
            timeoutTimer = UserThread.runAfter(() -> {  // setup before sending to avoid race conditions
                        if (!stopped) {
                            String errorMessage = "A timeout occurred at sending getDataRequest:" + getDataRequest +
                                    " on nodeAddress:" + nodeAddress;
                            log.warn(errorMessage + " / RequestDataHandler=" + MonitorRequestHandler.this);
                            handleFault(errorMessage, nodeAddress, CloseConnectionReason.SEND_MSG_TIMEOUT);
                        } else {
                            log.trace("We have stopped already. We ignore that timeoutTimer.run call. " +
                                    "Might be caused by an previous networkNode.sendMessage.onFailure.");
                        }
                    },
                    TIMEOUT);

            log.info("We send a PreliminaryGetDataRequest to peer {}. ", nodeAddress);
            networkNode.addMessageListener(this);
            SettableFuture<Connection> future = networkNode.sendMessage(nodeAddress, getDataRequest);
            Futures.addCallback(future, new FutureCallback<Connection>() {
                @Override
                public void onSuccess(Connection connection) {
                    if (!stopped) {
                        log.info("Send PreliminaryGetDataRequest to " + nodeAddress + " has succeeded.");
                    } else {
                        log.trace("We have stopped already. We ignore that networkNode.sendMessage.onSuccess call." +
                                "Might be caused by an previous timeout.");
                    }
                }

                @Override
                public void onFailure(@NotNull Throwable throwable) {
                    if (!stopped) {
                        String errorMessage = "Sending getDataRequest to " + nodeAddress +
                                " failed.\n\t" +
                                "getDataRequest=" + getDataRequest + "." +
                                "\n\tException=" + throwable.getMessage();
                        log.warn(errorMessage);
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
        if (networkEnvelop instanceof GetDataResponse &&
                connection.getPeersNodeAddressOptional().isPresent() &&
                connection.getPeersNodeAddressOptional().get().equals(peersNodeAddress)) {
            Log.traceCall(networkEnvelop.toString() + "\n\tconnection=" + connection);
            if (!stopped) {
                GetDataResponse getDataResponse = (GetDataResponse) networkEnvelop;
                if (getDataResponse.getRequestNonce() == nonce) {
                    stopTimeoutTimer();

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
                    sb.append("Connected to node: ").append(peersNodeAddress.getFullAddress()).append("\n");
                    final int items = dataSet.size() +
                            (persistableNetworkPayloadSet != null ? persistableNetworkPayloadSet.size() : 0);
                    sb.append("Received ").append(items).append(" instances\n");
                    Map<String, Integer> receivedObjects = new HashMap<>();
                    final boolean[] arbitratorReceived = new boolean[1];
                    payloadByClassName.entrySet().stream().forEach(e -> {
                        final String dataItemName = e.getKey();
                        // We expect always at least an Arbitrator
                        if (!arbitratorReceived[0] && dataItemName.equals("Arbitrator"))
                            arbitratorReceived[0] = true;

                        sb.append(dataItemName)
                                .append(": ")
                                .append(e.getValue().size())
                                .append("\n");
                        receivedObjects.put(dataItemName, e.getValue().size());
                    });
                    sb.append("#################################################################");
                    log.info(sb.toString());
                    metrics.getReceivedObjectsList().add(receivedObjects);

                    final long duration = new Date().getTime() - requestTs;
                    log.info("Requesting data took {} ms", duration);
                    metrics.getRequestDurations().add(duration);
                    metrics.getErrorMessages().add(arbitratorReceived[0] ? "" : "No Arbitrator objects received! Seed node need to be restarted!");

                    cleanup();
                    connection.shutDown(CloseConnectionReason.CLOSE_REQUESTED_BY_PEER, listener::onComplete);
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
        // We do not log every error only if it fails several times in a row.

        // We do not close the connection as it might be we have opened a new connection for that peer and
        // we don't want to close that. We do not know the connection at fault as the fault handler does not contain that,
        // so we could only search for connections for that nodeAddress but that would close an new connection attempt.
        listener.onFault(errorMessage, nodeAddress);
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
