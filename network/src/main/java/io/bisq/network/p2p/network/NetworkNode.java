package io.bisq.network.p2p.network;

import com.google.common.util.concurrent.*;
import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.common.proto.network.NetworkProtoResolver;
import io.bisq.common.util.Utilities;
import io.bisq.network.p2p.NodeAddress;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

// Run in UserThread
public abstract class NetworkNode implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(NetworkNode.class);
    private static final int CREATE_SOCKET_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(120);

    final int servicePort;
    private final NetworkProtoResolver networkProtoResolver;

    private final CopyOnWriteArraySet<InboundConnection> inBoundConnections = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<MessageListener> messageListeners = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<ConnectionListener> connectionListeners = new CopyOnWriteArraySet<>();
    final CopyOnWriteArraySet<SetupListener> setupListeners = new CopyOnWriteArraySet<>();
    ListeningExecutorService executorService;
    private Server server;

    private volatile boolean shutDownInProgress;
    // accessed from different threads
    private final CopyOnWriteArraySet<OutboundConnection> outBoundConnections = new CopyOnWriteArraySet<>();
    protected final ObjectProperty<NodeAddress> nodeAddressProperty = new SimpleObjectProperty<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    NetworkNode(int servicePort, NetworkProtoResolver networkProtoResolver) {
        this.servicePort = servicePort;
        this.networkProtoResolver = networkProtoResolver;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Calls this (and other registered) setup listener's ``onTorNodeReady()`` and ``onHiddenServicePublished``
    // when the events happen.
    abstract public void start(@Nullable SetupListener setupListener);

    public SettableFuture<Connection> sendMessage(@NotNull NodeAddress peersNodeAddress, NetworkEnvelope networkEnvelop) {
        Log.traceCall("peersNodeAddress=" + peersNodeAddress + "\n\tmessage=" + Utilities.toTruncatedString(networkEnvelop));
        checkNotNull(peersNodeAddress, "peerAddress must not be null");

        Connection connection = getOutboundConnection(peersNodeAddress);
        if (connection == null)
            connection = getInboundConnection(peersNodeAddress);

        if (connection != null) {
            return sendMessage(connection, networkEnvelop);
        } else {
            log.debug("We have not found any connection for peerAddress {}.\n\t" +
                    "We will create a new outbound connection.", peersNodeAddress);

            final SettableFuture<Connection> resultFuture = SettableFuture.create();
            ListenableFuture<Connection> future = executorService.submit(() -> {
                Thread.currentThread().setName("NetworkNode:SendMessage-to-" + peersNodeAddress);
                OutboundConnection outboundConnection = null;
                try {
                    // can take a while when using tor
                    long startTs = System.currentTimeMillis();
                    log.debug("Start create socket to peersNodeAddress {}", peersNodeAddress.getFullAddress());
                    Socket socket = createSocket(peersNodeAddress);
                    long duration = System.currentTimeMillis() - startTs;
                    log.debug("Socket creation to peersNodeAddress {} took {} ms", peersNodeAddress.getFullAddress(),
                            duration);

                    if (duration > CREATE_SOCKET_TIMEOUT)
                        throw new TimeoutException("A timeout occurred when creating a socket.");

                    // Tor needs sometimes quite long to create a connection. To avoid that we get too many double
                    // sided connections we check again if we still don't have any connection for that node address.
                    Connection existingConnection = getInboundConnection(peersNodeAddress);
                    if (existingConnection == null)
                        existingConnection = getOutboundConnection(peersNodeAddress);

                    if (existingConnection != null) {
                        log.debug("We found in the meantime a connection for peersNodeAddress {}, " +
                                        "so we use that for sending the message.\n" +
                                        "That can happen if Tor needs long for creating a new outbound connection.\n" +
                                        "We might have got a new inbound or outbound connection.",
                                peersNodeAddress.getFullAddress());
                        try {
                            socket.close();
                        } catch (Throwable throwable) {
                            log.error("Error at closing socket " + throwable);
                        }
                        existingConnection.sendMessage(networkEnvelop);
                        return existingConnection;
                    } else {
                        final ConnectionListener connectionListener = new ConnectionListener() {
                            @Override
                            public void onConnection(Connection connection) {
                                if (!connection.isStopped()) {
                                    outBoundConnections.add((OutboundConnection) connection);
                                    printOutBoundConnections();
                                    connectionListeners.stream().forEach(e -> e.onConnection(connection));
                                }
                            }

                            @Override
                            public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
                                log.trace("onDisconnect connectionListener\n\tconnection={}" + connection);
                                //noinspection SuspiciousMethodCalls
                                outBoundConnections.remove(connection);
                                printOutBoundConnections();
                                connectionListeners.stream().forEach(e -> e.onDisconnect(closeConnectionReason, connection));
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                log.error("new OutboundConnection.ConnectionListener.onError " + throwable.getMessage());
                                connectionListeners.stream().forEach(e -> e.onError(throwable));
                            }
                        };
                        outboundConnection = new OutboundConnection(socket,
                                NetworkNode.this,
                                connectionListener,
                                peersNodeAddress,
                                networkProtoResolver);

                        log.debug("\n\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n" +
                                "NetworkNode created new outbound connection:"
                                + "\nmyNodeAddress=" + getNodeAddress()
                                + "\npeersNodeAddress=" + peersNodeAddress
                                + "\nuid=" + outboundConnection.getUid()
                                + "\nmessage=" + networkEnvelop
                                + "\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n");

                        // can take a while when using tor
                        outboundConnection.sendMessage(networkEnvelop);
                        return outboundConnection;
                    }
                } catch (Throwable throwable) {
                    if (!(throwable instanceof ConnectException ||
                            throwable instanceof IOException ||
                            throwable instanceof TimeoutException)) {
                        log.warn("Executing task failed. " + throwable.getMessage());
                    }
                    throw throwable;
                }
            });

            Futures.addCallback(future, new FutureCallback<Connection>() {
                public void onSuccess(Connection connection) {
                    UserThread.execute(() -> resultFuture.set(connection));
                }

                public void onFailure(@NotNull Throwable throwable) {
                    UserThread.execute(() -> resultFuture.setException(throwable));
                }
            });

            return resultFuture;
        }
    }

    @Nullable
    private InboundConnection getInboundConnection(@NotNull NodeAddress peersNodeAddress) {
        Optional<InboundConnection> inboundConnectionOptional = lookupInBoundConnection(peersNodeAddress);
        if (inboundConnectionOptional.isPresent()) {
            InboundConnection connection = inboundConnectionOptional.get();
            log.trace("We have found a connection in inBoundConnections. Connection.uid=" + connection.getUid());
            if (connection.isStopped()) {
                log.warn("We have a connection which is already stopped in inBoundConnections. Connection.uid=" + connection.getUid());
                inBoundConnections.remove(connection);
                return null;
            } else {
                return connection;
            }
        } else {
            return null;
        }
    }

    @Nullable
    private OutboundConnection getOutboundConnection(@NotNull NodeAddress peersNodeAddress) {
        Optional<OutboundConnection> outboundConnectionOptional = lookupOutBoundConnection(peersNodeAddress);
        if (outboundConnectionOptional.isPresent()) {
            OutboundConnection connection = outboundConnectionOptional.get();
            log.trace("We have found a connection in outBoundConnections. Connection.uid=" + connection.getUid());
            if (connection.isStopped()) {
                log.warn("We have a connection which is already stopped in outBoundConnections. Connection.uid=" + connection.getUid());
                outBoundConnections.remove(connection);
                return null;
            } else {
                return connection;
            }
        } else {
            return null;
        }
    }

    @Nullable
    public Socks5Proxy getSocksProxy() {
        return null;
    }


    public SettableFuture<Connection> sendMessage(Connection connection, NetworkEnvelope networkEnvelop) {
        Log.traceCall("\n\tmessage=" + Utilities.toTruncatedString(networkEnvelop) + "\n\tconnection=" + connection);
        // connection.sendMessage might take a bit (compression, write to stream), so we use a thread to not block
        ListenableFuture<Connection> future = executorService.submit(() -> {
            Thread.currentThread().setName("NetworkNode:SendMessage-to-" + connection.getUid());
            connection.sendMessage(networkEnvelop);
            return connection;
        });
        final SettableFuture<Connection> resultFuture = SettableFuture.create();
        Futures.addCallback(future, new FutureCallback<Connection>() {
            public void onSuccess(Connection connection) {
                UserThread.execute(() -> resultFuture.set(connection));
            }

            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> resultFuture.setException(throwable));
            }
        });
        return resultFuture;
    }

    public ReadOnlyObjectProperty<NodeAddress> nodeAddressProperty() {
        return nodeAddressProperty;
    }

    public Set<Connection> getAllConnections() {
        // Can contain inbound and outbound connections with the same peer node address,
        // as connection hashcode is using uid and port info
        Set<Connection> set = new HashSet<>(inBoundConnections);
        set.addAll(outBoundConnections);
        return set;
    }

    public Set<Connection> getConfirmedConnections() {
        // Can contain inbound and outbound connections with the same peer node address,
        // as connection hashcode is using uid and port info
        return getAllConnections().stream()
                .filter(Connection::hasPeersNodeAddress)
                .collect(Collectors.toSet());
    }

    public Set<NodeAddress> getNodeAddressesOfConfirmedConnections() {
        // Does not contain inbound and outbound connection with the same peer node address
        return getConfirmedConnections().stream()
                .map(e -> e.getPeersNodeAddressOptional().get())
                .collect(Collectors.toSet());
    }


    public void shutDown(Runnable shutDownCompleteHandler) {
        Log.traceCall();
        if (!shutDownInProgress) {
            shutDownInProgress = true;
            if (server != null) {
                server.shutDown();
                server = null;
            }

            getAllConnections().stream().forEach(c -> c.shutDown(CloseConnectionReason.APP_SHUT_DOWN));
            log.debug("NetworkNode shutdown complete");
        }
        if (shutDownCompleteHandler != null) shutDownCompleteHandler.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // SetupListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    void addSetupListener(SetupListener setupListener) {
        boolean isNewEntry = setupListeners.add(setupListener);
        if (!isNewEntry)
            log.warn("Try to add a setupListener which was already added.");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(NetworkEnvelope networkEnvelop, Connection connection) {
        messageListeners.stream().forEach(e -> e.onMessage(networkEnvelop, connection));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addConnectionListener(ConnectionListener connectionListener) {
        boolean isNewEntry = connectionListeners.add(connectionListener);
        if (!isNewEntry)
            log.warn("Try to add a connectionListener which was already added.\n\tconnectionListener={}\n\tconnectionListeners={}"
                    , connectionListener, connectionListeners);
    }

    public void removeConnectionListener(ConnectionListener connectionListener) {
        boolean contained = connectionListeners.remove(connectionListener);
        if (!contained)
            log.debug("Try to remove a connectionListener which was never added.\n\t" +
                    "That might happen because of async behaviour of CopyOnWriteArraySet");
    }

    public void addMessageListener(MessageListener messageListener) {
        boolean isNewEntry = messageListeners.add(messageListener);
        if (!isNewEntry)
            log.warn("Try to add a messageListener which was already added.");
    }

    public void removeMessageListener(MessageListener messageListener) {
        boolean contained = messageListeners.remove(messageListener);
        if (!contained)
            log.debug("Try to remove a messageListener which was never added.\n\t" +
                    "That might happen because of async behaviour of CopyOnWriteArraySet");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    void createExecutorService() {
        if (executorService == null)
            executorService = Utilities.getListeningExecutorService("NetworkNode-" + servicePort, 15, 30, 60);
    }

    void startServer(ServerSocket serverSocket) {
        final ConnectionListener connectionListener = new ConnectionListener() {
            @Override
            public void onConnection(Connection connection) {
                if (!connection.isStopped()) {
                    inBoundConnections.add((InboundConnection) connection);
                    printInboundConnections();
                    connectionListeners.stream().forEach(e -> e.onConnection(connection));
                }
            }

            @Override
            public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
                log.trace("onDisconnect at server socket connectionListener\n\tconnection={}" + connection);
                //noinspection SuspiciousMethodCalls
                inBoundConnections.remove(connection);
                printInboundConnections();
                connectionListeners.stream().forEach(e -> e.onDisconnect(closeConnectionReason, connection));
            }

            @Override
            public void onError(Throwable throwable) {
                log.error("server.ConnectionListener.onError " + throwable.getMessage());
                connectionListeners.stream().forEach(e -> e.onError(throwable));
            }
        };
        server = new Server(serverSocket,
                NetworkNode.this,
                connectionListener,
                networkProtoResolver);
        executorService.submit(server);
    }

    private Optional<OutboundConnection> lookupOutBoundConnection(NodeAddress peersNodeAddress) {
        log.trace("lookupOutboundConnection for peersNodeAddress={}", peersNodeAddress.getFullAddress());
        printOutBoundConnections();
        return outBoundConnections.stream()
                .filter(connection -> connection.hasPeersNodeAddress() &&
                        peersNodeAddress.equals(connection.getPeersNodeAddressOptional().get())).findAny();
    }

    private void printOutBoundConnections() {
        StringBuilder sb = new StringBuilder("outBoundConnections size()=")
                .append(outBoundConnections.size()).append("\n\toutBoundConnections=");
        outBoundConnections.stream().forEach(e -> sb.append(e).append("\n\t"));
        log.debug(sb.toString());
    }

    private Optional<InboundConnection> lookupInBoundConnection(NodeAddress peersNodeAddress) {
        log.trace("lookupInboundConnection for peersNodeAddress={}", peersNodeAddress.getFullAddress());
        printInboundConnections();
        return inBoundConnections.stream()
                .filter(connection -> connection.hasPeersNodeAddress() &&
                        peersNodeAddress.equals(connection.getPeersNodeAddressOptional().get())).findAny();
    }

    private void printInboundConnections() {
        StringBuilder sb = new StringBuilder("inBoundConnections size()=")
                .append(inBoundConnections.size()).append("\n\tinBoundConnections=");
        inBoundConnections.stream().forEach(e -> sb.append(e).append("\n\t"));
        log.debug(sb.toString());
    }

    abstract protected Socket createSocket(NodeAddress peersNodeAddress) throws IOException;

    @Nullable
    public NodeAddress getNodeAddress() {
        return nodeAddressProperty.get();
    }
}
