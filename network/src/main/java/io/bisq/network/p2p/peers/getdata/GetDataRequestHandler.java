package io.bisq.network.p2p.peers.getdata;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.common.util.Utilities;
import io.bisq.network.p2p.network.CloseConnectionReason;
import io.bisq.network.p2p.network.Connection;
import io.bisq.network.p2p.network.NetworkNode;
import io.bisq.network.p2p.peers.getdata.messages.GetDataRequest;
import io.bisq.network.p2p.peers.getdata.messages.GetDataResponse;
import io.bisq.network.p2p.peers.getdata.messages.GetUpdatedDataRequest;
import io.bisq.network.p2p.storage.P2PDataStorage;
import io.bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import io.bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import io.bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import io.bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GetDataRequestHandler {
    private static final Logger log = LoggerFactory.getLogger(GetDataRequestHandler.class);

    private static final long TIME_OUT_SEC = 60;


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
        Log.traceCall(getDataRequest + "\n\tconnection=" + connection);

        final Set<ProtectedStorageEntry> filteredDataSet = new HashSet<>();
        final Set<Integer> lookupSet = new HashSet<>();

        Set<P2PDataStorage.ByteArray> excludedKeysAsByteArray =  P2PDataStorage.ByteArray.convertBytesSetToByteArraySet(getDataRequest.getExcludedKeys());
        Set<ProtectedStorageEntry> filteredSet = dataStorage.getMap().entrySet().stream()
                .filter(e -> !excludedKeysAsByteArray.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .collect(Collectors.toSet());
        
        for (ProtectedStorageEntry protectedStorageEntry : filteredSet) {
            final ProtectedStoragePayload protectedStoragePayload = protectedStorageEntry.getProtectedStoragePayload();
            boolean doAdd = false;
            if (protectedStoragePayload instanceof CapabilityRequiringPayload) {
                final List<Integer> requiredCapabilities = ((CapabilityRequiringPayload) protectedStoragePayload).getRequiredCapabilities();
                final List<Integer> supportedCapabilities = connection.getSupportedCapabilities();
                if (supportedCapabilities != null) {
                    for (int messageCapability : requiredCapabilities) {
                        for (int connectionCapability : supportedCapabilities) {
                            if (messageCapability == connectionCapability) {
                                doAdd = true;
                                break;
                            }
                        }
                    }
                    if (!doAdd)
                        log.debug("We do not send the message to the peer because he does not support the required capability for that message type.\n" +
                                "Required capabilities is: " + requiredCapabilities.toString() + "\n" +
                                "Supported capabilities is: " + supportedCapabilities.toString() + "\n" +
                                "storagePayload is: " + Utilities.toTruncatedString(protectedStoragePayload));
                } else {
                    log.debug("We do not send the message to the peer because he uses an old version which does not support capabilities.\n" +
                            "Required capabilities is: " + requiredCapabilities.toString() + "\n" +
                            "storagePayload is: " + Utilities.toTruncatedString(protectedStoragePayload));
                }
            } else {
                doAdd = true;
            }
            if (doAdd) {
                // We have TradeStatistic data of both traders but we only send 1 item, 
                // so we use lookupSet as for a fast lookup. Using filteredDataSet would require a loop as it stores 
                // protectedStorageEntry not storagePayload. protectedStorageEntry is different for both traders but storagePayload not, 
                // as we ignore the pubKey and data there in the hashCode method.
                boolean notContained = lookupSet.add(protectedStoragePayload.hashCode());
                if (notContained)
                    filteredDataSet.add(protectedStorageEntry);
            }
        }

        Set<P2PDataStorage.ByteArray> excludedPnpKeysAsByteArray =  P2PDataStorage.ByteArray.convertBytesSetToByteArraySet(getDataRequest.getExcludedPnpKeys());
        Set<PersistableNetworkPayload> filteredPnpSet = dataStorage.getPersistableNetworkPayloadCollection().getMap().entrySet().stream()
                .filter(e -> !excludedPnpKeysAsByteArray.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .collect(Collectors.toSet());

        GetDataResponse getDataResponse = new GetDataResponse(filteredDataSet,
                filteredPnpSet,
                getDataRequest.getNonce(),
                getDataRequest instanceof GetUpdatedDataRequest);

        if (timeoutTimer == null) {
            timeoutTimer = UserThread.runAfter(() -> {  // setup before sending to avoid race conditions
                        String errorMessage = "A timeout occurred for getDataResponse:" + getDataResponse +
                                " on connection:" + connection;
                        handleFault(errorMessage, CloseConnectionReason.SEND_MSG_TIMEOUT, connection);
                    },
                    TIME_OUT_SEC, TimeUnit.SECONDS);
        }

        SettableFuture<Connection> future = networkNode.sendMessage(connection, getDataResponse);
        Futures.addCallback(future, new FutureCallback<Connection>() {
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
    }

    public void stop() {
        cleanup();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handleFault(String errorMessage, CloseConnectionReason closeConnectionReason, Connection connection) {
        if (!stopped) {
            log.debug(errorMessage + "\n\tcloseConnectionReason=" + closeConnectionReason);
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
