package io.bitsquare.p2p.network;

import com.google.common.util.concurrent.*;
import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;
import org.apache.commons.lang3.StringUtils;
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
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

// Run in UserThread
public abstract class NetworkNode implements MessageListener, ConnectionListener {
    private static final Logger log = LoggerFactory.getLogger(NetworkNode.class);
    private static final int CREATE_SOCKET_TIMEOUT_MILLIS = 5000;

    final int servicePort;

    private final CopyOnWriteArraySet<InboundConnection> inBoundConnections = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<MessageListener> messageListeners = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<ConnectionListener> connectionListeners = new CopyOnWriteArraySet<>();
    final CopyOnWriteArraySet<SetupListener> setupListeners = new CopyOnWriteArraySet<>();
    ListeningExecutorService executorService;
    private Server server;

    private volatile boolean shutDownInProgress;
    // accessed from different threads
    private final CopyOnWriteArraySet<OutboundConnection> outBoundConnections = new CopyOnWriteArraySet<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    NetworkNode(int servicePort) {
        this.servicePort = servicePort;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    abstract public void start(@Nullable SetupListener setupListener);

    public SettableFuture<Connection> sendMessage(@NotNull NodeAddress peersNodeAddress, Message message) {
        Log.traceCall("peersNodeAddress=" + peersNodeAddress + "\n\tmessage=" + StringUtils.abbreviate(message.toString(), 100));
        checkNotNull(peersNodeAddress, "peerAddress must not be null");

        Connection connection = getOutboundConnection(peersNodeAddress);
        if (connection == null)
            connection = getInboundConnection(peersNodeAddress);

        if (connection != null) {
            return sendMessage(connection, message);
        } else {
            log.info("We have not found any connection for peerAddress {}.\n\t" +
                    "We will create a new outbound connection.", peersNodeAddress);

            final SettableFuture<Connection> resultFuture = SettableFuture.create();
            ListenableFuture<Connection> future = executorService.submit(() -> {
                Thread.currentThread().setName("NetworkNode:SendMessage-to-" + peersNodeAddress);
                OutboundConnection outboundConnection = null;
                try {
                    // can take a while when using tor
                    long startTs = System.currentTimeMillis();
                    log.info("Start create socket to peersNodeAddress {}", peersNodeAddress.getFullAddress());
                    Socket socket = createSocket(peersNodeAddress);
                    long duration = System.currentTimeMillis() - startTs;
                    log.info("Socket creation to peersNodeAddress {} took {} ms", peersNodeAddress.getFullAddress(),
                            duration);

                    if (duration > CREATE_SOCKET_TIMEOUT_MILLIS)
                        throw new TimeoutException("A timeout occurred when creating a socket.");

                    // Tor needs sometimes quite long to create a connection. To avoid that we get too many double 
                    // sided connections we check again if we still don't have an incoming connection.
                    Connection inboundConnection = getInboundConnection(peersNodeAddress);
                    if (inboundConnection != null) {
                        log.info("We found in the meantime an inbound connection for peersNodeAddress {}, " +
                                        "so we use that for sending the message.\n" +
                                        "That happens when Tor needs long for creating a new outbound connection.",
                                peersNodeAddress.getFullAddress());
                        try {
                            socket.close();
                        } catch (Throwable throwable) {
                            log.error("Error at closing socket " + throwable);
                        }
                        inboundConnection.sendMessage(message);
                        return inboundConnection;
                    } else {
                        outboundConnection = new OutboundConnection(socket, NetworkNode.this, NetworkNode.this, peersNodeAddress);
                        outBoundConnections.add(outboundConnection);

                        log.info("\n\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n" +
                                "NetworkNode created new outbound connection:"
                                + "\nmyNodeAddress=" + getNodeAddress()
                                + "\npeersNodeAddress=" + peersNodeAddress
                                + "\nuid=" + outboundConnection.getUid()
                                + "\nmessage=" + message
                                + "\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n");

                        // can take a while when using tor
                        outboundConnection.sendMessage(message);
                        return outboundConnection;
                    }
                } catch (Throwable throwable) {
                    if (!(throwable instanceof ConnectException || throwable instanceof IOException)) {
                        log.error("Executing task failed. " + throwable.getMessage());
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
        Optional<InboundConnection> inboundConnectionOptional = lookupInboundConnection(peersNodeAddress);
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
        Optional<OutboundConnection> outboundConnectionOptional = lookupOutboundConnection(peersNodeAddress);
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

    public SettableFuture<Connection> sendMessage(Connection connection, Message message) {
        Log.traceCall("\n\tmessage=" + StringUtils.abbreviate(message.toString(), 100) + "\n\tconnection=" + connection);
        // connection.sendMessage might take a bit (compression, write to stream), so we use a thread to not block
        ListenableFuture<Connection> future = executorService.submit(() -> {
            Thread.currentThread().setName("NetworkNode:SendMessage-to-" + connection.getUid());
            connection.sendMessage(message);
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
            log.info("NetworkNode shutdown complete");
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
    // ConnectionListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnection(Connection connection) {
        connectionListeners.stream().forEach(e -> e.onConnection(connection));
    }

    @Override
    public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
        outBoundConnections.remove(connection);
        // inbound connections are removed in the listener of the server
        connectionListeners.stream().forEach(e -> e.onDisconnect(closeConnectionReason, connection));
    }

    @Override
    public void onError(Throwable throwable) {
        connectionListeners.stream().forEach(e -> e.onError(throwable));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        messageListeners.stream().forEach(e -> e.onMessage(message, connection));
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
        executorService = Utilities.getListeningExecutorService("NetworkNode-" + servicePort, 20, 50, 120L);
    }

    void startServer(ServerSocket serverSocket) {
        ConnectionListener connectionListener = new ConnectionListener() {
            @Override
            public void onConnection(Connection connection) {
                inBoundConnections.add((InboundConnection) connection);
                NetworkNode.this.onConnection(connection);
            }

            @Override
            public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
                inBoundConnections.remove(connection);
                NetworkNode.this.onDisconnect(closeConnectionReason, connection);
            }

            @Override
            public void onError(Throwable throwable) {
                NetworkNode.this.onError(throwable);
            }
        };
        server = new Server(serverSocket,
                NetworkNode.this,
                connectionListener);
        executorService.submit(server);
    }

    private Optional<OutboundConnection> lookupOutboundConnection(NodeAddress peersNodeAddress) {
        StringBuilder sb = new StringBuilder("Lookup for peersNodeAddress=");
        sb.append(peersNodeAddress.toString()).append("/ outBoundConnections.size()=")
                .append(outBoundConnections.size()).append("/\n\toutBoundConnections=");
        outBoundConnections.stream().forEach(e -> sb.append(e).append("\n\t"));
        log.debug(sb.toString());
        return outBoundConnections.stream()
                .filter(connection -> connection.hasPeersNodeAddress() &&
                        peersNodeAddress.equals(connection.getPeersNodeAddressOptional().get())).findAny();
    }

    private Optional<InboundConnection> lookupInboundConnection(NodeAddress peersNodeAddress) {
        StringBuilder sb = new StringBuilder("Lookup for peersNodeAddress=");
        sb.append(peersNodeAddress.toString()).append("/ inBoundConnections.size()=")
                .append(inBoundConnections.size()).append("/\n\tinBoundConnections=");
        inBoundConnections.stream().forEach(e -> sb.append(e).append("\n\t"));
        log.debug(sb.toString());
        return inBoundConnections.stream()
                .filter(connection -> connection.hasPeersNodeAddress() &&
                        peersNodeAddress.equals(connection.getPeersNodeAddressOptional().get())).findAny();
    }

    abstract protected Socket createSocket(NodeAddress peersNodeAddress) throws IOException;

    @Nullable
    abstract public NodeAddress getNodeAddress();
}
