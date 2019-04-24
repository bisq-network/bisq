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
import bisq.network.p2p.network.ConnectionListener;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.peers.getdata.messages.GetDataRequest;
import bisq.network.p2p.peers.peerexchange.Peer;
import bisq.network.p2p.seed.SeedNodeRepository;
import bisq.network.p2p.storage.P2PDataStorage;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.proto.network.NetworkEnvelope;

import javax.inject.Inject;

import javafx.beans.property.SimpleObjectProperty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class RequestDataManager implements MessageListener, ConnectionListener, PeerManager.Listener {
    private static final long RETRY_DELAY_SEC = 10;
    private static final long CLEANUP_TIMER = 120;
    // How many seeds we request the PreliminaryGetDataRequest from
    private static int NUM_SEEDS_FOR_PRELIMINARY_REQUEST = 2;
    // how many seeds additional to the first responding PreliminaryGetDataRequest seed we request the GetUpdatedDataRequest from
    private static int NUM_ADDITIONAL_SEEDS_FOR_UPDATE_REQUEST = 1;
    private boolean isPreliminaryDataRequest = true;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface Listener {
        void onPreliminaryDataReceived();

        void onUpdatedDataReceived();

        void onDataReceived();

        void onNoPeersAvailable();

        void onNoSeedNodeAvailable();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final NetworkNode networkNode;
    private final P2PDataStorage dataStorage;
    private final PeerManager peerManager;
    private final Set<NodeAddress> seedNodeAddresses;
    private Listener listener;

    private final Map<NodeAddress, RequestDataHandler> handlerMap = new HashMap<>();
    private final Map<String, GetDataRequestHandler> getDataRequestHandlers = new HashMap<>();
    private Optional<NodeAddress> nodeAddressOfPreliminaryDataRequest = Optional.empty();
    private Timer retryTimer;
    private boolean dataUpdateRequested;
    private boolean stopped;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public RequestDataManager(NetworkNode networkNode,
                              SeedNodeRepository seedNodeRepository,
                              P2PDataStorage dataStorage,
                              PeerManager peerManager) {
        this.networkNode = networkNode;
        this.dataStorage = dataStorage;
        this.peerManager = peerManager;

        this.networkNode.addMessageListener(this);
        this.networkNode.addConnectionListener(this);
        this.peerManager.addListener(this);

        this.seedNodeAddresses = new HashSet<>(seedNodeRepository.getSeedNodeAddresses());

        this.networkNode.getNodeAddressProperty().addListener(observable -> {

            NodeAddress myAddress = (NodeAddress) ((SimpleObjectProperty) observable).get();

            seedNodeAddresses.remove(myAddress);

            if (myAddress != null && seedNodeRepository.isSeedNode(myAddress)) {
                NUM_SEEDS_FOR_PRELIMINARY_REQUEST = 3;
                NUM_ADDITIONAL_SEEDS_FOR_UPDATE_REQUEST = 2;
            }
        });
    }

    public void shutDown() {
        stopped = true;
        stopRetryTimer();
        networkNode.removeMessageListener(this);
        networkNode.removeConnectionListener(this);
        peerManager.removeListener(this);
        closeAllHandlers();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addListener(Listener listener) {
        this.listener = listener;
    }

    public boolean requestPreliminaryData() {
        ArrayList<NodeAddress> nodeAddresses = new ArrayList<>(seedNodeAddresses);
        if (!nodeAddresses.isEmpty()) {
            Collections.shuffle(nodeAddresses);
            ArrayList<NodeAddress> finalNodeAddresses = new ArrayList<>(nodeAddresses);
            final int size = Math.min(NUM_SEEDS_FOR_PRELIMINARY_REQUEST, finalNodeAddresses.size());
            for (int i = 0; i < size; i++) {
                NodeAddress nodeAddress = finalNodeAddresses.get(i);
                nodeAddresses.remove(nodeAddress);
                // We clone list to avoid mutable change during iterations
                List<NodeAddress> remainingNodeAddresses = new ArrayList<>(nodeAddresses);
                UserThread.runAfter(() -> requestData(nodeAddress, remainingNodeAddresses), (i * 200 + 1), TimeUnit.MILLISECONDS);
            }

            isPreliminaryDataRequest = true;
            return true;
        } else {
            return false;
        }
    }

    public void requestUpdateData() {
        checkArgument(nodeAddressOfPreliminaryDataRequest.isPresent(), "nodeAddressOfPreliminaryDataRequest must be present");
        dataUpdateRequested = true;
        isPreliminaryDataRequest = false;
        List<NodeAddress> nodeAddresses = new ArrayList<>(seedNodeAddresses);
        if (!nodeAddresses.isEmpty()) {
            // We use the node we have already connected to to request again
            nodeAddressOfPreliminaryDataRequest.ifPresent(candidate -> {
                nodeAddresses.remove(candidate);
                requestData(candidate, nodeAddresses);

                // For more redundancy we request as well from other random nodes.
                Collections.shuffle(nodeAddresses);
                ArrayList<NodeAddress> finalNodeAddresses = new ArrayList<>(nodeAddresses);
                int numRequests = 0;
                for (int i = 0; i < finalNodeAddresses.size() && numRequests < NUM_ADDITIONAL_SEEDS_FOR_UPDATE_REQUEST; i++) {
                    NodeAddress nodeAddress = finalNodeAddresses.get(i);
                    nodeAddresses.remove(nodeAddress);

                    // It might be that we have a prelim. request open for the same seed, if so we skip to the next.
                    if (!handlerMap.containsKey(nodeAddress)) {
                        UserThread.runAfter(() -> requestData(nodeAddress, nodeAddresses), (i * 200 + 1), TimeUnit.MILLISECONDS);
                        numRequests++;
                    }
                }
            });
        }
    }

    public Optional<NodeAddress> getNodeAddressOfPreliminaryDataRequest() {
        return nodeAddressOfPreliminaryDataRequest;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ConnectionListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnection(Connection connection) {
    }

    @Override
    public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
        closeHandler(connection);

        if (peerManager.isNodeBanned(closeConnectionReason, connection) && connection.getPeersNodeAddressOptional().isPresent()) {
            final NodeAddress nodeAddress = connection.getPeersNodeAddressOptional().get();
            seedNodeAddresses.remove(nodeAddress);
            handlerMap.remove(nodeAddress);
        }
    }

    @Override
    public void onError(Throwable throwable) {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PeerManager.Listener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAllConnectionsLost() {
        closeAllHandlers();
        stopRetryTimer();
        stopped = true;
        restart();
    }

    @Override
    public void onNewConnectionAfterAllConnectionsLost() {
        closeAllHandlers();
        stopped = false;
        restart();
    }

    @Override
    public void onAwakeFromStandby() {
        closeAllHandlers();
        stopped = false;
        if (!networkNode.getAllConnections().isEmpty())
            restart();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (networkEnvelope instanceof GetDataRequest) {
            if (!stopped) {
                if (peerManager.isSeedNode(connection))
                    connection.setPeerType(Connection.PeerType.SEED_NODE);

                final String uid = connection.getUid();
                if (!getDataRequestHandlers.containsKey(uid)) {
                    GetDataRequestHandler getDataRequestHandler = new GetDataRequestHandler(networkNode, dataStorage,
                            new GetDataRequestHandler.Listener() {
                                @Override
                                public void onComplete() {
                                    getDataRequestHandlers.remove(uid);
                                    log.trace("requestDataHandshake completed.\n\tConnection={}", connection);
                                }

                                @Override
                                public void onFault(String errorMessage, @Nullable Connection connection) {
                                    getDataRequestHandlers.remove(uid);
                                    if (!stopped) {
                                        log.trace("GetDataRequestHandler failed.\n\tConnection={}\n\t" +
                                                "ErrorMessage={}", connection, errorMessage);
                                        peerManager.handleConnectionFault(connection);
                                    } else {
                                        log.warn("We have stopped already. We ignore that getDataRequestHandler.handle.onFault call.");
                                    }
                                }
                            });
                    getDataRequestHandlers.put(uid, getDataRequestHandler);
                    getDataRequestHandler.handle((GetDataRequest) networkEnvelope, connection);
                } else {
                    log.warn("We have already a GetDataRequestHandler for that connection started. " +
                            "We start a cleanup timer if the handler has not closed by itself in between 2 minutes.");

                    UserThread.runAfter(() -> {
                        if (getDataRequestHandlers.containsKey(uid)) {
                            GetDataRequestHandler handler = getDataRequestHandlers.get(uid);
                            handler.stop();
                            getDataRequestHandlers.remove(uid);
                        }
                    }, CLEANUP_TIMER);
                }
            } else {
                log.warn("We have stopped already. We ignore that onMessage call.");
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // RequestData
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void requestData(NodeAddress nodeAddress, List<NodeAddress> remainingNodeAddresses) {
        if (!stopped) {
            if (!handlerMap.containsKey(nodeAddress)) {
                RequestDataHandler requestDataHandler = new RequestDataHandler(networkNode, dataStorage, peerManager,
                        new RequestDataHandler.Listener() {
                            @Override
                            public void onComplete() {
                                log.trace("RequestDataHandshake of outbound connection complete. nodeAddress={}",
                                        nodeAddress);
                                stopRetryTimer();

                                // need to remove before listeners are notified as they cause the update call
                                handlerMap.remove(nodeAddress);

                                // 1. We get a response from requestPreliminaryData
                                if (!nodeAddressOfPreliminaryDataRequest.isPresent()) {
                                    nodeAddressOfPreliminaryDataRequest = Optional.of(nodeAddress);
                                    // We delay because it can be that we get the HS published before we receive the
                                    // preliminary data and the onPreliminaryDataReceived call triggers the
                                    // dataUpdateRequested set to true, so we would also call the onUpdatedDataReceived.
                                    UserThread.runAfter(listener::onPreliminaryDataReceived, 100, TimeUnit.MILLISECONDS);
                                }

                                // 2. Later we get a response from requestUpdatesData
                                if (dataUpdateRequested) {
                                    dataUpdateRequested = false;
                                    listener.onUpdatedDataReceived();
                                }

                                listener.onDataReceived();
                            }

                            @Override
                            public void onFault(String errorMessage, @Nullable Connection connection) {
                                log.trace("requestDataHandshake with outbound connection failed.\n\tnodeAddress={}\n\t" +
                                        "ErrorMessage={}", nodeAddress, errorMessage);

                                peerManager.handleConnectionFault(nodeAddress);
                                handlerMap.remove(nodeAddress);

                                if (!remainingNodeAddresses.isEmpty()) {
                                    log.debug("There are remaining nodes available for requesting data. " +
                                            "We will try requestDataFromPeers again.");
                                    NodeAddress nextCandidate = remainingNodeAddresses.get(0);
                                    remainingNodeAddresses.remove(nextCandidate);
                                    requestData(nextCandidate, remainingNodeAddresses);
                                } else if (handlerMap.isEmpty()) {
                                    // If not other connection attempts are in the handlerMap we assume that no seed
                                    // nodes are available.
                                    log.debug("There is no remaining node available for requesting data. " +
                                            "That is expected if no other node is online.\n\t" +
                                            "We will try to use reported peers (if no available we use persisted peers) " +
                                            "and try again to request data from our seed nodes after a random pause.");

                                    // Notify listeners
                                    if (!nodeAddressOfPreliminaryDataRequest.isPresent()) {
                                        if (peerManager.isSeedNode(nodeAddress))
                                            listener.onNoSeedNodeAvailable();
                                        else
                                            listener.onNoPeersAvailable();
                                    }

                                    restart();
                                } else {
                                    log.info("We could not connect to seed node {} but we have other connection attempts open.", nodeAddress.getFullAddress());
                                }
                            }
                        });
                handlerMap.put(nodeAddress, requestDataHandler);
                requestDataHandler.requestData(nodeAddress, isPreliminaryDataRequest);
            } else {
                log.warn("We have started already a requestDataHandshake to peer. nodeAddress=" + nodeAddress + "\n" +
                        "We start a cleanup timer if the handler has not closed by itself in between 2 minutes.");

                UserThread.runAfter(() -> {
                    if (handlerMap.containsKey(nodeAddress)) {
                        RequestDataHandler handler = handlerMap.get(nodeAddress);
                        handler.stop();
                        handlerMap.remove(nodeAddress);
                    }
                }, CLEANUP_TIMER);
            }
        } else {
            log.warn("We have stopped already. We ignore that requestData call.");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void restart() {
        if (retryTimer == null) {
            retryTimer = UserThread.runAfter(() -> {
                        stopped = false;

                        stopRetryTimer();

                        // We create a new list of candidates
                        // 1. shuffled seedNodes
                        // 2. reported peers sorted by last activity date
                        // 3. Add as last persisted peers sorted by last activity date
                        List<NodeAddress> list = getFilteredList(new ArrayList<>(seedNodeAddresses), new ArrayList<>());
                        Collections.shuffle(list);

                        List<NodeAddress> filteredReportedPeers = getFilteredNonSeedNodeList(getSortedNodeAddresses(peerManager.getReportedPeers()), list);
                        list.addAll(filteredReportedPeers);

                        List<NodeAddress> filteredPersistedPeers = getFilteredNonSeedNodeList(getSortedNodeAddresses(peerManager.getPersistedPeers()), list);
                        list.addAll(filteredPersistedPeers);

                        if (!list.isEmpty()) {
                            NodeAddress nextCandidate = list.get(0);
                            list.remove(nextCandidate);
                            requestData(nextCandidate, list);
                        }
                    },
                    RETRY_DELAY_SEC);
        }
    }

    private List<NodeAddress> getSortedNodeAddresses(Collection<Peer> collection) {
        return new ArrayList<>(collection)
                .stream()
                .sorted((o1, o2) -> o2.getDate().compareTo(o1.getDate()))
                .map(Peer::getNodeAddress)
                .collect(Collectors.toList());
    }

    private List<NodeAddress> getFilteredList(Collection<NodeAddress> collection, List<NodeAddress> list) {
        return collection.stream()
                .filter(e -> !list.contains(e) &&
                        !peerManager.isSelf(e))
                .collect(Collectors.toList());
    }

    private List<NodeAddress> getFilteredNonSeedNodeList(Collection<NodeAddress> collection, List<NodeAddress> list) {
        return getFilteredList(collection, list).stream()
                .filter(e -> !peerManager.isSeedNode(e))
                .collect(Collectors.toList());
    }

    private void stopRetryTimer() {
        if (retryTimer != null) {
            retryTimer.stop();
            retryTimer = null;
        }
    }

    private void closeHandler(Connection connection) {
        Optional<NodeAddress> peersNodeAddressOptional = connection.getPeersNodeAddressOptional();
        if (peersNodeAddressOptional.isPresent()) {
            NodeAddress nodeAddress = peersNodeAddressOptional.get();
            if (handlerMap.containsKey(nodeAddress)) {
                handlerMap.get(nodeAddress).cancel();
                handlerMap.remove(nodeAddress);
            }
        } else {
            log.trace("closeRequestDataHandler: nodeAddress not set in connection {}", connection);
        }
    }

    private void closeAllHandlers() {
        handlerMap.values().forEach(RequestDataHandler::cancel);
        handlerMap.clear();
    }

}
