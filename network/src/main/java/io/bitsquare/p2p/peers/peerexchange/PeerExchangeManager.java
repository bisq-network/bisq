package io.bitsquare.p2p.peers.peerexchange;

import com.google.common.util.concurrent.MoreExecutors;
import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.*;
import io.bitsquare.p2p.peers.PeerManager;
import io.bitsquare.p2p.peers.peerexchange.messages.GetPeersRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class PeerExchangeManager implements MessageListener, ConnectionListener {
    private static final Logger log = LoggerFactory.getLogger(PeerExchangeManager.class);

    private static final long RETRY_DELAY_SEC = 60;
    private static final long REQUEST_PERIODICALLY_INTERVAL_MINUTES = 10;

    private final NetworkNode networkNode;
    private final PeerManager peerManager;
    private final Set<NodeAddress> seedNodeAddresses;
    private final Map<NodeAddress, PeerExchangeHandler> peerExchangeHandlerMap = new HashMap<>();
    private Timer connectToMorePeersTimer;
    private boolean shutDownInProgress;
    private ScheduledThreadPoolExecutor executor;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PeerExchangeManager(NetworkNode networkNode, PeerManager peerManager, Set<NodeAddress> seedNodeAddresses) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        checkArgument(!seedNodeAddresses.isEmpty(), "seedNodeAddresses must not be empty");
        this.seedNodeAddresses = new HashSet<>(seedNodeAddresses);

        networkNode.addMessageListener(this);
        networkNode.addConnectionListener(this);
    }

    public void shutDown() {
        Log.traceCall();
        shutDownInProgress = true;
        networkNode.removeMessageListener(this);
        networkNode.removeConnectionListener(this);
        stopConnectToMorePeersTimer();
        peerExchangeHandlerMap.values().stream().forEach(PeerExchangeHandler::cleanup);

        if (executor != null)
            MoreExecutors.shutdownAndAwaitTermination(executor, 100, TimeUnit.MILLISECONDS);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void requestReportedPeersFromSeedNodes(NodeAddress nodeAddress) {
        checkNotNull(networkNode.getNodeAddress(), "My node address must not be null at requestReportedPeers");
        ArrayList<NodeAddress> remainingNodeAddresses = new ArrayList<>(seedNodeAddresses);
        remainingNodeAddresses.remove(nodeAddress);
        Collections.shuffle(remainingNodeAddresses);
        requestReportedPeers(nodeAddress, remainingNodeAddresses);

        if (executor == null) {
            executor = Utilities.getScheduledThreadPoolExecutor("PeerExchangeManager", 1, 2, 5);
            executor.scheduleAtFixedRate(() -> UserThread.execute(this::requestAgain),
                    REQUEST_PERIODICALLY_INTERVAL_MINUTES, REQUEST_PERIODICALLY_INTERVAL_MINUTES, TimeUnit.MINUTES);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // ConnectionListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnection(Connection connection) {
    }

    @Override
    public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
        if (connectToMorePeersTimer == null)
            connectToMorePeersTimer = UserThread.runAfter(() -> {
                log.trace("ConnectToMorePeersTimer called from onDisconnect code path");
                stopConnectToMorePeersTimer();
                requestWithAvailablePeers();
            }, RETRY_DELAY_SEC);
    }

    @Override
    public void onError(Throwable throwable) {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof GetPeersRequest) {
            Log.traceCall(message.toString() + "\n\tconnection=" + connection);
            GetPeersRequestHandler getPeersRequestHandler = new GetPeersRequestHandler(networkNode,
                    peerManager,
                    new GetPeersRequestHandler.Listener() {
                        @Override
                        public void onComplete() {
                            log.trace("PeerExchangeHandshake of inbound connection complete.\n\tConnection={}", connection);
                        }

                        @Override
                        public void onFault(String errorMessage, Connection connection) {
                            log.trace("PeerExchangeHandshake of outbound connection failed.\n\terrorMessage={}\n\t" +
                                    "connection={}", errorMessage, connection);
                            peerManager.handleConnectionFault(connection);
                        }
                    });
            getPeersRequestHandler.handle((GetPeersRequest) message, connection);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Request
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void requestReportedPeers(NodeAddress nodeAddress, List<NodeAddress> remainingNodeAddresses) {
        Log.traceCall("nodeAddress=" + nodeAddress);
        if (!peerExchangeHandlerMap.containsKey(nodeAddress)) {
            PeerExchangeHandler peerExchangeHandler = new PeerExchangeHandler(networkNode,
                    peerManager,
                    new PeerExchangeHandler.Listener() {
                        @Override
                        public void onComplete() {
                            log.trace("PeerExchangeHandshake of outbound connection complete. nodeAddress={}", nodeAddress);
                            peerExchangeHandlerMap.remove(nodeAddress);
                            requestWithAvailablePeers();
                        }

                        @Override
                        public void onFault(String errorMessage, @Nullable Connection connection) {
                            log.trace("PeerExchangeHandshake of outbound connection failed.\n\terrorMessage={}\n\t" +
                                    "nodeAddress={}", errorMessage, nodeAddress);

                            peerExchangeHandlerMap.remove(nodeAddress);
                            peerManager.handleConnectionFault(nodeAddress, connection);
                            if (!shutDownInProgress) {
                                if (!remainingNodeAddresses.isEmpty()) {
                                    if (!peerManager.hasSufficientConnections()) {
                                        log.info("There are remaining nodes available for requesting peers. " +
                                                "We will try getReportedPeers again.");
                                        NodeAddress nextCandidate = remainingNodeAddresses.get(new Random().nextInt(remainingNodeAddresses.size()));
                                        remainingNodeAddresses.remove(nextCandidate);
                                        requestReportedPeers(nextCandidate, remainingNodeAddresses);
                                    } else {
                                        // That path will rarely be reached
                                        log.info("We have already sufficient connections.");
                                    }
                                } else {
                                    log.info("There is no remaining node available for requesting peers. " +
                                            "That is expected if no other node is online.\n\t" +
                                            "We will try again after a pause.");
                                    if (connectToMorePeersTimer == null)
                                        connectToMorePeersTimer = UserThread.runAfter(() -> {
                                            log.trace("ConnectToMorePeersTimer called from requestReportedPeers code path");
                                            stopConnectToMorePeersTimer();
                                            requestWithAvailablePeers();
                                        }, RETRY_DELAY_SEC);
                                }
                            }
                        }
                    });
            peerExchangeHandlerMap.put(nodeAddress, peerExchangeHandler);
            peerExchangeHandler.sendGetPeersRequest(nodeAddress);
        } else {
            //TODO check when that happens
            log.warn("We have started already a peerExchangeHandshake. " +
                    "We ignore that call. " +
                    "nodeAddress=" + nodeAddress);
        }
    }

    private void requestWithAvailablePeers() {
        Log.traceCall();

        if (!peerManager.hasSufficientConnections()) {
            // We create a new list of not connected candidates
            // 1. reported shuffled peers  
            // 2. persisted shuffled peers 
            // 3. Add as last shuffled seedNodes (least priority)
            List<NodeAddress> list = getFilteredNonSeedNodeList(getNodeAddresses(peerManager.getReportedPeers()), new ArrayList<>());
            Collections.shuffle(list);

            List<NodeAddress> filteredPersistedPeers = getFilteredNonSeedNodeList(getNodeAddresses(peerManager.getPersistedPeers()), list);
            Collections.shuffle(filteredPersistedPeers);
            list.addAll(filteredPersistedPeers);

            List<NodeAddress> filteredSeedNodeAddresses = getFilteredList(new ArrayList<>(seedNodeAddresses), list);
            Collections.shuffle(filteredSeedNodeAddresses);
            list.addAll(filteredSeedNodeAddresses);

            log.info("Number of peers in list for connectToMorePeers: {}", list.size());
            log.trace("Filtered connectToMorePeers list: list=" + list);
            if (!list.isEmpty()) {
                // Dont shuffle as we want the seed nodes at the last entries
                NodeAddress nextCandidate = list.get(0);
                list.remove(nextCandidate);
                requestReportedPeers(nextCandidate, list);
            } else {
                log.info("No more peers are available for requestReportedPeers. We will try again after a pause.");
                if (connectToMorePeersTimer == null)
                    connectToMorePeersTimer = UserThread.runAfter(() -> {
                        log.trace("ConnectToMorePeersTimer called from requestWithAvailablePeers code path");
                        stopConnectToMorePeersTimer();
                        requestWithAvailablePeers();
                    }, RETRY_DELAY_SEC);
            }
        } else {
            log.info("We have already sufficient connections.");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Maintenance
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void requestAgain() {
        checkNotNull(networkNode.getNodeAddress(), "My node address must not be null at requestAgain");
        Set<NodeAddress> candidates = new HashSet<>(getNodeAddresses(peerManager.getReportedPeers()));
        candidates.addAll(getNodeAddresses(peerManager.getPersistedPeers()));
        candidates.addAll(seedNodeAddresses);
        candidates.remove(networkNode.getNodeAddress());
        ArrayList<NodeAddress> list = new ArrayList<>(candidates);
        Collections.shuffle(list);
        NodeAddress candidate = list.remove(0);
        requestReportedPeers(candidate, list);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private List<NodeAddress> getNodeAddresses(Collection<ReportedPeer> collection) {
        return collection.stream()
                .map(e -> e.nodeAddress)
                .collect(Collectors.toList());
    }

    private List<NodeAddress> getFilteredList(Collection<NodeAddress> collection, List<NodeAddress> list) {
        return collection.stream()
                .filter(e -> !list.contains(e) &&
                        !peerManager.isSelf(e) &&
                        !peerManager.isConfirmed(e))
                .collect(Collectors.toList());
    }

    private List<NodeAddress> getFilteredNonSeedNodeList(Collection<NodeAddress> collection, List<NodeAddress> list) {
        return getFilteredList(collection, list).stream()
                .filter(e -> !peerManager.isSeedNode(e))
                .collect(Collectors.toList());
    }

    private void stopConnectToMorePeersTimer() {
        if (connectToMorePeersTimer != null) {
            connectToMorePeersTimer.cancel();
            connectToMorePeersTimer = null;
        }
    }
}
