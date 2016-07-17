package io.bitsquare.p2p.peers.peerexchange;

import io.bitsquare.app.Log;
import io.bitsquare.common.Timer;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.*;
import io.bitsquare.p2p.network.connection.CloseConnectionReason;
import io.bitsquare.p2p.network.connection.Connection;
import io.bitsquare.p2p.network.connection.ConnectionListener;
import io.bitsquare.p2p.network.connection.MessageListener;
import io.bitsquare.p2p.peers.PeerManager;
import io.bitsquare.p2p.peers.peerexchange.messages.GetPeersRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public class PeerExchangeManager implements MessageListener, ConnectionListener, PeerManager.Listener {
    private static final Logger log = LoggerFactory.getLogger(PeerExchangeManager.class);

    private static final long RETRY_DELAY_SEC = 10;
    private static final long RETRY_DELAY_AFTER_ALL_CON_LOST_SEC = 3;
    private static final long REQUEST_PERIODICALLY_INTERVAL_SEC = 10 * 60;

    private final NetworkNode networkNode;
    private final PeerManager peerManager;
    private final Set<NodeAddress> seedNodeAddresses;
    private final Map<NodeAddress, PeerExchangeHandler> handlerMap = new HashMap<>();
    private Timer retryTimer, periodicTimer;
    private boolean stopped;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PeerExchangeManager(NetworkNode networkNode, PeerManager peerManager, Set<NodeAddress> seedNodeAddresses) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        // seedNodeAddresses can be empty (in case there is only 1 seed node, the seed node starting up has no other seed nodes)
        this.seedNodeAddresses = new HashSet<>(seedNodeAddresses);

        networkNode.addMessageListener(this);
        networkNode.addConnectionListener(this);
        peerManager.addListener(this);
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
        checkNotNull(networkNode.getNodeAddress(), "My node address must not be null at requestReportedPeers");
        ArrayList<NodeAddress> remainingNodeAddresses = new ArrayList<>(seedNodeAddresses);
        remainingNodeAddresses.remove(nodeAddress);
        Collections.shuffle(remainingNodeAddresses);
        requestReportedPeers(nodeAddress, remainingNodeAddresses);

        startPeriodicTimer();
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
    public void onMessage(Message message, Connection connection) {
        if (message instanceof GetPeersRequest) {
            Log.traceCall(message.toString() + "\n\tconnection=" + connection);
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
                getPeersRequestHandler.handle((GetPeersRequest) message, connection);
            } else {
                log.warn("We have stopped already. We ignore that onMessage call.");
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Request
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void requestReportedPeers(NodeAddress nodeAddress, List<NodeAddress> remainingNodeAddresses) {
        Log.traceCall("nodeAddress=" + nodeAddress);
        if (!stopped) {
            if (!handlerMap.containsKey(nodeAddress)) {
                PeerExchangeHandler peerExchangeHandler = new PeerExchangeHandler(networkNode,
                        peerManager,
                        new PeerExchangeHandler.Listener() {
                            @Override
                            public void onComplete() {
                                log.trace("PeerExchangeHandshake of outbound connection complete. nodeAddress={}", nodeAddress);
                                handlerMap.remove(nodeAddress);
                                requestWithAvailablePeers();
                            }

                            @Override
                            public void onFault(String errorMessage, @Nullable Connection connection) {
                                log.trace("PeerExchangeHandshake of outbound connection failed.\n\terrorMessage={}\n\t" +
                                        "nodeAddress={}", errorMessage, nodeAddress);

                                peerManager.handleConnectionFault(nodeAddress);
                                handlerMap.remove(nodeAddress);
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

                log.info("Number of peers in list for connectToMorePeers: {}", list.size());
                log.trace("Filtered connectToMorePeers list: list=" + list);
                if (!list.isEmpty()) {
                    // Dont shuffle as we want the seed nodes at the last entries
                    NodeAddress nextCandidate = list.get(0);
                    list.remove(nextCandidate);
                    requestReportedPeers(nextCandidate, list);
                } else {
                    log.info("No more peers are available for requestReportedPeers. We will try again after a pause.");
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
                log.info("We have already sufficient connections.");
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
                    REQUEST_PERIODICALLY_INTERVAL_SEC, TimeUnit.SECONDS);
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
            log.warn("retryTimer already started");
        }
    }

    private List<NodeAddress> getNodeAddresses(Collection<Peer> collection) {
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
