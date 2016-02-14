package io.bitsquare.p2p.peers;

import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.MessageListener;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.p2p.peers.messages.data.GetDataRequest;
import io.bitsquare.p2p.storage.P2PDataStorage;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

public class RequestDataManager implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(RequestDataManager.class);

    private static final long RETRY_DELAY_SEC = 10;


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
    private final Collection<NodeAddress> seedNodeAddresses;
    private final Listener listener;

    private final Map<NodeAddress, RequestDataHandshake> requestDataHandshakeMap = new HashMap<>();
    private Optional<NodeAddress> nodeOfPreliminaryDataRequest = Optional.empty();
    private Timer requestDataTimer;
    private boolean dataUpdateRequested;
    private boolean shutDownInProgress;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public RequestDataManager(NetworkNode networkNode, P2PDataStorage dataStorage, PeerManager peerManager,
                              Set<NodeAddress> seedNodeAddresses, Listener listener) {
        this.networkNode = networkNode;
        this.dataStorage = dataStorage;
        this.peerManager = peerManager;
        this.seedNodeAddresses = new HashSet<>(seedNodeAddresses);
        this.listener = listener;

        checkArgument(!seedNodeAddresses.isEmpty(), "seedNodeAddresses must not be empty.");
        networkNode.addMessageListener(this);
    }

    public void shutDown() {
        Log.traceCall();
        shutDownInProgress = true;
        stopRequestDataTimer();
        networkNode.removeMessageListener(this);
        requestDataHandshakeMap.values().stream().forEach(RequestDataHandshake::shutDown);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void requestPreliminaryData() {
        Log.traceCall();
        ArrayList<NodeAddress> nodeAddresses = new ArrayList<>(seedNodeAddresses);
        Collections.shuffle(nodeAddresses);
        NodeAddress nextCandidate = nodeAddresses.get(0);
        nodeAddresses.remove(nextCandidate);
        requestData(nextCandidate, nodeAddresses);
    }

    public void requestUpdatesData() {
        Log.traceCall();
        checkArgument(nodeOfPreliminaryDataRequest.isPresent(), "seedNodeOfPreliminaryDataRequest must be present");
        dataUpdateRequested = true;
        List<NodeAddress> remainingNodeAddresses = new ArrayList<>(seedNodeAddresses);
        Collections.shuffle(remainingNodeAddresses);
        NodeAddress candidate = nodeOfPreliminaryDataRequest.get();
        remainingNodeAddresses.remove(candidate);
        requestData(candidate, remainingNodeAddresses);
    }

    public Optional<NodeAddress> getNodeOfPreliminaryDataRequest() {
        return nodeOfPreliminaryDataRequest;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof GetDataRequest) {
            Log.traceCall(message.toString() + "\n\tconnection=" + connection);
            RequestDataHandshake requestDataHandshake = new RequestDataHandshake(networkNode, dataStorage, peerManager,
                    new RequestDataHandshake.Listener() {
                        @Override
                        public void onComplete() {
                            log.trace("requestDataHandshake of inbound connection complete.\n\tConnection={}",
                                    connection);
                        }

                        @Override
                        public void onFault(String errorMessage, @Nullable Connection connection) {
                            log.trace("requestDataHandshake of inbound connection failed.\n\tConnection={}\n\t" +
                                    "ErrorMessage={}", connection, errorMessage);
                            peerManager.handleConnectionFault(connection);
                        }
                    });
            requestDataHandshake.onDataRequest(message, connection);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void requestData(NodeAddress nodeAddress, List<NodeAddress> remainingNodeAddresses) {
        Log.traceCall("nodeAddress=" + nodeAddress + " /  remainingNodeAddresses=" + remainingNodeAddresses);
        if (!requestDataHandshakeMap.containsKey(nodeAddress)) {
            RequestDataHandshake requestDataHandshake = new RequestDataHandshake(networkNode, dataStorage, peerManager,
                    new RequestDataHandshake.Listener() {
                        @Override
                        public void onComplete() {
                            log.trace("RequestDataHandshake of outbound connection complete. nodeAddress={}",
                                    nodeAddress);
                            stopRequestDataTimer();

                            // need to remove before listeners are notified as they cause the update call
                            requestDataHandshakeMap.remove(nodeAddress);

                            // 1. We get a response from requestPreliminaryData
                            if (!nodeOfPreliminaryDataRequest.isPresent()) {
                                nodeOfPreliminaryDataRequest = Optional.of(nodeAddress);
                                listener.onPreliminaryDataReceived();
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
                            log.trace("requestDataHandshake of outbound connection failed.\n\tnodeAddress={}\n\t" +
                                    "ErrorMessage={}", nodeAddress, errorMessage);

                            peerManager.handleConnectionFault(nodeAddress, connection);

                            if (!shutDownInProgress) {
                                if (!remainingNodeAddresses.isEmpty()) {
                                    log.info("There are remaining nodes available for requesting data. " +
                                            "We will try requestDataFromPeers again.");
                                    NodeAddress nextCandidate = remainingNodeAddresses.get(0);
                                    remainingNodeAddresses.remove(nextCandidate);
                                    requestData(nextCandidate, remainingNodeAddresses);
                                } else {
                                    log.info("There is no remaining node available for requesting data. " +
                                            "That is expected if no other node is online.\n\t" +
                                            "We will try to use reported peers (if no available we use persisted peers) " +
                                            "and try again to request data from our seed nodes after a random pause.");

                                    // try again after a pause
                                    stopRequestDataTimer();
                                    requestDataTimer = UserThread.runAfter(() -> {
                                                log.trace("requestDataAfterDelayTimer called");
                                                // We want to keep it sorted but avoid duplicates
                                                // We don't filter out already established connections for seed nodes as it might be that
                                                // we got from the other seed node contacted but we still have not requested the initial 
                                                // data set
                                                List<NodeAddress> list = new ArrayList<>(seedNodeAddresses);
                                                Collections.shuffle(list);
                                                list.addAll(getFilteredAndSortedList(peerManager.getReportedPeers(), list));
                                                list.addAll(getFilteredAndSortedList(peerManager.getPersistedPeers(), list));
                                                checkArgument(!list.isEmpty(), "seedNodeAddresses must not be empty.");
                                                NodeAddress nextCandidate = list.get(0);
                                                list.remove(nextCandidate);
                                                requestData(nextCandidate, list);
                                            },
                                            RETRY_DELAY_SEC, TimeUnit.SECONDS);
                                }

                                requestDataHandshakeMap.remove(nodeAddress);

                                // Notify listeners
                                if (!nodeOfPreliminaryDataRequest.isPresent()) {
                                    if (peerManager.isSeedNode(nodeAddress))
                                        listener.onNoSeedNodeAvailable();
                                    else
                                        listener.onNoPeersAvailable();
                                }
                            }
                        }
                    });
            requestDataHandshakeMap.put(nodeAddress, requestDataHandshake);
            requestDataHandshake.requestData(nodeAddress);
        } else {
            log.warn("We have started already a requestDataHandshake to peer. nodeAddress=" + nodeAddress);
        }
    }

    // sorted by most recent lastActivityDate
    private List<NodeAddress> getFilteredAndSortedList(Set<ReportedPeer> set, List<NodeAddress> list) {
        return set.stream()
                .filter(e -> !list.contains(e.nodeAddress) &&
                        !peerManager.isSeedNode(e) &&
                        !peerManager.isSelf(e))
                .collect(Collectors.toList())
                .stream()
                .filter(e -> e.lastActivityDate != null)
                .sorted((o1, o2) -> o2.lastActivityDate.compareTo(o1.lastActivityDate))
                .map(e -> e.nodeAddress)
                .collect(Collectors.toList());
    }

    private void stopRequestDataTimer() {
        if (requestDataTimer != null) {
            requestDataTimer.cancel();
            requestDataTimer = null;
        }
    }
}
