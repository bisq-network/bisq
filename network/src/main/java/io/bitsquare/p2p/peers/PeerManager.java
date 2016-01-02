package io.bitsquare.p2p.peers;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.network.*;
import io.bitsquare.p2p.peers.messages.auth.AuthenticationRejection;
import io.bitsquare.p2p.peers.messages.auth.AuthenticationRequest;
import io.bitsquare.p2p.storage.messages.DataBroadcastMessage;
import io.bitsquare.storage.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

public class PeerManager implements MessageListener, ConnectionListener {
    private static final Logger log = LoggerFactory.getLogger(PeerManager.class);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected static int MAX_CONNECTIONS_LOW_PRIORITY;
    private static int MAX_CONNECTIONS_NORMAL_PRIORITY;
    private static int MAX_CONNECTIONS_HIGH_PRIORITY;

    public static void setMaxConnectionsLowPriority(int maxConnectionsLowPriority) {
        MAX_CONNECTIONS_LOW_PRIORITY = maxConnectionsLowPriority;
        MAX_CONNECTIONS_NORMAL_PRIORITY = MAX_CONNECTIONS_LOW_PRIORITY + 6;
        MAX_CONNECTIONS_HIGH_PRIORITY = MAX_CONNECTIONS_NORMAL_PRIORITY + 6;
    }

    static {
        setMaxConnectionsLowPriority(10);
    }

    private static final int MAX_REPORTED_PEERS = 1000;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final NetworkNode networkNode;
    private final MaintenanceManager maintenanceManager;
    private final PeerExchangeManager peerExchangeManager;
    protected final ScheduledThreadPoolExecutor checkSeedNodeConnectionExecutor;
    private final Storage<HashSet<ReportedPeer>> dbStorage;

    private final CopyOnWriteArraySet<AuthenticationListener> authenticationListeners = new CopyOnWriteArraySet<>();
    protected final Map<Address, Peer> authenticatedPeers = new HashMap<>();
    private final HashSet<ReportedPeer> reportedPeers = new HashSet<>();
    private final HashSet<ReportedPeer> persistedPeers = new HashSet<>();
    protected final Map<Address, AuthenticationHandshake> authenticationHandshakes = new HashMap<>();
    protected final List<Address> remainingSeedNodes = new ArrayList<>();
    protected Optional<Set<Address>> seedNodeAddressesOptional = Optional.empty();
    protected Timer authenticateToRemainingSeedNodeTimer, authenticateToRemainingReportedPeerTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PeerManager(NetworkNode networkNode, File storageDir) {
        Log.traceCall();

        this.networkNode = networkNode;
        dbStorage = new Storage<>(storageDir);

        maintenanceManager = new MaintenanceManager(networkNode,
                () -> getAuthenticatedPeers(),
                address -> removePeer(address));
        peerExchangeManager = new PeerExchangeManager(networkNode,
                () -> getAuthenticatedAndReportedPeers(),
                () -> getAuthenticatedPeers(),
                address -> removePeer(address),
                (newReportedPeers, connection) -> addToReportedPeers(newReportedPeers, connection));

        checkSeedNodeConnectionExecutor = Utilities.getScheduledThreadPoolExecutor("checkSeedNodeConnection", 1, 10, 5);
        init();
    }

