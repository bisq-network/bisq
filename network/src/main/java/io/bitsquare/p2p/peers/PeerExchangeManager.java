package io.bitsquare.p2p.peers;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.ConnectionListener;
import io.bitsquare.p2p.network.MessageListener;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.p2p.peers.messages.peers.GetPeersRequest;
import io.bitsquare.p2p.peers.messages.peers.GetPeersResponse;
import io.bitsquare.p2p.peers.messages.peers.PeerExchangeMessage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PeerExchangeManager implements MessageListener, ConnectionListener {
    private static final Logger log = LoggerFactory.getLogger(PeerExchangeManager.class);

    private final NetworkNode networkNode;
    private final PeerManager peerManager;
    private final Set<NodeAddress> seedNodeAddresses;
    private final ScheduledThreadPoolExecutor executor;
    private Timer requestReportedPeersTimer, checkForSeedNodesTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PeerExchangeManager(NetworkNode networkNode, PeerManager peerManager, Set<NodeAddress> seedNodeAddresses) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        this.seedNodeAddresses = new HashSet<>(seedNodeAddresses);

        networkNode.addMessageListener(this);

        executor = Utilities.getScheduledThreadPoolExecutor("PeerExchangeManager", 1, 10, 5);
        long delay = new Random().nextInt(60) + 60 * 6; // 6-7 min. 
        executor.scheduleAtFixedRate(() -> UserThread.execute(() -> {
            sendGetPeersRequestToAllConnectedPeers();
            checkSeedNodes();
        }), delay, delay, TimeUnit.SECONDS);
    }

    public void shutDown() {
        Log.traceCall();

        networkNode.removeMessageListener(this);
        MoreExecutors.shutdownAndAwaitTermination(executor, 500, TimeUnit.MILLISECONDS);
        stopRequestReportedPeersTimer();
        stopCheckForSeedNodesTimer();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ConnectionListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnection(Connection connection) {
    }

    @Override
    public void onDisconnect(Reason reason, Connection connection) {
        if (checkForSeedNodesTimer == null)
            checkForSeedNodesTimer = UserThread.runAfter(this::checkSeedNodes,
                    networkNode.getAllConnections().size() * 10, TimeUnit.SECONDS);
    }

    @Override
    public void onError(Throwable throwable) {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof PeerExchangeMessage) {
            Log.traceCall(message.toString());
            if (message instanceof GetPeersRequest) {
                GetPeersRequest getPeersRequestMessage = (GetPeersRequest) message;
                HashSet<ReportedPeer> reportedPeers = getPeersRequestMessage.reportedPeers;
                log.trace("Received peers: " + reportedPeers);
                if (!connection.getPeersNodeAddressOptional().isPresent())
                    connection.setPeersNodeAddress(getPeersRequestMessage.senderNodeAddress);

                SettableFuture<Connection> future = networkNode.sendMessage(connection,
                        new GetPeersResponse(getReportedPeersHashSet(getPeersRequestMessage.senderNodeAddress)));
                Futures.addCallback(future, new FutureCallback<Connection>() {
                    @Override
                    public void onSuccess(Connection connection) {
                        log.trace("GetPeersResponse sent successfully");

                        handleOnSuccess(connection, null);
                    }

                    @Override
                    public void onFailure(@NotNull Throwable throwable) {
                        log.info("GetPeersResponse sending failed " + throwable.getMessage());
                    }
                });
                peerManager.addToReportedPeers(reportedPeers, connection);
            } else if (message instanceof GetPeersResponse) {
                HashSet<ReportedPeer> reportedPeers = ((GetPeersResponse) message).reportedPeers;
                log.trace("Received peers: " + reportedPeers);
                peerManager.addToReportedPeers(reportedPeers, connection);

                if (!peerManager.hasSufficientConnections()) {
                    List<NodeAddress> remainingNodeAddresses = new ArrayList<>(peerManager.getNodeAddressesOfReportedPeers());
                    networkNode.getNodeAddressesOfSucceededConnections().stream().forEach(remainingNodeAddresses::remove);
                    if (!remainingNodeAddresses.isEmpty()) {
                        NodeAddress nodeAddress = remainingNodeAddresses.get(new Random().nextInt(remainingNodeAddresses.size()));
                        remainingNodeAddresses.remove(nodeAddress);
                        requestPeersFromReportedPeers(nodeAddress, remainingNodeAddresses);
                    }
                }
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void getReportedPeersFromFirstSeedNode(NodeAddress nodeAddress) {
        getReportedPeersFromSeedNode(nodeAddress, new ArrayList<>(seedNodeAddresses));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void getReportedPeersFromSeedNode(NodeAddress nodeAddress, List<NodeAddress> remainingNodeAddresses) {
        Log.traceCall("nodeAddress=" + nodeAddress);
        stopRequestReportedPeersTimer();
        stopCheckForSeedNodesTimer();

        SettableFuture<Connection> future = networkNode.sendMessage(nodeAddress,
                new GetPeersRequest(networkNode.getNodeAddress(), getReportedPeersHashSet(nodeAddress)));
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(Connection connection) {
                log.trace("GetPeersRequest sent successfully");

                handleOnSuccess(connection, nodeAddress);
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                log.info("GetPeersRequest sending failed " + throwable.getMessage());

                if (!remainingNodeAddresses.isEmpty()) {
                    log.info("There are more seed nodes available for requesting peers. " +
                            "We will try getReportedPeersFromFirstSeedNode again.");

                    NodeAddress nextCandidate = remainingNodeAddresses.get(new Random().nextInt(remainingNodeAddresses.size()));
                    remainingNodeAddresses.remove(nextCandidate);
                    getReportedPeersFromSeedNode(nextCandidate, remainingNodeAddresses);
                } else {
                    log.info("There is no seed node available for requesting peers. " +
                            "That is expected if no seed node is online.\n" +
                            "We will try again to request peers from a seed node after a random pause.");
                    requestReportedPeersTimer = UserThread.runAfterRandomDelay(() -> {
                                if (!seedNodeAddresses.isEmpty()) {
                                    List<NodeAddress> nodeAddresses = new ArrayList<>(seedNodeAddresses);
                                    NodeAddress nextCandidate = nodeAddresses.get(new Random().nextInt(nodeAddresses.size()));
                                    nodeAddresses.remove(nextCandidate);
                                    getReportedPeersFromSeedNode(nextCandidate, nodeAddresses);
                                }
                            },
                            30, 40, TimeUnit.SECONDS);
                }
            }
        });
    }

    private void handleOnSuccess(Connection connection, @Nullable NodeAddress nodeAddress) {
        if (connection != null) {
            if (!connection.getPeersNodeAddressOptional().isPresent() && nodeAddress != null)
                connection.setPeersNodeAddress(nodeAddress);

            if (connection.getPeerType() == null)
                connection.setPeerType(peerManager.isSeedNode(connection) ? Connection.PeerType.SEED_NODE : Connection.PeerType.PEER);
        }
    }

    private void requestPeersFromReportedPeers(NodeAddress nodeAddress, List<NodeAddress> remainingNodeAddresses) {
        Log.traceCall("nodeAddress=" + nodeAddress);
        stopRequestReportedPeersTimer();

        SettableFuture<Connection> future = networkNode.sendMessage(nodeAddress,
                new GetPeersRequest(networkNode.getNodeAddress(), getReportedPeersHashSet(nodeAddress)));
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(Connection connection) {
                log.trace("sendGetPeersRequest sent successfully");

                handleOnSuccess(connection, nodeAddress);
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                log.info("sendGetPeersRequest sending failed " + throwable.getMessage());

                peerManager.removePeer(nodeAddress);

                if (!remainingNodeAddresses.isEmpty()) {
                    log.info("There are more reported peers available for requesting peers. " +
                            "We will try getReportedPeersFromFirstSeedNode again.");

                    NodeAddress nextCandidate = remainingNodeAddresses.get(new Random().nextInt(remainingNodeAddresses.size()));
                    remainingNodeAddresses.remove(nextCandidate);
                    requestPeersFromReportedPeers(nextCandidate, remainingNodeAddresses);
                } else {
                    log.info("There are no more reported peers available for requesting peers. " +
                            "We will try again to request peers from the reported peers again after a random pause.");
                    requestReportedPeersTimer = UserThread.runAfterRandomDelay(() -> {
                                List<NodeAddress> nodeAddresses = new ArrayList<>(peerManager.getNodeAddressesOfReportedPeers());
                                if (!nodeAddresses.isEmpty()) {
                                    NodeAddress nextCandidate = nodeAddresses.get(new Random().nextInt(nodeAddresses.size()));
                                    nodeAddresses.remove(nextCandidate);
                                    requestPeersFromReportedPeers(nextCandidate, nodeAddresses);
                                }
                            },
                            30, 40, TimeUnit.SECONDS);
                }
            }
        });
    }

    private void stopRequestReportedPeersTimer() {
        if (requestReportedPeersTimer != null) {
            requestReportedPeersTimer.cancel();
            requestReportedPeersTimer = null;
        }
    }

    private void stopCheckForSeedNodesTimer() {
        if (checkForSeedNodesTimer != null) {
            checkForSeedNodesTimer.cancel();
            checkForSeedNodesTimer = null;
        }
    }


    private void sendGetPeersRequestToAllConnectedPeers() {
        // copy set to avoid issues with changes in set (we dont need to be perfectly in sync so no need to use a concurrent set)
        Set<NodeAddress> connectedPeers = new HashSet<>(networkNode.getNodeAddressesOfSucceededConnections());
        if (!connectedPeers.isEmpty()) {
            Log.traceCall("connectedPeers.size=" + connectedPeers.size());
            connectedPeers.stream().forEach(nodeAddress ->
                    UserThread.runAfterRandomDelay(() ->
                            sendGetPeersRequest(nodeAddress), 3, 6));
        }
    }

    private void sendGetPeersRequest(NodeAddress nodeAddress) {
        Log.traceCall("nodeAddress=" + nodeAddress);
        SettableFuture<Connection> future = networkNode.sendMessage(nodeAddress,
                new GetPeersRequest(networkNode.getNodeAddress(), getReportedPeersHashSet(nodeAddress)));
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(Connection connection) {
                log.trace("sendGetPeersRequest sent successfully");
                handleOnSuccess(connection, nodeAddress);
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                log.info("sendGetPeersRequest sending failed " + throwable.getMessage());
                peerManager.removePeer(nodeAddress);
            }
        });
    }

    private HashSet<ReportedPeer> getReportedPeersHashSet(@Nullable NodeAddress receiverNodeAddress) {
        return new HashSet<>(peerManager.getConnectedAndReportedPeers().stream()
                .filter(e -> !peerManager.isSeedNode(e) &&
                                !e.nodeAddress.equals(networkNode.getNodeAddress()) &&
                                !e.nodeAddress.equals(receiverNodeAddress)
                )
                .collect(Collectors.toSet()));
    }

    private void checkSeedNodes() {
        Log.traceCall();
        Set<Connection> allConnections = networkNode.getAllConnections();
        List<Connection> seedNodes = allConnections.stream()
                .filter(peerManager::isSeedNode)
                .collect(Collectors.toList());

        if (seedNodes.size() == 0 && !seedNodeAddresses.isEmpty()) {
            List<NodeAddress> nodeAddresses = new ArrayList<>(seedNodeAddresses);
            NodeAddress nextCandidate = nodeAddresses.get(new Random().nextInt(nodeAddresses.size()));
            nodeAddresses.remove(nextCandidate);
            getReportedPeersFromSeedNode(nextCandidate, nodeAddresses);
        }
    }
}
