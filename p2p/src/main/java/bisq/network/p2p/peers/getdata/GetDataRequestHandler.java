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

import bisq.network.p2p.network.CloseConnectionReason;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.getdata.messages.GetDataRequest;
import bisq.network.p2p.peers.getdata.messages.GetDataResponse;
import bisq.network.p2p.peers.getdata.messages.GetUpdatedDataRequest;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.util.Utilities;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

@Slf4j
public class GetDataRequestHandler {
    private static final long TIMEOUT = 90;
    private static final int MAX_ENTRIES = 10000;


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
    private final P2PDataStorage dataStorage;
    private final Listener listener;
    private Timer timeoutTimer;
    private boolean stopped;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public GetDataRequestHandler(NetworkNode networkNode, P2PDataStorage dataStorage, Listener listener) {
        this.networkNode = networkNode;
        this.dataStorage = dataStorage;
        this.listener = listener;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void handle(GetDataRequest getDataRequest, final Connection connection) {
        long ts = System.currentTimeMillis();
        GetDataResponse getDataResponse = new GetDataResponse(getFilteredProtectedStorageEntries(getDataRequest, connection),
                getFilteredPersistableNetworkPayload(getDataRequest, connection),
                getDataRequest.getNonce(),
                getDataRequest instanceof GetUpdatedDataRequest);

        if (timeoutTimer == null) {
            timeoutTimer = UserThread.runAfter(() -> {  // setup before sending to avoid race conditions
                        String errorMessage = "A timeout occurred for getDataResponse " +
                                " on connection:" + connection;
                        handleFault(errorMessage, CloseConnectionReason.SEND_MSG_TIMEOUT, connection);
                    },
                    TIMEOUT, TimeUnit.SECONDS);
        }

        SettableFuture<Connection> future = networkNode.sendMessage(connection, getDataResponse);
        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(Connection connection) {
                if (!stopped) {
                    log.trace("Send DataResponse to {} succeeded. getDataResponse={}",
                            connection.getPeersNodeAddressOptional(), getDataResponse);
                    cleanup();
                    listener.onComplete();
                } else {
                    log.trace("We have stopped already. We ignore that networkNode.sendMessage.onSuccess call.");
                }
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                if (!stopped) {
                    String errorMessage = "Sending getDataRequest to " + connection +
                            " failed. That is expected if the peer is offline. getDataResponse=" + getDataResponse + "." +
                            "Exception: " + throwable.getMessage();
                    handleFault(errorMessage, CloseConnectionReason.SEND_MSG_FAILURE, connection);
                } else {
                    log.trace("We have stopped already. We ignore that networkNode.sendMessage.onFailure call.");
                }
            }
        });
        log.info("handle GetDataRequest took {} ms", System.currentTimeMillis() - ts);
    }

    private Set<PersistableNetworkPayload> getFilteredPersistableNetworkPayload(GetDataRequest getDataRequest,
                                                                                Connection connection) {
        Set<P2PDataStorage.ByteArray> tempLookupSet = new HashSet<>();
        String connectionInfo = "connectionInfo" + connection.getPeersNodeAddressOptional()
                .map(e -> "node address " + e.getFullAddress())
                .orElseGet(() -> "connection UID " + connection.getUid());

        Set<P2PDataStorage.ByteArray> excludedKeysAsByteArray = P2PDataStorage.ByteArray.convertBytesSetToByteArraySet(getDataRequest.getExcludedKeys());
        AtomicInteger maxSize = new AtomicInteger(MAX_ENTRIES);
        Set<PersistableNetworkPayload> result = dataStorage.getAppendOnlyDataStoreMap().entrySet().stream()
                .filter(e -> !excludedKeysAsByteArray.contains(e.getKey()))
                .filter(e -> maxSize.decrementAndGet() >= 0)
                .map(Map.Entry::getValue)
                .filter(connection::noCapabilityRequiredOrCapabilityIsSupported)
                .filter(payload -> {
                    boolean notContained = tempLookupSet.add(new P2PDataStorage.ByteArray(payload.getHash()));
                    return notContained;
                })
                .collect(Collectors.toSet());
        if (maxSize.get() <= 0) {
            log.warn("The getData request from peer with {} caused too much PersistableNetworkPayload " +
                            "entries to get delivered. We limited the entries for the response to {} entries",
                    connectionInfo, MAX_ENTRIES);
        }
        log.info("The getData request from peer with {} contains {} PersistableNetworkPayload entries ",
                connectionInfo, result.size());
        return result;
    }

    private Set<ProtectedStorageEntry> getFilteredProtectedStorageEntries(GetDataRequest getDataRequest,
                                                                          Connection connection) {
        Set<ProtectedStorageEntry> filteredDataSet = new HashSet<>();
        Set<Integer> lookupSet = new HashSet<>();
        String connectionInfo = "connectionInfo" + connection.getPeersNodeAddressOptional()
                .map(e -> "node address " + e.getFullAddress())
                .orElseGet(() -> "connection UID " + connection.getUid());

        AtomicInteger maxSize = new AtomicInteger(MAX_ENTRIES);
        Set<P2PDataStorage.ByteArray> excludedKeysAsByteArray = P2PDataStorage.ByteArray.convertBytesSetToByteArraySet(getDataRequest.getExcludedKeys());
        Set<ProtectedStorageEntry> filteredSet = dataStorage.getMap().entrySet().stream()
                .filter(e -> !excludedKeysAsByteArray.contains(e.getKey()))
                .filter(e -> maxSize.decrementAndGet() >= 0)
                .map(Map.Entry::getValue)
                .collect(Collectors.toSet());
        if (maxSize.get() <= 0) {
            log.warn("The getData request from peer with {} caused too much ProtectedStorageEntry " +
                            "entries to get delivered. We limited the entries for the response to {} entries",
                    connectionInfo, MAX_ENTRIES);
        }
        log.info("getFilteredProtectedStorageEntries " + filteredSet.size());

        for (ProtectedStorageEntry protectedStorageEntry : filteredSet) {
            final ProtectedStoragePayload protectedStoragePayload = protectedStorageEntry.getProtectedStoragePayload();
            boolean doAdd = false;
            if (protectedStoragePayload instanceof CapabilityRequiringPayload) {
                if (connection.getCapabilities().containsAll(((CapabilityRequiringPayload) protectedStoragePayload).getRequiredCapabilities()))
                    doAdd = true;
                else
                    log.debug("We do not send the message to the peer because they do not support the required capability for that message type.\n" +
                            "storagePayload is: " + Utilities.toTruncatedString(protectedStoragePayload));
            } else {
                doAdd = true;
            }
            if (doAdd) {
                boolean notContained = lookupSet.add(protectedStoragePayload.hashCode());
                if (notContained)
                    filteredDataSet.add(protectedStorageEntry);
            }
        }

        log.info("The getData request from peer with {} contains {} ProtectedStorageEntry entries ",
                connectionInfo, filteredDataSet.size());
        return filteredDataSet;
    }

    public void stop() {
        cleanup();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handleFault(String errorMessage, CloseConnectionReason closeConnectionReason, Connection connection) {
        if (!stopped) {
            log.info(errorMessage + "\n\tcloseConnectionReason=" + closeConnectionReason);
            cleanup();
            listener.onFault(errorMessage, connection);
        } else {
            log.warn("We have already stopped (handleFault)");
        }
    }

    private void cleanup() {
        stopped = true;
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }
}
