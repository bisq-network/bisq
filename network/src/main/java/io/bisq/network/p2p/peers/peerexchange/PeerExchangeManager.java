package io.bisq.network.p2p.peers.peerexchange;

import com.google.common.base.Preconditions;
import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.network.*;
import io.bisq.network.p2p.peers.PeerManager;
import io.bisq.network.p2p.peers.peerexchange.messages.GetPeersRequest;
import io.bisq.network.p2p.seed.SeedNodesRepository;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class PeerExchangeManager implements MessageListener, ConnectionListener, PeerManager.Listener {
    private static final long RETRY_DELAY_SEC = 10;
    private static final long RETRY_DELAY_AFTER_ALL_CON_LOST_SEC = 3;
    private static final long REQUEST_PERIODICALLY_INTERVAL_MIN = 10;

    private final NetworkNode networkNode;
    private final PeerManager peerManager;

    private final Set<NodeAddress> seedNodeAddresses;
    private final Map<NodeAddress, PeerExchangeHandler> handlerMap = new HashMap<>();

    private Timer retryTimer, periodicTimer;
    private boolean stopped;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PeerExchangeManager(NetworkNode networkNode,
                               SeedNodesRepository seedNodesRepository,
                               PeerManager peerManager) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;

        this.networkNode.addMessageListener(this);
        this.networkNode.addConnectionListener(this);
        this.peerManager.addListener(this);

        this.seedNodeAddresses = new HashSet<>(seedNodesRepository.getSeedNodeAddresses());
    }

    public void shutDown() {
        Log.traceCall();
        stopped = true;
        networkNode.removeMessageListener(this);
        networkNode.removeConnectionListener(this);
        peerManager.removeListener(this);

        stopPeriodicTimer();
        stopRetryTimer();
        closeAllHandlers();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void requestReportedPeersFromSeedNodes(NodeAddress nodeAddress) {
        Preconditions.checkNotNull(networkNode.getNodeAddress(), "My node address must not be null at requestReportedPeers");
        ArrayList<NodeAddress> remainingNodeAddresses = new ArrayList<>(seedNodeAddresses);
        remainingNodeAddresses.remove(nodeAddress);
        Collections.shuffle(remainingNodeAddresses);
        requestReportedPeers(nodeAddress, remainingNodeAddresses);

        startPeriodicTimer();
    }

    public void initialRequestPeersFromReportedOrPersistedPeers() {
        if (!peerManager.getReportedPeers().isEmpty() || !peerManager.getPersistedPeers().isEmpty()) {
            // We will likely get more connections as the GetPeersResponse onComplete handler triggers a new request if the confirmed
            // connections have not reached the min connection target.
            // So we potentially request 2 times 8 but we prefer to get fast connected
            // and disconnect afterwards when we exceed max connections rather to delay connection in case many of our peers from the list are dead.
            for (int i = 0; i < Math.min(8, peerManager.getMaxConnections()); i++)
                requestWithAvailablePeers();
        } else {
            log.info("We don't have any reported or persisted peers, so we need to wait until we receive from the seed node the initial peer list.");
        }
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
        log.info("onDisconnect closeConnectionReason={}, nodeAddressOpt={}", closeConnectionReason, connection.getPeersNodeAddressOptional());
        closeHandler(connection);

        if (retryTimer == null) {
            retryTimer = UserThread.runAfter(() -> {
                log.trace("ConnectToMorePeersTimer called from onDisconnect code path");
                stopRetryTimer();
                requestWithAvailablePeers();
            }, RETRY_DELAY_SEC);
        }

        if (peerManager.isNodeBanned(closeConnectionReason, connection))
            seedNodeAddresses.remove(connection.getPeersNodeAddressOptional().get());
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
        stopPeriodicTimer();
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
        if (networkEnvelop instanceof GetPeersRequest) {
            Log.traceCall(networkEnvelop.toString() + "\n\tconnection=" + connection);
            if (!stopped) {
                if (peerManager.isSeedNode(connection))
                    connection.setPeerType(Connection.PeerType.SEED_NODE);

                GetPeersRequestHandler getPeersRequestHandler = new GetPeersRequestHandler(networkNode,
                        peerManager,
                        new GetPeersRequestHandler.Listener() {
                            @Override
                            public void onComplete() {
                                log.trace("PeerExchangeHandshake completed.\n\tConnection={}", connection);
                            }

                            @Override
                            public void onFault(String errorMessage, Connection connection) {
                                log.trace("PeerExchangeHandshake failed.\n\terrorMessage={}\n\t" +
                                        "connection={}", errorMessage, connection);
                                peerManager.handleConnectionFault(connection);
                            }
                        });
                getPeersRequestHandler.handle((GetPeersRequest) networkEnvelop, connection);
            } else {
                log.warn("We have stopped already. We ignore that onMessage call.");
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Request
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void requestReportedPeers(NodeAddress nodeAddress, List<NodeAddress> remainingNodeAddresses) {
        log.info("requestReportedPeers nodeAddress={}; remainingNodeAddresses.size={}" , nodeAddress,remainingNodeAddresses.size());
        if (!stopped) {
            if (!handlerMap.containsKey(nodeAddress)) {
                PeerExchangeHandler peerExchangeHandler = new PeerExchangeHandler(networkNode,
                        peerManager,
                        new PeerExchangeHandler.Listener() {
                            @Override
                            public void onComplete() {
                                handlerMap.remove(nodeAddress);
                                requestWithAvailablePeers();
                            }

                            @Override
                            public void onFault(String errorMessage, @Nullable Connection connection) {
                                log.info("PeerExchangeHandshake of outbound connection failed.\n\terrorMessage={}\n\t" +
                                        "nodeAddress={}", errorMessage, nodeAddress);

                                peerManager.handleConnectionFault(nodeAddress);
                                handlerMap.remove(nodeAddress);
                                if (!remainingNodeAddresses.isEmpty()) {
                                    if (!peerManager.hasSufficientConnections()) {
                                        log.debug("There are remaining nodes available for requesting peers. " +
                                                "We will try getReportedPeers again.");
                                        NodeAddress nextCandidate = remainingNodeAddresses.get(new Random().nextInt(remainingNodeAddresses.size()));
                                        remainingNodeAddresses.remove(nextCandidate);
                                        requestReportedPeers(nextCandidate, remainingNodeAddresses);
                                    } else {
                                        // That path will rarely be reached
                                        log.debug("We have already sufficient connections.");
                                    }
                                } else {
                                    log.debug("There is no remaining node available for requesting peers. " +
                                            "That is expected if no other node is online.\n\t" +
                                            "We will try again after a pause.");
                                    if (retryTimer == null)
                                        retryTimer = UserThread.runAfter(() -> {
                                            if (!stopped) {
                                                log.trace("retryTimer called from requestReportedPeers code path");
                                                stopRetryTimer();
                                                requestWithAvailablePeers();
                                            } else {
                                                stopRetryTimer();
                                                log.warn("We have stopped already. We ignore that retryTimer.run call.");
                                            }
                                        }, RETRY_DELAY_SEC);
                                }
                            }
                        });
                handlerMap.put(nodeAddress, peerExchangeHandler);
                peerExchangeHandler.sendGetPeersRequestAfterRandomDelay(nodeAddress);
            } else {
                log.trace("We have started already a peerExchangeHandler. " +
                        "We ignore that call. nodeAddress=" + nodeAddress);
            }
        } else {
            log.trace("We have stopped already. We ignore that requestReportedPeers call.");
        }
    }

    private void requestWithAvailablePeers() {
        Log.traceCall();
        if (!stopped) {
            if (!peerManager.hasSufficientConnections()) {
                // We create a new list of not connected candidates
                // 1. shuffled reported peers
                // 2. shuffled persisted peers
                // 3. Add as last shuffled seedNodes (least priority)
                List<NodeAddress> list = getFilteredNonSeedNodeList(getNodeAddresses(peerManager.getReportedPeers()), new ArrayList<>());
                Collections.shuffle(list);

                List<NodeAddress> filteredPersistedPeers = getFilteredNonSeedNodeList(getNodeAddresses(peerManager.getPersistedPeers()), list);
                Collections.shuffle(filteredPersistedPeers);
                list.addAll(filteredPersistedPeers);

                List<NodeAddress> filteredSeedNodeAddresses = getFilteredList(new ArrayList<>(seedNodeAddresses), list);
                Collections.shuffle(filteredSeedNodeAddresses);
                list.addAll(filteredSeedNodeAddresses);

                log.debug("Number of peers in list for connectToMorePeers: {}", list.size());
                log.trace("Filtered connectToMorePeers list: list=" + list);
                if (!list.isEmpty()) {
                    // Dont shuffle as we want the seed nodes at the last entries
                    NodeAddress nextCandidate = list.get(0);
                    list.remove(nextCandidate);
                    requestReportedPeers(nextCandidate, list);
                } else {
                    log.debug("No more peers are available for requestReportedPeers. We will try again after a pause.");
                    if (retryTimer == null)
                        retryTimer = UserThread.runAfter(() -> {
                            if (!stopped) {
                                log.trace("retryTimer called from requestWithAvailablePeers code path");
                                stopRetryTimer();
                                requestWithAvailablePeers();
                            } else {
                                stopRetryTimer();
                                log.warn("We have stopped already. We ignore that retryTimer.run call.");
                            }
                        }, RETRY_DELAY_SEC);
                }
            } else {
                log.debug("We have already sufficient connections.");
            }
        } else {
            log.trace("We have stopped already. We ignore that requestWithAvailablePeers call.");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void startPeriodicTimer() {
        stopped = false;
        if (periodicTimer == null)
            periodicTimer = UserThread.runPeriodically(this::requestWithAvailablePeers,
                    REQUEST_PERIODICALLY_INTERVAL_MIN, TimeUnit.MINUTES);
    }

    private void restart() {
        startPeriodicTimer();

        if (retryTimer == null) {
            retryTimer = UserThread.runAfter(() -> {
                stopped = false;
                log.trace("retryTimer called from restart");
                stopRetryTimer();
                requestWithAvailablePeers();
            }, RETRY_DELAY_AFTER_ALL_CON_LOST_SEC);
        } else {
            log.debug("retryTimer already started");
        }
    }

    private List<NodeAddress> getNodeAddresses(Collection<Peer> collection) {
        return collection.stream()
                .map(Peer::getNodeAddress)
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

    private void stopPeriodicTimer() {
        stopped = true;
        if (periodicTimer != null) {
            periodicTimer.stop();
            periodicTimer = null;
        }
    }

    private void stopRetryTimer() {
        if (retryTimer != null) {
            retryTimer.stop();
            retryTimer = null;
        }
    }

    private void closeHandler(Connection connection) {
        Log.traceCall();
        Optional<NodeAddress> peersNodeAddressOptional = connection.getPeersNodeAddressOptional();
        if (peersNodeAddressOptional.isPresent()) {
            NodeAddress nodeAddress = peersNodeAddressOptional.get();
            if (handlerMap.containsKey(nodeAddress)) {
                handlerMap.get(nodeAddress).cancel();
                handlerMap.remove(nodeAddress);
            }
        } else {
            log.trace("closeHandler: nodeAddress not set in connection " + connection);
        }
    }

    private void closeAllHandlers() {
        Log.traceCall();
        handlerMap.values().stream().forEach(PeerExchangeHandler::cancel);
        handlerMap.clear();
    }
}
