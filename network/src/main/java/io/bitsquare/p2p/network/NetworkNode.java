package io.bitsquare.p2p.network;

import com.google.common.util.concurrent.*;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.Message;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class NetworkNode implements MessageListener, ConnectionListener {
    private static final Logger log = LoggerFactory.getLogger(NetworkNode.class);

    protected final int port;
    private final Map<Address, Connection> outBoundConnections = new ConcurrentHashMap<>();
    private final Map<Address, Connection> inBoundAuthenticatedConnections = new ConcurrentHashMap<>();
    private final List<Connection> inBoundTempConnections = new CopyOnWriteArrayList<>();
    private final List<MessageListener> messageListeners = new CopyOnWriteArrayList<>();
    private final List<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();
    protected final List<SetupListener> setupListeners = new CopyOnWriteArrayList<>();
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
        final SettableFuture<Connection> resultFuture = SettableFuture.create();

        Callable<Connection> task = () -> {
            Thread.currentThread().setName("Outgoing-connection-to-" + peerAddress);
            Connection connection = outBoundConnections.get(peerAddress);

            if (connection != null && connection.isStopped()) {
                // can happen because of threading...
                log.trace("We have a connection which is already stopped in outBoundConnections. Connection.uid=" + connection.getUid());
                outBoundConnections.remove(peerAddress);
                connection = null;
            }

            if (connection == null) {
                Optional<Connection> connectionOptional = inBoundAuthenticatedConnections.values().stream()
                        .filter(e -> peerAddress.equals(e.getPeerAddress()))
                        .findAny();
                if (connectionOptional.isPresent())
                    connection = connectionOptional.get();
                if (connection != null)
                    log.trace("We have found a connection in inBoundAuthenticatedConnections. Connection.uid=" + connection.getUid());
            }
            if (connection == null) {
                Optional<Connection> connectionOptional = inBoundTempConnections.stream()
                        .filter(e -> peerAddress.equals(e.getPeerAddress()))
                        .findAny();
                if (connectionOptional.isPresent())
                    connection = connectionOptional.get();
                if (connection != null)
                    log.trace("We have found a connection in inBoundTempConnections. Connection.uid=" + connection.getUid());
            }

            if (connection == null) {
                try {
                    Socket socket = getSocket(peerAddress); // can take a while when using tor
                    connection = new Connection(socket,
                            (message1, connection1) -> NetworkNode.this.onMessage(message1, connection1),
                            new ConnectionListener() {
                                @Override
                                public void onConnection(Connection connection) {
                                    NetworkNode.this.onConnection(connection);
                                }

                                @Override
                                public void onPeerAddressAuthenticated(Address peerAddress, Connection connection) {
                                    NetworkNode.this.onPeerAddressAuthenticated(peerAddress, connection);
                                }

                                @Override
                                public void onDisconnect(Reason reason, Connection connection) {
                                    log.trace("onDisconnect at outgoing connection to peerAddress " + peerAddress);
                                    NetworkNode.this.onDisconnect(reason, connection);
                                }

                                @Override
                                public void onError(Throwable throwable) {
                                    NetworkNode.this.onError(throwable);
                                }
                            });
                    outBoundConnections.put(peerAddress, connection);

                    log.info("\n\nNetworkNode created new outbound connection:"
                            + "\npeerAddress=" + peerAddress.port
                            + "\nconnection.uid=" + connection.getUid()
                            + "\nmessage=" + message
                            + "\n\n");
                } catch (Throwable t) {
                    resultFuture.setException(t);
                    return null;
                }
            }

            connection.sendMessage(message);

            return connection;
        };

        ListenableFuture<Connection> future = executorService.submit(task);
        Futures.addCallback(future, new FutureCallback<Connection>() {
            public void onSuccess(Connection connection) {
                resultFuture.set(connection);
            }

            public void onFailure(@NotNull Throwable throwable) {
                resultFuture.setException(throwable);
            }
        });
        return resultFuture;
    }

    public SettableFuture<Connection> sendMessage(Connection connection, Message message) {
        final SettableFuture<Connection> resultFuture = SettableFuture.create();

        ListenableFuture<Connection> future = executorService.submit(() -> {
            connection.sendMessage(message);
            return connection;
        });
        Futures.addCallback(future, new FutureCallback<Connection>() {
            public void onSuccess(Connection connection) {
                resultFuture.set(connection);
            }

            public void onFailure(@NotNull Throwable throwable) {
                resultFuture.setException(throwable);
            }
        });
        return resultFuture;
    }

    public Set<Connection> getAllConnections() {
        Set<Connection> set = new HashSet<>(inBoundAuthenticatedConnections.values());
        set.addAll(outBoundConnections.values());
        set.addAll(inBoundTempConnections);
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
            if (shutDownCompleteHandler != null) new Thread(shutDownCompleteHandler).start();
            ;
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
        connectionListeners.stream().forEach(e -> e.onConnection(connection));
    }

    @Override
    public void onPeerAddressAuthenticated(Address peerAddress, Connection connection) {
        log.trace("onAuthenticationComplete peerAddress=" + peerAddress);
        log.trace("onAuthenticationComplete connection=" + connection);

        connectionListeners.stream().forEach(e -> e.onPeerAddressAuthenticated(peerAddress, connection));
    }

    @Override
    public void onDisconnect(Reason reason, Connection connection) {
        Address peerAddress = connection.getPeerAddress();
        log.trace("onDisconnect connection " + connection + ", peerAddress= " + peerAddress);
        if (peerAddress != null) {
            inBoundAuthenticatedConnections.remove(peerAddress);
            outBoundConnections.remove(peerAddress);
        } else {
            // try to find if we have connection
            outBoundConnections.values().stream()
                    .filter(e -> e.equals(connection))
                    .findAny()
                    .ifPresent(e -> outBoundConnections.remove(e.getPeerAddress()));
            inBoundAuthenticatedConnections.values().stream()
                    .filter(e -> e.equals(connection))
                    .findAny()
                    .ifPresent(e -> inBoundAuthenticatedConnections.remove(e.getPeerAddress()));
        }
        inBoundTempConnections.remove(connection);

        connectionListeners.stream().forEach(e -> e.onDisconnect(reason, connection));
    }

    @Override
    public void onError(Throwable throwable) {
        connectionListeners.stream().forEach(e -> e.onError(throwable));
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
        messageListeners.stream().forEach(e -> e.onMessage(message, connection));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void startServer(ServerSocket serverSocket) {
        server = new Server(serverSocket, (message, connection) -> {
            NetworkNode.this.onMessage(message, connection);
        }, new ConnectionListener() {
            @Override
            public void onConnection(Connection connection) {
                // we still have not authenticated so put it to the temp list
                inBoundTempConnections.add(connection);
                NetworkNode.this.onConnection(connection);
            }

            @Override
            public void onPeerAddressAuthenticated(Address peerAddress, Connection connection) {
                NetworkNode.this.onPeerAddressAuthenticated(peerAddress, connection);
                // now we know the the peers address is correct and we add it to inBoundConnections and 
                // remove it from tempConnections
                inBoundAuthenticatedConnections.put(peerAddress, connection);
                inBoundTempConnections.remove(connection);
            }

            @Override
            public void onDisconnect(Reason reason, Connection connection) {
                Address peerAddress = connection.getPeerAddress();
                log.trace("onDisconnect at incoming connection to peerAddress " + peerAddress);
                if (peerAddress != null)
                    inBoundAuthenticatedConnections.remove(peerAddress);

                inBoundTempConnections.remove(connection);

                NetworkNode.this.onDisconnect(reason, connection);
            }

            @Override
            public void onError(Throwable throwable) {
                NetworkNode.this.onError(throwable);
            }
        });
        executorService.submit(server);
    }

    abstract protected Socket getSocket(Address peerAddress) throws IOException;

    @Nullable
    abstract public Address getAddress();
}
