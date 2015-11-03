package io.bitsquare.p2p.routing;

import com.google.common.util.concurrent.*;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.Utils;
import io.bitsquare.p2p.network.*;
import io.bitsquare.p2p.routing.messages.*;
import io.bitsquare.p2p.storage.messages.BroadcastMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Routing {
    private static final Logger log = LoggerFactory.getLogger(Routing.class);

    private static int simulateAuthTorNode = 0;

    public static void setSimulateAuthTorNode(int simulateAuthTorNode) {
        Routing.simulateAuthTorNode = simulateAuthTorNode;
    }

    private static int MAX_CONNECTIONS = 8;
    private static int MAINTENANCE_INTERVAL = new Random().nextInt(15 * 60 * 1000) + 15 * 60 * 1000; // 15-30 min.
    private static int PING_AFTER_CONNECTION_INACTIVITY = 5 * 60 * 1000;   // 5 min
    private long startAuthTs;

    public static void setMaxConnections(int maxConnections) {
        MAX_CONNECTIONS = maxConnections;
    }

    private final NetworkNode networkNode;
    private final List<Address> seedNodes;
    private final Map<Address, Long> nonceMap = new ConcurrentHashMap<>();
    private final List<RoutingListener> routingListeners = new CopyOnWriteArrayList<>();
    private final Map<Address, Neighbor> connectedNeighbors = new ConcurrentHashMap<>();
    private final List<Address> reportedNeighborAddresses = new CopyOnWriteArrayList<>();
    private final Map<Address, Runnable> authenticationCompleteHandlers = new ConcurrentHashMap<>();
    private final Timer maintenanceTimer = new Timer();
    private final ExecutorService executorService;
    private volatile boolean shutDownInProgress;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Routing(final NetworkNode networkNode, List<Address> seeds) {
        this.networkNode = networkNode;

        // We copy it as we remove ourselves later from the list if we are a seed node
        this.seedNodes = new CopyOnWriteArrayList<>(seeds);

        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("Routing-%d")
                .setDaemon(true)
                .build();

        executorService = new ThreadPoolExecutor(5, 50, 10L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(50), threadFactory);

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
                    removeNeighbor(connection.getPeerAddress());
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
                try {
                    disconnectOldConnections();
                    pingNeighbors();
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
            connection.shutDown(() -> disconnectOldConnections());
            Uninterruptibles.sleepUninterruptibly(200, TimeUnit.MILLISECONDS);
        }
    }

    private void pingNeighbors() {
        log.trace("pingNeighbors");
        List<Neighbor> connectedNeighborsList = new ArrayList<>(connectedNeighbors.values());
        connectedNeighborsList.stream()
                .filter(e -> (new Date().getTime() - e.connection.getLastActivityDate().getTime()) > PING_AFTER_CONNECTION_INACTIVITY)
                .forEach(e -> {
                    SettableFuture<Connection> future = networkNode.sendMessage(e.connection, new PingMessage(e.getPingNonce()));
                    Futures.addCallback(future, new FutureCallback<Connection>() {
                        @Override
                        public void onSuccess(Connection connection) {
                            log.trace("PingMessage sent successfully");
                        }

                        @Override
                        public void onFailure(@NotNull Throwable throwable) {
                            log.info("PingMessage sending failed " + throwable.getMessage());
                            removeNeighbor(e.address);
                        }
                    });
                    Uninterruptibles.sleepUninterruptibly(new Random().nextInt(5000) + 5000, TimeUnit.MILLISECONDS);
                });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void shutDown() {
        if (!shutDownInProgress) {
            shutDownInProgress = true;
            if (maintenanceTimer != null)
                maintenanceTimer.cancel();

            Utils.shutDownExecutorService(executorService);
        }
    }

    public void broadcast(BroadcastMessage message, @Nullable Address sender) {
        log.trace("Broadcast message to " + connectedNeighbors.values().size() + " neighbors.");
        log.trace("message = " + message);
        log.trace("connectedNeighbors = " + connectedNeighbors);
        connectedNeighbors.values().parallelStream()
                .filter(e -> !e.address.equals(sender))
                .forEach(neighbor -> {
                    log.trace("Broadcast message from " + getAddress() + " to " + neighbor.address + ".");
                    SettableFuture<Connection> future = networkNode.sendMessage(neighbor.address, message);
                    Futures.addCallback(future, new FutureCallback<Connection>() {
                        @Override
                        public void onSuccess(Connection connection) {
                            log.trace("Broadcast from " + getAddress() + " to " + neighbor.address + " succeeded.");
                        }

                        @Override
                        public void onFailure(@NotNull Throwable throwable) {
                            log.info("Broadcast failed. " + throwable.getMessage());
                            removeNeighbor(neighbor.address);
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

    public void addRoutingListener(RoutingListener routingListener) {
        routingListeners.add(routingListener);
    }

    public void removeRoutingListener(RoutingListener routingListener) {
        routingListeners.remove(routingListener);
    }

    public Map<Address, Neighbor> getConnectedNeighbors() {
        return connectedNeighbors;
    }

    // Use ArrayList not List as we need it serializable
    public ArrayList<Address> getAllNeighborAddresses() {
        ArrayList<Address> allNeighborAddresses = new ArrayList<>(reportedNeighborAddresses);
        allNeighborAddresses.addAll(connectedNeighbors.values().stream()
                .map(e -> e.address).collect(Collectors.toList()));
        // remove own address and seed nodes
        allNeighborAddresses.remove(getAddress());
        return allNeighborAddresses;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Authentication
    ///////////////////////////////////////////////////////////////////////////////////////////

    // authentication example: 
    // node2 -> node1 RequestAuthenticationMessage
    // node1: close connection
    // node1 -> node2 ChallengeMessage on new connection
    // node2: authentication to node1 done if nonce ok
    // node2 -> node1 GetNeighborsMessage
    // node1: authentication to node2 done if nonce ok
    // node1 -> node2 NeighborsMessage

    public void startAuthentication(List<Address> connectedSeedNodes) {
        connectedSeedNodes.forEach(connectedSeedNode -> {
            executorService.submit(() -> {
                try {
                    sendRequestAuthenticationMessage(seedNodes, connectedSeedNode);
                    // give a random pause of 3-5 sec. before using the next
                    Uninterruptibles.sleepUninterruptibly(new Random().nextInt(2000) + 3000, TimeUnit.MILLISECONDS);
                } catch (Throwable t) {
                    t.printStackTrace();
                    log.error("Executing task failed. " + t.getMessage());
                }
            });
        });
    }

    private void sendRequestAuthenticationMessage(final List<Address> remainingSeedNodes, final Address address) {
        log.info("We try to authenticate to a random seed node. " + address);
        startAuthTs = System.currentTimeMillis();
        final boolean[] alreadyConnected = {false};
        connectedNeighbors.values().stream().forEach(e -> {
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
            connection.shutDown(() -> {
                // we delay a bit as listeners for connection.onDisconnect are on other threads and might lead to 
                // inconsistent state (removal of connection from NetworkNode.authenticatedConnections)
                Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);

                if (simulateAuthTorNode > 0)
                    Uninterruptibles.sleepUninterruptibly(simulateAuthTorNode, TimeUnit.MILLISECONDS);

                log.trace("processAuthenticationMessage: connection.shutDown complete. RequestAuthenticationMessage from " + peerAddress + " at " + getAddress());
                long nonce = addToMapAndGetNonce(peerAddress);
                SettableFuture<Connection> future = networkNode.sendMessage(peerAddress, new ChallengeMessage(getAddress(), requestAuthenticationMessage.nonce, nonce));
                Futures.addCallback(future, new FutureCallback<Connection>() {
                    @Override
                    public void onSuccess(Connection connection) {
                        log.debug("onSuccess ");

                        // TODO check nr. of connections, remove older connections (?)
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        log.debug("onFailure ");
                        // TODO skip to next node or retry?
                        SettableFuture<Connection> future = networkNode.sendMessage(peerAddress, new ChallengeMessage(getAddress(), requestAuthenticationMessage.nonce, nonce));
                        Futures.addCallback(future, new FutureCallback<Connection>() {
                            @Override
                            public void onSuccess(Connection connection) {
                                log.debug("onSuccess ");
                            }

                            @Override
                            public void onFailure(Throwable throwable) {
                                log.debug("onFailure ");
                            }
                        });
                    }
                });
            });
        } else if (message instanceof ChallengeMessage) {
            ChallengeMessage challengeMessage = (ChallengeMessage) message;
            Address peerAddress = challengeMessage.address;
            log.trace("ChallengeMessage from " + peerAddress + " at " + getAddress());
            HashMap<Address, Long> tempNonceMap = new HashMap<>(nonceMap);
            boolean verified = verifyNonceAndAuthenticatePeerAddress(challengeMessage.requesterNonce, peerAddress);
            if (verified) {
                SettableFuture<Connection> future = networkNode.sendMessage(peerAddress,
                        new GetNeighborsMessage(getAddress(), challengeMessage.challengerNonce, getAllNeighborAddresses()));
                Futures.addCallback(future, new FutureCallback<Connection>() {
                    @Override
                    public void onSuccess(Connection connection) {
                        log.trace("GetNeighborsMessage sent successfully from " + getAddress() + " to " + peerAddress);

                       /* // we wait to get the success to reduce the time span of the moment of 
                        // authentication at both sides of the connection
                        setAuthenticated(connection, peerAddress);*/
                    }

                    @Override
                    public void onFailure(@NotNull Throwable throwable) {
                        log.info("GetNeighborsMessage sending failed " + throwable.getMessage());
                        removeNeighbor(peerAddress);
                    }
                });
            } else {
                log.warn("verifyNonceAndAuthenticatePeerAddress failed. challengeMessage=" + challengeMessage + " / nonceMap=" + tempNonceMap);
            }
        } else if (message instanceof GetNeighborsMessage) {
            GetNeighborsMessage getNeighborsMessage = (GetNeighborsMessage) message;
            Address peerAddress = getNeighborsMessage.address;
            log.trace("GetNeighborsMessage from " + peerAddress + " at " + getAddress());
            boolean verified = verifyNonceAndAuthenticatePeerAddress(getNeighborsMessage.challengerNonce, peerAddress);
            if (verified) {
                setAuthenticated(connection, peerAddress);
                purgeReportedNeighbors();
                SettableFuture<Connection> future = networkNode.sendMessage(peerAddress,
                        new NeighborsMessage(getAddress(), getAllNeighborAddresses()));
                log.trace("sent NeighborsMessage to " + peerAddress + " from " + getAddress()
                        + " with allNeighbors=" + getAllNeighborAddresses());
                Futures.addCallback(future, new FutureCallback<Connection>() {
                    @Override
                    public void onSuccess(Connection connection) {
                        log.trace("NeighborsMessage sent successfully from " + getAddress() + " to " + peerAddress);
                    }

                    @Override
                    public void onFailure(@NotNull Throwable throwable) {
                        log.info("NeighborsMessage sending failed " + throwable.getMessage());
                        removeNeighbor(peerAddress);
                    }
                });

                // now we add the reported neighbors to our own set
                ArrayList<Address> neighborAddresses = ((GetNeighborsMessage) message).neighborAddresses;
                log.trace("Received neighbors: " + neighborAddresses);
                // remove ourselves
                addToReportedNeighbors(neighborAddresses, connection);
            }
        } else if (message instanceof NeighborsMessage) {
            NeighborsMessage neighborsMessage = (NeighborsMessage) message;
            Address peerAddress = neighborsMessage.address;
            log.trace("NeighborsMessage from " + peerAddress + " at " + getAddress());
            ArrayList<Address> neighborAddresses = neighborsMessage.neighborAddresses;
            log.trace("Received neighbors: " + neighborAddresses);
            // remove ourselves
            addToReportedNeighbors(neighborAddresses, connection);

            // we wait until the handshake is completed before setting the authenticate flag
            // authentication at both sides of the connection

            log.info("\n\nAuthenticationComplete\nPeer with address " + peerAddress
                    + " authenticated (" + connection.getObjectId() + "). Took "
                    + (System.currentTimeMillis() - startAuthTs) + " ms. \n\n");

            setAuthenticated(connection, peerAddress);

            Runnable authenticationCompleteHandler = authenticationCompleteHandlers.remove(connection.getPeerAddress());
            if (authenticationCompleteHandler != null)
                authenticationCompleteHandler.run();

            authenticateToNextRandomNeighbor();
        }
    }

    private void addToReportedNeighbors(ArrayList<Address> neighborAddresses, Connection connection) {
        log.trace("addToReportedNeighbors");
        // we disconnect misbehaving nodes trying to send too many neighbors
        // reported neighbors include the peers connected neighbors which is normally max. 8 but we give some headroom 
        // for safety
        if (neighborAddresses.size() > 1100) {
            connection.shutDown();
        } else {
            neighborAddresses.remove(getAddress());
            reportedNeighborAddresses.addAll(neighborAddresses);
            purgeReportedNeighbors();
        }
    }

    private void purgeReportedNeighbors() {
        log.trace("purgeReportedNeighbors");
        int all = getAllNeighborAddresses().size();
        if (all > 1000) {
            int diff = all - 100;
            List<Address> list = getNotConnectedNeighborAddresses();
            for (int i = 0; i < diff; i++) {
                Address neighborToRemove = list.remove(new Random().nextInt(list.size()));
                reportedNeighborAddresses.remove(neighborToRemove);
            }
        }
    }

    private List<Address> getNotConnectedNeighborAddresses() {
        ArrayList<Address> reportedNeighborsList = new ArrayList<>(getAllNeighborAddresses());
        log.debug("## getNotConnectedNeighborAddresses ");
        log.debug("##  reportedNeighborsList=" + reportedNeighborsList);
        connectedNeighbors.values().stream().forEach(e -> reportedNeighborsList.remove(e.address));
        log.debug("##  connectedNeighbors=" + connectedNeighbors);
        log.debug("##  reportedNeighborsList=" + reportedNeighborsList);
        return reportedNeighborsList;
    }

    private void authenticateToNextRandomNeighbor() {
        executorService.submit(() -> {
            try {
                Uninterruptibles.sleepUninterruptibly(new Random().nextInt(200) + 200, TimeUnit.MILLISECONDS);
                if (getConnectedNeighbors().size() <= MAX_CONNECTIONS) {
                    Address randomNotConnectedNeighborAddress = getRandomNotConnectedNeighborAddress();
                    if (randomNotConnectedNeighborAddress != null) {
                        log.info("We try to build an authenticated connection to a random neighbor. " + randomNotConnectedNeighborAddress);
                        authenticateToPeer(randomNotConnectedNeighborAddress, null, () -> authenticateToNextRandomNeighbor());
                    } else {
                        log.info("No more neighbors available for connecting.");
                    }
                } else {
                    log.info("We have already enough connections.");
                }
            } catch (Throwable t) {
                t.printStackTrace();
                log.error("Executing task failed. " + t.getMessage());
            }
        });
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
                removeNeighbor(address);
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

        connection.onAuthenticationComplete(peerAddress, connection);

        Neighbor neighbor = new Neighbor(connection);
        addConnectedNeighbor(peerAddress, neighbor);

        routingListeners.stream().forEach(e -> e.onConnectionAuthenticated(connection));

        log.debug("\n### setAuthenticated post connection " + connection);
    }

    private Address getRandomNotConnectedNeighborAddress() {
        List<Address> list = getNotConnectedNeighborAddresses();
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
        log.debug("Received routing message " + message + " at " + getAddress() + " from " + connection.getPeerAddress());
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
                    removeNeighbor(connection.getPeerAddress());
                }
            });
        } else if (message instanceof PongMessage) {
            Neighbor neighbor = connectedNeighbors.get(connection.getPeerAddress());
            if (neighbor != null) {
                if (((PongMessage) message).nonce != neighbor.getPingNonce()) {
                    removeNeighbor(neighbor.address);
                    log.warn("PongMessage invalid: self/peer " + getAddress() + "/" + connection.getPeerAddress());
                }
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Neighbors
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void removeNeighbor(@Nullable Address peerAddress) {
        reportedNeighborAddresses.remove(peerAddress);

        Neighbor disconnectedNeighbor;
        disconnectedNeighbor = connectedNeighbors.remove(peerAddress);

        if (disconnectedNeighbor != null)
            UserThread.execute(() -> routingListeners.stream().forEach(e -> e.onNeighborRemoved(peerAddress)));

        log.trace("removeNeighbor nonceMap=" + nonceMap + " / peerAddress=" + peerAddress);
        nonceMap.remove(peerAddress);
    }

    private void addConnectedNeighbor(Address address, Neighbor neighbor) {
        boolean firstNeighborAdded;
        connectedNeighbors.put(address, neighbor);
        firstNeighborAdded = connectedNeighbors.size() == 1;

        UserThread.execute(() -> routingListeners.stream().forEach(e -> e.onNeighborAdded(neighbor)));

        if (firstNeighborAdded)
            UserThread.execute(() -> routingListeners.stream().forEach(e -> e.onFirstNeighborAdded(neighbor)));

        if (connectedNeighbors.size() > MAX_CONNECTIONS)
            disconnectOldConnections();
    }

    private Address getAddress() {
        return networkNode.getAddress();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void printConnectedNeighborsMap() {
        StringBuilder result = new StringBuilder("\nConnected neighbors for node " + getAddress() + ":");
        connectedNeighbors.values().stream().forEach(e -> {
            result.append("\n\t" + e.address);
        });
        result.append("\n");
        log.info(result.toString());
    }

    public void printReportedNeighborsMap() {
        StringBuilder result = new StringBuilder("\nReported neighborAddresses for node " + getAddress() + ":");
        reportedNeighborAddresses.stream().forEach(e -> {
            result.append("\n\t" + e);
        });
        result.append("\n");
        log.info(result.toString());
    }

    private String getObjectId() {
        return super.toString().split("@")[1].toString();
    }

}
