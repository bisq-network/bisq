package io.bitsquare.p2p.peers;

import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.*;
import io.bitsquare.p2p.peers.messages.data.GetUpdatedDataRequest;
import io.bitsquare.storage.Storage;
import javafx.beans.value.ChangeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

public class PeerManager implements ConnectionListener, MessageListener {
    private static final Logger log = LoggerFactory.getLogger(PeerManager.class);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static int MAX_CONNECTIONS;
    private static int MIN_CONNECTIONS;
    private static int MAX_CONNECTIONS_EXTENDED_1;
    private static int MAX_CONNECTIONS_EXTENDED_2;
    private static int MAX_CONNECTIONS_EXTENDED_3;

    public static void setMaxConnections(int maxConnections) {
        MAX_CONNECTIONS = maxConnections;
        MIN_CONNECTIONS = maxConnections - 4;
        MAX_CONNECTIONS_EXTENDED_1 = MAX_CONNECTIONS + 5;
        MAX_CONNECTIONS_EXTENDED_2 = MAX_CONNECTIONS + 10;
        MAX_CONNECTIONS_EXTENDED_3 = MAX_CONNECTIONS + 20;
    }

    static {
        setMaxConnections(12);
    }

    private static final int MAX_REPORTED_PEERS = 1000;
    private static final int MAX_PERSISTED_PEERS = 500;
    private static final long MAX_AGE = TimeUnit.DAYS.toMillis(14); // max age for reported peers is 14 days


    private final NetworkNode networkNode;
    private final Set<NodeAddress> seedNodeAddresses;
    private final Storage<HashSet<ReportedPeer>> dbStorage;

    private final HashSet<ReportedPeer> persistedPeers = new HashSet<>();
    private final Set<ReportedPeer> reportedPeers = new HashSet<>();
    private Timer checkMaxConnectionsTimer;
    private final ChangeListener<NodeAddress> connectionNodeAddressListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PeerManager(NetworkNode networkNode, Set<NodeAddress> seedNodeAddresses, File storageDir) {
        this.networkNode = networkNode;
        this.seedNodeAddresses = new HashSet<>(seedNodeAddresses);
        networkNode.addConnectionListener(this);
        dbStorage = new Storage<>(storageDir);
        HashSet<ReportedPeer> persistedPeers = dbStorage.initAndGetPersisted("PersistedPeers");
        if (persistedPeers != null) {
            log.info("We have persisted reported peers. persistedPeers.size()=" + persistedPeers.size());
            this.persistedPeers.addAll(persistedPeers);
        }

        connectionNodeAddressListener = (observable, oldValue, newValue) -> {
            // Every time we get a new peer connected with a known address we check if we need to remove peers
            printConnectedPeers();
            if (checkMaxConnectionsTimer == null && newValue != null)
                checkMaxConnectionsTimer = UserThread.runAfter(() -> {
                    removeTooOldReportedPeers();
                    removeTooOldPersistedPeers();
                    checkMaxConnections(MAX_CONNECTIONS);
                }, 3);
        };
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
        connection.getNodeAddressProperty().addListener(connectionNodeAddressListener);
        Optional<NodeAddress> peersNodeAddressOptional = connection.getPeersNodeAddressOptional();
        // OutboundConnection always know their peers address
        // A seed node get only InboundConnection from other seed nodes with getData or peer exchange, 
        // never direct messages, so we need to check for PeerType.SEED_NODE at onMessage
        if (connection instanceof OutboundConnection &&
                peersNodeAddressOptional.isPresent() &&
                seedNodeAddresses.contains(peersNodeAddressOptional.get())) {
            connection.setPeerType(Connection.PeerType.SEED_NODE);
        }
    }

