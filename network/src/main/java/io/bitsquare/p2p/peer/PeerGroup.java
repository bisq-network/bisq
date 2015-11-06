package io.bitsquare.p2p.peer;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.ConnectionListener;
import io.bitsquare.p2p.network.MessageListener;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.p2p.peer.messages.MaintenanceMessage;
import io.bitsquare.p2p.peer.messages.PingMessage;
import io.bitsquare.p2p.peer.messages.PongMessage;
import io.bitsquare.p2p.peer.messages.RequestAuthenticationMessage;
import io.bitsquare.p2p.storage.messages.BroadcastMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;


public class PeerGroup {
    private static final Logger log = LoggerFactory.getLogger(PeerGroup.class);

    static int simulateAuthTorNode = 0;

    public static void setSimulateAuthTorNode(int simulateAuthTorNode) {
        PeerGroup.simulateAuthTorNode = simulateAuthTorNode;
    }

    private static int MAX_CONNECTIONS = 8;
    private static int MAINTENANCE_INTERVAL = new Random().nextInt(2 * 60 * 1000) + 2 * 60 * 1000; // 2-4 min.
    private static int PING_AFTER_CONNECTION_INACTIVITY = 30 * 1000;

    public static void setMaxConnections(int maxConnections) {
        MAX_CONNECTIONS = maxConnections;
    }

    private final NetworkNode networkNode;


    private final Set<Address> seedNodeAddresses;
    private final CopyOnWriteArraySet<PeerListener> peerListeners = new CopyOnWriteArraySet<>();
    private final ConcurrentHashMap<Address, Peer> authenticatedPeers = new ConcurrentHashMap<>();
    private final CopyOnWriteArraySet<Address> reportedPeerAddresses = new CopyOnWriteArraySet<>();
    ;

    private final Timer maintenanceTimer = new Timer();
    private volatile boolean shutDownInProgress;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PeerGroup(final NetworkNode networkNode, Set<Address> seeds) {
        this.networkNode = networkNode;
        this.seedNodeAddresses = seeds;

        init(networkNode);
    }

