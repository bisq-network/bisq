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

import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class PeerExchangeManager implements MessageListener, ConnectionListener {
    private static final Logger log = LoggerFactory.getLogger(PeerExchangeManager.class);

    private final NetworkNode networkNode;
    private final PeerManager peerManager;
    private final Set<NodeAddress> seedNodeAddresses;
    private final ScheduledThreadPoolExecutor executor;
    private Timer requestReportedPeersAfterDelayTimer, timeoutTimer, checkForSeedNodesTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PeerExchangeManager(NetworkNode networkNode, PeerManager peerManager, Set<NodeAddress> seedNodeAddresses) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        this.seedNodeAddresses = new HashSet<>(seedNodeAddresses);

        executor = Utilities.getScheduledThreadPoolExecutor("PeerExchangeManager", 1, 10, 5);
        networkNode.addMessageListener(this);
    }

    public void shutDown() {
        Log.traceCall();

        networkNode.removeMessageListener(this);
        stopRequestReportedPeersAfterDelayTimer();
        stopCheckForSeedNodesTimer();
        stopTimeoutTimer();
        MoreExecutors.shutdownAndAwaitTermination(executor, 500, TimeUnit.MILLISECONDS);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void requestReportedPeers(NodeAddress nodeAddress) {
        requestReportedPeers(nodeAddress, new ArrayList<>(seedNodeAddresses));

        long delay = new Random().nextInt(60) + 60 * 3; // 3-4 min. 
        executor.scheduleAtFixedRate(() -> UserThread.execute(this::checkForSeedNode),
                delay, delay, TimeUnit.SECONDS);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ConnectionListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnection(Connection connection) {
    }

    @Override
    public void onDisconnect(Reason reason, Connection connection) {
        // We use a timer to throttle if we get a series of disconnects
        // The more connections we have the more relaxed we are with a checkForSeedNode
        if (checkForSeedNodesTimer == null)
            checkForSeedNodesTimer = UserThread.runAfter(this::checkForSeedNode,
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
                HashSet<ReportedPeer> reportedPeers = ((GetPeersRequest) message).reportedPeers;
                log.trace("Received reported peers: " + reportedPeers);
                checkArgument(connection.getPeersNodeAddressOptional().isPresent(),
                        "The peers address must have been already set at the moment");
                SettableFuture<Connection> future = networkNode.sendMessage(connection,
                        new GetPeersResponse(getReportedPeersHashSet(connection.getPeersNodeAddressOptional().get())));
                Futures.addCallback(future, new FutureCallback<Connection>() {
                    @Override
                    public void onSuccess(Connection connection) {
                        log.trace("GetPeersResponse sent successfully");
                    }

                    @Override
                    public void onFailure(@NotNull Throwable throwable) {
                        log.info("GetPeersResponse sending failed " + throwable.getMessage() +
                                " Maybe the peer went offline.");
                    }
                });
                peerManager.addToReportedPeers(reportedPeers, connection);
            } else if (message instanceof GetPeersResponse) {
                stopTimeoutTimer();
                HashSet<ReportedPeer> reportedPeers = ((GetPeersResponse) message).reportedPeers;
                log.trace("Received reported peers: " + reportedPeers);
                peerManager.addToReportedPeers(reportedPeers, connection);

                continueWithMorePeers();
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void requestReportedPeers(NodeAddress nodeAddress, List<NodeAddress> remainingNodeAddresses) {
        Log.traceCall("nodeAddress=" + nodeAddress + " /  remainingNodeAddresses=" + remainingNodeAddresses);
        checkNotNull(networkNode.getNodeAddress(), "My node address must not be null at requestReportedPeers");

        stopRequestReportedPeersAfterDelayTimer();
        stopTimeoutTimer();

        timeoutTimer = UserThread.runAfter(() -> {
                    log.info("timeoutTimer called");
                    handleError(nodeAddress, remainingNodeAddresses);
                },
                10, TimeUnit.SECONDS);

        SettableFuture<Connection> future = networkNode.sendMessage(nodeAddress,
                new GetPeersRequest(networkNode.getNodeAddress(), getReportedPeersHashSet(nodeAddress)));
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(Connection connection) {
                log.trace("GetPeersRequest sent successfully");
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                log.info("Sending GetPeersRequest to " + nodeAddress + " failed. " +
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
            log.info("There are remaining nodes available for requesting peers. " +
                    "We will try getReportedPeers again.");
            requestReportedPeersFromList(remainingNodeAddresses);
        } else {
            log.info("There is no remaining node available for requesting peers. " +
                    "That is expected if no other node is online.\n" +
                    "We will try to use reported peers (if no available we use persisted peers) " +
                    "and try again to request peers from our seed nodes after a random pause.");
            requestReportedPeersAfterDelayTimer = UserThread.runAfter(this::continueWithMorePeers,
                    10, TimeUnit.SECONDS);
        }
    }

    private void requestReportedPeersFromList(List<NodeAddress> remainingNodeAddresses) {
        NodeAddress nextCandidate = remainingNodeAddresses.get(new Random().nextInt(remainingNodeAddresses.size()));
        remainingNodeAddresses.remove(nextCandidate);
        requestReportedPeers(nextCandidate, remainingNodeAddresses);
    }

    private void continueWithMorePeers() {
        Log.traceCall();
        if (!peerManager.hasSufficientConnections()) {
            // We want to keep it sorted but avoid duplicates
            List<NodeAddress> list = new ArrayList<>(getFilteredAndSortedList(peerManager.getReportedPeers(), new ArrayList<>()));
            list.addAll(getFilteredAndSortedList(peerManager.getPersistedPeers(), list));
            list.addAll(seedNodeAddresses.stream()
                    .filter(e -> !list.contains(e) &&
                            !peerManager.isSelf(e) &&
                            !peerManager.isConfirmed(e))
                    .collect(Collectors.toSet()));
            log.trace("Sorted and filtered list: list=" + list);
            if (!list.isEmpty()) {
                NodeAddress nextCandidate = list.get(0);
                list.remove(nextCandidate);
                requestReportedPeers(nextCandidate, list);
            } else {
                log.info("No more peers are available for requestReportedPeers.");
            }
        } else {
            log.info("We have already sufficient connections.");
        }
    }

    // sorted by most recent lastActivityDate
    private List<NodeAddress> getFilteredAndSortedList(Set<ReportedPeer> set, List<NodeAddress> list) {
        return set.stream()
                .filter(e -> !list.contains(e.nodeAddress) &&
                        !peerManager.isSeedNode(e) &&
                        !peerManager.isSelf(e) &&
                        !peerManager.isConfirmed(e))
                .collect(Collectors.toList())
                .stream()
                .sorted((o1, o2) -> o2.lastActivityDate.compareTo(o1.lastActivityDate))
                .map(e -> e.nodeAddress)
                .collect(Collectors.toList());
    }


    // we check if we have at least one seed node connected
    private void checkForSeedNode() {
        Log.traceCall();
        stopCheckForSeedNodesTimer();
        Set<Connection> allConnections = networkNode.getConfirmedConnections();
        List<Connection> seedNodes = allConnections.stream()
                .filter(peerManager::isSeedNode)
                .collect(Collectors.toList());

        if (seedNodes.size() == 0 && !seedNodeAddresses.isEmpty()) {
            requestReportedPeersFromList(new ArrayList<>(seedNodeAddresses));
        }
    }

    private HashSet<ReportedPeer> getReportedPeersHashSet(NodeAddress receiverNodeAddress) {
        return new HashSet<>(peerManager.getConnectedAndReportedPeers().stream()
                .filter(e -> !peerManager.isSeedNode(e) &&
                                !peerManager.isSelf(e) &&
                                !e.nodeAddress.equals(receiverNodeAddress)
                )
                .collect(Collectors.toSet()));
    }

    private void stopRequestReportedPeersAfterDelayTimer() {
        if (requestReportedPeersAfterDelayTimer != null) {
            requestReportedPeersAfterDelayTimer.cancel();
            requestReportedPeersAfterDelayTimer = null;
        }
    }

    private void stopCheckForSeedNodesTimer() {
        if (checkForSeedNodesTimer != null) {
            checkForSeedNodesTimer.cancel();
            checkForSeedNodesTimer = null;
        }
    }

    private void stopTimeoutTimer() {
        if (timeoutTimer != null) {
            timeoutTimer.cancel();
            timeoutTimer = null;
        }
    }
}
