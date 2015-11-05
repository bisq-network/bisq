package io.bitsquare.p2p.network;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Uninterruptibles;
import io.bitsquare.common.ByteArrayUtils;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.Utils;
import io.bitsquare.p2p.network.messages.CloseConnectionMessage;
import javafx.concurrent.Task;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Connection is created by the server thread or by send message from NetworkNode.
 * All handlers are called on User thread.
 * Shared data between InputHandler thread and that
 */
public class Connection {
    private static final Logger log = LoggerFactory.getLogger(Connection.class);
    private static final int MAX_MSG_SIZE = 5 * 1024 * 1024;         // 5 MB of compressed data
    private static final int MAX_ILLEGAL_REQUESTS = 5;
    private static final int SOCKET_TIMEOUT = 10 * 60 * 1000;        // 10 min. //TODO set shorter
    private InputHandler inputHandler;
    private boolean isAuthenticated;

    public static int getMaxMsgSize() {
        return MAX_MSG_SIZE;
    }

    private final String portInfo;
    private final String uid;
    private final ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();

    // set in init
    private ObjectOutputStream objectOutputStream;
    // holder of state shared between InputHandler and Connection
    private SharedSpace sharedSpace;

    // mutable data, set from other threads but not changed internally.
    @Nullable
    private Address peerAddress;

    private volatile boolean stopped;
    private volatile boolean shutDownInProgress;

    //TODO got java.util.zip.DataFormatException: invalid distance too far back
    // java.util.zip.DataFormatException: invalid literal/lengths set
    // use GZIPInputStream but problems with blocking
    boolean useCompression = false;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Connection(Socket socket, MessageListener messageListener, ConnectionListener connectionListener) {
        portInfo = "localPort=" + socket.getLocalPort() + "/port=" + socket.getPort();
        uid = UUID.randomUUID().toString();

        init(socket, messageListener, connectionListener);
    }

    private void init(Socket socket, MessageListener messageListener, ConnectionListener connectionListener) {
        sharedSpace = new SharedSpace(this, socket, messageListener, connectionListener, useCompression);
        try {
            socket.setSoTimeout(SOCKET_TIMEOUT);
            // Need to access first the ObjectOutputStream otherwise the ObjectInputStream would block
            // See: https://stackoverflow.com/questions/5658089/java-creating-a-new-objectinputstream-blocks/5658109#5658109
            // When you construct an ObjectInputStream, in the constructor the class attempts to read a header that 
            // the associated ObjectOutputStream on the other end of the connection has written.
            // It will not return until that header has been read. 
            objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());

            // We create a thread for handling inputStream data
            inputHandler = new InputHandler(sharedSpace, objectInputStream, portInfo);
            singleThreadExecutor.submit(inputHandler);
        } catch (IOException e) {
            sharedSpace.handleConnectionException(e);
        }

        sharedSpace.updateLastActivityDate();

        log.trace("\nNew connection created " + this.toString());
        UserThread.execute(() -> connectionListener.onConnection(this));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public synchronized void setAuthenticated(Address peerAddress, Connection connection) {
        this.peerAddress = peerAddress;
        isAuthenticated = true;
        UserThread.execute(() -> sharedSpace.getConnectionListener().onPeerAddressAuthenticated(peerAddress, connection));
    }

    public void sendMessage(Message message) {
        if (!stopped) {
            try {
                log.trace("writeObject " + message + " on connection with port " + portInfo);
                if (!stopped) {
                    Object objectToWrite;
                    if (useCompression) {
                        byte[] messageAsBytes = ByteArrayUtils.objectToByteArray(message);
                        // log.trace("Write object uncompressed data size: " + messageAsBytes.length);
                        byte[] compressed = Utils.compress(message);
                        //log.trace("Write object compressed data size: " + compressed.length);
                        objectToWrite = compressed;
                    } else {
                        // log.trace("Write object data size: " + ByteArrayUtils.objectToByteArray(message).length);
                        objectToWrite = message;
                    }
                    objectOutputStream.writeObject(objectToWrite);
                    objectOutputStream.flush();

                    sharedSpace.updateLastActivityDate();
                }
            } catch (IOException e) {
                // an exception lead to a shutdown
                sharedSpace.handleConnectionException(e);
            }
        } else {
            UserThread.execute(() -> sharedSpace.getConnectionListener().onDisconnect(ConnectionListener.Reason.ALREADY_CLOSED, this));
        }
    }

