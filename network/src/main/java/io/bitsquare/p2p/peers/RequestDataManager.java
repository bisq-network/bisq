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
import io.bitsquare.p2p.storage.P2PDataStorage;
import io.bitsquare.p2p.storage.data.ProtectedData;
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
        void onNoSeedNodeAvailable();

        void onNoPeersAvailable();

        void onDataReceived();

        void onPreliminaryDataReceived();

        void onDataUpdate();
    }


    private final NetworkNode networkNode;
    protected final P2PDataStorage dataStorage;
    private final PeerManager peerManager;
    private final HashSet<ReportedPeer> persistedPeers = new HashSet<>();
    private final HashSet<ReportedPeer> remainingPersistedPeers = new HashSet<>();
    private Listener listener;
    private Optional<NodeAddress> seedNodeOfPreliminaryDataRequest = Optional.empty();
    private final Collection<NodeAddress> seedNodeAddresses;
    private Timer requestDataFromSeedNodesTimer, requestDataFromPersistedPeersTimer, dataRequestTimeoutTimer;
    private boolean noSeedNodeAvailableListenerNotified;
    private boolean dataUpdateRequested;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public RequestDataManager(NetworkNode networkNode, P2PDataStorage dataStorage, PeerManager peerManager, Set<NodeAddress> seedNodeAddresses) {
        this.networkNode = networkNode;
        this.dataStorage = dataStorage;
        this.peerManager = peerManager;
        this.seedNodeAddresses = new HashSet<>(seedNodeAddresses);

        networkNode.addMessageListener(this);
    }

    public void shutDown() {
        Log.traceCall();

        networkNode.removeMessageListener(this);

        stopRequestDataFromSeedNodesTimer();
        stopRequestDataFromPersistedPeersTimer();
        stopDataRequestTimeoutTimer();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setRequestDataManagerListener(Listener listener) {
        this.listener = listener;
    }

    public void requestPreliminaryData() {
        requestDataFromPeers(seedNodeAddresses);
    }

    public void updateDataFromConnectedSeedNode() {
        Log.traceCall();
        checkArgument(seedNodeOfPreliminaryDataRequest.isPresent(), "seedNodeOfPreliminaryDataRequest must be present");
        dataUpdateRequested = true;
        requestDataFromPeer(seedNodeOfPreliminaryDataRequest.get(), seedNodeAddresses);
    }

    public Optional<NodeAddress> getSeedNodeOfPreliminaryDataRequest() {
        return seedNodeOfPreliminaryDataRequest;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        Optional<NodeAddress> peersNodeAddressOptional = connection.getPeersNodeAddressOptional();
        if (message instanceof DataRequest) {
            // We are a seed node and receive that msg from a new node
            Log.traceCall(message.toString());
            DataRequest dataRequest = (DataRequest) message;
            if (peersNodeAddressOptional.isPresent()) {
                checkArgument(peersNodeAddressOptional.get().equals(dataRequest.senderNodeAddress),
                        "Sender address in message not matching the peers address in our connection.");
            } else if (dataRequest.senderNodeAddress != null) {
                // If first data request the peer does not has its address
                // in case of requesting from first seed node after hidden service is published we did not knew the peers address
                connection.setPeersNodeAddress(dataRequest.senderNodeAddress);
            }
            networkNode.sendMessage(connection, new DataResponse(new HashSet<>(dataStorage.getMap().values())));
        } else if (message instanceof DataResponse) {
            // We are the new node which has requested the data
            Log.traceCall(message.toString());
            DataResponse dataResponse = (DataResponse) message;
            HashSet<ProtectedData> set = dataResponse.set;
            // we keep that connection open as the bootstrapping peer will use that later for a re-sync
            // as the hidden service is  not published yet the data adding will not be broadcasted to others
            peersNodeAddressOptional.ifPresent(peersNodeAddress -> set.stream().forEach(e -> dataStorage.add(e, peersNodeAddress)));

            stopDataRequestTimeoutTimer();
            connection.getPeersNodeAddressOptional().ifPresent(e -> {
                if (!seedNodeOfPreliminaryDataRequest.isPresent()) {
                    seedNodeOfPreliminaryDataRequest = Optional.of(e);
                    listener.onPreliminaryDataReceived();
                }

                if (dataUpdateRequested) {
                    dataUpdateRequested = false;
                    listener.onDataUpdate();
                }

                listener.onDataReceived();
            });
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void requestDataFromPeers(Collection<NodeAddress> nodeAddresses) {
        Log.traceCall(nodeAddresses.toString());
        checkArgument(!nodeAddresses.isEmpty(), "requestDataFromPeers: nodeAddresses must not be empty.");
        stopRequestDataFromSeedNodesTimer();
        List<NodeAddress> remainingNodeAddresses = new ArrayList<>(nodeAddresses);
        NodeAddress candidate = remainingNodeAddresses.get(new Random().nextInt(remainingNodeAddresses.size()));
        requestDataFromPeer(candidate, remainingNodeAddresses);
    }

    private void requestDataFromPeer(NodeAddress nodeAddress, Collection<NodeAddress> remainingNodeAddresses) {
        Log.traceCall(nodeAddress.toString());
        remainingNodeAddresses.remove(nodeAddress);
        log.info("We try to send a DataRequest request to node. " + nodeAddress);

        stopDataRequestTimeoutTimer();
        dataRequestTimeoutTimer = UserThread.runAfter(() -> {
                    log.info("firstDataRequestTimeoutTimer called");
                    if (!remainingNodeAddresses.isEmpty()) {
                        requestDataFromPeers(remainingNodeAddresses);
                    } else {
                        requestDataFromPersistedPeersAfterDelay(nodeAddress);
                        requestDataFromSeedNodesAfterDelay();
                    }
                },
                10, TimeUnit.SECONDS);

        SettableFuture<Connection> future = networkNode.sendMessage(nodeAddress, new DataRequest(networkNode.getNodeAddress()));
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(@Nullable Connection connection) {
                log.info("Send DataRequest to " + nodeAddress + " succeeded.");

                if (connection != null) {
                    if (!connection.getPeersNodeAddressOptional().isPresent())
                        connection.setPeersNodeAddress(nodeAddress);

                    if (connection.getPeerType() == null)
                        connection.setPeerType(peerManager.isSeedNode(connection) ? Connection.PeerType.SEED_NODE : Connection.PeerType.PEER);
                }
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                log.info("Send DataRequest to " + nodeAddress + " failed. " +
                        "That is expected if the node is offline. " +
                        "Exception:" + throwable.getMessage());

                if (!remainingNodeAddresses.isEmpty()) {
                    log.info("There are more seed nodes available for requesting data. " +
                            "We will try requestData again.");

                    ReportedPeer reportedPeer = new ReportedPeer(nodeAddress);
                    if (remainingPersistedPeers.contains(reportedPeer))
                        remainingPersistedPeers.remove(reportedPeer);

                    requestDataFromPeers(remainingNodeAddresses);
                } else {
                    log.info("There is no seed node available for requesting data. " +
                            "That is expected if no seed node is online.\n" +
                            "We will try again to request data from a seed node after a random pause.");

                    requestDataFromPersistedPeersAfterDelay(nodeAddress);
                    requestDataFromSeedNodesAfterDelay();
                }
            }
        });
    }

    private void requestDataFromSeedNodesAfterDelay() {
        Log.traceCall();
        // We only want to notify the first time
        if (!noSeedNodeAvailableListenerNotified) {
            noSeedNodeAvailableListenerNotified = true;
            listener.onNoSeedNodeAvailable();
        }

        if (requestDataFromSeedNodesTimer == null)
            requestDataFromSeedNodesTimer = UserThread.runAfterRandomDelay(() -> requestDataFromPeers(seedNodeAddresses),
                    10, 20, TimeUnit.SECONDS);
    }

    private void requestDataFromPersistedPeersAfterDelay(@Nullable NodeAddress failedPeer) {
        Log.traceCall("failedPeer=" + failedPeer);

        stopRequestDataFromPersistedPeersTimer();

        if (persistedPeers.isEmpty()) {
            persistedPeers.addAll(peerManager.getPersistedPeers());
            log.info("persistedPeers = " + persistedPeers);
            remainingPersistedPeers.addAll(persistedPeers);
        }

        if (failedPeer != null) {
            ReportedPeer reportedPeer = new ReportedPeer(failedPeer);
            if (remainingPersistedPeers.contains(reportedPeer))
                remainingPersistedPeers.remove(reportedPeer);
        }

        boolean persistedPeersAvailable = false;
        if (!remainingPersistedPeers.isEmpty()) {
            Set<NodeAddress> persistedPeerNodeAddresses = remainingPersistedPeers.stream().map(e -> e.nodeAddress).collect(Collectors.toSet());
            if (!persistedPeerNodeAddresses.isEmpty()) {
                log.info("We try to use persisted peers for requestData.");
                persistedPeersAvailable = true;
                requestDataFromPeers(persistedPeerNodeAddresses);
            }
        }

        if (!persistedPeersAvailable) {
            log.warn("No seed nodes and no persisted peers are available for requesting data.\n" +
                    "We will try again after a random pause.");
            noSeedNodeAvailableListenerNotified = true;
            listener.onNoPeersAvailable();

            // reset remainingPersistedPeers
            remainingPersistedPeers.clear();
            remainingPersistedPeers.addAll(persistedPeers);

            if (!remainingPersistedPeers.isEmpty() && requestDataFromPersistedPeersTimer == null)
                requestDataFromPersistedPeersTimer = UserThread.runAfterRandomDelay(() ->
                                requestDataFromPersistedPeersAfterDelay(null),
                        30, 40, TimeUnit.SECONDS);
        }
    }

    private void stopRequestDataFromSeedNodesTimer() {
        if (requestDataFromSeedNodesTimer != null) {
            requestDataFromSeedNodesTimer.cancel();
            requestDataFromSeedNodesTimer = null;
        }
    }

    private void stopRequestDataFromPersistedPeersTimer() {
        if (requestDataFromPersistedPeersTimer != null) {
            requestDataFromPersistedPeersTimer.cancel();
            requestDataFromPersistedPeersTimer = null;
        }
    }

    private void stopDataRequestTimeoutTimer() {
        if (dataRequestTimeoutTimer != null) {
            dataRequestTimeoutTimer.cancel();
            dataRequestTimeoutTimer = null;
        }
    }
}
