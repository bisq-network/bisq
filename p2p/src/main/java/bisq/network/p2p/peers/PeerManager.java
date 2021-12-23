/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.network.p2p.peers;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.CloseConnectionReason;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.ConnectionListener;
import bisq.network.p2p.network.InboundConnection;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.network.PeerType;
import bisq.network.p2p.network.RuleViolation;
import bisq.network.p2p.peers.peerexchange.Peer;
import bisq.network.p2p.peers.peerexchange.PeerList;
import bisq.network.p2p.seed.SeedNodeRepository;

import bisq.common.ClockWatcher;
import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.Capabilities;
import bisq.common.app.Capability;
import bisq.common.config.Config;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.persistable.PersistedDataHost;

import javax.inject.Inject;
import javax.inject.Named;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public final class PeerManager implements ConnectionListener, PersistedDataHost {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static final long CHECK_MAX_CONN_DELAY_SEC = 10;
    // Use a long delay as the bootstrapping peer might need a while until it knows its onion address
    private static final long REMOVE_ANONYMOUS_PEER_SEC = 240;

    private static final int MAX_REPORTED_PEERS = 1000;
    private static final int MAX_PERSISTED_PEERS = 500;
    // max age for reported peers is 14 days
    private static final long MAX_AGE = TimeUnit.DAYS.toMillis(14);
    // Age of what we consider connected peers still as live peers
    private static final long MAX_AGE_LIVE_PEERS = TimeUnit.MINUTES.toMillis(30);
    private static final boolean PRINT_REPORTED_PEERS_DETAILS = true;
    private Timer printStatisticsTimer;
    private boolean shutDownRequested;


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
    private final ClockWatcher clockWatcher;
    private final Set<NodeAddress> seedNodeAddresses;
    private final PersistenceManager<PeerList> persistenceManager;
    private final ClockWatcher.Listener clockWatcherListener;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    // Persistable peerList
    private final PeerList peerList = new PeerList();
    // Peers we got reported from other peers
    @Getter
    private final Set<Peer> reportedPeers = new HashSet<>();
    // Most recent peers with activity date of last 30 min.
    private final Set<Peer> latestLivePeers = new HashSet<>();

    private Timer checkMaxConnectionsTimer;
    private boolean stopped;
    private boolean lostAllConnections;
    private int maxConnections;

    @Getter
    private int minConnections;
    private int outBoundPeerTrigger;
    private int initialDataExchangeTrigger;
    private int maxConnectionsAbsolute;
    @Getter
    private int peakNumConnections;
    @Getter
    private int numAllConnectionsLostEvents;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PeerManager(NetworkNode networkNode,
                       SeedNodeRepository seedNodeRepository,
                       ClockWatcher clockWatcher,
                       PersistenceManager<PeerList> persistenceManager,
                       @Named(Config.MAX_CONNECTIONS) int maxConnections) {
        this.networkNode = networkNode;
        this.seedNodeAddresses = new HashSet<>(seedNodeRepository.getSeedNodeAddresses());
        this.clockWatcher = clockWatcher;
        this.persistenceManager = persistenceManager;

        this.persistenceManager.initialize(peerList, PersistenceManager.Source.PRIVATE_LOW_PRIO);
        this.networkNode.addConnectionListener(this);

        setConnectionLimits(maxConnections);

        // we check if app was idle for more then 5 sec.
        clockWatcherListener = new ClockWatcher.Listener() {
            @Override
            public void onSecondTick() {
            }

            @Override
            public void onMinuteTick() {
            }

            @Override
            public void onAwakeFromStandby(long missedMs) {
                // We got probably stopped set to true when we got a longer interruption (e.g. lost all connections),
                // now we get awake again, so set stopped to false.
                stopped = false;
                listeners.forEach(Listener::onAwakeFromStandby);
            }
        };
        clockWatcher.addListener(clockWatcherListener);

        printStatisticsTimer = UserThread.runPeriodically(this::printStatistics, TimeUnit.MINUTES.toSeconds(60));
    }

    public void shutDown() {
        shutDownRequested = true;

        networkNode.removeConnectionListener(this);
        clockWatcher.removeListener(clockWatcherListener);

        stopCheckMaxConnectionsTimer();

        if (printStatisticsTimer != null) {
            printStatisticsTimer.stop();
            printStatisticsTimer = null;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted(Runnable completeHandler) {
        persistenceManager.readPersisted(persisted -> {
                    peerList.setAll(persisted.getSet());
                    completeHandler.run();
                },
                completeHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ConnectionListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnection(Connection connection) {
        connection.getConnectionState().setSeedNode(isSeedNode(connection));

        doHouseKeeping();

        if (lostAllConnections) {
            lostAllConnections = false;
            stopped = false;
            log.info("\n------------------------------------------------------------\n" +
                    "Established a new connection from/to {} after all connections lost.\n" +
                    "------------------------------------------------------------", connection.getPeersNodeAddressOptional());
            listeners.forEach(Listener::onNewConnectionAfterAllConnectionsLost);
        }
        connection.getPeersNodeAddressOptional()
                .flatMap(this::findPeer)
                .ifPresent(Peer::onConnection);
    }

    @Override
    public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
        log.info("onDisconnect called: nodeAddress={}, closeConnectionReason={}",
                connection.getPeersNodeAddressOptional(), closeConnectionReason);
        handleConnectionFault(connection);

        boolean previousLostAllConnections = lostAllConnections;
        lostAllConnections = networkNode.getAllConnections().isEmpty();

        if (lostAllConnections) {
            stopped = true;

            if (!shutDownRequested) {
                if (!previousLostAllConnections) {
                    // If we enter to 'All connections lost' we count the event.
                    numAllConnectionsLostEvents++;
                }

                log.warn("\n------------------------------------------------------------\n" +
                        "All connections lost\n" +
                        "------------------------------------------------------------");

                listeners.forEach(Listener::onAllConnectionsLost);
            }
        }
        maybeRemoveBannedPeer(closeConnectionReason, connection);
    }

    @Override
    public void onError(Throwable throwable) {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Connection
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean hasSufficientConnections() {
        return networkNode.getConfirmedConnections().size() >= minConnections;
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
        boolean doRemovePersistedPeer = false;
        removeReportedPeer(nodeAddress);
        Optional<Peer> persistedPeerOptional = findPersistedPeer(nodeAddress);
        if (persistedPeerOptional.isPresent()) {
            Peer persistedPeer = persistedPeerOptional.get();
            persistedPeer.onDisconnect();
            doRemovePersistedPeer = persistedPeer.tooManyFailedConnectionAttempts();
        }
        boolean ruleViolation = connection != null && connection.getRuleViolation() != null;
        doRemovePersistedPeer = doRemovePersistedPeer || ruleViolation;

        if (doRemovePersistedPeer)
            removePersistedPeer(nodeAddress);
        else
            removeTooOldPersistedPeers();
    }

    public boolean isSeedNode(Connection connection) {
        return connection.getPeersNodeAddressOptional().isPresent() &&
                isSeedNode(connection.getPeersNodeAddressOptional().get());
    }

    public boolean isSelf(NodeAddress nodeAddress) {
        return nodeAddress.equals(networkNode.getNodeAddress());
    }

    private boolean isSeedNode(Peer peer) {
        return seedNodeAddresses.contains(peer.getNodeAddress());
    }

    public boolean isSeedNode(NodeAddress nodeAddress) {
        return seedNodeAddresses.contains(nodeAddress);
    }

    public boolean isPeerBanned(CloseConnectionReason closeConnectionReason, Connection connection) {
        return closeConnectionReason == CloseConnectionReason.PEER_BANNED &&
                connection.getPeersNodeAddressOptional().isPresent();
    }

    private void maybeRemoveBannedPeer(CloseConnectionReason closeConnectionReason, Connection connection) {
        if (connection.getPeersNodeAddressOptional().isPresent() && isPeerBanned(closeConnectionReason, connection)) {
            NodeAddress nodeAddress = connection.getPeersNodeAddressOptional().get();
            seedNodeAddresses.remove(nodeAddress);
            removePersistedPeer(nodeAddress);
            removeReportedPeer(nodeAddress);
        }
    }

    public void maybeResetNumAllConnectionsLostEvents() {
        if (!networkNode.getAllConnections().isEmpty()) {
            numAllConnectionsLostEvents = 0;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unused")
    public Optional<Peer> findPeer(NodeAddress peersNodeAddress) {
        return getAllPeers().stream()
                .filter(peer -> peer.getNodeAddress().equals(peersNodeAddress))
                .findAny();
    }

    public Set<Peer> getAllPeers() {
        Set<Peer> allPeers = new HashSet<>(getLivePeers());
        allPeers.addAll(getPersistedPeers());
        allPeers.addAll(reportedPeers);
        return allPeers;
    }

    public Collection<Peer> getPersistedPeers() {
        return peerList.getSet();
    }

    public void addToReportedPeers(Set<Peer> reportedPeersToAdd,
                                   Connection connection,
                                   Capabilities capabilities) {
        applyCapabilities(connection, capabilities);

        Set<Peer> peers = reportedPeersToAdd.stream()
                .filter(peer -> !isSelf(peer.getNodeAddress()))
                .collect(Collectors.toSet());

        printNewReportedPeers(peers);

        // We check if the reported msg is not violating our rules
        if (peers.size() <= (MAX_REPORTED_PEERS + maxConnectionsAbsolute + 10)) {
            reportedPeers.addAll(peers);
            purgeReportedPeersIfExceeds();

            getPersistedPeers().addAll(peers);
            purgePersistedPeersIfExceeds();
            requestPersistence();

            printReportedPeers();
        } else {
            // If a node is trying to send too many list we treat it as rule violation.
            // Reported list include the connected list. We use the max value and give some extra headroom.
            // Will trigger a shutdown after 2nd time sending too much
            connection.reportInvalidRequest(RuleViolation.TOO_MANY_REPORTED_PEERS_SENT);
        }
    }

    // Delivers the live peers from the last 30 min (MAX_AGE_LIVE_PEERS)
    // We include older peers to avoid risks for network partitioning
    public Set<Peer> getLivePeers() {
        return getLivePeers(null);
    }

    public Set<Peer> getLivePeers(@Nullable NodeAddress excludedNodeAddress) {
        int oldNumLatestLivePeers = latestLivePeers.size();

        Set<Peer> peers = new HashSet<>(latestLivePeers);
        Set<Peer> currentLivePeers = getConnectedReportedPeers().stream()
                .filter(e -> !isSeedNode(e))
                .filter(e -> !e.getNodeAddress().equals(excludedNodeAddress))
                .collect(Collectors.toSet());
        peers.addAll(currentLivePeers);

        long maxAge = new Date().getTime() - MAX_AGE_LIVE_PEERS;
        latestLivePeers.clear();
        Set<Peer> recentPeers = peers.stream()
                .filter(peer -> peer.getDateAsLong() > maxAge)
                .collect(Collectors.toSet());
        latestLivePeers.addAll(recentPeers);

        if (oldNumLatestLivePeers != latestLivePeers.size())
            log.info("Num of latestLivePeers={}", latestLivePeers.size());
        return latestLivePeers;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Capabilities
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean peerHasCapability(NodeAddress peersNodeAddress, Capability capability) {
        return findPeersCapabilities(peersNodeAddress)
                .map(capabilities -> capabilities.contains(capability))
                .orElse(false);
    }

    public Optional<Capabilities> findPeersCapabilities(NodeAddress nodeAddress) {
        // We look up first our connections as that is our own data. If not found there we look up the peers which
        // include reported peers.
        Optional<Capabilities> optionalCapabilities = networkNode.findPeersCapabilities(nodeAddress);
        if (optionalCapabilities.isPresent() && !optionalCapabilities.get().isEmpty()) {
            return optionalCapabilities;
        }

        // Reported peers are not trusted data. We could get capabilities which miss the
        // peers real capability or we could get maliciously altered capabilities telling us the peer supports a
        // capability which is in fact not supported. This could lead to connection loss as we might send data not
        // recognized by the peer. As we register a listener on connection if we don't have set the capability from our
        // own sources we would get it fixed as soon we have a connection with that peer, rendering such an attack
        // inefficient.
        // Also this risk is only for not updated peers, so in case that would be abused for an
        // attack all users have a strong incentive to update ;-).
        return getAllPeers().stream()
                .filter(peer -> peer.getNodeAddress().equals(nodeAddress))
                .findAny()
                .map(Peer::getCapabilities);
    }

    private void applyCapabilities(Connection connection, Capabilities newCapabilities) {
        if (newCapabilities == null || newCapabilities.isEmpty()) {
            return;
        }

        connection.getPeersNodeAddressOptional().ifPresent(nodeAddress -> {
            getAllPeers().stream()
                    .filter(peer -> peer.getNodeAddress().equals(nodeAddress))
                    .filter(peer -> peer.getCapabilities().hasLess(newCapabilities))
                    .forEach(peer -> peer.setCapabilities(newCapabilities));
        });
        requestPersistence();
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
                    Set<Connection> allConnections = new HashSet<>(networkNode.getAllConnections());
                    int size = allConnections.size();
                    peakNumConnections = Math.max(peakNumConnections, size);

                    removeAnonymousPeers();
                    removeTooOldReportedPeers();
                    removeTooOldPersistedPeers();
                    checkMaxConnections();
                } else {
                    log.debug("We have stopped already. We ignore that checkMaxConnectionsTimer.run call.");
                }
            }, CHECK_MAX_CONN_DELAY_SEC);
        }
    }

    @VisibleForTesting
    boolean checkMaxConnections() {
        Set<Connection> allConnections = new HashSet<>(networkNode.getAllConnections());
        int size = allConnections.size();
        log.info("We have {} connections open. Our limit is {}", size, maxConnections);

        if (size <= maxConnections) {
            log.debug("We have not exceeded the maxConnections limit of {} " +
                    "so don't need to close any connections.", size);
            return false;
        }

        log.info("We have too many connections open. " +
                "Lets try first to remove the inbound connections of type PEER.");
        List<Connection> candidates = allConnections.stream()
                .filter(e -> e instanceof InboundConnection)
                .filter(e -> e.getConnectionState().getPeerType() == PeerType.PEER)
                .sorted(Comparator.comparingLong(o -> o.getStatistic().getLastActivityTimestamp()))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            log.info("No candidates found. We check if we exceed our " +
                    "outBoundPeerTrigger of {}", outBoundPeerTrigger);
            if (size <= outBoundPeerTrigger) {
                log.info("We have not exceeded outBoundPeerTrigger of {} " +
                        "so don't need to close any connections", outBoundPeerTrigger);
                return false;
            }

            log.info("We have exceeded outBoundPeerTrigger of {}. " +
                    "Lets try to remove outbound connection of type PEER.", outBoundPeerTrigger);
            candidates = allConnections.stream()
                    .filter(e -> e.getConnectionState().getPeerType() == PeerType.PEER)
                    .sorted(Comparator.comparingLong(o -> o.getStatistic().getLastActivityTimestamp()))
                    .collect(Collectors.toList());

            if (candidates.isEmpty()) {
                log.info("No candidates found. We check if we exceed our " +
                        "initialDataExchangeTrigger of {}", initialDataExchangeTrigger);
                if (size <= initialDataExchangeTrigger) {
                    log.info("We have not exceeded initialDataExchangeTrigger of {} " +
                            "so don't need to close any connections", initialDataExchangeTrigger);
                    return false;
                }

                log.info("We have exceeded initialDataExchangeTrigger of {} " +
                        "Lets try to remove the oldest INITIAL_DATA_EXCHANGE connection.", initialDataExchangeTrigger);
                candidates = allConnections.stream()
                        .filter(e -> e.getConnectionState().getPeerType() == PeerType.INITIAL_DATA_EXCHANGE)
                        .sorted(Comparator.comparingLong(o -> o.getConnectionState().getLastInitialDataMsgTimeStamp()))
                        .collect(Collectors.toList());

                if (candidates.isEmpty()) {
                    log.info("No candidates found. We check if we exceed our " +
                            "maxConnectionsAbsolute limit of {}", maxConnectionsAbsolute);
                    if (size <= maxConnectionsAbsolute) {
                        log.info("We have not exceeded maxConnectionsAbsolute limit of {} " +
                                "so don't need to close any connections", maxConnectionsAbsolute);
                        return false;
                    }

                    log.info("We reached abs. max. connections. Lets try to remove ANY connection.");
                    candidates = allConnections.stream()
                            .sorted(Comparator.comparingLong(o -> o.getStatistic().getLastActivityTimestamp()))
                            .collect(Collectors.toList());
                }
            }
        }

        if (!candidates.isEmpty()) {
            Connection connection = candidates.remove(0);
            log.info("checkMaxConnections: Num candidates for shut down={}. We close oldest connection to peer {}",
                    candidates.size(), connection.getPeersNodeAddressOptional());
            if (!connection.isStopped()) {
                connection.shutDown(CloseConnectionReason.TOO_MANY_CONNECTIONS_OPEN,
                        () -> UserThread.runAfter(this::checkMaxConnections, 100, TimeUnit.MILLISECONDS));
                return true;
            }
        }

        log.info("No candidates found to remove. " +
                "size={}, allConnections={}", size, allConnections);
        return false;
    }

    private void removeAnonymousPeers() {
        networkNode.getAllConnections().stream()
                .filter(connection -> !connection.hasPeersNodeAddress())
                .filter(connection -> connection.getConnectionState().getPeerType() == PeerType.PEER)
                .forEach(connection -> UserThread.runAfter(() -> { // todo we keep a potentially dead connection in memory for too long...
                    // We give 240 seconds delay and check again if still no address is set
                    // Keep the delay long as we don't want to disconnect a peer in case we are a seed node just
                    // because he needs longer for the HS publishing
                    if (!connection.isStopped() && !connection.hasPeersNodeAddress()) {
                        log.info("removeAnonymousPeers: We close the connection as the peer address is still unknown. " +
                                "Peer: {}", connection.getPeersNodeAddressOptional());
                        connection.shutDown(CloseConnectionReason.UNKNOWN_PEER_ADDRESS);
                    }
                }, REMOVE_ANONYMOUS_PEER_SEC));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Reported peers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void removeReportedPeer(Peer reportedPeer) {
        reportedPeers.remove(reportedPeer);
        printReportedPeers();
    }

    private void removeReportedPeer(NodeAddress nodeAddress) {
        List<Peer> reportedPeersClone = new ArrayList<>(reportedPeers);
        reportedPeersClone.stream()
                .filter(e -> e.getNodeAddress().equals(nodeAddress))
                .findAny()
                .ifPresent(this::removeReportedPeer);
    }

    private void removeTooOldReportedPeers() {
        List<Peer> reportedPeersClone = new ArrayList<>(reportedPeers);
        Set<Peer> reportedPeersToRemove = reportedPeersClone.stream()
                .filter(reportedPeer -> new Date().getTime() - reportedPeer.getDate().getTime() > MAX_AGE)
                .collect(Collectors.toSet());
        reportedPeersToRemove.forEach(this::removeReportedPeer);
    }


    private void purgeReportedPeersIfExceeds() {
        int size = reportedPeers.size();
        if (size > MAX_REPORTED_PEERS) {
            log.info("We have already {} reported peers which exceeds our limit of {}." +
                    "We remove random peers from the reported peers list.", size, MAX_REPORTED_PEERS);
            int diff = size - MAX_REPORTED_PEERS;
            List<Peer> list = new ArrayList<>(reportedPeers);
            // we don't use sorting by lastActivityDate to keep it more random
            for (int i = 0; i < diff; i++) {
                if (!list.isEmpty()) {
                    Peer toRemove = list.remove(new Random().nextInt(list.size()));
                    removeReportedPeer(toRemove);
                }
            }
        } else {
            log.trace("No need to purge reported peers.\n\tWe don't have more then {} reported peers yet.", MAX_REPORTED_PEERS);
        }
    }

    private void printReportedPeers() {
        if (!reportedPeers.isEmpty()) {
            if (PRINT_REPORTED_PEERS_DETAILS) {
                StringBuilder result = new StringBuilder("\n\n------------------------------------------------------------\n" +
                        "Collected reported peers:");
                List<Peer> reportedPeersClone = new ArrayList<>(reportedPeers);
                reportedPeersClone.forEach(e -> result.append("\n").append(e));
                result.append("\n------------------------------------------------------------\n");
                log.trace(result.toString());
            }
            log.debug("Number of reported peers: {}", reportedPeers.size());
        }
    }

    private void printNewReportedPeers(Set<Peer> reportedPeers) {
        if (PRINT_REPORTED_PEERS_DETAILS) {
            StringBuilder result = new StringBuilder("We received new reportedPeers:");
            List<Peer> reportedPeersClone = new ArrayList<>(reportedPeers);
            reportedPeersClone.forEach(e -> result.append("\n\t").append(e));
            log.trace(result.toString());
        }
        log.debug("Number of new arrived reported peers: {}", reportedPeers.size());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    //  Persisted peers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean removePersistedPeer(Peer persistedPeer) {
        if (getPersistedPeers().contains(persistedPeer)) {
            getPersistedPeers().remove(persistedPeer);
            requestPersistence();
            return true;
        } else {
            return false;
        }
    }

    private void requestPersistence() {
        persistenceManager.requestPersistence();
    }

    @SuppressWarnings("UnusedReturnValue")
    private boolean removePersistedPeer(NodeAddress nodeAddress) {
        Optional<Peer> optionalPersistedPeer = findPersistedPeer(nodeAddress);
        return optionalPersistedPeer.isPresent() && removePersistedPeer(optionalPersistedPeer.get());
    }

    private Optional<Peer> findPersistedPeer(NodeAddress nodeAddress) {
        return getPersistedPeers().stream()
                .filter(e -> e.getNodeAddress().equals(nodeAddress))
                .findAny();
    }

    private void removeTooOldPersistedPeers() {
        Set<Peer> persistedPeersToRemove = getPersistedPeers().stream()
                .filter(reportedPeer -> new Date().getTime() - reportedPeer.getDate().getTime() > MAX_AGE)
                .collect(Collectors.toSet());
        persistedPeersToRemove.forEach(this::removePersistedPeer);
    }

    private void purgePersistedPeersIfExceeds() {
        int size = getPersistedPeers().size();
        int limit = MAX_PERSISTED_PEERS;
        if (size > limit) {
            log.trace("We have already {} persisted peers which exceeds our limit of {}." +
                    "We remove random peers from the persisted peers list.", size, limit);
            int diff = size - limit;
            List<Peer> list = new ArrayList<>(getPersistedPeers());
            // we don't use sorting by lastActivityDate to avoid attack vectors and keep it more random
            for (int i = 0; i < diff; i++) {
                if (!list.isEmpty()) {
                    Peer toRemove = list.remove(new Random().nextInt(list.size()));
                    removePersistedPeer(toRemove);
                }
            }
        } else {
            log.trace("No need to purge persisted peers.\n\tWe don't have more then {} persisted peers yet.", MAX_PERSISTED_PEERS);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public int getMaxConnections() {
        return maxConnections;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    //  Private misc
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Modify this to change the relationships between connection limits.
    // maxConnections default 12
    private void setConnectionLimits(int maxConnections) {
        this.maxConnections = maxConnections;                                                     // app node 12; seedNode 20
        minConnections = Math.max(1, (int) Math.round(maxConnections * 0.7));                     // app node  8; seedNode 14
        outBoundPeerTrigger = Math.max(4, (int) Math.round(maxConnections * 1.3));                // app node 16; seedNode 26
        initialDataExchangeTrigger = Math.max(8, (int) Math.round(maxConnections * 1.7));         // app node 20; seedNode 34
        maxConnectionsAbsolute = Math.max(12, (int) Math.round(maxConnections * 2.5));            // app node 30; seedNode 50
    }

    private Set<Peer> getConnectedReportedPeers() {
        // networkNode.getConfirmedConnections includes:
        // filter(connection -> connection.getPeersNodeAddressOptional().isPresent())
        return networkNode.getConfirmedConnections().stream()
                .map((Connection connection) -> {
                    Capabilities supportedCapabilities = new Capabilities(connection.getCapabilities());
                    // If we have a new connection the supportedCapabilities is empty.
                    // We lookup if we have already stored the supportedCapabilities at the persisted or reported peers
                    // and if so we use that.
                    Optional<NodeAddress> peersNodeAddressOptional = connection.getPeersNodeAddressOptional();
                    checkArgument(peersNodeAddressOptional.isPresent()); // getConfirmedConnections delivers only connections where we know the address
                    NodeAddress peersNodeAddress = peersNodeAddressOptional.get();
                    boolean capabilitiesNotFoundInConnection = supportedCapabilities.isEmpty();
                    if (capabilitiesNotFoundInConnection) {
                        // If not found in connection we look up if we got the Capabilities set from any of the
                        // reported or persisted peers
                        Set<Peer> persistedAndReported = new HashSet<>(getPersistedPeers());
                        persistedAndReported.addAll(getReportedPeers());
                        Optional<Peer> candidate = persistedAndReported.stream()
                                .filter(peer -> peer.getNodeAddress().equals(peersNodeAddress))
                                .filter(peer -> !peer.getCapabilities().isEmpty())
                                .findAny();
                        if (candidate.isPresent()) {
                            supportedCapabilities = new Capabilities(candidate.get().getCapabilities());
                        }
                    }
                    Peer peer = new Peer(peersNodeAddress, supportedCapabilities);

                    // If we did not found the capability from our own connection we add a listener,
                    // so once we get a connection with that peer and exchange a message containing the capabilities
                    // we get set the capabilities.
                    if (capabilitiesNotFoundInConnection) {
                        connection.addWeakCapabilitiesListener(peer);
                    }
                    return peer;
                })
                .collect(Collectors.toSet());
    }

    private void stopCheckMaxConnectionsTimer() {
        if (checkMaxConnectionsTimer != null) {
            checkMaxConnectionsTimer.stop();
            checkMaxConnectionsTimer = null;
        }
    }

    private void printStatistics() {
        String ls = System.lineSeparator();
        StringBuilder sb = new StringBuilder("Connection statistics: " + ls);
        AtomicInteger counter = new AtomicInteger();
        networkNode.getAllConnections().stream()
                .sorted(Comparator.comparingLong(o -> o.getConnectionStatistics().getConnectionCreationTimeStamp()))
                .forEach(e -> sb.append(ls).append("Connection ")
                        .append(counter.incrementAndGet()).append(ls)
                        .append(e.getConnectionStatistics().getInfo()).append(ls));
        log.info(sb.toString());
    }

    private void printConnectedPeers() {
        if (!networkNode.getConfirmedConnections().isEmpty()) {
            StringBuilder result = new StringBuilder("\n\n------------------------------------------------------------\n" +
                    "Connected peers for node " + networkNode.getNodeAddress() + ":");
            networkNode.getConfirmedConnections().forEach(e -> result.append("\n")
                    .append(e.getPeersNodeAddressOptional()).append(" ").append(e.getConnectionState().getPeerType()));
            result.append("\n------------------------------------------------------------\n");
            log.debug(result.toString());
        }
    }
}
