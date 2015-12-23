package io.bitsquare.p2p.network;

import com.google.common.util.concurrent.*;
import io.bitsquare.app.Log;
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
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeoutException;

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

    public SettableFuture<Connection> sendMessage(@NotNull Address peerAddress, Message message) {
        Log.traceCall("message: " + message + " to peerAddress: " + peerAddress);
        checkNotNull(peerAddress, "peerAddress must not be null");

        Optional<Connection> outboundConnectionOptional = lookupOutboundConnection(peerAddress);
        Connection connection = outboundConnectionOptional.isPresent() ? outboundConnectionOptional.get() : null;
        if (connection != null)
            log.trace("We have found a connection in outBoundConnections. Connection.uid=" + connection.getUid());

        if (connection != null && connection.isStopped()) {
            log.trace("We have a connection which is already stopped in outBoundConnections. Connection.uid=" + connection.getUid());
            outBoundConnections.remove(connection);
            connection = null;
        }

        if (connection == null) {
            Optional<Connection> inboundConnectionOptional = lookupInboundConnection(peerAddress);
            if (inboundConnectionOptional.isPresent()) connection = inboundConnectionOptional.get();
            if (connection != null)
                log.trace("We have found a connection in inBoundConnections. Connection.uid=" + connection.getUid());
        }

        if (connection != null) {
            return sendMessage(connection, message);
        } else {
            log.trace("We have not found any connection for that peerAddress. " +
                    "We will create a new outbound connection.");

            final SettableFuture<Connection> resultFuture = SettableFuture.create();
            final boolean[] timeoutOccurred = new boolean[1];
            timeoutOccurred[0] = false;
            ListenableFuture<Connection> future = executorService.submit(() -> {
                Thread.currentThread().setName("NetworkNode:SendMessage-to-" + peerAddress);
                try {
                    // can take a while when using tor
                    Socket socket = createSocket(peerAddress); 
                    if (timeoutOccurred[0])
                        throw new TimeoutException("Timeout occurred when tried to create Socket to peer: " + peerAddress);


                    Connection newConnection = new Connection(socket, NetworkNode.this, NetworkNode.this);
                    newConnection.setPeerAddress(peerAddress);
                    outBoundConnections.add(newConnection);

                    log.info("\n\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n" +
                            "NetworkNode created new outbound connection:"
                            + "\npeerAddress=" + peerAddress
                            + "\nconnection.uid=" + newConnection.getUid()
                            + "\nmessage=" + message
                            + "\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n");

                    // can take a while when using tor
                    newConnection.sendMessage(message);
                    return newConnection; 
                } catch (Throwable throwable) {
                    if (!(throwable instanceof ConnectException || throwable instanceof IOException || throwable instanceof TimeoutException)) {
                        throwable.printStackTrace();
                        log.error("Executing task failed. " + throwable.getMessage());
                    }
                    throw throwable;
                }
            });

            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Thread.currentThread().setName("TimerTask-" + new Random().nextInt(10000));
                    timeoutOccurred[0] = true;
                    future.cancel(true);
                    String message = "Timeout occurred when tried to create Socket to peer: " + peerAddress;
                    log.info(message);
                    UserThread.execute(() -> resultFuture.setException(new TimeoutException(message)));
                }
            }, CREATE_SOCKET_TIMEOUT);

            Futures.addCallback(future, new FutureCallback<Connection>() {
                public void onSuccess(Connection connection) {
                    UserThread.execute(() -> {
                        timer.cancel();
                        resultFuture.set(connection);
                    });
                }

                public void onFailure(@NotNull Throwable throwable) {
                    UserThread.execute(() -> {
                        timer.cancel();
                        resultFuture.setException(throwable);
                    });
                }
            });

            return resultFuture;
        }
    }

    public SettableFuture<Connection> sendMessage(Connection connection, Message message) {
        Log.traceCall();
        // connection.sendMessage might take a bit (compression, write to stream), so we use a thread to not block
        ListenableFuture<Connection> future = executorService.submit(() -> {
            Thread.currentThread().setName("NetworkNode:SendMessage-to-" + connection.getUid());
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
        Log.traceCall();
        Set<Connection> set = new HashSet<>(inBoundConnections);
        set.addAll(outBoundConnections);
        return set;
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

            getAllConnections().stream().forEach(e -> e.shutDown());

            log.info("NetworkNode shutdown complete");
            if (shutDownCompleteHandler != null) shutDownCompleteHandler.run();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // SetupListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addSetupListener(SetupListener setupListener) {
        Log.traceCall();
        setupListeners.add(setupListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ConnectionListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnection(Connection connection) {
        Log.traceCall("NetworkNode connection=" + connection);
        connectionListeners.stream().forEach(e -> e.onConnection(connection));
    }

    @Override
    public void onDisconnect(Reason reason, Connection connection) {
        Log.traceCall();
        Address peerAddress = connection.getPeerAddress();
        log.trace("onDisconnect connection " + connection + ", peerAddress= " + peerAddress);
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

        boolean newEntry = connectionListeners.add(connectionListener);
        if (!newEntry)
            log.warn("Try to add a connectionListener which was already added.\nconnectionListener={}\nconnectionListeners={}"
                    , connectionListener, connectionListeners);
    }

    public void removeConnectionListener(ConnectionListener connectionListener) {
        Log.traceCall();
        connectionListeners.remove(connectionListener);
    }

    public void addMessageListener(MessageListener messageListener) {
        Log.traceCall();
        boolean newEntry = messageListeners.add(messageListener);
        if (!newEntry)
            log.warn("Try to add a messageListener which was already added.\nmessageListener={}\nmessageListeners={}"
                    , messageListener, messageListeners);
    }

    public void removeMessageListener(MessageListener messageListener) {
        Log.traceCall();
        boolean contained = messageListeners.remove(messageListener);
        if (!contained)
            log.warn("Try to remove a messageListener which was never added.\nmessageListener={}\nmessageListeners={}"
                    , messageListener, messageListeners);
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
                // we still have not authenticated so put it to the temp list
                inBoundConnections.add(connection);
                NetworkNode.this.onConnection(connection);
            }

            @Override
            public void onDisconnect(Reason reason, Connection connection) {
                Log.traceCall();
                Address peerAddress = connection.getPeerAddress();
                log.trace("onDisconnect at incoming connection to peerAddress (or connection) "
                        + ((peerAddress == null) ? connection : peerAddress));
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

    private Optional<Connection> lookupOutboundConnection(Address peerAddress) {
        Log.traceCall("search for " + peerAddress.toString() + " / outBoundConnections " + outBoundConnections);
        return outBoundConnections.stream()
                .filter(e -> peerAddress.equals(e.getPeerAddress())).findAny();
    }

    private Optional<Connection> lookupInboundConnection(Address peerAddress) {
        Log.traceCall("search for " + peerAddress.toString() + " / inBoundConnections " + inBoundConnections);
        return inBoundConnections.stream()
                .filter(e -> peerAddress.equals(e.getPeerAddress())).findAny();
    }

    abstract protected Socket createSocket(Address peerAddress) throws IOException;

    @Nullable
    abstract public Address getAddress();
}
