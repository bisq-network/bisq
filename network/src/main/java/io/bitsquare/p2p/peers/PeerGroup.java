package io.bitsquare.p2p.peers;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.ConnectionListener;
import io.bitsquare.p2p.network.MessageListener;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.p2p.peers.messages.auth.AuthenticationRequest;
import io.bitsquare.p2p.peers.messages.maintenance.*;
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

    public static void setMaxConnections(int maxConnections) {
        MAX_CONNECTIONS = maxConnections;
    }

    private static final int MAINTENANCE_INTERVAL = new Random().nextInt(2 * 60 * 1000) + 2 * 60 * 1000; // 2-4 min.
    private static final int GET_PEERS_INTERVAL = new Random().nextInt(1 * 60 * 1000) + 1 * 60 * 1000; // 1-2 min.
    private static final int PING_AFTER_CONNECTION_INACTIVITY = 30 * 1000;


    private final NetworkNode networkNode;
    private final Set<Address> seedNodeAddresses;

    private final CopyOnWriteArraySet<PeerListener> peerListeners = new CopyOnWriteArraySet<>();
    private final ConcurrentHashMap<Address, Peer> authenticatedPeers = new ConcurrentHashMap<>();
    private final CopyOnWriteArraySet<Address> reportedPeerAddresses = new CopyOnWriteArraySet<>();
    private final Timer maintenanceTimer = new Timer();
    private final Timer getPeersTimer = new Timer();

    private volatile boolean shutDownInProgress;
    private boolean firstPeerAdded = false;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PeerGroup(NetworkNode networkNode, Set<Address> seeds) {
        this.networkNode = networkNode;
        this.seedNodeAddresses = seeds;

        init(networkNode);
    }

    private void init(NetworkNode networkNode) {
        networkNode.addMessageListener((message, connection) -> {
            if (message instanceof MaintenanceMessage)
                processMaintenanceMessage((MaintenanceMessage) message, connection);
            else if (message instanceof AuthenticationRequest) {
                processAuthenticationRequest(networkNode, (AuthenticationRequest) message, connection);
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

        setupMaintenanceTimer();
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
        printAuthenticatedPeers();

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

    private void processAuthenticationRequest(NetworkNode networkNode, AuthenticationRequest message, final Connection connection) {
        AuthenticationHandshake authenticationHandshake = new AuthenticationHandshake(networkNode, PeerGroup.this, getMyAddress());
        SettableFuture<Connection> future = authenticationHandshake.processAuthenticationRequest(message, connection);
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

    public void authenticateSeedNode(Address peerAddress) {
        authenticateToSeedNode(new HashSet<>(seedNodeAddresses), peerAddress, true);
    }

    // First we try to connect to 1 seed node. If we fail we try to connect to any reported peer.
    // After connection is authenticated, we try to connect to any reported peer as long we have not 
    // reached our max connection size.
    private void authenticateToSeedNode(Set<Address> remainingAddresses, Address peerAddress, boolean continueOnSuccess) {
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
                            authenticateToRemainingReportedPeers();
                        } else {
                            log.info("We have already enough connections.");
                        }
                    } else {
                        log.info("We have already tried all reported peers and seed nodes. " +
                                "We stop bootstrapping now, but will repeat after an while.");
                        UserThread.runAfter(() -> authenticateToRemainingReportedPeers(), 60);
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

                Optional<Tuple2<Address, Set<Address>>> tupleOptional = getRandomItemAndRemainingSet(remainingAddresses);
                if (tupleOptional.isPresent()) {
                    log.info("We try to authenticate to a seed node. " + tupleOptional.get().first);
                    authenticateToSeedNode(tupleOptional.get().second, tupleOptional.get().first, true);
                } else {
                    log.info("We don't have any more seed nodes for connecting. Lets try the reported peers.");
                    authenticateToRemainingReportedPeers();
                }
            }
        });
    }

    private void authenticateToRemainingReportedPeers() {
        Optional<Tuple2<Address, Set<Address>>> tupleOptional = getRandomItemAndRemainingSet(reportedPeerAddresses);
        if (tupleOptional.isPresent()) {
            log.info("We try to authenticate to a random peer. " + tupleOptional.get().first);
            authenticateToReportedPeer(tupleOptional.get().second, tupleOptional.get().first);
        } else {
            log.info("We don't have any reported peers for connecting. Lets try the remaining seed nodes.");
            authenticateToRemainingSeedNodes();
        }
    }

    // We try to connect to a reported peer. If we fail we repeat after the failed peer has been removed.
    // If we succeed we repeat until we are ut of addresses.
    private void authenticateToReportedPeer(Set<Address> remainingAddresses, Address peerAddress) {
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
                        if (reportedPeerAddresses.size() > 0) {
                            log.info("We still don't have enough connections. " +
                                    "Lets try the remaining reported peer addresses.");
                            authenticateToRemainingReportedPeers();
                        } else {
                            log.info("We still don't have enough connections. Lets try the remaining seed nodes.");
                            authenticateToRemainingSeedNodes();
                        }
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

                log.info("Authentication failed. Lets try again with the remaining reported peer addresses.");
                authenticateToRemainingReportedPeers();
            }
        });
    }

    private void authenticateToRemainingSeedNodes() {
        Optional<Tuple2<Address, Set<Address>>> tupleOptional = getRandomItemAndRemainingSet(seedNodeAddresses);
        if (tupleOptional.isPresent()) {
            log.info("We try to authenticate to a random seed node. " + tupleOptional.get().first);
            authenticateToSeedNode(tupleOptional.get().second, tupleOptional.get().first, false);
        } else {
            log.info("We don't have any more seed nodes for connecting. " +
                    "We stop bootstrapping now, but will repeat after an while.");
            UserThread.runAfter(() -> authenticateToRemainingReportedPeers(), 60);
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

    private void setAuthenticated(Connection connection, Address peerAddress) {
        log.info("\n\n############################################################\n" +
                "We are authenticated to:" +
                "\nconnection=" + connection
                + "\nmyAddress=" + getMyAddress()
                + "\npeerAddress= " + peerAddress
                + "\n############################################################\n");

        connection.setAuthenticated(peerAddress, connection);

        addAuthenticatedPeer(new Peer(connection));

        peerListeners.stream().forEach(e -> e.onConnectionAuthenticated(connection));
    }

    private void addAuthenticatedPeer(Peer peer) {
        authenticatedPeers.put(peer.address, peer);
        reportedPeerAddresses.remove(peer.address);
        firstPeerAdded = !firstPeerAdded && authenticatedPeers.size() == 1;

        UserThread.execute(() -> peerListeners.stream().forEach(e -> e.onPeerAdded(peer)));

        if (firstPeerAdded)
            UserThread.execute(() -> peerListeners.stream().forEach(e -> e.onFirstAuthenticatePeer(peer)));

        if (authenticatedPeers.size() > MAX_CONNECTIONS)
            disconnectOldConnections();

        printAuthenticatedPeers();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Maintenance
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupMaintenanceTimer() {
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

        getPeersTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Thread.currentThread().setName("GetPeersTimer-" + new Random().nextInt(1000));
                try {
                    UserThread.execute(() -> sendGetPeersRequest());
                } catch (Throwable t) {
                    t.printStackTrace();
                    log.error("Executing task failed. " + t.getMessage());
                }
            }
        }, GET_PEERS_INTERVAL, GET_PEERS_INTERVAL);
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

    private void sendGetPeersRequest() {
        log.trace("sendGetPeersRequest");
        Set<Peer> connectedPeersList = new HashSet<>(authenticatedPeers.values());
        connectedPeersList.stream()
                .forEach(e -> UserThread.runAfterRandomDelay(() -> {
                    SettableFuture<Connection> future = networkNode.sendMessage(e.connection,
                            new GetPeersRequest(getMyAddress(), new HashSet<>(getAllPeerAddresses())));
                    Futures.addCallback(future, new FutureCallback<Connection>() {
                        @Override
                        public void onSuccess(Connection connection) {
                            log.trace("sendGetPeersRequest sent successfully");
                        }

                        @Override
                        public void onFailure(@NotNull Throwable throwable) {
                            log.info("sendGetPeersRequest sending failed " + throwable.getMessage());
                            removePeer(e.address);
                        }
                    });
                }, 5, 10));
    }

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
            if (connection.getPeerAddress() != null) {
                Peer peer = authenticatedPeers.get(connection.getPeerAddress());
                if (peer != null) {
                    if (((PongMessage) message).nonce != peer.getPingNonce()) {
                        removePeer(peer.address);
                        log.warn("PongMessage invalid: self/peer " + getMyAddress() + "/" + connection.getPeerAddress());
                    }
                }
            }
        } else if (message instanceof GetPeersRequest) {
            GetPeersRequest getPeersRequestMessage = (GetPeersRequest) message;
            HashSet<Address> peerAddresses = getPeersRequestMessage.peerAddresses;
            log.trace("Received peers: " + peerAddresses);
            addToReportedPeers(peerAddresses, connection);

            SettableFuture<Connection> future = networkNode.sendMessage(connection,
                    new GetPeersResponse(new HashSet<>(getAllPeerAddresses())));
            Futures.addCallback(future, new FutureCallback<Connection>() {
                @Override
                public void onSuccess(Connection connection) {
                    log.trace("GetPeersResponse sent successfully");
                }

                @Override
                public void onFailure(@NotNull Throwable throwable) {
                    log.info("GetPeersResponse sending failed " + throwable.getMessage());
                    removePeer(getPeersRequestMessage.address);
                }
            });
        } else if (message instanceof GetPeersResponse) {
            GetPeersResponse getPeersResponse = (GetPeersResponse) message;
            HashSet<Address> peerAddresses = getPeersResponse.peerAddresses;
            log.trace("Received peers: " + peerAddresses);
            addToReportedPeers(peerAddresses, connection);
        }
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

    private Map<Address, Peer> getAuthenticatedPeers() {
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

    private void purgeReportedPeers() {
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
    // Peers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void removePeer(@Nullable Address peerAddress) {
        reportedPeerAddresses.remove(peerAddress);

        if (peerAddress != null) {
            Peer disconnectedPeer = authenticatedPeers.remove(peerAddress);
            if (disconnectedPeer != null)
                UserThread.execute(() -> peerListeners.stream().forEach(e -> e.onPeerRemoved(peerAddress)));
        }
        printAuthenticatedPeers();
        printReportedPeers();
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

    private Optional<Tuple2<Address, Set<Address>>> getRandomItemAndRemainingSet(Set<Address> remainingAddresses) {
        List<Address> list = new ArrayList<>(remainingAddresses);
        authenticatedPeers.values().stream().forEach(e -> list.remove(e.address));
        if (!list.isEmpty()) {
            Address item = getAndRemoveRandomItem(list);
            return Optional.of(new Tuple2<>(item, new HashSet<>(list)));
        } else {
            return Optional.empty();
        }
    }

    public void printAllPeers() {
        printAuthenticatedPeers();
        printReportedPeers();
    }

    public void printAuthenticatedPeers() {
        StringBuilder result = new StringBuilder("\n\n############################################################\n" +
                "Authenticated peers for node " + getMyAddress() + ":");
        authenticatedPeers.values().stream().forEach(e -> result.append("\n").append(e.address));
        result.append("\n############################################################\n");
        log.info(result.toString());
    }

    public void printReportedPeers() {
        StringBuilder result = new StringBuilder("\n\n############################################################\n" +
                "Reported peers for node " + getMyAddress() + ":");
        reportedPeerAddresses.stream().forEach(e -> result.append("\n").append(e));
        result.append("\n############################################################\n");
        log.info(result.toString());
    }
}