    private void init() {
        networkNode.addMessageListener(this);
        networkNode.addConnectionListener(this);

        HashSet<ReportedPeer> persistedPeers = dbStorage.initAndGetPersisted("persistedPeers");
        if (persistedPeers != null) {
            log.info("We have persisted reported peers. " +
                    "\npersistedPeers=" + persistedPeers);
            this.persistedPeers.addAll(persistedPeers);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // ConnectionListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnection(Connection connection) {
    }

    @Override
    public void onDisconnect(Reason reason, Connection connection) {
        log.debug("onDisconnect connection=" + connection + " / reason=" + reason);

        connection.getPeerAddressOptional().ifPresent(peerAddress -> {
            // We only remove the peer from the authenticationHandshakes and the reportedPeers 
            // if we are not in the authentication process
            // Connection shut down is an expected step in the authentication process.
            // If the disconnect happens on an authenticated peer we remove the peer.
            if (authenticatedPeers.containsKey(peerAddress) || !authenticationHandshakes.containsKey(peerAddress))
                removePeer(peerAddress);

            if (!authenticationHandshakes.containsKey(peerAddress)) {
                log.info("We got a disconnect. " +
                        "We will try again after a random pause to remaining reported peers.");
                if (authenticateToRemainingReportedPeerTimer == null)
                    authenticateToRemainingReportedPeerTimer = UserThread.runAfterRandomDelay(() -> authenticateToRemainingReportedPeer(),
                            10, 20, TimeUnit.SECONDS);
            }
        });
    }

    @Override
    public void onError(Throwable throwable) {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof AuthenticationRequest)
            processAuthenticationRequest((AuthenticationRequest) message, connection);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void broadcast(DataBroadcastMessage message, @Nullable Address sender) {
        Log.traceCall("Sender " + sender + ". Message " + message.toString());
        if (authenticatedPeers.values().size() > 0) {
            log.info("Broadcast message to {} peers. Message: {}", authenticatedPeers.values().size(), message);
            authenticatedPeers.values().stream()
                    .filter(e -> !e.address.equals(sender))
                    .forEach(peer -> {
                        if (authenticatedPeers.containsValue(peer)) {
                            final Address address = peer.address;
                            log.trace("Broadcast message from " + getMyAddress() + " to " + address + ".");
                            SettableFuture<Connection> future = networkNode.sendMessage(address, message);
                            Futures.addCallback(future, new FutureCallback<Connection>() {
                                @Override
                                public void onSuccess(Connection connection) {
                                    log.trace("Broadcast from " + getMyAddress() + " to " + address + " succeeded.");
                                }

                                @Override
                                public void onFailure(@NotNull Throwable throwable) {
                                    log.info("Broadcast failed. " + throwable.getMessage());
                                    UserThread.execute(() -> removePeer(address));
                                }
                            });
                        } else {
                            log.debug("Peer is not in our authenticated list anymore. " +
                                    "That can happen as we use a stream loop for the broadcast. " +
                                    "Peer.address={}", peer.address);
                        }
                    });
        } else {
            log.info("Message not broadcasted because we have no authenticated peers yet. " +
                    "message = {}", message);
        }
    }

    public void shutDown() {
        Log.traceCall();
        maintenanceManager.shutDown();
        peerExchangeManager.shutDown();

        networkNode.removeMessageListener(this);
        networkNode.removeConnectionListener(this);

        if (authenticateToRemainingReportedPeerTimer != null)
            authenticateToRemainingReportedPeerTimer.cancel();

        if (authenticateToRemainingSeedNodeTimer != null)
            authenticateToRemainingSeedNodeTimer.cancel();

        MoreExecutors.shutdownAndAwaitTermination(checkSeedNodeConnectionExecutor, 500, TimeUnit.MILLISECONDS);
    }

    public void addAuthenticationListener(AuthenticationListener listener) {
        authenticationListeners.add(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Process incoming authentication messages
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void processAuthenticationRequest(AuthenticationRequest message, final Connection connection) {
        Log.traceCall(message.toString());
        Address peerAddress = message.senderAddress;

        // We set the address to the connection, otherwise we will not find the connection when sending
        // a reject message and we would create a new outbound connection instead using the inbound.
        connection.setPeerAddress(message.senderAddress);

        if (!authenticatedPeers.containsKey(peerAddress)) {
            AuthenticationHandshake authenticationHandshake;
            if (!authenticationHandshakes.containsKey(peerAddress)) {
                log.info("We got an incoming AuthenticationRequest for the peerAddress {}. " +
                        "We create an AuthenticationHandshake.", peerAddress);
                // We protect that connection from getting closed by maintenance cleanup...
                connection.setConnectionPriority(ConnectionPriority.AUTH_REQUEST);
                authenticationHandshake = new AuthenticationHandshake(networkNode,
                        getMyAddress(),
                        peerAddress,
                        () -> getAuthenticatedAndReportedPeers(),
                        (newReportedPeers, connection1) -> addToReportedPeers(newReportedPeers, connection1)
                );
                authenticationHandshakes.put(peerAddress, authenticationHandshake);
                SettableFuture<Connection> future = authenticationHandshake.respondToAuthenticationRequest(message, connection);
                Futures.addCallback(future, new FutureCallback<Connection>() {
                            @Override
                            public void onSuccess(Connection connection) {
                                log.info("We got the peer ({}) who requested authentication authenticated.", peerAddress);
                                handleAuthenticationSuccess(connection, peerAddress);
                            }

                            @Override
                            public void onFailure(@NotNull Throwable throwable) {
                                log.info("Authentication with peer who requested authentication failed.\n" +
                                        "That can happen if the peer went offline. " + throwable.getMessage());
                                handleAuthenticationFailure(peerAddress, throwable);
                            }
                        }
                );
            } else {
                log.info("We got an incoming AuthenticationRequest but we have started ourselves already " +
                        "an authentication handshake for that peerAddress ({}).\n" +
                        "We terminate such race conditions by rejecting and cancelling the authentication on both " +
                        "peers.", peerAddress);

                rejectAuthenticationRequest(peerAddress);
                authenticationHandshakes.get(peerAddress).cancel(peerAddress);
                authenticationHandshakes.remove(peerAddress);
            }
        } else {
            log.info("We got an incoming AuthenticationRequest but we are already authenticated to peer {}.\n" +
                    "That should not happen. " +
                    "We reject the request.", peerAddress);
            rejectAuthenticationRequest(peerAddress);

            if (authenticationHandshakes.containsKey(peerAddress)) {
                authenticationHandshakes.get(peerAddress).cancel(peerAddress);
                authenticationHandshakes.remove(peerAddress);
            }
        }
    }

    private void rejectAuthenticationRequest(Address peerAddress) {
        Log.traceCall();
        networkNode.sendMessage(peerAddress, new AuthenticationRejection(getMyAddress()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Authentication to seed node
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setSeedNodeAddresses(Set<Address> seedNodeAddresses) {
        seedNodeAddressesOptional = Optional.of(seedNodeAddresses);
        checkArgument(!seedNodeAddressesOptional.get().isEmpty(),
                "seedNodeAddresses must not be empty");
    }

    public void authenticateToSeedNode(Address peerAddress) {
        Log.traceCall();

        checkArgument(seedNodeAddressesOptional.isPresent(),
                "seedNodeAddresses must be set before calling authenticateToSeedNode");
        remainingSeedNodes.remove(peerAddress);
        remainingSeedNodes.addAll(seedNodeAddressesOptional.get());
        authenticateToFirstSeedNode(peerAddress);

        startCheckSeedNodeConnectionTask();
    }

    protected void authenticateToFirstSeedNode(Address peerAddress) {
        Log.traceCall();
        if (!enoughConnections()) {
            if (!authenticationHandshakes.containsKey(peerAddress)) {
                log.info("We try to authenticate to seed node {}.", peerAddress);
                authenticate(peerAddress, new FutureCallback<Connection>() {
                    @Override
                    public void onSuccess(Connection connection) {
                        log.info("We got our first seed node authenticated. " +
                                "We try to authenticate to reported peers.");
                        handleAuthenticationSuccess(connection, peerAddress);
                        onFirstSeedNodeAuthenticated();
                    }

                    @Override
                    public void onFailure(@NotNull Throwable throwable) {
                        log.info("Authentication to " + peerAddress + " failed at authenticateToFirstSeedNode." +
                                "\nThat is expected if seed node is offline." +
                                "\nException:" + throwable.toString());
                        handleAuthenticationFailure(peerAddress, throwable);
                        Optional<Address> seedNodeOptional = getAndRemoveNotAuthenticatingSeedNode();
                        if (seedNodeOptional.isPresent()) {
                            log.info("We try another random seed node for authenticateToFirstSeedNode.");
                            authenticateToFirstSeedNode(seedNodeOptional.get());
                        } else {
                            log.info("There are no seed nodes available for authentication. " +
                                    "We try to authenticate to reported peers.");
                            authenticateToRemainingReportedPeer();
                        }
                    }
                });
            } else {
                log.info("We have already an open authenticationHandshakes for the first seed node. " +
                        "That can happen when we received an AuthenticationRequest before we start authenticating. " +
                        "We will try to authenticate to another seed node.");
                authenticateToRemainingSeedNode();
            }
        } else {
            log.info("We have already enough connections (at authenticateToFirstSeedNode). " +
                    "That is very unlikely to happen but can be a theoretical case.");
        }
    }

    protected void onFirstSeedNodeAuthenticated() {
        authenticateToRemainingReportedPeer();
    }

    protected void authenticateToRemainingSeedNode() {
        Log.traceCall();
        if (authenticateToRemainingSeedNodeTimer != null) {
            authenticateToRemainingSeedNodeTimer.cancel();
            authenticateToRemainingSeedNodeTimer = null;
        }

        if (!enoughConnections()) {
            Optional<Address> seedNodeOptional = getAndRemoveNotAuthenticatingSeedNode();
            if (seedNodeOptional.isPresent()) {
                Address peerAddress = seedNodeOptional.get();
                if (!authenticationHandshakes.containsKey(peerAddress)) {
                    log.info("We try to authenticate to a randomly selected seed node {}.", peerAddress);
                    authenticate(peerAddress, new FutureCallback<Connection>() {
                                @Override
                                public void onSuccess(Connection connection) {
                                    log.info("We got a seed node authenticated. " +
                                            "We try to authenticate to reported peers.");

                                    handleAuthenticationSuccess(connection, peerAddress);
                                    onRemainingSeedNodeAuthenticated();
                                }

                                @Override
                                public void onFailure(@NotNull Throwable throwable) {
                                    log.info("Authentication to " + peerAddress + " failed at authenticateToRemainingSeedNode." +
                                            "\nThat is expected if the seed node is offline." +
                                            "\nException:" + throwable.toString());

                                    handleAuthenticationFailure(peerAddress, throwable);

                                    log.info("We try authenticateToRemainingSeedNode again.");
                                    authenticateToRemainingSeedNode();
                                }
                            }
                    );
                } else {
                    log.info("We have already an open authenticationHandshakes for the selected seed node. " +
                            "That can happen in race conditions when we received an AuthenticationRequest before " +
                            "we start authenticating. " +
                            "We will try to authenticate to another seed node.");
                    authenticateToRemainingSeedNode();
                }
            } else {
                handleNoSeedNodesAvailableCase();
            }
        } else {
            log.info("We have already enough connections (at authenticateToRemainingSeedNode).");
        }
    }

    protected void onRemainingSeedNodeAuthenticated() {
        authenticateToRemainingReportedPeer();
    }

    protected void handleNoSeedNodesAvailableCase() {
        Log.traceCall();
        if (reportedPeersAvailable()) {
            authenticateToRemainingReportedPeer();
        } else {
            log.info("We don't have seed nodes or reported peers available. " +
                    "We try again after a random pause with the seed nodes which failed or if " +
                    "none available with the reported peers.");
            checkArgument(seedNodeAddressesOptional.isPresent(), "seedNodeAddresses must be present");
            resetRemainingSeedNodes();
            if (remainingSeedNodesAvailable()) {
                if (authenticateToRemainingSeedNodeTimer == null)
                    authenticateToRemainingSeedNodeTimer = UserThread.runAfterRandomDelay(() -> authenticateToRemainingSeedNode(),
                            10, 20, TimeUnit.SECONDS);
            } else {
                if (authenticateToRemainingReportedPeerTimer == null)
                    authenticateToRemainingReportedPeerTimer = UserThread.runAfterRandomDelay(() -> authenticateToRemainingReportedPeer(),
                            10, 20, TimeUnit.SECONDS);
            }
        }
    }

    protected void resetRemainingSeedNodes() {
        Log.traceCall();
        if (seedNodeAddressesOptional.isPresent()) {
            remainingSeedNodes.clear();
            seedNodeAddressesOptional.get().stream()
                    .filter(e -> !authenticatedPeers.containsKey(e) && !authenticationHandshakes.containsKey(e))
                    .forEach(e -> remainingSeedNodes.add(e));
        } else {
            log.error("seedNodeAddressesOptional must be present");
        }
    }

    protected void startCheckSeedNodeConnectionTask() {
        checkSeedNodeConnectionExecutor.schedule(() -> UserThread.execute(()
                -> checkSeedNodeConnections()), 2, TimeUnit.MINUTES);
    }

    // We want to stay connected to at least one seed node to avoid to get isolated with a group of peers
    // Also needed for cases when all seed nodes get restarted, so peers will connect to the seed nodes again from time 
    // to time and so keep the network connected.
    protected void checkSeedNodeConnections() {
        Log.traceCall();
        resetRemainingSeedNodes();
        if (!remainingSeedNodes.isEmpty()) {
            log.info("We have remaining not connected seed node(s) available. " +
                    "We will call authenticateToRemainingSeedNode.");
            // remove enough connections to be sure the authentication will succeed. I t might be that in the meantime 
            // we get other connection attempts, so remove 2 more than needed to have a bit of headroom.
            checkIfConnectedPeersExceeds(MAX_CONNECTIONS_LOW_PRIORITY - remainingSeedNodes.size() - 2);

            if (authenticateToRemainingSeedNodeTimer == null)
                authenticateToRemainingSeedNodeTimer = UserThread.runAfter(() -> authenticateToRemainingSeedNode(),
                        500, TimeUnit.MILLISECONDS);
        } else {
            log.debug("There are no remainingSeedNodes available.");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Authentication to reported peers
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void authenticateToRemainingReportedPeer() {
        Log.traceCall();

        if (authenticateToRemainingReportedPeerTimer != null) {
            authenticateToRemainingReportedPeerTimer.cancel();
            authenticateToRemainingReportedPeerTimer = null;
        }

        if (!enoughConnections()) {
            if (reportedPeersAvailable()) {
                Optional<ReportedPeer> reportedPeer = getAndRemoveNotAuthenticatingReportedPeer();
                if (reportedPeer.isPresent()) {
                    Address peerAddress = reportedPeer.get().address;
                    if (!authenticationHandshakes.containsKey(peerAddress)) {
                        log.info("We try to authenticate to peer {}.", peerAddress);
                        authenticate(peerAddress, new FutureCallback<Connection>() {
                            @Override
                            public void onSuccess(Connection connection) {
                                log.info("We got a peer authenticated. " +
                                        "We try if there are more reported peers available to authenticate.");

                                handleAuthenticationSuccess(connection, peerAddress);
                                authenticateToRemainingReportedPeer();
                            }

                            @Override
                            public void onFailure(@NotNull Throwable throwable) {
                                log.info("Authentication to " + peerAddress + " failed at authenticateToRemainingReportedPeer." +
                                        "\nThat is expected if the peer is offline." +
                                        "\nException:" + throwable.toString());

                                handleAuthenticationFailure(peerAddress, throwable);

                                log.info("We try another random seed node for authentication.");
                                authenticateToRemainingReportedPeer();
                            }
                        });
                    } else {
                        log.warn("We got the selected peer in the authenticationHandshakes. " +
                                "That should not happen. We will try again after a random pause.");
                        if (authenticateToRemainingReportedPeerTimer == null)
                            authenticateToRemainingReportedPeerTimer = UserThread.runAfterRandomDelay(() -> authenticateToRemainingReportedPeer(),
                                    10, 20, TimeUnit.SECONDS);
                    }
                } else {
                    log.info("We don't have a reported peers available. " +
                            "That should not happen. We will try again after a random pause.");
                    if (authenticateToRemainingReportedPeerTimer == null)
                        authenticateToRemainingReportedPeerTimer = UserThread.runAfterRandomDelay(() -> authenticateToRemainingReportedPeer(),
                                10, 20, TimeUnit.SECONDS);
                }
            } else if (remainingSeedNodesAvailable()) {
                authenticateToRemainingSeedNode();
            } else if (!persistedPeers.isEmpty()) {
                log.info("We don't have seed nodes or reported peers available. " +
                        "We will add 5 peers from our persistedReportedPeers to our reportedPeers list and " +
                        "try authenticateToRemainingReportedPeer again.");

                List<ReportedPeer> list = new ArrayList<>(persistedPeers);
                authenticationHandshakes.keySet().stream().forEach(e -> list.remove(new ReportedPeer(e)));
                authenticatedPeers.keySet().stream().forEach(e -> list.remove(new ReportedPeer(e)));
                if (!list.isEmpty()) {
                    int toRemove = Math.min(list.size(), 5);
                    for (int i = 0; i < toRemove; i++) {
                        ReportedPeer reportedPeer = list.get(i);
                        persistedPeers.remove(reportedPeer);
                        reportedPeers.add(reportedPeer);
                    }
                    authenticateToRemainingReportedPeer();
                }
            } else {
                log.info("We don't have seed nodes, reported peers nor persistedReportedPeers available. " +
                        "We will reset the seed nodes and try authenticateToRemainingSeedNode again after a random pause.");
                resetRemainingSeedNodes();
                if (authenticateToRemainingSeedNodeTimer == null)
                    authenticateToRemainingSeedNodeTimer = UserThread.runAfterRandomDelay(() ->
                                    authenticateToRemainingSeedNode(),
                            10, 20, TimeUnit.SECONDS);
            }
        } else {
            log.info("We have already enough connections.");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Authentication to peer used for direct messaging
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Priority is set when we receive a decrypted mail message as those are used for direct messages
    public void authenticateToDirectMessagePeer(Address peerAddress,
                                                @Nullable Runnable completeHandler,
                                                @Nullable Runnable faultHandler) {
        Log.traceCall(peerAddress.getFullAddress());

        if (authenticatedPeers.containsKey(peerAddress)) {
            log.warn("We have that peer already authenticated. That should never happen. peerAddress={}", peerAddress);
            if (completeHandler != null)
                completeHandler.run();
        } else if (authenticationHandshakes.containsKey(peerAddress)) {
            log.info("We are in the process to authenticate to that peer. peerAddress={}", peerAddress);
            Optional<SettableFuture<Connection>> resultFutureOptional = authenticationHandshakes.get(peerAddress).getResultFutureOptional();
            if (resultFutureOptional.isPresent()) {
                Futures.addCallback(resultFutureOptional.get(), new FutureCallback<Connection>() {
                    @Override
                    public void onSuccess(Connection connection) {
                        if (completeHandler != null)
                            completeHandler.run();
                    }

                    @Override
                    public void onFailure(@NotNull Throwable throwable) {
                        if (faultHandler != null)
                            faultHandler.run();
                    }
                });
            } else {
                log.warn("We are in the process to authenticate to that peer but the future object is not set. " +
                        "That should not happen. peerAddress={}", peerAddress);
                if (faultHandler != null)
                    faultHandler.run();
            }
        } else {
            log.info("We try to authenticate to peer {} for sending a private message.", peerAddress);

            authenticate(peerAddress, new FutureCallback<Connection>() {
                @Override
                public void onSuccess(Connection connection) {
                    log.info("We got a new peer for sending a private message authenticated.");

                    handleAuthenticationSuccess(connection, peerAddress);
                    if (completeHandler != null)
                        completeHandler.run();
                }

                @Override
                public void onFailure(@NotNull Throwable throwable) {
                    log.error("Authentication to " + peerAddress + " for sending a private message failed at authenticateToDirectMessagePeer." +
                            "\nSeems that the peer is offline." +
                            "\nException:" + throwable.toString());
                    handleAuthenticationFailure(peerAddress, throwable);
                    if (faultHandler != null)
                        faultHandler.run();
                }
            });
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Authentication private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void authenticate(Address peerAddress, FutureCallback<Connection> futureCallback) {
        Log.traceCall(peerAddress.getFullAddress());
        checkArgument(!authenticationHandshakes.containsKey(peerAddress),
                "An authentication handshake is already created for that peerAddress (" + peerAddress + ")");
        log.info("We create an AuthenticationHandshake to authenticate to peer {}.", peerAddress);
        AuthenticationHandshake authenticationHandshake = new AuthenticationHandshake(networkNode,
                getMyAddress(),
                peerAddress,
                () -> getAuthenticatedAndReportedPeers(),
                (newReportedPeers, connection) -> addToReportedPeers(newReportedPeers, connection)
        );
        authenticationHandshakes.put(peerAddress, authenticationHandshake);
        SettableFuture<Connection> authenticationFuture = authenticationHandshake.requestAuthentication();
        Futures.addCallback(authenticationFuture, futureCallback);
    }

    private void handleAuthenticationSuccess(Connection connection, Address peerAddress) {
        Log.traceCall(peerAddress.getFullAddress());

        log.info("\n\n############################################################\n" +
                "We are authenticated to:" +
                "\nconnection=" + connection.getUid()
                + "\nmyAddress=" + getMyAddress()
                + "\npeerAddress= " + peerAddress
                + "\n############################################################\n");

        removeFromAuthenticationHandshakes(peerAddress);
        connection.setPeerAddress(peerAddress);
        connection.setAuthenticated();
        authenticatedPeers.put(peerAddress, new Peer(connection, peerAddress));
        removeFromReportedPeers(peerAddress);
        authenticationListeners.stream().forEach(e -> e.onPeerAuthenticated(peerAddress, connection));

        printAuthenticatedPeers();

        // We give a bit headroom to avoid dangling disconnect/connect
        checkIfConnectedPeersExceeds(MAX_CONNECTIONS_LOW_PRIORITY + 2);
    }

    void handleAuthenticationFailure(@Nullable Address peerAddress, Throwable throwable) {
        if (throwable instanceof AuthenticationException)
            removeFromAuthenticationHandshakes(peerAddress);
        else
            removePeer(peerAddress);
    }

    void removePeer(@Nullable Address peerAddress) {
        Log.traceCall("peerAddress=" + peerAddress);
        if (peerAddress != null) {
            removeFromAuthenticationHandshakes(peerAddress);
            removeFromReportedPeers(peerAddress);
            removeFromAuthenticatedPeers(peerAddress);
            removeFromPersistedPeers(peerAddress);
        }
    }

    private void removeFromReportedPeers(Address peerAddress) {
        reportedPeers.remove(new ReportedPeer(peerAddress));
    }

    private void removeFromAuthenticationHandshakes(Address peerAddress) {
        if (authenticationHandshakes.containsKey(peerAddress))
            authenticationHandshakes.remove(peerAddress);
    }

    private void removeFromAuthenticatedPeers(Address peerAddress) {
        if (authenticatedPeers.containsKey(peerAddress))
            authenticatedPeers.remove(peerAddress);
        printAuthenticatedPeers();
    }

    private void removeFromPersistedPeers(Address peerAddress) {
        ReportedPeer reportedPeer = new ReportedPeer(peerAddress);
        if (persistedPeers.contains(reportedPeer)) {
            persistedPeers.remove(reportedPeer);
            dbStorage.queueUpForSave(persistedPeers, 5000);
        }
    }

    private boolean enoughConnections() {
        return authenticatedPeers.size() >= MAX_CONNECTIONS_LOW_PRIORITY;
    }

    protected boolean reportedPeersAvailable() {
        List<ReportedPeer> list = new ArrayList<>(reportedPeers);
        authenticationHandshakes.keySet().stream().forEach(e -> list.remove(new ReportedPeer(e)));
        authenticatedPeers.keySet().stream().forEach(e -> list.remove(new ReportedPeer(e)));
        return !list.isEmpty();
    }

    private boolean remainingSeedNodesAvailable() {
        List<Address> list = new ArrayList<>(remainingSeedNodes);
        authenticationHandshakes.keySet().stream().forEach(e -> list.remove(e));
        authenticatedPeers.keySet().stream().forEach(e -> list.remove(e));
        return !list.isEmpty();
    }

    protected boolean checkIfConnectedPeersExceeds(int limit) {
        Log.traceCall();
        int size = authenticatedPeers.size();
        if (size > limit) {
            Set<Connection> allConnections = networkNode.getAllConnections();
            int allConnectionsSize = allConnections.size();
            log.info("We have {} connections open (authenticatedPeers={}). Lets remove the passive connections" +
                    " which have not been active recently.", allConnectionsSize, size);
            // TODO Investigate inconsistency which between size and allConnectionsSize sometimes.
          /*  if (size != allConnectionsSize) {
                log.warn("authenticatedPeers.size()!=allConnections.size(). There is some inconsistency.");
                log.debug("authenticatedPeers={}", authenticatedPeers);
                log.debug("networkNode.getAllConnections()={}", networkNode.getAllConnections());
            }*/

            // We don't remove seed nodes to keep the core network well connected
            List<Connection> authenticatedConnections = allConnections.stream()
                    .filter(e -> e.isAuthenticated())
                    .filter(e -> e.getConnectionPriority() == ConnectionPriority.PASSIVE)
                    .filter(e -> !isSeedNode(e))
                    .collect(Collectors.toList());

            if (authenticatedConnections.size() == 0) {
                log.debug("There are no passive connections for closing. We check if we are exceeding " +
                        "MAX_CONNECTIONS_NORMAL ({}) ", PeerManager.MAX_CONNECTIONS_NORMAL_PRIORITY);
                if (size > PeerManager.MAX_CONNECTIONS_NORMAL_PRIORITY) {
                    authenticatedConnections = allConnections.stream()
                            .filter(e -> e.isAuthenticated())
                            .filter(e -> e.getConnectionPriority() == ConnectionPriority.PASSIVE || e.getConnectionPriority() == ConnectionPriority.ACTIVE)
                            .filter(e -> !isSeedNode(e))
                            .collect(Collectors.toList());

                    if (authenticatedConnections.size() == 0) {
                        log.debug("There are no passive or active connections for closing. We check if we are exceeding " +
                                "MAX_CONNECTIONS_HIGH ({}) ", PeerManager.MAX_CONNECTIONS_HIGH_PRIORITY);
                        if (size > PeerManager.MAX_CONNECTIONS_HIGH_PRIORITY) {
                            authenticatedConnections = allConnections.stream()
                                    .filter(e -> e.isAuthenticated())
                                    .filter(e -> e.getConnectionPriority() != ConnectionPriority.AUTH_REQUEST)
                                    .filter(e -> !isSeedNode(e))
                                    .collect(Collectors.toList());
                        }
                    }
                }
            }

            if (authenticatedConnections.size() > 0) {
                authenticatedConnections.sort((o1, o2) -> o1.getLastActivityDate().compareTo(o2.getLastActivityDate()));
                log.info("Number of connections exceeding MAX_CONNECTIONS. Current size=" + authenticatedConnections.size());
                Connection connection = authenticatedConnections.remove(0);
                log.info("We are going to shut down the oldest connection with last activity date="
                        + connection.getLastActivityDate() + " / connection=" + connection);
                connection.shutDown(() -> checkIfConnectedPeersExceeds(limit));
                return true;
            } else {
                log.debug("authenticatedConnections.size() == 0. That might happen in rare cases. (checkIfConnectedPeersExceeds)");
                return false;
            }
        } else {
            log.trace("We only have {} connections open and don't need to close any.", size);
            return false;
        }
    }

    private boolean isSeedNode(Connection connection) {
        return connection.getPeerAddressOptional().isPresent()
                && seedNodeAddressesOptional.isPresent()
                && seedNodeAddressesOptional.get().contains(connection.getPeerAddressOptional().get());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Set<ReportedPeer> getAuthenticatedAndReportedPeers() {
        Set<ReportedPeer> all = new HashSet<>(reportedPeers);
        Set<ReportedPeer> authenticated = authenticatedPeers.values().stream()
                .filter(e -> e.address != null)
                .filter(e -> !seedNodeAddressesOptional.isPresent() || !seedNodeAddressesOptional.get().contains(e.address))
                .map(e -> new ReportedPeer(e.address, new Date()))
                .collect(Collectors.toSet());
        all.addAll(authenticated);
        return all;
    }

    public Map<Address, Peer> getAuthenticatedPeers() {
        return authenticatedPeers;
    }

    public HashSet<ReportedPeer> getPersistedPeers() {
        return persistedPeers;
    }

    public boolean isInAuthenticationProcess(Address address) {
        return authenticationHandshakes.containsKey(address);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Reported peers
    ///////////////////////////////////////////////////////////////////////////////////////////

    void addToReportedPeers(HashSet<ReportedPeer> reportedPeersToAdd, Connection connection) {
        Log.traceCall("reportedPeersToAdd = " + reportedPeersToAdd);
        // we disconnect misbehaving nodes trying to send too many peers
        // reported peers include the authenticated peers which is normally max. 8 but we give some headroom 
        // for safety
        if (reportedPeersToAdd.size() > (MAX_REPORTED_PEERS + MAX_CONNECTIONS_LOW_PRIORITY * 3)) {
            connection.shutDown();
        } else {
            // In case we have one of the peers already we adjust the lastActivityDate by adjusting the date to the mid 
            // of the lastActivityDate of our already stored peer and the reported one
            Map<Address, ReportedPeer> reportedPeersMap = reportedPeers.stream()
                    .collect(Collectors.toMap(e -> e.address, Function.identity()));
            Set<ReportedPeer> adjustedReportedPeers = new HashSet<>();
            reportedPeersToAdd.stream()
                    .filter(e -> !e.address.equals(getMyAddress()))
                    .filter(e -> !seedNodeAddressesOptional.isPresent() || !seedNodeAddressesOptional.get().contains(e.address))
                    .filter(e -> !authenticatedPeers.containsKey(e.address))
                    .forEach(e -> {
                        if (reportedPeersMap.containsKey(e.address)) {
                            long adjustedTime = (e.lastActivityDate.getTime() +
                                    reportedPeersMap.get(e.address).lastActivityDate.getTime()) / 2;
                            adjustedReportedPeers.add(new ReportedPeer(e.address,
                                    new Date(adjustedTime)));
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
            persistedPeers.addAll(adjustedReportedPeers);
            // We add also our authenticated and authenticating peers
            authenticatedPeers.keySet().forEach(e -> persistedPeers.add(new ReportedPeer(e, new Date())));
            authenticationHandshakes.keySet().forEach(e -> persistedPeers.add(new ReportedPeer(e, new Date())));

            int toRemove = persistedPeers.size() - 500;
            if (toRemove > 0) {
                List<ReportedPeer> list = new ArrayList<>(persistedPeers);
                list.sort((o1, o2) -> o1.lastActivityDate.compareTo(o2.lastActivityDate));
                for (int i = 0; i < toRemove; i++) {
                    persistedPeers.remove(list.get(i));
                }
            }
            dbStorage.queueUpForSave(persistedPeers);
        }

        printReportedPeers();
    }

    private void purgeReportedPeersIfExceeds() {
        Log.traceCall();
        int size = reportedPeers.size();
        if (size > MAX_REPORTED_PEERS) {
            log.trace("We have more then {} reported peers. size={}. " +
                    "We remove random peers from the reported peers list.", MAX_REPORTED_PEERS, size);
            int diff = size - MAX_REPORTED_PEERS;
            List<ReportedPeer> list = new ArrayList<>(reportedPeers);
            // we dont use sorting by lastActivityDate to avoid attack vectors and keep it more random
            //log.debug("Peers before sort " + list);
            //list.sort((a, b) -> a.lastActivityDate.compareTo(b.lastActivityDate));
            //log.debug("Peers after sort  " + list);
            for (int i = 0; i < diff; i++) {
                ReportedPeer toRemove = getAndRemoveRandomReportedPeer(list);
                reportedPeers.remove(toRemove);
            }
        } else {
            log.trace("No need to purge reported peers. We don't have more then {} reported peers yet.", MAX_REPORTED_PEERS);
        }
    }

    @Nullable
    Address getMyAddress() {
        return networkNode.getAddress();
    }

    private ReportedPeer getAndRemoveRandomReportedPeer(List<ReportedPeer> list) {
        checkArgument(!list.isEmpty(), "List must not be empty");
        return list.remove(new Random().nextInt(list.size()));
    }

    private Optional<ReportedPeer> getAndRemoveNotAuthenticatingReportedPeer() {
        List<ReportedPeer> list = new ArrayList<>(reportedPeers);
        authenticationHandshakes.keySet().stream().forEach(e -> list.remove(new ReportedPeer(e)));
        authenticatedPeers.keySet().stream().forEach(e -> list.remove(new ReportedPeer(e)));
        if (!list.isEmpty()) {
            ReportedPeer reportedPeer = getAndRemoveRandomReportedPeer(list);
            removeFromReportedPeers(reportedPeer.address);
            return Optional.of(reportedPeer);
        } else {
            return Optional.empty();
        }
    }

    protected Address getAndRemoveRandomAddress(List<Address> list) {
        checkArgument(!list.isEmpty(), "List must not be empty");
        return list.remove(new Random().nextInt(list.size()));
    }


    private Optional<Address> getAndRemoveNotAuthenticatingSeedNode() {
        authenticationHandshakes.keySet().stream().forEach(e -> remainingSeedNodes.remove(e));
        authenticatedPeers.keySet().stream().forEach(e -> remainingSeedNodes.remove(e));
        if (remainingSeedNodesAvailable())
            return Optional.of(getAndRemoveRandomAddress(remainingSeedNodes));
        else
            return Optional.empty();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void printAllPeers() {
        printAuthenticatedPeers();
        printReportedPeers();
    }

    private void printAuthenticatedPeers() {
        if (!authenticatedPeers.isEmpty()) {
            StringBuilder result = new StringBuilder("\n\n------------------------------------------------------------\n" +
                    "Authenticated peers for node " + getMyAddress() + ":");
            authenticatedPeers.values().stream().forEach(e -> result.append("\n").append(e.address));
            result.append("\n------------------------------------------------------------\n");
            log.info(result.toString());
        }
    }

    private void printReportedPeers() {
        if (!reportedPeers.isEmpty()) {
            StringBuilder result = new StringBuilder("\n\n------------------------------------------------------------\n" +
                    "Reported peers for node " + getMyAddress() + ":");
            reportedPeers.stream().forEach(e -> result.append("\n").append(e));
            result.append("\n------------------------------------------------------------\n");
            log.info(result.toString());
        }
    }
}
