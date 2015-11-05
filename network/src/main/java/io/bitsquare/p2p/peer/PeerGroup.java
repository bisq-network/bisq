package io.bitsquare.p2p.peer;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.network.*;
import io.bitsquare.p2p.peer.messages.*;
import io.bitsquare.p2p.storage.messages.BroadcastMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PeerGroup {
    private static final Logger log = LoggerFactory.getLogger(PeerGroup.class);

    private static int simulateAuthTorNode = 0;

    public static void setSimulateAuthTorNode(int simulateAuthTorNode) {
        PeerGroup.simulateAuthTorNode = simulateAuthTorNode;
    }

    private static int MAX_CONNECTIONS = 8;
    private static int MAINTENANCE_INTERVAL = new Random().nextInt(2 * 60 * 1000) + 2 * 60 * 1000; // 2-4 min.
    private static int PING_AFTER_CONNECTION_INACTIVITY = 30 * 1000;
    private long startAuthTs;

    public static void setMaxConnections(int maxConnections) {
        MAX_CONNECTIONS = maxConnections;
    }

    private final NetworkNode networkNode;
    private final List<Address> seedNodes;
    private final Map<Address, Long> nonceMap = new ConcurrentHashMap<>();
    private final List<PeerListener> peerListeners = new CopyOnWriteArrayList<>();
    private final Map<Address, Peer> authenticatedPeers = new ConcurrentHashMap<>();
    private final Set<Address> reportedPeerAddresses = new CopyOnWriteArraySet<>();
    private final Map<Address, Runnable> authenticationCompleteHandlers = new ConcurrentHashMap<>();
    private final Timer maintenanceTimer = new Timer();
    private volatile boolean shutDownInProgress;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PeerGroup(final NetworkNode networkNode, List<Address> seeds) {
        this.networkNode = networkNode;

        // We copy it as we remove ourselves later from the list if we are a seed node
        this.seedNodes = new CopyOnWriteArrayList<>(seeds);

        init(networkNode);
    }

    private void init(NetworkNode networkNode) {
        networkNode.addMessageListener((message, connection) -> {
            if (message instanceof AuthenticationMessage)
                processAuthenticationMessage((AuthenticationMessage) message, connection);
            else if (message instanceof MaintenanceMessage)
                processMaintenanceMessage((MaintenanceMessage) message, connection);
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
                // only removes authenticated nodes
                if (connection.isAuthenticated())
                    removePeer(connection.getPeerAddress());
            }

            @Override
            public void onError(Throwable throwable) {
            }
        });

        networkNode.addSetupListener(new SetupListener() {
            @Override
            public void onTorNodeReady() {
            }

            @Override
            public void onHiddenServiceReady() {
                // remove ourselves in case we are a seed node
                Address myAddress = getAddress();
                if (myAddress != null)
                    seedNodes.remove(myAddress);
            }

            @Override
            public void onSetupFailed(Throwable throwable) {
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

            connection.shutDown(() -> Utilities.runTimerTask(() -> {
                Thread.currentThread().setName("DelayDisconnectOldConnectionsTimer-" + new Random().nextInt(1000));
                disconnectOldConnections();
            }, 1, TimeUnit.MILLISECONDS));
        }
    }

    private void pingPeers() {
        log.trace("pingPeers");
        List<Peer> connectedPeersList = new ArrayList<>(authenticatedPeers.values());
        connectedPeersList.stream()
                .filter(e -> (new Date().getTime() - e.connection.getLastActivityDate().getTime()) > PING_AFTER_CONNECTION_INACTIVITY)
                .forEach(e -> Utilities.runTimerTaskWithRandomDelay(() -> {
                    Thread.currentThread().setName("DelayPingPeersTimer-" + new Random().nextInt(1000));
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

        authenticatedPeers.values().stream()
                .filter(e -> !e.address.equals(sender))
                .forEach(peer -> {
                    log.trace("Broadcast message from " + getAddress() + " to " + peer.address + ".");
                    SettableFuture<Connection> future = networkNode.sendMessage(peer.address, message);
                    Futures.addCallback(future, new FutureCallback<Connection>() {
                        @Override
                        public void onSuccess(Connection connection) {
                            log.trace("Broadcast from " + getAddress() + " to " + peer.address + " succeeded.");
                        }

                        @Override
                        public void onFailure(@NotNull Throwable throwable) {
                            log.info("Broadcast failed. " + throwable.getMessage());
                            removePeer(peer.address);
                        }
                    });
                });
    }

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

    public Map<Address, Peer> getAuthenticatedPeers() {
        return authenticatedPeers;
    }

    // Use ArrayList not List as we need it serializable
    public ArrayList<Address> getAllPeerAddresses() {
        ArrayList<Address> allPeerAddresses = new ArrayList<>(reportedPeerAddresses);
        allPeerAddresses.addAll(authenticatedPeers.values().stream()
                .map(e -> e.address).collect(Collectors.toList()));
        // remove own address and seed nodes
        allPeerAddresses.remove(getAddress());
        return allPeerAddresses;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Authentication
    ///////////////////////////////////////////////////////////////////////////////////////////

    // authentication example: 
    // node2 -> node1 RequestAuthenticationMessage
    // node1: close connection
    // node1 -> node2 ChallengeMessage on new connection
    // node2: authentication to node1 done if nonce ok
    // node2 -> node1 GetPeersMessage
    // node1: authentication to node2 done if nonce ok
    // node1 -> node2 PeersMessage

    public void startAuthentication(Set<Address> connectedSeedNodes) {
        connectedSeedNodes.forEach(connectedSeedNode -> {
            sendRequestAuthenticationMessage(seedNodes, connectedSeedNode);
        });
    }

    private void sendRequestAuthenticationMessage(final List<Address> remainingSeedNodes, final Address address) {
        log.info("We try to authenticate to a random seed node. " + address);
        startAuthTs = System.currentTimeMillis();
        final boolean[] alreadyConnected = {false};
        authenticatedPeers.values().stream().forEach(e -> {
            remainingSeedNodes.remove(e.address);
            if (address.equals(e.address))
                alreadyConnected[0] = true;
        });
        if (!alreadyConnected[0]) {
            long nonce = addToMapAndGetNonce(address);
            SettableFuture<Connection> future = networkNode.sendMessage(address, new RequestAuthenticationMessage(getAddress(), nonce));
            Futures.addCallback(future, new FutureCallback<Connection>() {
                @Override
                public void onSuccess(@Nullable Connection connection) {
                    log.info("send RequestAuthenticationMessage to " + address + " succeeded.");
                }

                @Override
                public void onFailure(@NotNull Throwable throwable) {
                    log.info("Send RequestAuthenticationMessage to " + address + " failed. Exception:" + throwable.getMessage());
                    log.trace("We try to authenticate to another random seed nodes of that list: " + remainingSeedNodes);
                    getNextSeedNode(remainingSeedNodes);
                }
            });
        } else {
            getNextSeedNode(remainingSeedNodes);
        }
    }

    private void getNextSeedNode(List<Address> remainingSeedNodes) {
        List<Address> remainingSeedNodeAddresses = new CopyOnWriteArrayList<>(remainingSeedNodes);

        Address myAddress = getAddress();
        if (myAddress != null)
            remainingSeedNodeAddresses.remove(myAddress);

        if (!remainingSeedNodeAddresses.isEmpty()) {
            Collections.shuffle(remainingSeedNodeAddresses);
            Address address = remainingSeedNodeAddresses.remove(0);
            sendRequestAuthenticationMessage(remainingSeedNodeAddresses, address);
        } else {
            log.info("No other seed node found. That is expected for the first seed node.");
        }
    }


    private void processAuthenticationMessage(AuthenticationMessage message, Connection connection) {
        log.trace("processAuthenticationMessage " + message + " from " + connection.getPeerAddress() + " at " + getAddress());
        if (message instanceof RequestAuthenticationMessage) {
            RequestAuthenticationMessage requestAuthenticationMessage = (RequestAuthenticationMessage) message;
            Address peerAddress = requestAuthenticationMessage.address;
            log.trace("RequestAuthenticationMessage from " + peerAddress + " at " + getAddress());

            connection.shutDown(() -> Utilities.runTimerTask(() -> {
                        Thread.currentThread().setName("DelaySendChallengeMessageTimer-" + new Random().nextInt(1000));
                        // we delay a bit as listeners for connection.onDisconnect are on other threads and might lead to 
                        // inconsistent state (removal of connection from NetworkNode.authenticatedConnections)
                        log.trace("processAuthenticationMessage: connection.shutDown complete. RequestAuthenticationMessage from " + peerAddress + " at " + getAddress());
                        long nonce = addToMapAndGetNonce(peerAddress);
                        SettableFuture<Connection> future = networkNode.sendMessage(peerAddress, new ChallengeMessage(getAddress(), requestAuthenticationMessage.nonce, nonce));
                        Futures.addCallback(future, new FutureCallback<Connection>() {
                            @Override
                            public void onSuccess(Connection connection) {
                                log.debug("onSuccess sending ChallengeMessage");
                            }

                            @Override
                            public void onFailure(Throwable throwable) {
                                log.warn("onFailure sending ChallengeMessage. We try again.");
                                SettableFuture<Connection> future = networkNode.sendMessage(peerAddress, new ChallengeMessage(getAddress(), requestAuthenticationMessage.nonce, nonce));
                                Futures.addCallback(future, new FutureCallback<Connection>() {
                                    @Override
                                    public void onSuccess(Connection connection) {
                                        log.debug("onSuccess sending 2. ChallengeMessage");
                                    }

                                    @Override
                                    public void onFailure(Throwable throwable) {
                                        log.warn("onFailure sending ChallengeMessage. We give up.");
                                    }
                                });
                            }
                        });
                    },
                    100 + simulateAuthTorNode,
                    TimeUnit.MILLISECONDS));
        } else if (message instanceof ChallengeMessage) {
            ChallengeMessage challengeMessage = (ChallengeMessage) message;
            Address peerAddress = challengeMessage.address;
            log.trace("ChallengeMessage from " + peerAddress + " at " + getAddress());
            HashMap<Address, Long> tempNonceMap = new HashMap<>(nonceMap);
            boolean verified = verifyNonceAndAuthenticatePeerAddress(challengeMessage.requesterNonce, peerAddress);
            if (verified) {
                connection.setPeerAddress(peerAddress);
                SettableFuture<Connection> future = networkNode.sendMessage(peerAddress,
                        new GetPeersMessage(getAddress(), challengeMessage.challengerNonce, getAllPeerAddresses()));
                Futures.addCallback(future, new FutureCallback<Connection>() {
                    @Override
                    public void onSuccess(Connection connection) {
                        log.trace("GetPeersMessage sent successfully from " + getAddress() + " to " + peerAddress);
                    }

                    @Override
                    public void onFailure(@NotNull Throwable throwable) {
                        log.info("GetPeersMessage sending failed " + throwable.getMessage());
                        removePeer(peerAddress);
                    }
                });
            } else {
                log.warn("verifyNonceAndAuthenticatePeerAddress failed. challengeMessage=" + challengeMessage + " / nonceMap=" + tempNonceMap);
            }
        } else if (message instanceof GetPeersMessage) {
            GetPeersMessage getPeersMessage = (GetPeersMessage) message;
            Address peerAddress = getPeersMessage.address;
            log.trace("GetPeersMessage from " + peerAddress + " at " + getAddress());
            boolean verified = verifyNonceAndAuthenticatePeerAddress(getPeersMessage.challengerNonce, peerAddress);
            if (verified) {
                setAuthenticated(connection, peerAddress);
                purgeReportedPeers();
                SettableFuture<Connection> future = networkNode.sendMessage(peerAddress,
                        new PeersMessage(getAddress(), getAllPeerAddresses()));
                log.trace("sent PeersMessage to " + peerAddress + " from " + getAddress()
                        + " with allPeers=" + getAllPeerAddresses());
                Futures.addCallback(future, new FutureCallback<Connection>() {
                    @Override
                    public void onSuccess(Connection connection) {
                        log.trace("PeersMessage sent successfully from " + getAddress() + " to " + peerAddress);
                    }

                    @Override
                    public void onFailure(@NotNull Throwable throwable) {
                        log.info("PeersMessage sending failed " + throwable.getMessage());
                        removePeer(peerAddress);
                    }
                });

                // now we add the reported peers to our own set
                ArrayList<Address> peerAddresses = ((GetPeersMessage) message).peerAddresses;
                log.trace("Received peers: " + peerAddresses);
                // remove ourselves
                addToReportedPeers(peerAddresses, connection);
            }
        } else if (message instanceof PeersMessage) {
            PeersMessage peersMessage = (PeersMessage) message;
            Address peerAddress = peersMessage.address;
            log.trace("PeersMessage from " + peerAddress + " at " + getAddress());
            ArrayList<Address> peerAddresses = peersMessage.peerAddresses;
            log.trace("Received peers: " + peerAddresses);
            // remove ourselves
            addToReportedPeers(peerAddresses, connection);

            // we wait until the handshake is completed before setting the authenticate flag
            // authentication at both sides of the connection

            log.info("\n\nAuthenticationComplete\nPeer with address " + peerAddress
                    + " authenticated (" + connection.getObjectId() + "). Took "
                    + (System.currentTimeMillis() - startAuthTs) + " ms. \n\n");

            setAuthenticated(connection, peerAddress);

            Runnable authenticationCompleteHandler = authenticationCompleteHandlers.remove(connection.getPeerAddress());
            if (authenticationCompleteHandler != null)
                authenticationCompleteHandler.run();

            authenticateToNextRandomPeer();
        }
    }

    private void addToReportedPeers(ArrayList<Address> peerAddresses, Connection connection) {
        log.trace("addToReportedPeers");
        // we disconnect misbehaving nodes trying to send too many peers
        // reported peers include the peers connected peers which is normally max. 8 but we give some headroom 
        // for safety
        if (peerAddresses.size() > 1100) {
            connection.shutDown();
        } else {
            peerAddresses.remove(getAddress());
            reportedPeerAddresses.addAll(peerAddresses);
            purgeReportedPeers();
        }
    }

    private void purgeReportedPeers() {
        log.trace("purgeReportedPeers");
        int all = getAllPeerAddresses().size();
        if (all > 1000) {
            int diff = all - 100;
            List<Address> list = getNotConnectedPeerAddresses();
            for (int i = 0; i < diff; i++) {
                Address toRemove = list.remove(new Random().nextInt(list.size()));
                reportedPeerAddresses.remove(toRemove);
            }
        }
    }

    private List<Address> getNotConnectedPeerAddresses() {
        ArrayList<Address> list = new ArrayList<>(getAllPeerAddresses());
        log.debug("## getNotConnectedPeerAddresses ");
        log.debug("##  reportedPeersList=" + list);
        authenticatedPeers.values().stream().forEach(e -> list.remove(e.address));
        log.debug("##  connectedPeers=" + authenticatedPeers);
        log.debug("##  reportedPeersList=" + list);
        return list;
    }

    private void authenticateToNextRandomPeer() {
        Utilities.runTimerTaskWithRandomDelay(() -> {
            Thread.currentThread().setName("DelayAuthenticateToNextRandomPeerTimer-" + new Random().nextInt(1000));
            if (getAuthenticatedPeers().size() <= MAX_CONNECTIONS) {
                Address randomNotConnectedPeerAddress = getRandomNotConnectedPeerAddress();
                if (randomNotConnectedPeerAddress != null) {
                    log.info("We try to build an authenticated connection to a random peer. " + randomNotConnectedPeerAddress);
                    authenticateToPeer(randomNotConnectedPeerAddress, null, () -> authenticateToNextRandomPeer());
                } else {
                    log.info("No more peers available for connecting.");
                }
            } else {
                log.info("We have already enough connections.");
            }
        }, 200, 400, TimeUnit.MILLISECONDS);
    }

    public void authenticateToPeer(Address address, @Nullable Runnable authenticationCompleteHandler, @Nullable Runnable faultHandler) {
        startAuthTs = System.currentTimeMillis();

        if (authenticationCompleteHandler != null)
            authenticationCompleteHandlers.put(address, authenticationCompleteHandler);

        long nonce = addToMapAndGetNonce(address);
        SettableFuture<Connection> future = networkNode.sendMessage(address, new RequestAuthenticationMessage(getAddress(), nonce));
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(@Nullable Connection connection) {
                log.debug("send RequestAuthenticationMessage succeeded");
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                log.info("send IdMessage failed. " + throwable.getMessage());
                removePeer(address);
                if (faultHandler != null) faultHandler.run();
            }
        });
    }

    private long addToMapAndGetNonce(Address peerAddress) {
        long nonce = new Random().nextLong();
        while (nonce == 0) {
            nonce = new Random().nextLong();
        }
        log.trace("addToMapAndGetNonce nonceMap=" + nonceMap + " / peerAddress=" + peerAddress);
        nonceMap.put(peerAddress, nonce);
        return nonce;
    }

    private boolean verifyNonceAndAuthenticatePeerAddress(long peersNonce, Address peerAddress) {
        log.trace("verifyNonceAndAuthenticatePeerAddress nonceMap=" + nonceMap + " / peerAddress=" + peerAddress);
        Long nonce = nonceMap.remove(peerAddress);
        return nonce != null && nonce == peersNonce;
    }

    private void setAuthenticated(Connection connection, Address peerAddress) {
        log.info("\n\n############################################################\n" +
                "We are authenticated to:" +
                "\nconnection=" + connection
                + "\nmyAddress=" + getAddress()
                + "\npeerAddress= " + peerAddress
                + "\n############################################################\n");

        connection.setAuthenticated(peerAddress, connection);

        Peer peer = new Peer(connection);
        addAuthenticatedPeer(peerAddress, peer);

        peerListeners.stream().forEach(e -> e.onConnectionAuthenticated(connection));

        log.debug("\n### setAuthenticated post connection " + connection);
    }

    private Address getRandomNotConnectedPeerAddress() {
        List<Address> list = getNotConnectedPeerAddresses();
        if (list.size() > 0) {
            Collections.shuffle(list);
            return list.get(0);
        } else {
            return null;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Maintenance
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void processMaintenanceMessage(MaintenanceMessage message, Connection connection) {
        log.debug("Received message " + message + " at " + getAddress() + " from " + connection.getPeerAddress());
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
                    log.warn("PongMessage invalid: self/peer " + getAddress() + "/" + connection.getPeerAddress());
                }
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Peers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void removePeer(@Nullable Address peerAddress) {
        reportedPeerAddresses.remove(peerAddress);

        Peer disconnectedPeer;
        disconnectedPeer = authenticatedPeers.remove(peerAddress);

        if (disconnectedPeer != null)
            UserThread.execute(() -> peerListeners.stream().forEach(e -> e.onPeerRemoved(peerAddress)));

        log.trace("removePeer [post]");
        printConnectedPeersMap();
        printReportedPeersMap();

        log.trace("removePeer nonceMap=" + nonceMap + " / peerAddress=" + peerAddress);
        nonceMap.remove(peerAddress);
    }

    private void addAuthenticatedPeer(Address address, Peer peer) {
        boolean firstPeerAdded;
        authenticatedPeers.put(address, peer);
        firstPeerAdded = authenticatedPeers.size() == 1;

        UserThread.execute(() -> peerListeners.stream().forEach(e -> e.onPeerAdded(peer)));

        if (firstPeerAdded)
            UserThread.execute(() -> peerListeners.stream().forEach(e -> e.onFirstPeerAdded(peer)));

        if (authenticatedPeers.size() > MAX_CONNECTIONS)
            disconnectOldConnections();

        log.trace("addConnectedPeer [post]");
        printConnectedPeersMap();
    }

    private Address getAddress() {
        return networkNode.getAddress();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void printConnectedPeersMap() {
        StringBuilder result = new StringBuilder("\nConnected peers for node " + getAddress() + ":");
        authenticatedPeers.values().stream().forEach(e -> {
            result.append("\n\t" + e.address);
        });
        result.append("\n");
        log.info(result.toString());
    }

    public void printReportedPeersMap() {
        StringBuilder result = new StringBuilder("\nReported peerAddresses for node " + getAddress() + ":");
        reportedPeerAddresses.stream().forEach(e -> {
            result.append("\n\t" + e);
        });
        result.append("\n");
        log.info(result.toString());
    }

    private String getObjectId() {
        return super.toString().split("@")[1].toString();
    }

}