    @Override
    public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
        connection.getNodeAddressProperty().removeListener(connectionNodeAddressListener);
        handleConnectionFault(connection);
    }

    @Override
    public void onError(Throwable throwable) {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        // In case a seed node connects to another seed node we get his address at the DataRequest triggered from
        // RequestDataManager.updateDataFromConnectedSeedNode 
        if (message instanceof GetUpdatedDataRequest) {
            Log.traceCall(message.toString() + "\n\tconnection=" + connection);
            Optional<NodeAddress> peersNodeAddressOptional = connection.getPeersNodeAddressOptional();
            if (peersNodeAddressOptional.isPresent() &&
                    seedNodeAddresses.contains(peersNodeAddressOptional.get()))
                connection.setPeerType(Connection.PeerType.SEED_NODE);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Check seed node connections
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean checkMaxConnections(int limit) {
        Log.traceCall("limit=" + limit);
        stopCheckMaxConnectionsTimer();
        removeSuperfluousSeedNodes();
        Set<Connection> allConnections = networkNode.getAllConnections();
        int size = allConnections.size();
        log.info("We have {} connections open. Our limit is {}", size, limit);
        if (size > limit) {
            // Only InboundConnection, and PEER type connections
            log.info("We have too many connections open. We try to close some.\n\t" +
                    "Lets try first to remove the inbound connections of type PEER.");
            List<Connection> candidates = allConnections.stream()
                    .filter(e -> e instanceof InboundConnection)
                    .filter(e -> e.getPeerType() == Connection.PeerType.PEER)
                    .collect(Collectors.toList());

            if (candidates.size() == 0) {
                log.info("No candidates found. We go to the next level and check if we exceed our " +
                        "MAX_CONNECTIONS_EXTENDED_1 limit of {}", MAX_CONNECTIONS_EXTENDED_1);
                if (size > MAX_CONNECTIONS_EXTENDED_1) {
                    log.info("Lets try to remove any connection of type PEER.");
                    // Only PEER type connections
                    candidates = allConnections.stream()
                            .filter(e -> e.getPeerType() == Connection.PeerType.PEER)
                            .collect(Collectors.toList());

                    if (candidates.size() == 0) {
                        log.info("No candidates found. We go to the next level and check if we exceed our " +
                                "MAX_CONNECTIONS_EXTENDED_2 limit of {}", MAX_CONNECTIONS_EXTENDED_2);
                        if (size > MAX_CONNECTIONS_EXTENDED_2) {
                            log.info("Lets try to remove any connection which is not of type DIRECT_MSG_PEER.");
                            // All connections except DIRECT_MSG_PEER
                            candidates = allConnections.stream()
                                    .filter(e -> e.getPeerType() != Connection.PeerType.DIRECT_MSG_PEER)
                                    .collect(Collectors.toList());

                            if (candidates.size() == 0) {
                                log.info("No candidates found. We go to the next level and check if we exceed our " +
                                        "MAX_CONNECTIONS_EXTENDED_3 limit of {}", MAX_CONNECTIONS_EXTENDED_3);
                                if (size > MAX_CONNECTIONS_EXTENDED_3) {
                                    log.info("Lets try to remove any connection.");
                                    // All connections
                                    candidates = allConnections.stream()
                                            .collect(Collectors.toList());
                                }
                            }
                        }
                    }
                }
            }

            if (candidates.size() > 0) {
                candidates.sort((o1, o2) -> o1.getLastActivityDate().compareTo(o2.getLastActivityDate()));
                log.info("Candidates.size() for shut down=" + candidates.size());
                Connection connection = candidates.remove(0);
                log.info("We are going to shut down the oldest connection.\n\tconnection=" + connection.toString());
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

    private void removeSuperfluousSeedNodes() {
        Log.traceCall();
        Set<Connection> allConnections = networkNode.getAllConnections();
        if (allConnections.size() > MAX_CONNECTIONS_EXTENDED_1) {
            List<Connection> candidates = allConnections.stream()
                    .filter(this::isSeedNode)
                    .collect(Collectors.toList());

            if (candidates.size() > 1) {
                candidates.sort((o1, o2) -> o1.getLastActivityDate().compareTo(o2.getLastActivityDate()));
                log.info("Number of connections exceeding MAX_CONNECTIONS_EXTENDED_1. Current size=" + candidates.size());
                Connection connection = candidates.remove(0);
                log.info("We are going to shut down the oldest connection.\n\tconnection=" + connection.toString());
                connection.shutDown(CloseConnectionReason.TOO_MANY_SEED_NODES_CONNECTED, this::removeSuperfluousSeedNodes);
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Reported peers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean removeReportedPeer(ReportedPeer reportedPeer) {
        boolean contained = reportedPeers.remove(reportedPeer);
        printReportedPeers();
        return contained;
    }

    private ReportedPeer removeReportedPeer(NodeAddress nodeAddress) {
        Optional<ReportedPeer> reportedPeerOptional = reportedPeers.stream()
                .filter(e -> e.nodeAddress.equals(nodeAddress)).findAny();
        if (reportedPeerOptional.isPresent()) {
            ReportedPeer reportedPeer = reportedPeerOptional.get();
            reportedPeers.remove(reportedPeer);
            return reportedPeer;
        } else {
            return null;
        }
    }

    private void removeTooOldReportedPeers() {
        Log.traceCall();
        Set<ReportedPeer> reportedPeersToRemove = reportedPeers.stream()
                .filter(reportedPeer -> reportedPeer.lastActivityDate != null &&
                        new Date().getTime() - reportedPeer.lastActivityDate.getTime() > MAX_AGE)
                .collect(Collectors.toSet());
        reportedPeersToRemove.forEach(this::removeReportedPeer);
    }

    public Set<ReportedPeer> getReportedPeers() {
        return reportedPeers;
    }

    public void addToReportedPeers(HashSet<ReportedPeer> reportedPeersToAdd, Connection connection) {
        // we disconnect misbehaving nodes trying to send too many peers
        // reported peers include the connected peers which is normally max. 10 but we give some headroom 
        // for safety
        if (reportedPeersToAdd.size() > (MAX_REPORTED_PEERS + PeerManager.MIN_CONNECTIONS * 3)) {
            // Will trigger a shutdown after 2nd time sending too much
            connection.reportIllegalRequest(RuleViolation.TOO_MANY_REPORTED_PEERS_SENT);
        } else {
            // In case we have one of the peers already we adjust the lastActivityDate by adjusting the date to the mid 
            // of the lastActivityDate of our already stored peer and the reported one
            Map<ReportedPeer, ReportedPeer> reportedPeersMap = reportedPeers.stream()
                    .collect(Collectors.toMap(e -> e, Function.identity()));
            HashSet<ReportedPeer> adjustedReportedPeers = new HashSet<>();
            reportedPeersToAdd.stream()
                    .filter(e -> e.nodeAddress != null &&
                            !e.nodeAddress.equals(networkNode.getNodeAddress()) &&
                            !getConnectedPeers().contains(e))
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

            persistedPeers.addAll(reportedPeersToAdd);
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
                        list = list.stream().filter(e -> e.lastActivityDate != null).collect(Collectors.toList());
                        list.sort((o1, o2) -> o1.lastActivityDate.compareTo(o2.lastActivityDate));
                        for (int i = 0; i < toRemove2; i++) {
                            persistedPeers.remove(list.get(i));
                        }
                    }
                }
            }

            if (dbStorage != null)
                dbStorage.queueUpForSave(persistedPeers, 2000);
        }

        printReportedPeers();
    }

    private void printReportedPeers() {
        if (!reportedPeers.isEmpty()) {
            StringBuilder result = new StringBuilder("\n\n------------------------------------------------------------\n" +
                    "Reported peers:");
            reportedPeers.stream().forEach(e -> result.append("\n").append(e));
            result.append("\n------------------------------------------------------------\n");
            //log.trace(result.toString());
            log.info("Number of reported peers: {}", reportedPeers.size());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    //  Persisted peers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean removePersistedPeer(ReportedPeer reportedPeer) {
        if (persistedPeers.contains(reportedPeer)) {
            persistedPeers.remove(reportedPeer);

            if (dbStorage != null)
                dbStorage.queueUpForSave(persistedPeers, 5000);

            return true;
        } else {
            return false;
        }
    }

    private boolean removePersistedPeer(NodeAddress nodeAddress) {
        Optional<ReportedPeer> persistedPeerOptional = persistedPeers.stream()
                .filter(e -> e.nodeAddress.equals(nodeAddress)).findAny();
        persistedPeerOptional.ifPresent(persistedPeer -> {
            persistedPeers.remove(persistedPeer);
            if (dbStorage != null)
                dbStorage.queueUpForSave(persistedPeers, 5000);
        });
        return persistedPeerOptional.isPresent();
    }

    private void removeTooOldPersistedPeers() {
        Log.traceCall();
        Set<ReportedPeer> persistedPeersToRemove = persistedPeers.stream()
                .filter(reportedPeer -> reportedPeer.lastActivityDate != null &&
                        new Date().getTime() - reportedPeer.lastActivityDate.getTime() > MAX_AGE)
                .collect(Collectors.toSet());
        persistedPeersToRemove.forEach(this::removePersistedPeer);
    }


    public Set<ReportedPeer> getPersistedPeers() {
        return persistedPeers;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    //  Misc
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean hasSufficientConnections() {
        return networkNode.getNodeAddressesOfConfirmedConnections().size() >= MIN_CONNECTIONS;
    }

    public void handleConnectionFault(Connection connection) {
        connection.getPeersNodeAddressOptional().ifPresent(nodeAddress -> handleConnectionFault(nodeAddress, connection));
    }

    public void handleConnectionFault(NodeAddress nodeAddress, @Nullable Connection connection) {
        Log.traceCall("nodeAddress=" + nodeAddress);
        ReportedPeer reportedPeer = removeReportedPeer(nodeAddress);
        if (connection != null && connection.getRuleViolation() != null) {
            removePersistedPeer(nodeAddress);
        } else {
            if (reportedPeer != null) {
                removePersistedPeer(nodeAddress);
                reportedPeer.penalizeLastActivityDate();
                persistedPeers.add(reportedPeer);
                dbStorage.queueUpForSave(persistedPeers, 5000);

                removeTooOldPersistedPeers();
            }
        }
    }

   /* public Set<ReportedPeer> getConnectedAndReportedPeers() {
        Set<ReportedPeer> result = new HashSet<>(reportedPeers);
        result.addAll(getConnectedPeers());
        return result;
    }*/

    public boolean isSeedNode(ReportedPeer reportedPeer) {
        return seedNodeAddresses.contains(reportedPeer.nodeAddress);
    }

    public boolean isSeedNode(NodeAddress nodeAddress) {
        return seedNodeAddresses.contains(nodeAddress);
    }

    public boolean isSeedNode(Connection connection) {
        return connection.hasPeersNodeAddress() && seedNodeAddresses.contains(connection.getPeersNodeAddressOptional().get());
    }

    public boolean isSelf(ReportedPeer reportedPeer) {
        return isSelf(reportedPeer.nodeAddress);
    }

    public boolean isSelf(NodeAddress nodeAddress) {
        return nodeAddress.equals(networkNode.getNodeAddress());
    }

    public boolean isConfirmed(ReportedPeer reportedPeer) {
        return isConfirmed(reportedPeer.nodeAddress);
    }

    public boolean isConfirmed(NodeAddress nodeAddress) {
        return networkNode.getNodeAddressesOfConfirmedConnections().contains(nodeAddress);
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
                removePersistedPeer(toRemove);
            }
        } else {
            log.trace("No need to purge reported peers.\n\tWe don't have more then {} reported peers yet.", MAX_REPORTED_PEERS);
        }
    }

    private ReportedPeer getAndRemoveRandomReportedPeer(List<ReportedPeer> list) {
        checkArgument(!list.isEmpty(), "List must not be empty");
        return list.remove(new Random().nextInt(list.size()));
    }

    public Set<ReportedPeer> getConnectedPeers() {
        // networkNode.getConfirmedConnections includes:
        // filter(connection -> connection.getPeersNodeAddressOptional().isPresent())
        return networkNode.getConfirmedConnections().stream()
                .map(c -> new ReportedPeer(c.getPeersNodeAddressOptional().get(), c.getLastActivityDate()))
                .collect(Collectors.toSet());
    }

    private void stopCheckMaxConnectionsTimer() {
        if (checkMaxConnectionsTimer != null) {
            checkMaxConnectionsTimer.cancel();
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
            log.info(result.toString());
        }
    }
}
