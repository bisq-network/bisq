package io.bitsquare.p2p.network;

import com.google.common.util.concurrent.*;
import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;
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

    private static final int CREATE_SOCKET_TIMEOUT = 10 * 1000;        // 10 sec.

    protected final int servicePort;

    private final CopyOnWriteArraySet<Connection> inBoundConnections = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<MessageListener> messageListeners = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<ConnectionListener> connectionListeners = new CopyOnWriteArraySet<>();
    protected final CopyOnWriteArraySet<SetupListener> setupListeners = new CopyOnWriteArraySet<>();
    protected ListeningExecutorService executorService;
    private Server server;

    private volatile boolean shutDownInProgress;
    // accessed from different threads
    private final CopyOnWriteArraySet<Connection> outBoundConnections = new CopyOnWriteArraySet<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public NetworkNode(int servicePort) {
        Log.traceCall();
        this.servicePort = servicePort;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        Log.traceCall();
        start(null);
    }

    abstract public void start(@Nullable SetupListener setupListener);

    public SettableFuture<Connection> sendMessage(@NotNull NodeAddress peersNodeAddress, Message message) {
        Log.traceCall("peerAddress: " + peersNodeAddress + " / message: " + message);
        checkNotNull(peersNodeAddress, "peerAddress must not be null");

        Optional<Connection> outboundConnectionOptional = lookupOutboundConnection(peersNodeAddress);
        Connection connection = outboundConnectionOptional.isPresent() ? outboundConnectionOptional.get() : null;
        if (connection != null)
            log.trace("We have found a connection in outBoundConnections. Connection.uid=" + connection.getUid());

        if (connection != null && connection.isStopped()) {
            log.trace("We have a connection which is already stopped in outBoundConnections. Connection.uid=" + connection.getUid());
            outBoundConnections.remove(connection);
            connection = null;
        }

        if (connection == null) {
            Optional<Connection> inboundConnectionOptional = lookupInboundConnection(peersNodeAddress);
            if (inboundConnectionOptional.isPresent()) connection = inboundConnectionOptional.get();
            if (connection != null)
                log.trace("We have found a connection in inBoundConnections. Connection.uid=" + connection.getUid());
        }

        if (connection != null) {
            return sendMessage(connection, message);
        } else {
            log.trace("We have not found any connection for peerAddress {}. " +
                    "We will create a new outbound connection.", peersNodeAddress);

            final SettableFuture<Connection> resultFuture = SettableFuture.create();
            ListenableFuture<Connection> future = executorService.submit(() -> {
                Thread.currentThread().setName("NetworkNode:SendMessage-to-" + peersNodeAddress);
                OutboundConnection outboundConnection = null;
                try {
                    // can take a while when using tor
                    Socket socket = createSocket(peersNodeAddress);
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
                } catch (Throwable throwable) {
                    if (!(throwable instanceof ConnectException || throwable instanceof IOException || throwable instanceof TimeoutException)) {
                        throwable.printStackTrace();
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

    public SettableFuture<Connection> sendMessage(Connection connection, Message message) {
        Log.traceCall("message: " + message + " to connection: " + connection);
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
        log.info("Shutdown NetworkNode");
        if (!shutDownInProgress) {
            shutDownInProgress = true;
            if (server != null) {
                server.shutDown();
                server = null;
            }

            getAllConnections().stream().forEach(Connection::shutDown);

            log.info("NetworkNode shutdown complete");
            if (shutDownCompleteHandler != null) shutDownCompleteHandler.run();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // SetupListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addSetupListener(SetupListener setupListener) {
        Log.traceCall();
        boolean isNewEntry = setupListeners.add(setupListener);
        if (!isNewEntry)
            log.warn("Try to add a setupListener which was already added.");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ConnectionListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnection(Connection connection) {
        Log.traceCall("connection=" + connection);
        connectionListeners.stream().forEach(e -> e.onConnection(connection));
    }

    @Override
    public void onDisconnect(Reason reason, Connection connection) {
        Log.traceCall("connection = " + connection);
        outBoundConnections.remove(connection);
        inBoundConnections.remove(connection);
        connectionListeners.stream().forEach(e -> e.onDisconnect(reason, connection));
    }

    @Override
    public void onError(Throwable throwable) {
        Log.traceCall();
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
        Log.traceCall();

        boolean isNewEntry = connectionListeners.add(connectionListener);
        if (!isNewEntry)
            log.warn("Try to add a connectionListener which was already added.\nconnectionListener={}\nconnectionListeners={}"
                    , connectionListener, connectionListeners);
    }

    public void removeConnectionListener(ConnectionListener connectionListener) {
        Log.traceCall();
        boolean contained = connectionListeners.remove(connectionListener);
        if (!contained)
            log.debug("Try to remove a connectionListener which was never added. " +
                    "That might happen because of async behaviour of CopyOnWriteArraySet");
    }

    public void addMessageListener(MessageListener messageListener) {
        Log.traceCall();
        boolean isNewEntry = messageListeners.add(messageListener);
        if (!isNewEntry)
            log.warn("Try to add a messageListener which was already added.");
    }

    public void removeMessageListener(MessageListener messageListener) {
        Log.traceCall();
        boolean contained = messageListeners.remove(messageListener);
        if (!contained)
            log.debug("Try to remove a messageListener which was never added. " +
                    "That might happen because of async behaviour of CopyOnWriteArraySet");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void createExecutorService() {
        Log.traceCall();
        executorService = Utilities.getListeningExecutorService("NetworkNode-" + servicePort, 20, 50, 120L);
    }

    protected void startServer(ServerSocket serverSocket) {
        Log.traceCall();
        ConnectionListener startServerConnectionListener = new ConnectionListener() {
            @Override
            public void onConnection(Connection connection) {
                Log.traceCall("startServerConnectionListener connection=" + connection);
                inBoundConnections.add(connection);
                NetworkNode.this.onConnection(connection);
            }

            @Override
            public void onDisconnect(Reason reason, Connection connection) {
                Log.traceCall("onDisconnect at incoming connection = " + connection);
                inBoundConnections.remove(connection);
                NetworkNode.this.onDisconnect(reason, connection);
            }

            @Override
            public void onError(Throwable throwable) {
                Log.traceCall();
                NetworkNode.this.onError(throwable);
            }
        };
        server = new Server(serverSocket,
                NetworkNode.this,
                startServerConnectionListener);
        executorService.submit(server);
    }

    private Optional<Connection> lookupOutboundConnection(NodeAddress peersNodeAddress) {
        Log.traceCall("search for " + peersNodeAddress.toString() + " / outBoundConnections " + outBoundConnections);
        return outBoundConnections.stream()
                .filter(e -> e.getPeersNodeAddressOptional().isPresent() && peersNodeAddress.equals(e.getPeersNodeAddressOptional().get())).findAny();
    }

    private Optional<Connection> lookupInboundConnection(NodeAddress peersNodeAddress) {
        Log.traceCall("search for " + peersNodeAddress.toString() + " / inBoundConnections " + inBoundConnections);
        return inBoundConnections.stream()
                .filter(e -> e.getPeersNodeAddressOptional().isPresent() && peersNodeAddress.equals(e.getPeersNodeAddressOptional().get())).findAny();
    }

    abstract protected Socket createSocket(NodeAddress peersNodeAddress) throws IOException;

    @Nullable
    abstract public NodeAddress getNodeAddress();
}