    private void init(NetworkNode networkNode) {
        networkNode.addMessageListener((message, connection) -> {
            if (message instanceof MaintenanceMessage)
                processMaintenanceMessage((MaintenanceMessage) message, connection);
            else if (message instanceof RequestAuthenticationMessage) {
                AuthenticationHandshake authenticationHandshake = new AuthenticationHandshake(networkNode, PeerGroup.this, getMyAddress());
                SettableFuture<Connection> future = authenticationHandshake.processAuthenticationRequest((RequestAuthenticationMessage) message, connection);
                Futures.addCallback(future, new FutureCallback<Connection>() {
                    @Override
                    public void onSuccess(@Nullable Connection connection) {
                        if (connection != null) {
                            UserThread.execute(() -> {
                                setAuthenticated(connection, connection.getPeerAddress());
                                purgeReportedPeers();
                            });
                        }
                    }

                    @Override
                    public void onFailure(@NotNull Throwable throwable) {
                        throwable.printStackTrace();
                        log.error("AuthenticationHandshake failed. " + throwable.getMessage());
                        UserThread.execute(() -> removePeer(connection.getPeerAddress()));
                    }
                });
            }
        });


        networkNode.addConnectionListener(new ConnectionListener() {
            @Override
            public void onConnection(Connection connection) {
            }

            @Override
            public void onPeerAddressAuthenticated(Address peerAddress, Connection connection) {
            }

            @Override
            public void onDisconnect(Reason reason, Connection connection) {
                log.debug("onDisconnect connection=" + connection + " / reason=" + reason);
                log.debug("##### onDisconnect connection.isAuthenticated()=" + connection.isAuthenticated());
                // only removes authenticated nodes
                if (connection.isAuthenticated())
                    removePeer(connection.getPeerAddress());
            }

            @Override
            public void onError(Throwable throwable) {
            }
        });

        maintenanceTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Thread.currentThread().setName("MaintenanceTimer-" + new Random().nextInt(1000));
                try {
                    UserThread.execute(() -> {
                        disconnectOldConnections();
                        pingPeers();
                    });
                } catch (Throwable t) {
                    t.printStackTrace();
                    log.error("Executing task failed. " + t.getMessage());
                }
            }
        }, MAINTENANCE_INTERVAL, MAINTENANCE_INTERVAL);
    }

    private void disconnectOldConnections() {
        List<Connection> authenticatedConnections = networkNode.getAllConnections().stream()
                .filter(e -> e.isAuthenticated())
                .collect(Collectors.toList());
        if (authenticatedConnections.size() > MAX_CONNECTIONS) {
            authenticatedConnections.sort((o1, o2) -> o1.getLastActivityDate().compareTo(o2.getLastActivityDate()));
            log.info("Number of connections exceeds MAX_CONNECTIONS. Current size=" + authenticatedConnections.size());
            Connection connection = authenticatedConnections.remove(0);
            log.info("Shutdown oldest connection with last activity date=" + connection.getLastActivityDate() + " / connection=" + connection);
            connection.shutDown(() -> UserThread.runAfterRandomDelay(() -> disconnectOldConnections(), 100, 500, TimeUnit.MILLISECONDS));
        }
    }

    private void pingPeers() {
        log.trace("pingPeers");
        Set<Peer> connectedPeersList = new HashSet<>(authenticatedPeers.values());
        connectedPeersList.stream()
                .filter(e -> (new Date().getTime() - e.connection.getLastActivityDate().getTime()) > PING_AFTER_CONNECTION_INACTIVITY)
                .forEach(e -> UserThread.runAfterRandomDelay(() -> {
                    SettableFuture<Connection> future = networkNode.sendMessage(e.connection, new PingMessage(e.getPingNonce()));
                    Futures.addCallback(future, new FutureCallback<Connection>() {
                        @Override
                        public void onSuccess(Connection connection) {
                            log.trace("PingMessage sent successfully");
                        }

                        @Override
                        public void onFailure(@NotNull Throwable throwable) {
                            log.info("PingMessage sending failed " + throwable.getMessage());
                            removePeer(e.address);
                        }
                    });
                }, 5, 10));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void removeMySeedNodeAddressFromList(Address mySeedNodeAddress) {
        seedNodeAddresses.remove(mySeedNodeAddress);
    }

    public void shutDown() {
        if (!shutDownInProgress) {
            shutDownInProgress = true;
            if (maintenanceTimer != null)
                maintenanceTimer.cancel();
        }
    }

    public void broadcast(BroadcastMessage message, @Nullable Address sender) {
        log.trace("Broadcast message to " + authenticatedPeers.values().size() + " peers.");
        log.trace("message = " + message);
        printConnectedPeersMap();

        // TODO add randomized timing?
        authenticatedPeers.values().stream()
                .filter(e -> !e.address.equals(sender))
                .forEach(peer -> {
                    log.trace("Broadcast message from " + getMyAddress() + " to " + peer.address + ".");
                    SettableFuture<Connection> future = networkNode.sendMessage(peer.address, message);
                    Futures.addCallback(future, new FutureCallback<Connection>() {
                        @Override
                        public void onSuccess(Connection connection) {
                            log.trace("Broadcast from " + getMyAddress() + " to " + peer.address + " succeeded.");
                        }

                        @Override
                        public void onFailure(@NotNull Throwable throwable) {
                            log.info("Broadcast failed. " + throwable.getMessage());
                            removePeer(peer.address);
                        }
                    });
                });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Authentication to seed node
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void authenticateSeedNode(Address peerAddress) {
        authenticateToSeedNode(new HashSet<>(seedNodeAddresses), peerAddress, true);
    }

    public void authenticateToSeedNode(Set<Address> remainingAddresses, Address peerAddress, boolean continueOnSuccess) {
        checkArgument(!authenticatedPeers.containsKey(peerAddress),
                "We have that peer already authenticated. That must never happen.");

        AuthenticationHandshake authenticationHandshake = new AuthenticationHandshake(networkNode, this, getMyAddress());
        SettableFuture<Connection> future = authenticationHandshake.requestAuthentication(remainingAddresses, peerAddress);
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(@Nullable Connection connection) {
                if (connection != null) {
                    setAuthenticated(connection, peerAddress);
                    if (continueOnSuccess) {
                        if (getAuthenticatedPeers().size() <= MAX_CONNECTIONS) {
                            log.info("We still don't have enough connections. Lets try the reported peers.");
                            authenticateToAnyReportedPeer();
                        } else {
                            log.info("We have already enough connections.");
                        }
                    } else {
                        log.info("We have already tried all reported peers and seed nodes. " +
                                "We stop bootstrapping now, but will repeat after an while.");
                        UserThread.runAfter(() -> authenticateToAnyReportedPeer(), 60);
                    }
                }
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                throwable.printStackTrace();
                log.error("AuthenticationHandshake failed. " + throwable.getMessage());
                removePeer(peerAddress);

                // If we fail we try again with the remaining set
                remainingAddresses.remove(peerAddress);
                List<Address> list = new ArrayList<>(remainingAddresses);
                removeAuthenticatedPeersFromList(list);
                if (!list.isEmpty()) {
                    Address item = getAndRemoveRandomItem(list);
                    log.info("We try to build an authenticated connection to a seed node. " + item);
                    authenticateToSeedNode(remainingAddresses, item, true);
                } else {
                    log.info("We don't have any more seed nodes for connecting. Lets try the reported peers.");
                    authenticateToAnyReportedPeer();
                }
            }
        });
    }


    private void authenticateToAnyReportedPeer() {
        // after we have at least one seed node we try to get reported peers connected
        List<Address> list = new ArrayList<>(reportedPeerAddresses);
        removeAuthenticatedPeersFromList(list);
        if (!list.isEmpty()) {
            Address item = getAndRemoveRandomItem(list);
            log.info("We try to build an authenticated connection to a random peer. " + item + " / list=" + list);
            authenticateToReportedPeer(new HashSet<>(list), item);
        } else {
            log.info("We don't have any reported peers for connecting. Lets try the remaining seed nodes.");
            authenticateToRemainingSeedNodes();
        }
    }

    public void authenticateToReportedPeer(Set<Address> remainingAddresses, Address peerAddress) {
        checkArgument(!authenticatedPeers.containsKey(peerAddress),
                "We have that peer already authenticated. That must never happen.");

        AuthenticationHandshake authenticationHandshake = new AuthenticationHandshake(networkNode, this, getMyAddress());
        SettableFuture<Connection> future = authenticationHandshake.requestAuthentication(remainingAddresses, peerAddress);
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(@Nullable Connection connection) {
                if (connection != null) {
                    setAuthenticated(connection, peerAddress);
                    if (getAuthenticatedPeers().size() <= MAX_CONNECTIONS) {
                        log.info("We still don't have enough connections. Lets try the remaining seed nodes.");
                        authenticateToRemainingSeedNodes();
                    } else {
                        log.info("We have already enough connections.");
                    }
                }
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                throwable.printStackTrace();
                log.error("AuthenticationHandshake failed. " + throwable.getMessage());
                removePeer(peerAddress);
            }
        });
    }

    private void authenticateToRemainingSeedNodes() {
        // after we have at least one seed node we try to get reported peers connected
        List<Address> list = new ArrayList<>(seedNodeAddresses);
        removeAuthenticatedPeersFromList(list);
        if (!list.isEmpty()) {
            Address item = getAndRemoveRandomItem(list);
            log.info("We try to build an authenticated connection to a random seed node. " + item + " / list=" + list);
            authenticateToSeedNode(new HashSet<>(list), item, false);
        } else {
            log.info("We don't have any more seed nodes for connecting. " +
                    "We stop bootstrapping now, but will repeat after an while.");
            UserThread.runAfter(() -> authenticateToAnyReportedPeer(), 60);
        }
    }


    /*private void authenticateToAnyNode1(Set<Address> addresses, Address peerAddress, boolean prioritizeSeedNodes) {
        checkArgument(!authenticatedPeers.containsKey(peerAddress),
                "We have that peer already authenticated. That must never happen.");

        AuthenticationHandshake authenticationHandshake = new AuthenticationHandshake(networkNode, this, getMyAddress());
        SettableFuture<Connection> future = authenticationHandshake.requestAuthentication(addresses, peerAddress);
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(@Nullable Connection connection) {
                setAuthenticated(connection, peerAddress);
                authenticateToNextRandomPeer();
            }

            @Override
            public void onFailure(Throwable throwable) {
                throwable.printStackTrace();
                log.error("AuthenticationHandshake failed. " + throwable.getMessage());
                removePeer(peerAddress);
                authenticateToNextRandomPeer();
            }
        });
    }

    private void authenticateToNextRandomPeer() {
        UserThread.runAfterRandomDelay(() -> {
            log.info("authenticateToNextRandomPeer");
            if (getAuthenticatedPeers().size() <= MAX_CONNECTIONS) {
                Optional<Address> candidate = getRandomReportedPeerAddress();
                if (candidate.isPresent()) {
                    log.info("We try to build an authenticated connection to a random peer. " + candidate.get());
                    authenticateToReportedPeer(candidate.get(), );
                } else {
                    log.info("No more reportedPeerAddresses available for connecting. We try the remaining seed nodes");
                    candidate = getRandomSeedNodeAddress();
                    if (candidate.isPresent()) {
                        log.info("We try to build an authenticated connection to a random seed node. " + candidate.get());
                        authenticateToReportedPeer(candidate.get(), get);
                    } else {
                        log.info("No more seed nodes available for connecting.");
                    }
                }
            } else {
                log.info("We have already enough connections.");
            }
        }, 200, 400, TimeUnit.MILLISECONDS);
    }*/

    private Optional<Address> getRandomSeedNodeAddress() {
        List<Address> list = new ArrayList<>(seedNodeAddresses);
        log.debug("### getRandomSeedNodeAddress list " + list);
        removeAuthenticatedPeersFromList(list);
        log.debug("### list post removeAuthenticatedPeersFromList " + list);
        return getRandomEntry(list);
    }

    private Optional<Address> getRandomReportedPeerAddress() {
        List<Address> list = new ArrayList<>(reportedPeerAddresses);
        log.debug("### list reportedPeerAddresses " + reportedPeerAddresses);
        log.debug("### list authenticatedPeers " + authenticatedPeers);
        log.debug("### list pre " + list);
        removeAuthenticatedPeersFromList(list);
        log.debug("### list post " + list);
        return getRandomEntry(list);
    }

    private void removeAuthenticatedPeersFromList(List<Address> list) {
        authenticatedPeers.values().stream().forEach(e -> list.remove(e.address));
    }

    private Optional<Address> getRandomEntry(List<Address> list) {
        if (list.size() > 0) {
            Collections.shuffle(list);
            return Optional.of(list.get(0));
        } else {
            return Optional.empty();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Authentication to non-seed node peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void authenticateToPeer(Address peerAddress, @Nullable Runnable authenticationCompleteHandler, @Nullable Runnable faultHandler) {
        checkArgument(!authenticatedPeers.containsKey(peerAddress),
                "We have that seed node already authenticated. That must never happen.");

        AuthenticationHandshake authenticationHandshake = new AuthenticationHandshake(networkNode, this, getMyAddress());
        SettableFuture<Connection> future = authenticationHandshake.requestAuthenticationToPeer(peerAddress);
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(@Nullable Connection connection) {
                if (connection != null) {
                    setAuthenticated(connection, peerAddress);
                    if (authenticationCompleteHandler != null)
                        authenticationCompleteHandler.run();
                }
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                throwable.printStackTrace();
                log.error("AuthenticationHandshake failed. " + throwable.getMessage());
                removePeer(peerAddress);
                if (faultHandler != null)
                    faultHandler.run();
            }
        });
    }

    void setAuthenticated(Connection connection, Address peerAddress) {
        log.info("\n\n############################################################\n" +
                "We are authenticated to:" +
                "\nconnection=" + connection
                + "\nmyAddress=" + getMyAddress()
                + "\npeerAddress= " + peerAddress
                + "\n############################################################\n");

        connection.setAuthenticated(peerAddress, connection);

        Peer peer = new Peer(connection);
        addAuthenticatedPeer(peerAddress, peer);

        peerListeners.stream().forEach(e -> e.onConnectionAuthenticated(connection));
    }

    private void addAuthenticatedPeer(Address address, Peer peer) {
        boolean firstPeerAdded;
        authenticatedPeers.put(address, peer);
        firstPeerAdded = authenticatedPeers.size() == 1;

        UserThread.execute(() -> peerListeners.stream().forEach(e -> e.onPeerAdded(peer)));

        if (firstPeerAdded)
            UserThread.execute(() -> peerListeners.stream().forEach(e -> e.onFirstAuthenticatePeer(peer)));

        if (authenticatedPeers.size() > MAX_CONNECTIONS)
            disconnectOldConnections();

        printConnectedPeersMap();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addMessageListener(MessageListener messageListener) {
        networkNode.addMessageListener(messageListener);
    }

    public void removeMessageListener(MessageListener messageListener) {
        networkNode.removeMessageListener(messageListener);
    }

    public void addPeerListener(PeerListener peerListener) {
        peerListeners.add(peerListener);
    }

    public void removePeerListener(PeerListener peerListener) {
        peerListeners.remove(peerListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Map<Address, Peer> getAuthenticatedPeers() {
        return authenticatedPeers;
    }

    public Set<Address> getAllPeerAddresses() {
        CopyOnWriteArraySet<Address> allPeerAddresses = new CopyOnWriteArraySet<>(reportedPeerAddresses);
        allPeerAddresses.addAll(authenticatedPeers.values().stream()
                .map(e -> e.address).collect(Collectors.toList()));
        return allPeerAddresses;
    }

    public Set<Address> getSeedNodeAddresses() {
        return seedNodeAddresses;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Reported peers
    ///////////////////////////////////////////////////////////////////////////////////////////

    void addToReportedPeers(HashSet<Address> peerAddresses, Connection connection) {
        log.trace("addToReportedPeers");
        // we disconnect misbehaving nodes trying to send too many peers
        // reported peers include the peers connected peers which is normally max. 8 but we give some headroom 
        // for safety
        if (peerAddresses.size() > 1100) {
            connection.shutDown();
        } else {
            peerAddresses.remove(getMyAddress());
            reportedPeerAddresses.addAll(peerAddresses);
            purgeReportedPeers();
        }
    }

    void purgeReportedPeers() {
        log.trace("purgeReportedPeers");
        int all = getAllPeerAddresses().size();
        if (all > 1000) {
            int diff = all - 100;
            List<Address> list = new LinkedList<>(getReportedNotConnectedPeerAddresses());
            for (int i = 0; i < diff; i++) {
                Address toRemove = getAndRemoveRandomItem(list);
                reportedPeerAddresses.remove(toRemove);
            }
        }
    }

    private Set<Address> getReportedNotConnectedPeerAddresses() {
        Set<Address> set = new HashSet<>(reportedPeerAddresses);
        authenticatedPeers.values().stream().forEach(e -> set.remove(e.address));
        return set;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Maintenance
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void processMaintenanceMessage(MaintenanceMessage message, Connection connection) {
        log.debug("Received message " + message + " at " + getMyAddress() + " from " + connection.getPeerAddress());
        if (message instanceof PingMessage) {
            SettableFuture<Connection> future = networkNode.sendMessage(connection, new PongMessage(((PingMessage) message).nonce));
            Futures.addCallback(future, new FutureCallback<Connection>() {
                @Override
                public void onSuccess(Connection connection) {
                    log.trace("PongMessage sent successfully");
                }

                @Override
                public void onFailure(@NotNull Throwable throwable) {
                    log.info("PongMessage sending failed " + throwable.getMessage());
                    removePeer(connection.getPeerAddress());
                }
            });
        } else if (message instanceof PongMessage) {
            Peer peer = authenticatedPeers.get(connection.getPeerAddress());
            if (peer != null) {
                if (((PongMessage) message).nonce != peer.getPingNonce()) {
                    removePeer(peer.address);
                    log.warn("PongMessage invalid: self/peer " + getMyAddress() + "/" + connection.getPeerAddress());
                }
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Peers
    ///////////////////////////////////////////////////////////////////////////////////////////

    void removePeer(@Nullable Address peerAddress) {
        reportedPeerAddresses.remove(peerAddress);

        Peer disconnectedPeer = authenticatedPeers.remove(peerAddress);

        if (disconnectedPeer != null)
            UserThread.execute(() -> peerListeners.stream().forEach(e -> e.onPeerRemoved(peerAddress)));

        printConnectedPeersMap();
        printReportedPeersMap();
    }

    private Address getMyAddress() {
        return networkNode.getAddress();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Address getAndRemoveRandomItem(List<Address> list) {
        return list.remove(new Random().nextInt(list.size()));
    }

    public void printConnectedPeersMap() {
        StringBuilder result = new StringBuilder("\nConnected peers for node " + getMyAddress() + ":");
        authenticatedPeers.values().stream().forEach(e -> {
            result.append("\n\t" + e.address);
        });
        result.append("\n");
        log.info(result.toString());
    }

    public void printReportedPeersMap() {
        StringBuilder result = new StringBuilder("\nReported peerAddresses for node " + getMyAddress() + ":");
        reportedPeerAddresses.stream().forEach(e -> {
            result.append("\n\t" + e);
        });
        result.append("\n");
        log.info(result.toString());
    }
}
