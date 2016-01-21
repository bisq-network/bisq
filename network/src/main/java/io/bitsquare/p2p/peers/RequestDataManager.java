package io.bitsquare.p2p.peers;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.ConnectionPriority;
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
import static com.google.common.base.Preconditions.checkNotNull;

public class RequestDataManager implements MessageListener, AuthenticationListener {
    private static final Logger log = LoggerFactory.getLogger(RequestDataManager.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface Listener {
        void onNoSeedNodeAvailable();

        void onNoPeersAvailable();

        void onDataReceived(NodeAddress seedNode);
    }


    private final NetworkNode networkNode;
    protected final P2PDataStorage dataStorage;
    private final PeerManager peerManager;
    private final HashSet<ReportedPeer> persistedPeers = new HashSet<>();
    private final HashSet<ReportedPeer> remainingPersistedPeers = new HashSet<>();
    private Listener listener;
    private Optional<NodeAddress> optionalConnectedSeedNodeAddress = Optional.empty();
    private Collection<NodeAddress> seedNodeNodeAddresses;
    protected Timer requestDataFromAuthenticatedSeedNodeTimer;
    private Timer requestDataTimer, requestDataWithPersistedPeersTimer;
    private boolean doNotifyNoSeedNodeAvailableListener = true;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public RequestDataManager(NetworkNode networkNode, P2PDataStorage dataStorage, PeerManager peerManager) {
        this.networkNode = networkNode;
        this.dataStorage = dataStorage;
        this.peerManager = peerManager;

        networkNode.addMessageListener(this);
    }

    public void shutDown() {
        Log.traceCall();

        networkNode.removeMessageListener(this);

        stopRequestDataTimer();
        stopRequestDataWithPersistedPeersTimer();
        stopRequestDataFromAuthenticatedSeedNodeTimer();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setRequestDataManagerListener(Listener listener) {
        this.listener = listener;
    }

    public void requestDataFromSeedNodes(Collection<NodeAddress> seedNodeNodeAddresses) {
        checkNotNull(seedNodeNodeAddresses, "requestDataFromSeedNodes: seedNodeAddresses must not be null.");
        checkArgument(!seedNodeNodeAddresses.isEmpty(), "requestDataFromSeedNodes: seedNodeAddresses must not be empty.");

        this.seedNodeNodeAddresses = seedNodeNodeAddresses;
        requestData(seedNodeNodeAddresses);
    }

    private void requestData(Collection<NodeAddress> nodeAddresses) {
        Log.traceCall(nodeAddresses.toString());
        checkArgument(!nodeAddresses.isEmpty(), "requestData: addresses must not be empty.");
        stopRequestDataTimer();
        List<NodeAddress> remainingNodeAddresses = new ArrayList<>(nodeAddresses);
        NodeAddress candidate = remainingNodeAddresses.get(new Random().nextInt(remainingNodeAddresses.size()));
        if (!peerManager.isInAuthenticationProcess(candidate)) {
            // We only remove it if it is not in the process of authentication
            remainingNodeAddresses.remove(candidate);
            log.info("We try to send a GetAllDataMessage request to node. " + candidate);

            SettableFuture<Connection> future = networkNode.sendMessage(candidate, new DataRequest());
            Futures.addCallback(future, new FutureCallback<Connection>() {
                @Override
                public void onSuccess(@Nullable Connection connection) {
                    log.info("Send GetAllDataMessage to " + candidate + " succeeded.");
                    checkArgument(!optionalConnectedSeedNodeAddress.isPresent(), "We have already a connectedSeedNode. That must not happen.");
                    optionalConnectedSeedNodeAddress = Optional.of(candidate);

                    stopRequestDataTimer();
                    stopRequestDataWithPersistedPeersTimer();
                }

                @Override
                public void onFailure(@NotNull Throwable throwable) {
                    log.info("Send GetAllDataMessage to " + candidate + " failed. " +
                            "That is expected if the node is offline. " +
                            "Exception:" + throwable.getMessage());

                    if (!remainingNodeAddresses.isEmpty()) {
                        log.info("There are more seed nodes available for requesting data. " +
                                "We will try requestData again.");

                        ReportedPeer reportedPeer = new ReportedPeer(candidate);
                        if (remainingPersistedPeers.contains(reportedPeer))
                            remainingPersistedPeers.remove(reportedPeer);

                        requestData(remainingNodeAddresses);
                    } else {
                        log.info("There is no seed node available for requesting data. " +
                                "That is expected if no seed node is online.\n" +
                                "We will try again to request data from a seed node after a random pause.");

                        requestDataWithPersistedPeers(candidate);
                        requestDataWithSeedNodeAddresses();
                    }
                }
            });
        } else if (!remainingNodeAddresses.isEmpty()) {
            log.info("The node ({}) is in the process of authentication.\n" +
                    "We will try requestData again with the remaining addresses.", candidate);
            remainingNodeAddresses.remove(candidate);
            if (!remainingNodeAddresses.isEmpty()) {
                requestData(remainingNodeAddresses);
            } else {
                log.info("The node ({}) is in the process of authentication.\n" +
                        "There are no more remaining addresses available.\n" +
                        "We try requestData with the persistedPeers and after a pause with " +
                        "the seed nodes again.", candidate);
                requestDataWithPersistedPeers(candidate);
                requestDataWithSeedNodeAddresses();
            }
        } else {
            log.info("The node ({}) is in the process of authentication.\n" +
                    "There are no more remaining addresses available.\n" +
                    "We try requestData with the persistedPeers and after a pause with " +
                    "the seed nodes again.", candidate);
            requestDataWithPersistedPeers(candidate);
            requestDataWithSeedNodeAddresses();
        }
    }

    private void requestDataWithSeedNodeAddresses() {
        Log.traceCall();
        // We only want to notify the first time
        if (doNotifyNoSeedNodeAvailableListener) {
            doNotifyNoSeedNodeAvailableListener = false;
            listener.onNoSeedNodeAvailable();
        }
        if (requestDataTimer == null)
            requestDataTimer = UserThread.runAfterRandomDelay(() -> requestData(seedNodeNodeAddresses),
                    10, 20, TimeUnit.SECONDS);
    }

    private void requestDataWithPersistedPeers(@Nullable NodeAddress failedPeer) {
        Log.traceCall("failedPeer=" + failedPeer);

        stopRequestDataWithPersistedPeersTimer();

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
                requestData(persistedPeerNodeAddresses);
            }
        }

        if (!persistedPeersAvailable) {
            log.warn("No seed nodes and no persisted peers are available for requesting data.\n" +
                    "We will try again after a random pause.");
            doNotifyNoSeedNodeAvailableListener = false;
            listener.onNoPeersAvailable();

            // reset remainingPersistedPeers
            remainingPersistedPeers.clear();
            remainingPersistedPeers.addAll(persistedPeers);

            if (!remainingPersistedPeers.isEmpty() && requestDataWithPersistedPeersTimer == null)
                requestDataWithPersistedPeersTimer = UserThread.runAfterRandomDelay(() ->
                                requestDataWithPersistedPeers(null),
                        30, 40, TimeUnit.SECONDS);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof DataRequest) {
            // We are a seed node and receive that msg from a new node
            Log.traceCall(message.toString());
            networkNode.sendMessage(connection, new DataResponse(new HashSet<>(dataStorage.getMap().values())));
        } else if (message instanceof DataResponse) {
            // We are the new node which has requested the data
            Log.traceCall(message.toString());
            DataResponse dataResponse = (DataResponse) message;
            HashSet<ProtectedData> set = dataResponse.set;
            // we keep that connection open as the bootstrapping peer will use that for the authentication
            // as we are not authenticated yet the data adding will not be broadcasted 
            connection.getPeerAddressOptional().ifPresent(peerAddress -> set.stream().forEach(e -> dataStorage.add(e, peerAddress)));
            optionalConnectedSeedNodeAddress.ifPresent(connectedSeedNodeAddress -> listener.onDataReceived(connectedSeedNodeAddress));
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // AuthenticationListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onPeerAuthenticated(NodeAddress peerNodeAddress, Connection connection) {
        optionalConnectedSeedNodeAddress.ifPresent(connectedSeedNodeAddress -> {
            // We only request the data again if we have initiated the authentication (ConnectionPriority.ACTIVE)
            // We delay a bit to be sure that the authentication state is applied to all listeners
            if (connectedSeedNodeAddress.equals(peerNodeAddress) && connection.getConnectionPriority() == ConnectionPriority.ACTIVE) {
                // We are the node (can be a seed node as well) which requested the authentication
                if (requestDataFromAuthenticatedSeedNodeTimer == null)
                    requestDataFromAuthenticatedSeedNodeTimer = UserThread.runAfter(()
                            -> requestDataFromAuthenticatedSeedNode(peerNodeAddress, connection), 100, TimeUnit.MILLISECONDS);
            }
        });
    }

    // 5. Step after authentication to first seed node we request again the data
    protected void requestDataFromAuthenticatedSeedNode(NodeAddress peerNodeAddress, Connection connection) {
        Log.traceCall(peerNodeAddress.toString());

        stopRequestDataFromAuthenticatedSeedNodeTimer();

        // We have to request the data again as we might have missed pushed data in the meantime
        SettableFuture<Connection> future = networkNode.sendMessage(connection, new DataRequest());
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(@Nullable Connection connection) {
                log.info("requestDataFromAuthenticatedSeedNode from " + peerNodeAddress + " succeeded.");
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                log.warn("requestDataFromAuthenticatedSeedNode from " + peerNodeAddress + " failed. " +
                        "Exception:" + throwable.getMessage()
                        + "\nWe will try again to request data from any of our seed nodes.");

                // We will try again to request data from any of our seed nodes. 
                if (seedNodeNodeAddresses != null && !seedNodeNodeAddresses.isEmpty())
                    requestData(seedNodeNodeAddresses);
                else
                    log.error("seedNodeAddresses is null or empty. That must not happen. seedNodeAddresses="
                            + seedNodeNodeAddresses);
            }
        });
    }

    private void stopRequestDataTimer() {
        if (requestDataTimer != null) {
            requestDataTimer.cancel();
            requestDataTimer = null;
        }
    }

    private void stopRequestDataWithPersistedPeersTimer() {
        if (requestDataWithPersistedPeersTimer != null) {
            requestDataWithPersistedPeersTimer.cancel();
            requestDataWithPersistedPeersTimer = null;
        }
    }

    private void stopRequestDataFromAuthenticatedSeedNodeTimer() {
        if (requestDataFromAuthenticatedSeedNodeTimer != null) {
            requestDataFromAuthenticatedSeedNodeTimer.cancel();
            requestDataFromAuthenticatedSeedNodeTimer = null;
        }
    }
}
