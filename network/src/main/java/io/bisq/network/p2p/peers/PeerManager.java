package io.bisq.network.p2p.peers;

import com.google.inject.name.Named;
import io.bisq.common.Clock;
import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.common.proto.persistable.PersistedDataHost;
import io.bisq.common.proto.persistable.PersistenceProtoResolver;
import io.bisq.common.storage.Storage;
import io.bisq.network.NetworkOptionKeys;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.network.*;
import io.bisq.network.p2p.peers.peerexchange.Peer;
import io.bisq.network.p2p.peers.peerexchange.PeerList;
import io.bisq.network.p2p.seed.SeedNodesRepository;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class PeerManager implements ConnectionListener, PersistedDataHost {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static final long CHECK_MAX_CONN_DELAY_SEC = 10;
    // Use a long delay as the bootstrapping peer might need a while until it knows its onion address
    private static final long REMOVE_ANONYMOUS_PEER_SEC = 120;

    private static final int MAX_REPORTED_PEERS = 1000;
    private static final int MAX_PERSISTED_PEERS = 500;
    private static final long MAX_AGE = TimeUnit.DAYS.toMillis(14); // max age for reported peers is 14 days
    private static final boolean PRINT_REPORTED_PEERS_DETAILS = true;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface Listener {
        void onAllConnectionsLost();

        void onNewConnectionAfterAllConnectionsLost();

        void onAwakeFromStandby();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final NetworkNode networkNode;
    private final Clock clock;

    private int maxConnections;
    private final Set<NodeAddress> seedNodeAddresses;

    private final Storage<PeerList> storage;
    private final HashSet<Peer> persistedPeers = new HashSet<>();
    private final Set<Peer> reportedPeers = new HashSet<>();
    private final Clock.Listener listener;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private Timer checkMaxConnectionsTimer;
    private boolean stopped;
    private boolean lostAllConnections;

    private int minConnections;
    private int maxConnectionsPeer;
    private int maxConnectionsNonDirect;
    private int maxConnectionsAbsolute;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PeerManager(NetworkNode networkNode,
                       SeedNodesRepository seedNodesRepository,
                       Clock clock,
                       PersistenceProtoResolver persistenceProtoResolver,
                       @Named(NetworkOptionKeys.MAX_CONNECTIONS) int maxConnections,
                       @Named(Storage.STORAGE_DIR) File storageDir) {
        this.networkNode = networkNode;
        this.seedNodeAddresses = new HashSet<>(seedNodesRepository.getSeedNodeAddresses());
        this.clock = clock;
        storage = new Storage<>(storageDir, persistenceProtoResolver);

        this.networkNode.addConnectionListener(this);

        setConnectionLimits(maxConnections);

        // we check if app was idle for more then 5 sec.
        listener = new Clock.Listener() {
            @Override
            public void onSecondTick() {
            }

            @Override
            public void onMinuteTick() {
            }

            @Override
            public void onMissedSecondTick(long missed) {
                if (missed > Clock.IDLE_TOLERANCE) {
                    log.info("We have been in standby mode for {} sec", missed / 1000);
                    stopped = false;
                    listeners.stream().forEach(Listener::onAwakeFromStandby);
                }
            }
        };
        clock.addListener(listener);
    }

    public void shutDown() {
        Log.traceCall();

        networkNode.removeConnectionListener(this);
        clock.removeListener(listener);
        stopCheckMaxConnectionsTimer();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        PeerList persistedPeerList = storage.initAndGetPersistedWithFileName("PeerList", 1000);
        if (persistedPeerList != null)
            this.persistedPeers.addAll(persistedPeerList.getList());
    }

    public int getMaxConnections() {
        return maxConnectionsAbsolute;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    // Modify this to change the relationships between connection limits.
    private void setConnectionLimits(int maxConnections) {
        this.maxConnections = maxConnections;
        minConnections = Math.max(1, maxConnections - 4);
        maxConnectionsPeer = maxConnections + 4;
        maxConnectionsNonDirect = maxConnections + 8;
        maxConnectionsAbsolute = maxConnections + 18;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // ConnectionListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnection(Connection connection) {
        Log.logIfStressTests("onConnection to peer " +
            (connection.getPeersNodeAddressOptional().isPresent() ? connection.getPeersNodeAddressOptional().get() : "PeersNode unknown") +
            " / No. of connections: " + networkNode.getAllConnections().size());

        final boolean seedNode = isSeedNode(connection);

        final Optional<NodeAddress> addressOptional = connection.getPeersNodeAddressOptional();
        log.debug("onConnection: peer = {}{}",
            (addressOptional.isPresent() ? addressOptional.get().getFullAddress() : "not known yet (connection id=" + connection.getUid() + ")"),
            seedNode ? " (SeedNode)" : "");

        if (seedNode)
            connection.setPeerType(Connection.PeerType.SEED_NODE);

        doHouseKeeping();

        if (lostAllConnections) {
            lostAllConnections = false;
            stopped = false;
            listeners.stream().forEach(Listener::onNewConnectionAfterAllConnectionsLost);
        }
    }

    @Override
    public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
        Log.logIfStressTests("onDisconnect of peer " +
            (connection.getPeersNodeAddressOptional().isPresent() ? connection.getPeersNodeAddressOptional().get() : "PeersNode unknown") +
            " / No. of connections: " + networkNode.getAllConnections().size() +
            " / closeConnectionReason: " + closeConnectionReason);

        final Optional<NodeAddress> addressOptional = connection.getPeersNodeAddressOptional();
        log.debug("onDisconnect: peer = {}{} / closeConnectionReason: {}",
            (addressOptional.isPresent() ? addressOptional.get().getFullAddress() : "not known yet (connection id=" + connection.getUid() + ")"),
            isSeedNode(connection) ? " (SeedNode)" : "",
            closeConnectionReason);

        handleConnectionFault(connection);

        lostAllConnections = networkNode.getAllConnections().isEmpty();
        if (lostAllConnections) {
            stopped = true;
            listeners.stream().forEach(Listener::onAllConnectionsLost);
        }

        if (connection.getPeersNodeAddressOptional().isPresent() && isNodeBanned(closeConnectionReason, connection)) {
            final NodeAddress nodeAddress = connection.getPeersNodeAddressOptional().get();
            seedNodeAddresses.remove(nodeAddress);
            removePersistedPeer(nodeAddress);
            removeReportedPeer(nodeAddress);
        }
    }

    public boolean isNodeBanned(CloseConnectionReason closeConnectionReason, Connection connection) {
        return closeConnectionReason == CloseConnectionReason.PEER_BANNED &&
            connection.getPeersNodeAddressOptional().isPresent();
    }

    @Override
    public void onError(Throwable throwable) {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Housekeeping
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void doHouseKeeping() {
        if (checkMaxConnectionsTimer == null) {
            printConnectedPeers();
            checkMaxConnectionsTimer = UserThread.runAfter(() -> {
                stopCheckMaxConnectionsTimer();
                if (!stopped) {
                    removeAnonymousPeers();
                    removeSuperfluousSeedNodes();
                    removeTooOldReportedPeers();
                    removeTooOldPersistedPeers();
                    checkMaxConnections(maxConnections);
                } else {
                    log.debug("We have stopped already. We ignore that checkMaxConnectionsTimer.run call.");
                }
            }, CHECK_MAX_CONN_DELAY_SEC);
        }
    }

    private boolean checkMaxConnections(int limit) {
        Log.traceCall("limit=" + limit);
        Set<Connection> allConnections = networkNode.getAllConnections();
        int size = allConnections.size();
        log.debug("We have {} connections open. Our limit is {}", size, limit);

        if (size > limit) {
            log.debug("We have too many connections open.\n\t" +
                "Lets try first to remove the inbound connections of type PEER.");
            List<Connection> candidates = allConnections.stream()
                .filter(e -> e instanceof InboundConnection)
                .filter(e -> e.getPeerType() == Connection.PeerType.PEER)
                .collect(Collectors.toList());

            if (candidates.size() == 0) {
                log.debug("No candidates found. We check if we exceed our " +
                    "maxConnectionsPeer limit of {}", maxConnectionsPeer);
                if (size > maxConnectionsPeer) {
                    log.debug("Lets try to remove ANY connection of type PEER.");
                    candidates = allConnections.stream()
                        .filter(e -> e.getPeerType() == Connection.PeerType.PEER)
                        .collect(Collectors.toList());

                    if (candidates.size() == 0) {
                        log.debug("No candidates found. We check if we exceed our " +
                            "maxConnectionsNonDirect limit of {}", maxConnectionsNonDirect);
                        if (size > maxConnectionsNonDirect) {
                            log.debug("Lets try to remove any connection which is not of type DIRECT_MSG_PEER or INITIAL_DATA_REQUEST.");
                            candidates = allConnections.stream()
                                .filter(e -> e.getPeerType() != Connection.PeerType.DIRECT_MSG_PEER && e.getPeerType() != Connection.PeerType.INITIAL_DATA_REQUEST)
                                .collect(Collectors.toList());

                            if (candidates.size() == 0) {
                                log.debug("No candidates found. We check if we exceed our " +
                                    "maxConnectionsAbsolute limit of {}", maxConnectionsAbsolute);
                                if (size > maxConnectionsAbsolute) {
                                    log.debug("Lets try to remove any connection.");
                                    candidates = allConnections.stream().collect(Collectors.toList());
                                }
                            }
                        }
                    }
                }
            }

            if (candidates.size() > 0) {
                candidates.sort((o1, o2) -> ((Long) o1.getStatistic().getLastActivityTimestamp()).compareTo(((Long) o2.getStatistic().getLastActivityTimestamp())));
                log.debug("Candidates.size() for shut down=" + candidates.size());
                Connection connection = candidates.remove(0);
                log.debug("We are going to shut down the oldest connection.\n\tconnection=" + connection.toString());
                if (!connection.isStopped())
                    connection.shutDown(CloseConnectionReason.TOO_MANY_CONNECTIONS_OPEN, () -> checkMaxConnections(limit));
                return true;
            } else {
                log.warn("No candidates found to remove (That case should not be possible as we use in the " +
                    "last case all connections).\n\t" +
                    "allConnections=", allConnections);
                return false;
            }
        } else {
            log.trace("We only have {} connections open and don't need to close any.", size);
            return false;
        }
    }

    private void removeAnonymousPeers() {
        Log.traceCall();
        networkNode.getAllConnections().stream()
            .filter(connection -> !connection.hasPeersNodeAddress())
            .forEach(connection -> UserThread.runAfter(() -> {
                // We give 30 seconds delay and check again if still no address is set
                if (!connection.hasPeersNodeAddress() && !connection.isStopped()) {
                    log.debug("We close the connection as the peer address is still unknown.\n\t" +
                        "connection=" + connection);
                    connection.shutDown(CloseConnectionReason.UNKNOWN_PEER_ADDRESS);
                }
            }, REMOVE_ANONYMOUS_PEER_SEC));
    }

    private void removeSuperfluousSeedNodes() {
        Log.traceCall();
        if (networkNode.getConfirmedConnections().size() > maxConnections) {
            Set<Connection> connections = networkNode.getConfirmedConnections();
            if (hasSufficientConnections()) {
                List<Connection> candidates = connections.stream()
                    .filter(this::isSeedNode)
                    .collect(Collectors.toList());

                if (candidates.size() > 1) {
                    candidates.sort((o1, o2) -> ((Long) o1.getStatistic().getLastActivityTimestamp()).compareTo(((Long) o2.getStatistic().getLastActivityTimestamp())));
                    log.debug("Number of connections exceeding MAX_CONNECTIONS_EXTENDED_1. Current size=" + candidates.size());
                    Connection connection = candidates.remove(0);
                    log.debug("We are going to shut down the oldest connection.\n\tconnection=" + connection.toString());
                    connection.shutDown(CloseConnectionReason.TOO_MANY_SEED_NODES_CONNECTED, this::removeSuperfluousSeedNodes);
                }
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Reported peers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean removeReportedPeer(Peer reportedPeer) {
        boolean contained = reportedPeers.remove(reportedPeer);
        printReportedPeers();
        return contained;
    }

    @SuppressWarnings("UnusedReturnValue")
    @Nullable
    private Peer removeReportedPeer(NodeAddress nodeAddress) {
        List<Peer> reportedPeersClone = new ArrayList<>(reportedPeers);
        Optional<Peer> reportedPeerOptional = reportedPeersClone.stream()
            .filter(e -> e.getNodeAddress().equals(nodeAddress)).findAny();
        if (reportedPeerOptional.isPresent()) {
            Peer reportedPeer = reportedPeerOptional.get();
            removeReportedPeer(reportedPeer);
            return reportedPeer;
        } else {
            return null;
        }
    }

    private void removeTooOldReportedPeers() {
        Log.traceCall();
        List<Peer> reportedPeersClone = new ArrayList<>(reportedPeers);
        Set<Peer> reportedPeersToRemove = reportedPeersClone.stream()
            .filter(reportedPeer -> new Date().getTime() - reportedPeer.getDate().getTime() > MAX_AGE)
            .collect(Collectors.toSet());
        reportedPeersToRemove.forEach(this::removeReportedPeer);
    }

    public Set<Peer> getReportedPeers() {
        return reportedPeers;
    }

    public void addToReportedPeers(HashSet<Peer> reportedPeersToAdd, Connection connection) {
        printNewReportedPeers(reportedPeersToAdd);

        // We check if the reported msg is not violating our rules
        if (reportedPeersToAdd.size() <= (MAX_REPORTED_PEERS + maxConnectionsAbsolute + 10)) {
            reportedPeers.addAll(reportedPeersToAdd);
            purgeReportedPeersIfExceeds();

            persistedPeers.addAll(reportedPeersToAdd);
            purgePersistedPeersIfExceeds();
            storage.queueUpForSave(new PeerList(new ArrayList<>(persistedPeers)), 2000);

            printReportedPeers();
        } else {
            // If a node is trying to send too many list we treat it as rule violation.
            // Reported list include the connected list. We use the max value and give some extra headroom.
            // Will trigger a shutdown after 2nd time sending too much
            connection.reportIllegalRequest(RuleViolation.TOO_MANY_REPORTED_PEERS_SENT);
        }
    }

    private void purgeReportedPeersIfExceeds() {
        Log.traceCall();
        int size = reportedPeers.size();
        int limit = MAX_REPORTED_PEERS - maxConnectionsAbsolute;
        if (size > limit) {
            log.trace("We have already {} reported peers which exceeds our limit of {}." +
                "We remove random peers from the reported peers list.", size, limit);
            int diff = size - limit;
            List<Peer> list = new ArrayList<>(reportedPeers);
            // we dont use sorting by lastActivityDate to keep it more random
            for (int i = 0; i < diff; i++) {
                Peer toRemove = list.remove(new Random().nextInt(list.size()));
                removeReportedPeer(toRemove);
            }
        } else {
            log.trace("No need to purge reported peers.\n\tWe don't have more then {} reported peers yet.", MAX_REPORTED_PEERS);
        }
    }

    private void printReportedPeers() {
        if (!reportedPeers.isEmpty()) {
            //noinspection ConstantConditions
            if (PRINT_REPORTED_PEERS_DETAILS) {
                StringBuilder result = new StringBuilder("\n\n------------------------------------------------------------\n" +
                    "Collected reported peers:");
                List<Peer> reportedPeersClone = new ArrayList<>(reportedPeers);
                reportedPeersClone.stream().forEach(e -> result.append("\n").append(e));
                result.append("\n------------------------------------------------------------\n");
                log.debug(result.toString());
            }
            log.debug("Number of collected reported peers: {}", reportedPeers.size());
        }
    }

    private void printNewReportedPeers(HashSet<Peer> reportedPeers) {
        //noinspection ConstantConditions
        if (PRINT_REPORTED_PEERS_DETAILS) {
            StringBuilder result = new StringBuilder("We received new reportedPeers:");
            List<Peer> reportedPeersClone = new ArrayList<>(reportedPeers);
            reportedPeersClone.stream().forEach(e -> result.append("\n\t").append(e));
            log.debug(result.toString());
        }
        log.debug("Number of new arrived reported peers: {}", reportedPeers.size());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    //  Persisted list
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean removePersistedPeer(Peer persistedPeer) {
        if (persistedPeers.contains(persistedPeer)) {
            persistedPeers.remove(persistedPeer);
            storage.queueUpForSave(new PeerList(new ArrayList<>(persistedPeers)), 2000);
            return true;
        } else {
            return false;
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    private boolean removePersistedPeer(NodeAddress nodeAddress) {
        Optional<Peer> persistedPeerOptional = getPersistedPeerOptional(nodeAddress);
        return persistedPeerOptional.isPresent() && removePersistedPeer(persistedPeerOptional.get());
    }

    private Optional<Peer> getPersistedPeerOptional(NodeAddress nodeAddress) {
        return persistedPeers.stream()
            .filter(e -> e.getNodeAddress().equals(nodeAddress)).findAny();
    }

    private void removeTooOldPersistedPeers() {
        Log.traceCall();
        Set<Peer> persistedPeersToRemove = persistedPeers.stream()
            .filter(reportedPeer -> new Date().getTime() - reportedPeer.getDate().getTime() > MAX_AGE)
            .collect(Collectors.toSet());
        persistedPeersToRemove.forEach(this::removePersistedPeer);
    }

    private void purgePersistedPeersIfExceeds() {
        Log.traceCall();
        int size = persistedPeers.size();
        int limit = MAX_PERSISTED_PEERS;
        if (size > limit) {
            log.trace("We have already {} persisted peers which exceeds our limit of {}." +
                "We remove random peers from the persisted peers list.", size, limit);
            int diff = size - limit;
            List<Peer> list = new ArrayList<>(persistedPeers);
            // we dont use sorting by lastActivityDate to avoid attack vectors and keep it more random
            for (int i = 0; i < diff; i++) {
                Peer toRemove = list.remove(new Random().nextInt(list.size()));
                removePersistedPeer(toRemove);
            }
        } else {
            log.trace("No need to purge persisted peers.\n\tWe don't have more then {} persisted peers yet.", MAX_PERSISTED_PEERS);
        }
    }

    public Set<Peer> getPersistedPeers() {
        return persistedPeers;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    //  Misc
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean hasSufficientConnections() {
        return networkNode.getNodeAddressesOfConfirmedConnections().size() >= minConnections;
    }

    private boolean isSeedNode(Peer reportedPeer) {
        return seedNodeAddresses.contains(reportedPeer.getNodeAddress());
    }

    public boolean isSeedNode(NodeAddress nodeAddress) {
        return seedNodeAddresses.contains(nodeAddress);
    }

    public boolean isSeedNode(Connection connection) {
        return connection.hasPeersNodeAddress() && seedNodeAddresses.contains(connection.getPeersNodeAddressOptional().get());
    }

    public boolean isSelf(Peer reportedPeer) {
        return isSelf(reportedPeer.getNodeAddress());
    }

    public boolean isSelf(NodeAddress nodeAddress) {
        return nodeAddress.equals(networkNode.getNodeAddress());
    }

    public boolean isConfirmed(Peer reportedPeer) {
        return isConfirmed(reportedPeer.getNodeAddress());
    }

    // Checks if that connection has the peers node address
    public boolean isConfirmed(NodeAddress nodeAddress) {
        return networkNode.getNodeAddressesOfConfirmedConnections().contains(nodeAddress);
    }

    public void handleConnectionFault(Connection connection) {
        connection.getPeersNodeAddressOptional().ifPresent(nodeAddress -> handleConnectionFault(nodeAddress, connection));
    }

    public void handleConnectionFault(NodeAddress nodeAddress) {
        handleConnectionFault(nodeAddress, null);
    }

    public void handleConnectionFault(NodeAddress nodeAddress, @Nullable Connection connection) {
        Log.traceCall("nodeAddress=" + nodeAddress);
        boolean doRemovePersistedPeer = false;
        removeReportedPeer(nodeAddress);
        Optional<Peer> persistedPeerOptional = getPersistedPeerOptional(nodeAddress);
        if (persistedPeerOptional.isPresent()) {
            Peer persistedPeer = persistedPeerOptional.get();
            persistedPeer.increaseFailedConnectionAttempts();
            doRemovePersistedPeer = persistedPeer.tooManyFailedConnectionAttempts();
        }
        doRemovePersistedPeer = doRemovePersistedPeer || (connection != null && connection.getRuleViolation() != null);

        if (doRemovePersistedPeer)
            removePersistedPeer(nodeAddress);
        else
            removeTooOldPersistedPeers();
    }

    public void shutDownConnection(Connection connection, CloseConnectionReason closeConnectionReason) {
        if (connection.getPeerType() != Connection.PeerType.DIRECT_MSG_PEER)
            connection.shutDown(closeConnectionReason);
    }

    public void shutDownConnection(NodeAddress peersNodeAddress, CloseConnectionReason closeConnectionReason) {
        networkNode.getAllConnections().stream()
            .filter(connection -> connection.getPeersNodeAddressOptional().isPresent() &&
                connection.getPeersNodeAddressOptional().get().equals(peersNodeAddress) &&
                connection.getPeerType() != Connection.PeerType.DIRECT_MSG_PEER)
            .findAny()
            .ifPresent(connection -> connection.shutDown(closeConnectionReason));
    }

    public HashSet<Peer> getConnectedNonSeedNodeReportedPeers(NodeAddress excludedNodeAddress) {
        return new HashSet<>(getConnectedNonSeedNodeReportedPeers().stream()
            .filter(e -> !e.getNodeAddress().equals(excludedNodeAddress))
            .collect(Collectors.toSet()));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    //  Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Set<Peer> getConnectedReportedPeers() {
        // networkNode.getConfirmedConnections includes:
        // filter(connection -> connection.getPeersNodeAddressOptional().isPresent())
        return networkNode.getConfirmedConnections().stream()
            .map(c -> new Peer(c.getPeersNodeAddressOptional().get()))
            .collect(Collectors.toSet());
    }

    private HashSet<Peer> getConnectedNonSeedNodeReportedPeers() {
        return new HashSet<>(getConnectedReportedPeers().stream()
            .filter(e -> !isSeedNode(e))
            .collect(Collectors.toSet()));
    }

    private void stopCheckMaxConnectionsTimer() {
        if (checkMaxConnectionsTimer != null) {
            checkMaxConnectionsTimer.stop();
            checkMaxConnectionsTimer = null;
        }
    }

    private void printConnectedPeers() {
        if (!networkNode.getConfirmedConnections().isEmpty()) {
            StringBuilder result = new StringBuilder("\n\n------------------------------------------------------------\n" +
                "Connected peers for node " + networkNode.getNodeAddress() + ":");
            networkNode.getConfirmedConnections().stream().forEach(e -> result.append("\n")
                .append(e.getPeersNodeAddressOptional().get()).append(" ").append(e.getPeerType()));
            result.append("\n------------------------------------------------------------\n");
            log.debug(result.toString());
        }
    }
}
