package io.bisq.network.p2p.peers.getdata;

import com.google.inject.name.Named;
import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.network.NetworkOptionKeys;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.network.*;
import io.bisq.network.p2p.peers.PeerManager;
import io.bisq.network.p2p.peers.getdata.messages.GetDataRequest;
import io.bisq.network.p2p.peers.peerexchange.Peer;
import io.bisq.network.p2p.seed.SeedNodesRepository;
import io.bisq.network.p2p.storage.P2PDataStorage;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    private Optional<NodeAddress> nodeAddressOfPreliminaryDataRequest = Optional.<NodeAddress>empty();
    private Timer retryTimer;
    private boolean dataUpdateRequested;
    private boolean stopped;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public RequestDataManager(NetworkNode networkNode,
                              SeedNodesRepository seedNodesRepository,
                              P2PDataStorage dataStorage,
                              PeerManager peerManager,
                              @javax.annotation.Nullable @Named(NetworkOptionKeys.MY_ADDRESS) String myAddress) {
        this.networkNode = networkNode;
        this.dataStorage = dataStorage;
        this.peerManager = peerManager;

        this.networkNode.addMessageListener(this);
        this.networkNode.addConnectionListener(this);
        this.peerManager.addListener(this);

        this.seedNodeAddresses = new HashSet<>(seedNodesRepository.getSeedNodeAddresses());

        // If we are a seed node we use more redundancy at startup to be sure we get all data.
        // We cannot use networkNode.getNodeAddress() as nodeAddress as that is null at this point, so we use
        // new NodeAddress(myAddress) for checking if we are a seed node.
        // seedNodeAddresses do not contain my own address as that gets filtered out
        if (myAddress != null && !myAddress.isEmpty() && seedNodesRepository.isSeedNode(new NodeAddress(myAddress))) {
            NUM_SEEDS_FOR_PRELIMINARY_REQUEST = 3;
            NUM_ADDITIONAL_SEEDS_FOR_UPDATE_REQUEST = 2;
        }
    }

    public void shutDown() {
        Log.traceCall();
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
        Log.traceCall();
        ArrayList<NodeAddress> nodeAddresses = new ArrayList<>(seedNodeAddresses);
        if (!nodeAddresses.isEmpty()) {
            Collections.shuffle(nodeAddresses);
            ArrayList<NodeAddress> finalNodeAddresses = new ArrayList<>(nodeAddresses);
            final int size = Math.min(NUM_SEEDS_FOR_PRELIMINARY_REQUEST, finalNodeAddresses.size());
            for (int i = 0; i < size; i++) {
                NodeAddress nodeAddress = finalNodeAddresses.get(i);
                nodeAddresses.remove(nodeAddress);
                UserThread.runAfter(() -> requestData(nodeAddress, nodeAddresses), (i * 200 + 1), TimeUnit.MILLISECONDS);
            }

            isPreliminaryDataRequest = true;
            return true;
        } else {
            return false;
        }
    }

    public void requestUpdateData() {
        Log.traceCall();
        checkArgument(nodeAddressOfPreliminaryDataRequest.isPresent(), "nodeAddressOfPreliminaryDataRequest must be present");
        dataUpdateRequested = true;
        isPreliminaryDataRequest = false;
        List<NodeAddress> nodeAddresses = new ArrayList<>(seedNodeAddresses);
        if (!nodeAddresses.isEmpty()) {
            // We use the node we have already connected to to request again
            NodeAddress candidate = nodeAddressOfPreliminaryDataRequest.get();
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
        Log.traceCall();
    }

    @Override
    public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
        Log.traceCall();
        closeHandler(connection);

        if (peerManager.isNodeBanned(closeConnectionReason, connection)) {
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
        Log.traceCall();
        closeAllHandlers();
        stopRetryTimer();
        stopped = true;
        restart();
    }

    @Override
    public void onNewConnectionAfterAllConnectionsLost() {
        Log.traceCall();
        closeAllHandlers();
        stopped = false;
        restart();
    }

    @Override
    public void onAwakeFromStandby() {
        Log.traceCall();
        closeAllHandlers();
        stopped = false;
        if (!networkNode.getAllConnections().isEmpty())
            restart();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(NetworkEnvelope networkEnvelop, Connection connection) {
        if (networkEnvelop instanceof GetDataRequest) {
            Log.traceCall(networkEnvelop.toString() + "\n\tconnection=" + connection);
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
                    getDataRequestHandler.handle((GetDataRequest) networkEnvelop, connection);
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
        Log.traceCall("nodeAddress=" + nodeAddress + " /  remainingNodeAddresses=" + remainingNodeAddresses);
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
                                    UserThread.runAfter(listener::onPreliminaryDataReceived, 100 , TimeUnit.MILLISECONDS);
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
                                } else {
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
        Log.traceCall();
        if (retryTimer == null) {
            retryTimer = UserThread.runAfter(() -> {
                        log.trace("retryTimer called");
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
        return collection.stream()
                .collect(Collectors.toList())
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
            log.trace("closeRequestDataHandler: nodeAddress not set in connection " + connection);
        }
    }

    private void closeAllHandlers() {
        handlerMap.values().stream().forEach(RequestDataHandler::cancel);
        handlerMap.clear();
    }

}
