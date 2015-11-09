package io.bitsquare.p2p.peers;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.Message;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

// Run in UserThread
public class PeerGroup implements MessageListener, ConnectionListener {
    private static final Logger log = LoggerFactory.getLogger(PeerGroup.class);

    static int simulateAuthTorNode = 0;

    public static void setSimulateAuthTorNode(int simulateAuthTorNode) {
        PeerGroup.simulateAuthTorNode = simulateAuthTorNode;
    }

    private static int MAX_CONNECTIONS = 8;

    public static void setMaxConnections(int maxConnections) {
        MAX_CONNECTIONS = maxConnections;
    }

    private static final int SEND_PING_INTERVAL = new Random().nextInt(2 * 60 * 1000) + 2 * 60 * 1000; // 2-4 min.
    private static final int GET_PEERS_INTERVAL = new Random().nextInt(1 * 60 * 1000) + 1 * 60 * 1000; // 1-2 min.
    private static final int PING_AFTER_CONNECTION_INACTIVITY = 30 * 1000;
    private static final int MAX_REPORTED_PEERS = 1000;

    private final NetworkNode networkNode;
    private final Set<Address> seedNodeAddresses;

    private final Set<PeerListener> peerListeners = new HashSet<>();
    private final Map<Address, Peer> authenticatedPeers = new HashMap<>();
    private final Set<Address> reportedPeerAddresses = new HashSet<>();
    private final Timer sendPingTimer = new Timer();
    private final Timer getPeersTimer = new Timer();

