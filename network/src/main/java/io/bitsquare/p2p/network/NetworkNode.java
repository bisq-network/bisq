package io.bitsquare.p2p.network;

import com.google.common.util.concurrent.*;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.Message;
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

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class NetworkNode implements MessageListener, ConnectionListener {
    private static final Logger log = LoggerFactory.getLogger(NetworkNode.class);

    protected final int port;
    private final CopyOnWriteArraySet<Connection> outBoundConnections = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<Connection> inBoundConnections = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<MessageListener> messageListeners = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<ConnectionListener> connectionListeners = new CopyOnWriteArraySet<>();
    protected final CopyOnWriteArraySet<SetupListener> setupListeners = new CopyOnWriteArraySet<>();
    protected ListeningExecutorService executorService;
    private Server server;
    private volatile boolean shutDownInProgress;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public NetworkNode(int port) {
        this.port = port;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        start(null);
    }

    abstract public void start(@Nullable SetupListener setupListener);

    public SettableFuture<Connection> sendMessage(@NotNull Address peerAddress, Message message) {
        log.trace("sendMessage message=" + message);
        checkNotNull(peerAddress, "peerAddress must not be null");

        Optional<Connection> outboundConnectionOptional = findOutboundConnection(peerAddress);
        Connection connection = outboundConnectionOptional.isPresent() ? outboundConnectionOptional.get() : null;
        if (connection != null)
            log.trace("We have found a connection in outBoundConnections. Connection.uid=" + connection.getUid());

        if (connection != null && connection.isStopped()) {
            log.trace("We have a connection which is already stopped in outBoundConnections. Connection.uid=" + connection.getUid());
            outBoundConnections.remove(connection);
            connection = null;
        }

        if (connection == null) {
            Optional<Connection> inboundConnectionOptional = findInboundConnection(peerAddress);
            if (inboundConnectionOptional.isPresent()) connection = inboundConnectionOptional.get();
            if (connection != null)
                log.trace("We have found a connection in inBoundConnections. Connection.uid=" + connection.getUid());
        }

        if (connection != null) {
            return sendMessage(connection, message);
        } else {
            final SettableFuture<Connection> resultFuture = SettableFuture.create();
            ListenableFuture<Connection> future = executorService.submit(() -> {
                Thread.currentThread().setName("NetworkNode:SendMessage-create-new-outbound-connection-to-" + peerAddress);
                try {
                    Connection newConnection;
                    log.trace("We have not found any connection for that peerAddress. " +
                            "We will create a new outbound connection.");
                    Socket socket = getSocket(peerAddress); // can take a while when using tor
                    newConnection = new Connection(socket, NetworkNode.this, NetworkNode.this);
                    newConnection.setPeerAddress(peerAddress);
                    outBoundConnections.add(newConnection);

                    log.info("\n\nNetworkNode created new outbound connection:"
                            + "\npeerAddress=" + peerAddress.port
                            + "\nconnection.uid=" + newConnection.getUid()
                            + "\nmessage=" + message
                            + "\n\n");

                    newConnection.sendMessage(message);

                    return newConnection;
                } catch (Throwable throwable) {
                    if (!(throwable instanceof ConnectException || throwable instanceof IOException)) {
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
        // connection.sendMessage might take a bit (compression, write to stream), so we use a thread to not block
        ListenableFuture<Connection> future = executorService.submit(() -> {
            Thread.currentThread().setName("NetworkNode:SendMessage-to-connection-" + connection.getObjectId());
            try {
                connection.sendMessage(message);
                return connection;
            } catch (Throwable t) {
                throw t;
            }
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
        Set<Connection> set = new HashSet<>(inBoundConnections);
        set.addAll(outBoundConnections);
        return set;
    }

    public void shutDown(Runnable shutDownCompleteHandler) {
        log.info("Shutdown NetworkNode");
        if (!shutDownInProgress) {
            shutDownInProgress = true;
            if (server != null) {
                server.shutDown();
                server = null;
            }

            getAllConnections().stream().forEach(e -> e.shutDown());

            log.info("NetworkNode shutdown complete");
            if (shutDownCompleteHandler != null) UserThread.execute(() -> shutDownCompleteHandler.run());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // SetupListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addSetupListener(SetupListener setupListener) {
        setupListeners.add(setupListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ConnectionListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addConnectionListener(ConnectionListener connectionListener) {
        connectionListeners.add(connectionListener);
    }

    public void removeConnectionListener(ConnectionListener connectionListener) {
        connectionListeners.remove(connectionListener);
    }

    @Override
    public void onConnection(Connection connection) {
        connectionListeners.stream().forEach(e -> UserThread.execute(() -> e.onConnection(connection)));
    }

    @Override
    public void onPeerAddressAuthenticated(Address peerAddress, Connection connection) {
        log.trace("onAuthenticationComplete peerAddress=" + peerAddress);
        log.trace("onAuthenticationComplete connection=" + connection);

        connectionListeners.stream().forEach(e -> UserThread.execute(() -> e.onPeerAddressAuthenticated(peerAddress, connection)));
    }

    @Override
    public void onDisconnect(Reason reason, Connection connection) {
        Address peerAddress = connection.getPeerAddress();
        log.trace("onDisconnect connection " + connection + ", peerAddress= " + peerAddress);
        outBoundConnections.remove(connection);
        inBoundConnections.remove(connection);
        connectionListeners.stream().forEach(e -> UserThread.execute(() -> e.onDisconnect(reason, connection)));
    }

    @Override
    public void onError(Throwable throwable) {
        connectionListeners.stream().forEach(e -> UserThread.execute(() -> e.onError(throwable)));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addMessageListener(MessageListener messageListener) {
        messageListeners.add(messageListener);
    }

    public void removeMessageListener(MessageListener messageListener) {
        messageListeners.remove(messageListener);
    }

    @Override
    public void onMessage(Message message, Connection connection) {
        messageListeners.stream().forEach(e -> UserThread.execute(() -> e.onMessage(message, connection)));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void createExecutor() {
        executorService = Utilities.getListeningExecutorService("NetworkNode-" + port, 20, 50, 120L);
    }

    protected void startServer(ServerSocket serverSocket) {
        server = new Server(serverSocket,
                (message, connection) -> NetworkNode.this.onMessage(message, connection),
                new ConnectionListener() {
                    @Override
                    public void onConnection(Connection connection) {
                        // we still have not authenticated so put it to the temp list
                        inBoundConnections.add(connection);
                        NetworkNode.this.onConnection(connection);
                    }

                    @Override
                    public void onPeerAddressAuthenticated(Address peerAddress, Connection connection) {
                        NetworkNode.this.onPeerAddressAuthenticated(peerAddress, connection);
                    }

                    @Override
                    public void onDisconnect(Reason reason, Connection connection) {
                        Address peerAddress = connection.getPeerAddress();
                        log.trace("onDisconnect at incoming connection to peerAddress " + peerAddress);
                        inBoundConnections.remove(connection);
                        NetworkNode.this.onDisconnect(reason, connection);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        NetworkNode.this.onError(throwable);
                    }
                });
        executorService.submit(server);
    }

    private Optional<Connection> findOutboundConnection(Address peerAddress) {
        return outBoundConnections.stream()
                .filter(e -> peerAddress.equals(e.getPeerAddress())).findAny();
    }

    private Optional<Connection> findInboundConnection(Address peerAddress) {
        return inBoundConnections.stream()
                .filter(e -> peerAddress.equals(e.getPeerAddress())).findAny();
    }

    abstract protected Socket getSocket(Address peerAddress) throws IOException;

    @Nullable
    abstract public Address getAddress();
}
