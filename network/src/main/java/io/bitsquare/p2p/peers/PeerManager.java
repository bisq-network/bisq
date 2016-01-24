package io.bitsquare.p2p.peers;

import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.ConnectionListener;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.storage.Storage;
import javafx.beans.value.ChangeListener;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;

public class PeerManager implements ConnectionListener {
    private static final Logger log = LoggerFactory.getLogger(PeerManager.class);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static int MAX_CONNECTIONS;
    private static int MIN_CONNECTIONS;
    private static int MAX_CONNECTIONS_EXTENDED_1;
    private static int MAX_CONNECTIONS_EXTENDED_2;

    public static void setMaxConnections(int maxConnections) {
        MAX_CONNECTIONS = maxConnections;
        MIN_CONNECTIONS = maxConnections - 2;
        MAX_CONNECTIONS_EXTENDED_1 = MAX_CONNECTIONS + 5;
        MAX_CONNECTIONS_EXTENDED_2 = MAX_CONNECTIONS_EXTENDED_1 + 5;
    }

    static {
        setMaxConnections(10);
    }

    private static final int MAX_REPORTED_PEERS = 1000;
    private static final int MAX_PERSISTED_PEERS = 500;


    private final NetworkNode networkNode;
    private final Set<NodeAddress> seedNodeAddresses;
    @Nullable
    private Storage<HashSet<ReportedPeer>> dbStorage;

    private final HashSet<ReportedPeer> persistedPeers = new HashSet<>();
    private final Set<ReportedPeer> reportedPeers = new HashSet<>();
    private Timer checkMaxConnectionsTimer;
    private final ChangeListener<Connection.State> stateChangeListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PeerManager(NetworkNode networkNode, Set<NodeAddress> seedNodeAddresses, File storageDir) {
        this.networkNode = networkNode;
        this.seedNodeAddresses = new HashSet<>(seedNodeAddresses);
        networkNode.addConnectionListener(this);
        createDbStorage(storageDir);

        stateChangeListener = (observable, oldValue, newValue) -> {
            printConnectedPeers();
            if (checkMaxConnectionsTimer == null && newValue == Connection.State.SUCCEEDED)
                checkMaxConnectionsTimer = UserThread.runAfter(() -> checkMaxConnections(MAX_CONNECTIONS), 3);
        };
    }

    protected void createDbStorage(File storageDir) {
        dbStorage = new Storage<>(storageDir);
        initPersistedPeers();
    }

    protected void initPersistedPeers() {
        if (dbStorage != null) {
            HashSet<ReportedPeer> persistedPeers = dbStorage.initAndGetPersisted("persistedPeers");
            if (persistedPeers != null) {
                log.info("We have persisted reported peers. " +
                        "\npersistedPeers=" + persistedPeers);
                this.persistedPeers.addAll(persistedPeers);
            }
        }
    }

