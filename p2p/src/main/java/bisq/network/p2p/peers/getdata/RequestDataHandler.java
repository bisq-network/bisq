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
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Slf4j
class RequestDataHandler implements MessageListener {
    private static final long TIMEOUT = 180;

    private NodeAddress peersNodeAddress;
    private String getDataRequestType;
    /*
     */

    /**
     * when we are run as a seed node, we spawn a RequestDataHandler every hour. However, we do not want to receive
     * {@link PersistableNetworkPayload}s (for now, as there are hardly any cases where such data goes out of sync). This
     * flag indicates whether we already received our first set of {@link PersistableNetworkPayload}s.
     *//*
    private static boolean firstRequest = true;*/

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

    RequestDataHandler(NetworkNode networkNode,
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

    void requestData(NodeAddress nodeAddress, boolean isPreliminaryDataRequest) {
        peersNodeAddress = nodeAddress;
        if (!stopped) {
            GetDataRequest getDataRequest;

            if (isPreliminaryDataRequest)
                getDataRequest = dataStorage.buildPreliminaryGetDataRequest(nonce);
            else
                getDataRequest = dataStorage.buildGetUpdatedDataRequest(networkNode.getNodeAddress(), nonce);

            if (timeoutTimer == null) {
                timeoutTimer = UserThread.runAfter(() -> {  // setup before sending to avoid race conditions
                            if (!stopped) {
                                String errorMessage = "A timeout occurred at sending getDataRequest:" + getDataRequest +
                                        " on nodeAddress:" + nodeAddress;
                                log.debug(errorMessage + " / RequestDataHandler=" + RequestDataHandler.this);
                                handleFault(errorMessage, nodeAddress, CloseConnectionReason.SEND_MSG_TIMEOUT);
                            } else {
                                log.trace("We have stopped already. We ignore that timeoutTimer.run call. " +
                                        "Might be caused by a previous networkNode.sendMessage.onFailure.");
                            }
                        },
                        TIMEOUT);
            }

            getDataRequestType = getDataRequest.getClass().getSimpleName();
            log.info("We send a {} to peer {}. ", getDataRequestType, nodeAddress);
            networkNode.addMessageListener(this);
            SettableFuture<Connection> future = networkNode.sendMessage(nodeAddress, getDataRequest);
            //noinspection UnstableApiUsage
            Futures.addCallback(future, new FutureCallback<>() {
                @Override
                public void onSuccess(Connection connection) {
                    if (!stopped) {
                        log.trace("Send {} to {} succeeded.", getDataRequest, nodeAddress);
                    } else {
                        log.trace("We have stopped already. We ignore that networkNode.sendMessage.onSuccess call." +
                                "Might be caused by a previous timeout.");
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
                                "Might be caused by a previous timeout.");
                    }
                }
            }, MoreExecutors.directExecutor());
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
                    long ts1 = System.currentTimeMillis();
                    GetDataResponse getDataResponse = (GetDataResponse) networkEnvelope;
                    logContents(getDataResponse);
                    if (getDataResponse.getRequestNonce() == nonce) {
                        stopTimeoutTimer();
                        if (!connection.getPeersNodeAddressOptional().isPresent()) {
                            log.error("RequestDataHandler.onMessage: connection.getPeersNodeAddressOptional() must be present " +
                                    "at that moment");
                            return;
                        }

                        dataStorage.processGetDataResponse(getDataResponse,
                                connection.getPeersNodeAddressOptional().get());

                        cleanup();
                        listener.onComplete();
                        // firstRequest = false;
                    } else {
                        log.warn("Nonce not matching. That can happen rarely if we get a response after a canceled " +
                                        "handshake (timeout causes connection close but peer might have sent a msg before " +
                                        "connection was closed).\n\t" +
                                        "We drop that message. nonce={} / requestNonce={}",
                                nonce, getDataResponse.getRequestNonce());
                    }
                    log.info("Processing GetDataResponse took {} ms", System.currentTimeMillis() - ts1);
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

    private void logContents(GetDataResponse getDataResponse) {
        Set<ProtectedStorageEntry> dataSet = getDataResponse.getDataSet();
        Set<PersistableNetworkPayload> persistableNetworkPayloadSet = getDataResponse.getPersistableNetworkPayloadSet();
        Map<String, Tuple2<AtomicInteger, AtomicInteger>> numPayloadsByClassName = new HashMap<>();
        dataSet.forEach(protectedStorageEntry -> {
            String className = protectedStorageEntry.getProtectedStoragePayload().getClass().getSimpleName();
            addDetails(numPayloadsByClassName, protectedStorageEntry, className);
        });
        persistableNetworkPayloadSet.forEach(persistableNetworkPayload -> {
            String className = persistableNetworkPayload.getClass().getSimpleName();
            addDetails(numPayloadsByClassName, persistableNetworkPayload, className);
        });
        StringBuilder sb = new StringBuilder();
        String sep = System.lineSeparator();
        sb.append(sep).append("#################################################################").append(sep);
        sb.append("Connected to node: ").append(peersNodeAddress.getFullAddress()).append(sep);
        int items = dataSet.size() + persistableNetworkPayloadSet.size();
        sb.append("Received ").append(items).append(" instances from a ")
                .append(getDataRequestType).append(sep);
        numPayloadsByClassName.forEach((key, value) -> sb.append(key)
                .append(": ")
                .append(value.first.get())
                .append(" / ")
                .append(Utilities.readableFileSize(value.second.get()))
                .append(sep));
        sb.append("#################################################################");
        log.info(sb.toString());
    }

    private void addDetails(Map<String, Tuple2<AtomicInteger, AtomicInteger>> numPayloadsByClassName,
                            NetworkPayload networkPayload, String className) {
        numPayloadsByClassName.putIfAbsent(className, new Tuple2<>(new AtomicInteger(0),
                new AtomicInteger(0)));
        numPayloadsByClassName.get(className).first.getAndIncrement();
        // toProtoMessage().getSerializedSize() is not very cheap. For about 1500 objects it takes about 20 ms
        // I think its justified to get accurate metrics but if it turns out to be a performance issue we might need
        // to remove it and use some more rough estimation by taking only the size of one data type and multiply it.
        numPayloadsByClassName.get(className).second.getAndAdd(networkPayload.toProtoMessage().getSerializedSize());
    }

    @SuppressWarnings("UnusedParameters")
    private void handleFault(String errorMessage,
                             NodeAddress nodeAddress,
                             CloseConnectionReason closeConnectionReason) {
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