    private boolean shutDownInProgress;
    private boolean firstPeerAdded = false;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PeerGroup(NetworkNode networkNode, Set<Address> seeds) {
        Log.traceCall();

        this.networkNode = networkNode;
        this.seedNodeAddresses = seeds;

        networkNode.addMessageListener(this);
        networkNode.addConnectionListener(this);

        setupMaintenanceTimer();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        Log.traceCall();
        if (message instanceof MaintenanceMessage)
            processMaintenanceMessage((MaintenanceMessage) message, connection);
        else if (message instanceof AuthenticationRequest) {
            processAuthenticationRequest(networkNode, (AuthenticationRequest) message, connection);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ConnectionListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnection(Connection connection) {
    }

    @Override
    public void onPeerAddressAuthenticated(Address peerAddress, Connection connection) {
    }

    @Override
    public void onDisconnect(Reason reason, Connection connection) {
        log.debug("onDisconnect connection=" + connection + " / reason=" + reason);
        // only removes authenticated nodes
        if (connection.isAuthenticated())
            removePeer(connection.getPeerAddress());
    }

    @Override
    public void onError(Throwable throwable) {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void removeMySeedNodeAddressFromList(Address mySeedNodeAddress) {
        Log.traceCall();
        seedNodeAddresses.remove(mySeedNodeAddress);
    }

    public void broadcast(BroadcastMessage message, @Nullable Address sender) {
        Log.traceCall("Sender " + sender + ". Message " + message.toString());
        if (authenticatedPeers.values().size() > 0) {
            log.info("Broadcast message to {} peers. Message:", authenticatedPeers.values().size(), message);
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
        } else {
            log.trace("Message {} not broadcasted because we are not authenticated yet. " +
                    "That is expected at startup.", message);
        }
    }

    public void shutDown() {
        Log.traceCall();
        if (!shutDownInProgress) {
            shutDownInProgress = true;
            if (sendPingTimer != null)
                sendPingTimer.cancel();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Authentication to seed node
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void processAuthenticationRequest(NetworkNode networkNode, AuthenticationRequest message, final Connection connection) {
        Log.traceCall();
        AuthenticationHandshake authenticationHandshake = new AuthenticationHandshake(networkNode, PeerGroup.this, getMyAddress());
        SettableFuture<Connection> future = authenticationHandshake.processAuthenticationRequest(message, connection);
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(@Nullable Connection connection) {
                if (connection != null) {
                    setAuthenticated(connection, connection.getPeerAddress());
                    purgeReportedPeersIfExceeds();
                }
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                throwable.printStackTrace();
                log.error("AuthenticationHandshake failed. " + throwable.getMessage());
                removePeer(connection.getPeerAddress());
            }
        });
    }

    public void authenticateSeedNode(Address peerAddress) {
        Log.traceCall();
        authenticateToSeedNode(new HashSet<>(seedNodeAddresses), peerAddress, true);
    }

    // First we try to connect to 1 seed node. If we fail we try to connect to any reported peer.
    // After connection is authenticated, we try to connect to any reported peer as long we have not 
    // reached our max connection size.
    private void authenticateToSeedNode(Set<Address> remainingAddresses, Address peerAddress, boolean continueOnSuccess) {
        Log.traceCall();
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
        Log.traceCall();
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
        Log.traceCall();
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
        Log.traceCall();
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
        Log.traceCall();
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
        Log.traceCall();
        log.info("\n\n############################################################\n" +
                "We are authenticated to:" +
                "\nconnection=" + connection.getUid()
                + "\nmyAddress=" + getMyAddress()
                + "\npeerAddress= " + peerAddress
                + "\n############################################################\n");

        connection.setAuthenticated(peerAddress, connection);

        addAuthenticatedPeer(new Peer(connection));

        peerListeners.stream().forEach(e -> e.onConnectionAuthenticated(connection));
    }

    private void addAuthenticatedPeer(Peer peer) {
        Log.traceCall();
        authenticatedPeers.put(peer.address, peer);
        reportedPeerAddresses.remove(peer.address);
        firstPeerAdded = !firstPeerAdded && authenticatedPeers.size() == 1;

        peerListeners.stream().forEach(e -> e.onPeerAdded(peer));

        if (firstPeerAdded)
            peerListeners.stream().forEach(e -> e.onFirstAuthenticatePeer(peer));

        if (!checkIfConnectedPeersExceeds())
            printAuthenticatedPeers();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Maintenance
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupMaintenanceTimer() {
        Log.traceCall();
        sendPingTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Utilities.setThreadName("MaintenanceTimer");
                try {
                    UserThread.execute(() -> {
                        checkIfConnectedPeersExceeds();
                        pingPeers();
                    });
                } catch (Throwable t) {
                    t.printStackTrace();
                    log.error("Executing task failed. " + t.getMessage());
                }
            }
        }, SEND_PING_INTERVAL, SEND_PING_INTERVAL);

        getPeersTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Utilities.setThreadName("GetPeersTimer");
                try {
                    UserThread.execute(() -> trySendGetPeersRequest());
                } catch (Throwable t) {
                    t.printStackTrace();
                    log.error("Executing task failed. " + t.getMessage());
                }
            }
        }, GET_PEERS_INTERVAL, GET_PEERS_INTERVAL);
    }


    private boolean checkIfConnectedPeersExceeds() {
        Log.traceCall();
        if (authenticatedPeers.size() > MAX_CONNECTIONS) {
            log.trace("We have too many connections open. Lets remove the one which was not active recently.");
            List<Connection> authenticatedConnections = networkNode.getAllConnections().stream()
                    .filter(e -> e.isAuthenticated())
                    .collect(Collectors.toList());
            authenticatedConnections.sort((o1, o2) -> o1.getLastActivityDate().compareTo(o2.getLastActivityDate()));
            log.info("Number of connections exceeds MAX_CONNECTIONS. Current size=" + authenticatedConnections.size());
            Connection connection = authenticatedConnections.remove(0);
            log.info("We had shut down the oldest connection with last activity date="
                    + connection.getLastActivityDate() + " / connection=" + connection);
            connection.shutDown(() -> UserThread.runAfterRandomDelay(() -> checkIfConnectedPeersExceeds(), 100, 500, TimeUnit.MILLISECONDS));
            return true;
        } else {
            log.trace("We don't have too many connections open.");
            return false;
        }
    }

    private void pingPeers() {
        Log.traceCall();
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

    private void trySendGetPeersRequest() {
        Log.traceCall();
        Collection<Peer> peers = authenticatedPeers.values();
        if (!peers.isEmpty()) {
            Set<Peer> connectedPeersList = new HashSet<>(peers);
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
        } else {
            log.debug("No peers available for requesting data.");
        }
    }

    private void processMaintenanceMessage(MaintenanceMessage message, Connection connection) {
        Log.traceCall();
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
                        log.warn("PongMessage invalid: self/peer " + getMyAddress() + "/" + connection.getPeerAddress());
                        removePeer(peer.address);
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

    public void addPeerListener(PeerListener peerListener) {
        Log.traceCall();
        peerListeners.add(peerListener);
    }

    public void removePeerListener(PeerListener peerListener) {
        Log.traceCall();
        peerListeners.remove(peerListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Map<Address, Peer> getAuthenticatedPeers() {
        Log.traceCall();
        return authenticatedPeers;
    }

    public Set<Address> getAllPeerAddresses() {
        Log.traceCall();
        Set<Address> allPeerAddresses = new HashSet<>(reportedPeerAddresses);
        allPeerAddresses.addAll(authenticatedPeers.values().stream()
                .map(e -> e.address).collect(Collectors.toSet()));
        return allPeerAddresses;
    }

    public Set<Address> getSeedNodeAddresses() {
        Log.traceCall();
        return seedNodeAddresses;
    }

    public NetworkNode getNetworkNode() {
        return networkNode;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Reported peers
    ///////////////////////////////////////////////////////////////////////////////////////////

    void addToReportedPeers(HashSet<Address> peerAddresses, Connection connection) {
        Log.traceCall();
        // we disconnect misbehaving nodes trying to send too many peers
        // reported peers include the peers connected peers which is normally max. 8 but we give some headroom 
        // for safety
        if (peerAddresses.size() > 1100) {
            connection.shutDown();
        } else {
            peerAddresses.remove(getMyAddress());
            reportedPeerAddresses.addAll(peerAddresses);
            purgeReportedPeersIfExceeds();
        }
    }

    private void purgeReportedPeersIfExceeds() {
        Log.traceCall();
        int size = reportedPeerAddresses.size();
        if (size > MAX_REPORTED_PEERS) {
            log.trace("We have more then {} reported peers. size={}. " +
                    "We remove random peers from the reported peers list.", MAX_REPORTED_PEERS, size);
            int diff = size - MAX_REPORTED_PEERS;
            List<Address> list = new LinkedList<>(getReportedNotConnectedPeerAddresses());
            for (int i = 0; i < diff; i++) {
                Address toRemove = getAndRemoveRandomItem(list);
                reportedPeerAddresses.remove(toRemove);
            }
        } else {
            log.trace("We don't have more then {} reported peers yet.", MAX_REPORTED_PEERS);
        }
    }

    private Set<Address> getReportedNotConnectedPeerAddresses() {
        Log.traceCall();
        Set<Address> set = new HashSet<>(reportedPeerAddresses);
        authenticatedPeers.values().stream().forEach(e -> set.remove(e.address));
        return set;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Peers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void removePeer(@Nullable Address peerAddress) {
        Log.traceCall("peerAddress=" + peerAddress);
        if (peerAddress != null) {
            boolean contained = reportedPeerAddresses.remove(peerAddress);
            Peer disconnectedPeer = authenticatedPeers.remove(peerAddress);
            if (disconnectedPeer != null)
                peerListeners.stream().forEach(e -> e.onPeerRemoved(peerAddress));

            if (contained || disconnectedPeer != null)
                printAllPeers();
        }
    }

    private Address getMyAddress() {
        // Log.traceCall();
        return networkNode.getAddress();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Address getAndRemoveRandomItem(List<Address> list) {
        Log.traceCall();
        return list.remove(new Random().nextInt(list.size()));
    }

    private Optional<Tuple2<Address, Set<Address>>> getRandomItemAndRemainingSet(Set<Address> remainingAddresses) {
        Log.traceCall();
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
        Log.traceCall();
        printAuthenticatedPeers();
        printReportedPeers();
    }

    public void printAuthenticatedPeers() {
        Log.traceCall();
        StringBuilder result = new StringBuilder("\n\n------------------------------------------------------------\n" +
                "Authenticated peers for node " + getMyAddress() + ":");
        authenticatedPeers.values().stream().forEach(e -> result.append("\n").append(e.address));
        result.append("\n------------------------------------------------------------\n");
        log.info(result.toString());
    }

    public void printReportedPeers() {
        Log.traceCall();
        StringBuilder result = new StringBuilder("\n\n------------------------------------------------------------\n" +
                "Reported peers for node " + getMyAddress() + ":");
        reportedPeerAddresses.stream().forEach(e -> result.append("\n").append(e));
        result.append("\n------------------------------------------------------------\n");
        log.info(result.toString());
    }

}