    public void reportIllegalRequest(IllegalRequest illegalRequest) {
        sharedSpace.reportIllegalRequest(illegalRequest);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Nullable
    public synchronized Address getPeerAddress() {
        return peerAddress;
    }

    public Date getLastActivityDate() {
        return sharedSpace.getLastActivityDate();
    }

    public synchronized boolean isAuthenticated() {
        return isAuthenticated;
    }

    public String getUid() {
        return uid;
    }

    public boolean isStopped() {
        return stopped;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ShutDown
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void shutDown(Runnable completeHandler) {
        shutDown(true, completeHandler);
    }

    public void shutDown() {
        shutDown(true, null);
    }

    void shutDown(boolean sendCloseConnectionMessage) {
        shutDown(sendCloseConnectionMessage, null);
    }

    private void shutDown(boolean sendCloseConnectionMessage, @Nullable Runnable shutDownCompleteHandler) {
        if (!shutDownInProgress) {
            log.info("\n\nShutDown connection:"
                    + "\npeerAddress=" + peerAddress
                    + "\nobjectId=" + getObjectId()
                    + "\nuid=" + getUid()
                    + "\nisAuthenticated=" + isAuthenticated()
                    + "\nsocket.getPort()=" + sharedSpace.getSocket().getPort()
                    + "\n\n");
            log.debug("ShutDown " + this.getObjectId());
            log.debug("ShutDown connection requested. Connection=" + this.toString());

            if (!stopped) {
                stopped = true;
                inputHandler.stop();

                shutDownInProgress = true;
                UserThread.execute(() -> sharedSpace.getConnectionListener().onDisconnect(ConnectionListener.Reason.SHUT_DOWN, this));

                if (sendCloseConnectionMessage) {
                    new Thread(() -> {
                        Thread.currentThread().setName("Connection:SendCloseConnectionMessage-" + this.getObjectId());
                        try {
                            sendMessage(new CloseConnectionMessage());
                            // give a bit of time for closing gracefully
                            Uninterruptibles.sleepUninterruptibly(200, TimeUnit.MILLISECONDS);
                        } catch (Throwable t) {
                            t.printStackTrace();
                            log.error(t.getMessage());
                        } finally {
                            UserThread.execute(() -> continueShutDown(shutDownCompleteHandler));
                        }
                    }).start();
                } else {
                    continueShutDown(shutDownCompleteHandler);
                }
            }
        }
    }

    private void continueShutDown(@Nullable Runnable shutDownCompleteHandler) {
        try {
            sharedSpace.getSocket().close();
        } catch (SocketException e) {
            log.trace("SocketException at shutdown might be expected " + e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            MoreExecutors.shutdownAndAwaitTermination(singleThreadExecutor, 500, TimeUnit.MILLISECONDS);

            log.debug("Connection shutdown complete " + this.toString());
            // dont use executorService as its shut down but call handler on own thread 
            // to not get interrupted by caller
            if (shutDownCompleteHandler != null)
                UserThread.execute(shutDownCompleteHandler);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Connection)) return false;

        Connection that = (Connection) o;

        if (portInfo != null ? !portInfo.equals(that.portInfo) : that.portInfo != null) return false;
        return !(uid != null ? !uid.equals(that.uid) : that.uid != null);

    }

    @Override
    public int hashCode() {
        int result = portInfo != null ? portInfo.hashCode() : 0;
        result = 31 * result + (uid != null ? uid.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Connection{" +
                "portInfo=" + portInfo +
                ", uid='" + uid + '\'' +
                ", objectId='" + getObjectId() + '\'' +
                ", sharedSpace=" + sharedSpace.toString() +
                ", peerAddress=" + peerAddress +
                ", stopped=" + stopped +
                ", shutDownInProgress=" + shutDownInProgress +
                ", useCompression=" + useCompression +
                '}';
    }

    public String getObjectId() {
        return super.toString().split("@")[1].toString();
    }

    public void setPeerAddress(@Nullable Address peerAddress) {
        this.peerAddress = peerAddress;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // SharedSpace
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Holds all shared data between Connection and InputHandler
     */
    private static class SharedSpace {
        private static final Logger log = LoggerFactory.getLogger(SharedSpace.class);

        private final Connection connection;
        private final Socket socket;
        private final MessageListener messageListener;
        private final ConnectionListener connectionListener;
        private final boolean useCompression;
        private final ConcurrentHashMap<IllegalRequest, Integer> illegalRequests = new ConcurrentHashMap<>();

        // mutable
        private Date lastActivityDate;

        public SharedSpace(Connection connection, Socket socket, MessageListener messageListener,
                           ConnectionListener connectionListener, boolean useCompression) {
            this.connection = connection;
            this.socket = socket;
            this.messageListener = messageListener;
            this.connectionListener = connectionListener;
            this.useCompression = useCompression;
        }

        public synchronized void updateLastActivityDate() {
            lastActivityDate = new Date();
        }

        public synchronized Date getLastActivityDate() {
            return lastActivityDate;
        }

        public void reportIllegalRequest(IllegalRequest illegalRequest) {
            log.warn("We got reported an illegal request " + illegalRequest);
            int prevCounter = illegalRequests.get(illegalRequest);
            if (prevCounter > illegalRequest.maxTolerance) {
                log.warn("We close connection as we received too many illegal requests.\n" + illegalRequests.toString());
                connection.shutDown(false);
            } else {
                illegalRequests.put(illegalRequest, ++prevCounter);
            }
        }

        public void handleConnectionException(Exception e) {
            if (e instanceof SocketException) {
                if (socket.isClosed())
                    UserThread.execute(() -> connectionListener.onDisconnect(ConnectionListener.Reason.SOCKET_CLOSED, connection));
                else
                    UserThread.execute(() -> connectionListener.onDisconnect(ConnectionListener.Reason.RESET, connection));
            } else if (e instanceof SocketTimeoutException) {
                UserThread.execute(() -> connectionListener.onDisconnect(ConnectionListener.Reason.TIMEOUT, connection));
            } else if (e instanceof EOFException) {
                UserThread.execute(() -> connectionListener.onDisconnect(ConnectionListener.Reason.PEER_DISCONNECTED, connection));
            } else {
                log.info("Exception at connection with port " + socket.getLocalPort());
                e.printStackTrace();
                UserThread.execute(() -> connectionListener.onDisconnect(ConnectionListener.Reason.UNKNOWN, connection));
            }

            connection.shutDown(false);
        }

        public void onMessage(Message message) {
            UserThread.execute(() -> messageListener.onMessage(message, connection));
        }

        public boolean useCompression() {
            return useCompression;
        }

        public void shutDown(boolean sendCloseConnectionMessage) {
            connection.shutDown(sendCloseConnectionMessage);
        }

        public ConnectionListener getConnectionListener() {
            return connectionListener;
        }

        public Socket getSocket() {
            return socket;
        }

        public String getConnectionId() {
            return connection.getObjectId();
        }

        @Override
        public String toString() {
            return "SharedSpace{" +
                    ", socket=" + socket +
                    ", useCompression=" + useCompression +
                    ", illegalRequests=" + illegalRequests +
                    ", lastActivityDate=" + lastActivityDate +
                    '}';
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // InputHandler
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static class InputHandler implements Runnable {
        private static final Logger log = LoggerFactory.getLogger(InputHandler.class);

        private final SharedSpace sharedSpace;
        private final ObjectInputStream objectInputStream;
        private final String portInfo;
        private volatile boolean stopped;

        public InputHandler(SharedSpace sharedSpace, ObjectInputStream objectInputStream, String portInfo) {
            this.sharedSpace = sharedSpace;
            this.objectInputStream = objectInputStream;
            this.portInfo = portInfo;
        }

        public void stop() {
            stopped = true;
        }

        @Override
        public void run() {
            try {
                Thread.currentThread().setName("InputHandler-" + portInfo);
                while (!stopped && !Thread.currentThread().isInterrupted()) {
                    try {
                        log.trace("InputHandler waiting for incoming messages connection=" + sharedSpace.getConnectionId());
                        Object rawInputObject = objectInputStream.readObject();
                        log.trace("New data arrived at inputHandler of connection=" + sharedSpace.getConnectionId()
                                + " rawInputObject " + rawInputObject);

                        int size = ByteArrayUtils.objectToByteArray(rawInputObject).length;
                        if (size <= getMaxMsgSize()) {
                            Serializable serializable = null;
                            if (sharedSpace.useCompression()) {
                                if (rawInputObject instanceof byte[]) {
                                    byte[] compressedObjectAsBytes = (byte[]) rawInputObject;
                                    size = compressedObjectAsBytes.length;
                                    //log.trace("Read object compressed data size: " + size);
                                    serializable = Utils.decompress(compressedObjectAsBytes);
                                } else {
                                    sharedSpace.reportIllegalRequest(IllegalRequest.InvalidDataType);
                                }
                            } else {
                                if (rawInputObject instanceof Serializable) {
                                    serializable = (Serializable) rawInputObject;
                                } else {
                                    sharedSpace.reportIllegalRequest(IllegalRequest.InvalidDataType);
                                }
                            }
                            //log.trace("Read object decompressed data size: " + ByteArrayUtils.objectToByteArray(serializable).length);

                            // compressed size might be bigger theoretically so we check again after decompression
                            if (size <= getMaxMsgSize()) {
                                if (serializable instanceof Message) {
                                    sharedSpace.updateLastActivityDate();
                                    Message message = (Message) serializable;
                                    if (message instanceof CloseConnectionMessage) {
                                        stopped = true;
                                        sharedSpace.shutDown(false);
                                    } else {
                                        Task task = new Task() {
                                            @Override
                                            protected Object call() throws Exception {
                                                return null;
                                            }
                                        };
                                        sharedSpace.onMessage(message);
                                    }
                                } else {
                                    sharedSpace.reportIllegalRequest(IllegalRequest.InvalidDataType);
                                }
                            } else {
                                log.error("Received decompressed data exceeds max. msg size.");
                                sharedSpace.reportIllegalRequest(IllegalRequest.MaxSizeExceeded);
                            }
                        } else {
                            log.error("Received compressed data exceeds max. msg size.");
                            sharedSpace.reportIllegalRequest(IllegalRequest.MaxSizeExceeded);
                        }
                    } catch (IOException | ClassNotFoundException e) {
                        stopped = true;
                        sharedSpace.handleConnectionException(e);
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
                log.error("Executing task failed. " + t.getMessage());
            }
        }

        @Override
        public String toString() {
            return "InputHandler{" +
                    "sharedSpace=" + sharedSpace +
                    ", port=" + portInfo +
                    ", stopped=" + stopped +
                    '}';
        }
    }
}