/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.monitor.metrics.p2p;

import bisq.monitor.metrics.Metrics;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.CloseConnectionReason;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.getdata.messages.GetDataRequest;
import bisq.network.p2p.peers.getdata.messages.GetDataResponse;
import bisq.network.p2p.peers.getdata.messages.PreliminaryGetDataRequest;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.app.Log;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.network.NetworkPayload;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

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
    private final P2PDataStorage dataStorage;
    private final Metrics metrics;
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
            Set<byte[]> excludedKeys = dataStorage.getAppendOnlyDataStoreMap().entrySet().stream()
                    .map(e -> e.getKey().bytes)
                    .collect(Collectors.toSet());

            GetDataRequest getDataRequest = new PreliminaryGetDataRequest(nonce, excludedKeys);
            metrics.setLastDataRequestTs(System.currentTimeMillis());

            if (timeoutTimer != null) {
                log.warn("timeoutTimer was already set. That must not happen.");
                timeoutTimer.stop();

                if (DevEnv.isDevMode())
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
                    metrics.setLastDataResponseTs(System.currentTimeMillis());

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