    public void shutDown() {
        Log.traceCall();

        networkNode.removeConnectionListener(this);
        stopCheckMaxConnectionsTimer();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ConnectionListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnection(Connection connection) {
        connection.getStateProperty().addListener(stateChangeListener);
    }

    @Override
    public void onDisconnect(Reason reason, Connection connection) {
        connection.getStateProperty().removeListener(stateChangeListener);
        connection.getPeersNodeAddressOptional().ifPresent(this::removePeer);
    }

    @Override
    public void onError(Throwable throwable) {
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Check seed node connections
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected boolean checkMaxConnections(int limit) {
        Log.traceCall();
        stopCheckMaxConnectionsTimer();
        cleanupFailedConnections();
        removeSuperfluousSeedNodes();
        Set<Connection> allConnections = networkNode.getAllConnections();
        int size = allConnections.size();
        if (size > limit) {
            log.info("We have {} connections open. Our limit is {}", size, limit);
            log.info("Lets try to remove the inbound connections of type PEER.");

            // Only outbound, SUCCEEDED, and PEER type connections
            List<Connection> candidates = allConnections.stream()
                    .filter(e -> e.getState() == Connection.State.SUCCEEDED)
                    .filter(e -> e.getDirection() == Connection.Direction.INBOUND)
                    .filter(e -> e.getPeerType() == Connection.PeerType.PEER)
                    .collect(Collectors.toList());

            if (candidates.size() == 0) {
                log.info("No candidates found. We go to the next level and check if we exceed our " +
                        "MAX_CONNECTIONS_NORMAL_PRIORITY limit of {}", MAX_CONNECTIONS_EXTENDED_1);
                if (size > MAX_CONNECTIONS_EXTENDED_1) {
                    log.info("Lets try to remove any connection of type PEER.");
                    // All expect SUCCEEDED and DIRECT_MSG_PEER type connections
                    candidates = allConnections.stream()
                            .filter(e -> e.getState() == Connection.State.SUCCEEDED)
                            .filter(e -> e.getPeerType() != Connection.PeerType.DIRECT_MSG_PEER)
                            .collect(Collectors.toList());
                    if (candidates.size() == 0) {
                        log.info("No candidates found. We go to the next level and check if we exceed our " +
                                "MAX_CONNECTIONS_HIGH_PRIORITY limit of {}", MAX_CONNECTIONS_EXTENDED_2);
                        if (size > MAX_CONNECTIONS_EXTENDED_2) {
                            log.info("Lets try to remove any connection which is not of type DIRECT_MSG_PEER.");
                            // All expect DIRECT_MSG_PEER type connections
                            candidates = allConnections.stream()
                                    .filter(e -> e.getPeerType() != Connection.PeerType.DIRECT_MSG_PEER)
                                    .collect(Collectors.toList());
                        }
                    }
                }
            }

            if (candidates.size() > 0) {
                candidates.sort((o1, o2) -> o1.getLastActivityDate().compareTo(o2.getLastActivityDate()));
                log.info("Candidates for shut down=" + candidates);
                Connection connection = candidates.remove(0);
                log.info("We are going to shut down the oldest connection with last activity date="
                        + connection.getLastActivityDate() + " / connection=" + connection);
                connection.shutDown(() -> checkMaxConnections(limit));
                return true;
            } else {
                log.debug("No candidates found to remove. allConnections=", allConnections);
                return false;
            }
        } else {
            log.trace("We only have {} connections open and don't need to close any.", size);
            return false;
        }
    }

    protected void cleanupFailedConnections() {
        // We close any failed but still open connection (check if that can happen at all)
        Stream<Connection> failedConnections = networkNode.getAllConnections().stream()
                .filter(e -> e.getState() == Connection.State.FAILED);
        failedConnections.findAny().ifPresent(e -> {
            log.warn("There is a connection with a failed state. That should not happen. We close it.");
            e.shutDown(this::cleanupFailedConnections);
        });
    }

    protected void removeSuperfluousSeedNodes() {
        Set<Connection> allConnections = networkNode.getAllConnections();
        if (allConnections.size() > MAX_CONNECTIONS_EXTENDED_1) {
            List<Connection> candidates = allConnections.stream()
                    .filter(this::isSeedNode)
                    .collect(Collectors.toList());

            if (candidates.size() > 1) {
                candidates.sort((o1, o2) -> o1.getLastActivityDate().compareTo(o2.getLastActivityDate()));
                log.info("Number of connections exceeding MAX_CONNECTIONS. Current size=" + candidates.size());
                Connection connection = candidates.remove(0);
                log.info("We are going to shut down the oldest connection with last activity date="
                        + connection.getLastActivityDate() + " / connection=" + connection);
                connection.shutDown(this::removeSuperfluousSeedNodes);
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Reported peers
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void removeReportedPeer(NodeAddress nodeAddress) {
        removeReportedPeer(new ReportedPeer(nodeAddress));
    }

    public void removeReportedPeer(ReportedPeer reportedPeer) {
        reportedPeers.remove(reportedPeer);
        printReportedPeers();
    }

    public Set<ReportedPeer> getReportedPeers() {
        return reportedPeers;
    }

    public Set<NodeAddress> getNodeAddressesOfReportedPeers() {
        return reportedPeers.stream().map(e -> e.nodeAddress).collect(Collectors.toSet());
    }

    public void addToReportedPeers(HashSet<ReportedPeer> reportedPeersToAdd, Connection connection) {
        Log.traceCall("reportedPeersToAdd = " + reportedPeersToAdd);
        // we disconnect misbehaving nodes trying to send too many peers
        // reported peers include the authenticated peers which is normally max. 8 but we give some headroom 
        // for safety
        if (reportedPeersToAdd.size() > (MAX_REPORTED_PEERS + PeerManager.MIN_CONNECTIONS * 3)) {
            connection.shutDown();
        } else {
            // In case we have one of the peers already we adjust the lastActivityDate by adjusting the date to the mid 
            // of the lastActivityDate of our already stored peer and the reported one
            Map<ReportedPeer, ReportedPeer> reportedPeersMap = reportedPeers.stream()
                    .collect(Collectors.toMap(e -> e, Function.identity()));
            HashSet<ReportedPeer> adjustedReportedPeers = new HashSet<>();
            reportedPeersToAdd.stream()
                    .filter(e -> !e.nodeAddress.equals(networkNode.getNodeAddress()))
                    .filter(e -> !getConnectedPeers().contains(e))
                    .forEach(e -> {
                        if (reportedPeersMap.containsKey(e)) {
                            if (e.lastActivityDate != null && reportedPeersMap.get(e).lastActivityDate != null) {
                                long adjustedTime = (e.lastActivityDate.getTime() +
                                        reportedPeersMap.get(e).lastActivityDate.getTime()) / 2;
                                adjustedReportedPeers.add(new ReportedPeer(e.nodeAddress,
                                        new Date(adjustedTime)));
                            } else if (e.lastActivityDate == null) {
                                adjustedReportedPeers.add(reportedPeersMap.get(e));
                            } else if (reportedPeersMap.get(e).lastActivityDate == null) {
                                adjustedReportedPeers.add(e);
                            }
                        } else {
                            adjustedReportedPeers.add(e);
                        }
                    });

            reportedPeers.addAll(adjustedReportedPeers);

            purgeReportedPeersIfExceeds();

            // We add all adjustedReportedPeers to persistedReportedPeers but only save the 500 peers with the most
            // recent lastActivityDate. 
            // ReportedPeers is changing when peers authenticate (remove) so we don't use that but 
            // the persistedReportedPeers set.
            persistedPeers.addAll(reportedPeersToAdd);

            // We add also our authenticated and authenticating peers
            persistedPeers.addAll(new HashSet<>(getConnectedPeers()));

            // We remove if we exceeds MAX_PERSISTED_PEERS limit
            int toRemove = persistedPeers.size() - MAX_PERSISTED_PEERS;
            if (toRemove > 0) {
                int toRemove1 = toRemove / 2;
                if (toRemove1 > 0) {
                    // we remove the first half randomly to avoid attack vectors with lastActivityDate
                    List<ReportedPeer> list = new ArrayList<>(persistedPeers);
                    for (int i = 0; i < toRemove1; i++) {
                        persistedPeers.remove(list.get(i));
                    }
                    int toRemove2 = toRemove - toRemove1;
                    if (toRemove2 > 0) {
                        // now we remove second half with a list sorted by oldest lastActivityDate
                        list = new ArrayList<>(persistedPeers);
                        list.sort((o1, o2) -> o1.lastActivityDate.compareTo(o2.lastActivityDate));
                        for (int i = 0; i < toRemove2; i++) {
                            persistedPeers.remove(list.get(i));
                        }
                    }
                }
            }

            if (dbStorage != null)
                dbStorage.queueUpForSave(persistedPeers);
        }

        printReportedPeers();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    //  Persisted peers
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void removeFromPersistedPeers(ReportedPeer reportedPeer) {
        if (persistedPeers.contains(reportedPeer)) {
            persistedPeers.remove(reportedPeer);

            if (dbStorage != null)
                dbStorage.queueUpForSave(persistedPeers, 5000);
        }
    }

    public void removeFromPersistedPeers(NodeAddress peerNodeAddress) {
        removeFromPersistedPeers(new ReportedPeer(peerNodeAddress));
    }

    public HashSet<ReportedPeer> getPersistedPeers() {
        return persistedPeers;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    //  Misc
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean hasSufficientConnections() {
        return networkNode.getNodeAddressesOfSucceededConnections().size() >= MIN_CONNECTIONS;
    }

    public void removePeer(NodeAddress nodeAddress) {
        removeReportedPeer(nodeAddress);
        removeFromPersistedPeers(nodeAddress);
    }

    public Set<ReportedPeer> getConnectedAndReportedPeers() {
        Set<ReportedPeer> result = new HashSet<>(reportedPeers);
        result.addAll(getConnectedPeers());
        return result;
    }

    public Set<ReportedPeer> getConnectedPeers() {
        // networkNode.getSucceededConnections includes:
        // filter(connection -> connection.getPeersNodeAddressOptional().isPresent())
        return networkNode.getSucceededConnections().stream()
                .map(c -> new ReportedPeer(c.getPeersNodeAddressOptional().get(), c.getLastActivityDate()))
                .collect(Collectors.toSet());
    }

    public boolean isSeedNode(ReportedPeer reportedPeer) {
        return seedNodeAddresses.contains(reportedPeer.nodeAddress);
    }

    public boolean isSeedNode(NodeAddress nodeAddress) {
        return seedNodeAddresses.contains(nodeAddress);
    }

    public boolean isSeedNode(Connection connection) {
        return connection.getPeersNodeAddressOptional().isPresent() && seedNodeAddresses.contains(connection.getPeersNodeAddressOptional().get());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    //  Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void purgeReportedPeersIfExceeds() {
        Log.traceCall();
        int size = getReportedPeers().size();
        if (size > MAX_REPORTED_PEERS) {
            log.trace("We have more then {} reported peers. size={}. " +
                    "We remove random peers from the reported peers list.", MAX_REPORTED_PEERS, size);
            int diff = size - MAX_REPORTED_PEERS;
            List<ReportedPeer> list = new ArrayList<>(getReportedPeers());
            // we dont use sorting by lastActivityDate to avoid attack vectors and keep it more random
            for (int i = 0; i < diff; i++) {
                ReportedPeer toRemove = getAndRemoveRandomReportedPeer(list);
                removeReportedPeer(toRemove);
                removeFromPersistedPeers(toRemove);
            }
        } else {
            log.trace("No need to purge reported peers. We don't have more then {} reported peers yet.", MAX_REPORTED_PEERS);
        }
    }

    private ReportedPeer getAndRemoveRandomReportedPeer(List<ReportedPeer> list) {
        checkArgument(!list.isEmpty(), "List must not be empty");
        return list.remove(new Random().nextInt(list.size()));
    }


    private void stopCheckMaxConnectionsTimer() {
        if (checkMaxConnectionsTimer != null) {
            checkMaxConnectionsTimer.cancel();
            checkMaxConnectionsTimer = null;
        }
    }

    private void printConnectedPeers() {
        if (!networkNode.getNodeAddressesOfSucceededConnections().isEmpty()) {
            StringBuilder result = new StringBuilder("\n\n------------------------------------------------------------\n" +
                    "Connected peers for node " + networkNode.getNodeAddress() + ":");
            networkNode.getNodeAddressesOfSucceededConnections().stream().forEach(e -> result.append("\n").append(e));
            result.append("\n------------------------------------------------------------\n");
            log.info(result.toString());
        }
    }

    private void printReportedPeers() {
        if (!reportedPeers.isEmpty()) {
            StringBuilder result = new StringBuilder("\n\n------------------------------------------------------------\n" +
                    "Reported peers for node " + networkNode.getNodeAddress() + ":");
            reportedPeers.stream().forEach(e -> result.append("\n").append(e));
            result.append("\n------------------------------------------------------------\n");
            log.info(result.toString());
        }
    }


}
