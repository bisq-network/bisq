package io.bitsquare.p2p.peers;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.MessageListener;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.p2p.peers.messages.data.DataRequest;
import io.bitsquare.p2p.peers.messages.data.DataResponse;
import io.bitsquare.p2p.peers.messages.data.PreliminaryDataRequest;
import io.bitsquare.p2p.storage.P2PDataStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

public class RequestDataManager implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(RequestDataManager.class);

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

    private Optional<NodeAddress> nodeOfPreliminaryDataRequest = Optional.empty();
    private Timer requestDataAfterDelayTimer, timeoutTimer;
    private boolean dataUpdateRequested;


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

        networkNode.addMessageListener(this);
    }

    public void shutDown() {
        Log.traceCall();

        networkNode.removeMessageListener(this);

        stopRequestDataTimer();
        stopTimeoutTimer();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void requestPreliminaryData() {
        Log.traceCall();
        checkArgument(!seedNodeAddresses.isEmpty(), "seedNodeAddresses must not be empty.");
        requestDataFromList(new ArrayList<>(seedNodeAddresses));
    }

    public void requestUpdatesData() {
        Log.traceCall();
        checkArgument(nodeOfPreliminaryDataRequest.isPresent(), "seedNodeOfPreliminaryDataRequest must be present");
        dataUpdateRequested = true;
        List<NodeAddress> remainingNodeAddresses = new ArrayList<>(seedNodeAddresses);
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
        if (message instanceof PreliminaryDataRequest || message instanceof DataRequest) {
            Log.traceCall(message.toString());
            networkNode.sendMessage(connection, new DataResponse(new HashSet<>(dataStorage.getMap().values())));
        } else if (message instanceof DataResponse) {
            Log.traceCall(message.toString());
            stopTimeoutTimer();
            connection.getPeersNodeAddressOptional().ifPresent(peersNodeAddress -> {
                ((DataResponse) message).dataSet.stream()
                        .forEach(e -> dataStorage.add(e, peersNodeAddress));

                // 1. We get a response from requestPreliminaryData
                if (!nodeOfPreliminaryDataRequest.isPresent()) {
                    nodeOfPreliminaryDataRequest = Optional.of(peersNodeAddress);
                    listener.onPreliminaryDataReceived();
                }

                // 2. Later we get a response from requestUpdatesData
                if (dataUpdateRequested) {
                    dataUpdateRequested = false;
                    listener.onUpdatedDataReceived();
                }

                listener.onDataReceived();
            });
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void requestDataFromList(List<NodeAddress> nodeAddresses) {
        Log.traceCall("remainingNodeAddresses=" + nodeAddresses);
        NodeAddress nextCandidate = nodeAddresses.get(new Random().nextInt(nodeAddresses.size()));
        nodeAddresses.remove(nextCandidate);
        requestData(nextCandidate, nodeAddresses);
    }

    private void requestData(NodeAddress nodeAddress, List<NodeAddress> remainingNodeAddresses) {
        Log.traceCall("nodeAddress=" + nodeAddress + " /  remainingNodeAddresses=" + remainingNodeAddresses);
        log.info("We try to send a DataRequest request to peer. " + nodeAddress);

        stopTimeoutTimer();
        stopRequestDataTimer();

        timeoutTimer = UserThread.runAfter(() -> {
                    log.info("timeoutTimer called");
                    handleError(nodeAddress, remainingNodeAddresses);
                },
                10, TimeUnit.SECONDS);

        Message dataRequest;
        if (networkNode.getNodeAddress() == null)
            dataRequest = new PreliminaryDataRequest();
        else
            dataRequest = new DataRequest(networkNode.getNodeAddress());

        SettableFuture<Connection> future = networkNode.sendMessage(nodeAddress, dataRequest);
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(@Nullable Connection connection) {
                log.trace("Send DataRequest to " + nodeAddress + " succeeded.");
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                log.info("Send DataRequest to " + nodeAddress + " failed. " +
                        "That is expected if the peer is offline. " +
                        "Exception:" + throwable.getMessage());

                handleError(nodeAddress, remainingNodeAddresses);
            }
        });
    }

    private void handleError(NodeAddress nodeAddress, List<NodeAddress> remainingNodeAddresses) {
        Log.traceCall("nodeAddress=" + nodeAddress + " /  remainingNodeAddresses=" + remainingNodeAddresses);
        stopTimeoutTimer();

        if (!remainingNodeAddresses.isEmpty()) {
            log.info("There are remaining nodes available for requesting data. " +
                    "We will try requestDataFromPeers again.");
            requestDataFromList(remainingNodeAddresses);
        } else {
            log.info("There is no remaining node available for requesting data. " +
                    "That is expected if no other node is online.\n" +
                    "We will try to use reported peers (if no available we use persisted peers) " +
                    "and try again to request data from our seed nodes after a random pause.");

            if (peerManager.isSeedNode(nodeAddress))
                listener.onNoSeedNodeAvailable();
            else
                listener.onNoPeersAvailable();

            requestDataAfterDelayTimer = UserThread.runAfterRandomDelay(() -> {
                        log.trace("requestDataAfterDelayTimer called");
                        if (!seedNodeAddresses.isEmpty()) {
                            Set<NodeAddress> nodeAddressesOfConfirmedConnections = networkNode.getNodeAddressesOfConfirmedConnections();
                            // We want to keep it sorted but avoid duplicates
                            // We don't filter out already established connections for seed nodes as it might be that
                            // we got from the other seed node contacted but we still have not requested the initial 
                            // data set
                            List<NodeAddress> list = new ArrayList<>(seedNodeAddresses);
                            list.addAll(getFilteredAndSortedList(peerManager.getReportedPeers(), list));
                            list.addAll(getFilteredAndSortedList(peerManager.getPersistedPeers(), list));
                            log.trace("Sorted and filtered list: list=" + list);
                            if (!list.isEmpty()) {
                                NodeAddress nextCandidate = list.get(0);
                                list.remove(nextCandidate);
                                requestData(nextCandidate, list);
                            } else {
                                log.info("Neither seed nodes, reported peers nor persisted peers are available. " +
                                        "At least seed nodes should be always available.");
                            }
                        }
                    },
                    10, 15, TimeUnit.SECONDS);
        }
    }

    // sorted by most recent lastActivityDate
    private List<NodeAddress> getFilteredAndSortedList(Set<ReportedPeer> set, List<NodeAddress> list) {
        return set.stream()
                .filter(e -> !list.contains(e.nodeAddress) &&
                        !peerManager.isSeedNode(e) &&
                        !peerManager.isSelf(e.nodeAddress))
                .collect(Collectors.toList())
                .stream()
                .sorted((o1, o2) -> o2.lastActivityDate.compareTo(o1.lastActivityDate))
                .map(e -> e.nodeAddress)
                .collect(Collectors.toList());
    }

    private void stopRequestDataTimer() {
        if (requestDataAfterDelayTimer != null) {
            requestDataAfterDelayTimer.cancel();
            requestDataAfterDelayTimer = null;
        }
    }

    private void stopTimeoutTimer() {
        if (timeoutTimer != null) {
            timeoutTimer.cancel();
            timeoutTimer = null;
        }
    }
}
