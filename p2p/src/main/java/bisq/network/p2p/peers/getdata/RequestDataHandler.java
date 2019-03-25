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

package bisq.network.p2p.peers.getdata;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.CloseConnectionReason;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.peers.getdata.messages.GetDataRequest;
import bisq.network.p2p.peers.getdata.messages.GetDataResponse;
import bisq.network.p2p.peers.getdata.messages.GetUpdatedDataRequest;
import bisq.network.p2p.peers.getdata.messages.PreliminaryGetDataRequest;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.LazyProcessedPayload;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.network.NetworkPayload;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
class RequestDataHandler implements MessageListener {
    private static final long TIMEOUT = 90;
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
        peersNodeAddress = nodeAddress;
        if (!stopped) {
            GetDataRequest getDataRequest;

            // We collect the keys of the PersistableNetworkPayload items so we exclude them in our request.
            // PersistedStoragePayload items don't get removed, so we don't have an issue with the case that
            // an object gets removed in between PreliminaryGetDataRequest and the GetUpdatedDataRequest and we would
            // miss that event if we do not load the full set or use some delta handling.
            Set<byte[]> excludedKeys = dataStorage.getAppendOnlyDataStoreMap().keySet().stream()
                    .map(e -> e.bytes)
                    .collect(Collectors.toSet());

            Set<byte[]> excludedKeysFromPersistedEntryMap = dataStorage.getProtectedDataStoreMap().keySet()
                    .stream()
                    .map(e -> e.bytes)
                    .collect(Collectors.toSet());

            excludedKeys.addAll(excludedKeysFromPersistedEntryMap);

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
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (networkEnvelope instanceof GetDataResponse) {
            if (connection.getPeersNodeAddressOptional().isPresent() && connection.getPeersNodeAddressOptional().get().equals(peersNodeAddress)) {
                if (!stopped) {
                    GetDataResponse getDataResponse = (GetDataResponse) networkEnvelope;
                    Map<String, Set<NetworkPayload>> payloadByClassName = new HashMap<>();
                    final Set<ProtectedStorageEntry> dataSet = getDataResponse.getDataSet();
                    dataSet.stream().forEach(e -> {
                        final ProtectedStoragePayload protectedStoragePayload = e.getProtectedStoragePayload();
                        if (protectedStoragePayload == null) {
                            log.warn("StoragePayload was null: {}", networkEnvelope.toString());
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

                        // We changed the earlier behaviour with delayed execution of chunks of the list as it caused
                        // worse results as if it is processed in one go.
                        // Main reason is probably that listeners trigger more code and if that is called early at
                        // startup we have better chances that the user has not already navigated to a screen where the
                        // trade statistics are used for UI rendering.
                        // We need to take care that the update period between releases stay short as with the current
                        // situation before 0.9 release we receive 4000 objects with a newly installed client, which
                        // causes the application to stay stuck for quite a while at startup.
                        log.info("Start processing {} items.", processDelayedItems.size());
                        processDelayedItems.forEach(item -> {
                            if (item instanceof ProtectedStorageEntry)
                                dataStorage.addProtectedStorageEntry((ProtectedStorageEntry) item, sender, null,
                                        false, false);
                            else if (item instanceof PersistableNetworkPayload)
                                dataStorage.addPersistableNetworkPayload((PersistableNetworkPayload) item, sender,
                                        false, false, false, false);
                        });

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
