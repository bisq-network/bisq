package io.bitsquare.p2p.network;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.common.util.concurrent.Uninterruptibles;
import io.bitsquare.common.ByteArrayUtils;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class Connection {
    private static final Logger log = LoggerFactory.getLogger(Connection.class);
    private static final int MAX_MSG_SIZE = 5 * 1024 * 1024;         // 5 MB of compressed data
    private static final int MAX_ILLEGAL_REQUESTS = 5;
    private static final int SOCKET_TIMEOUT = 30 * 60 * 1000;        // 30 min.

    public static int getMaxMsgSize() {
        return MAX_MSG_SIZE;
    }

    private final Socket socket;
    private final int port;
    private final MessageListener messageListener;
    private final ConnectionListener connectionListener;
    private final String uid;

    private final Map<IllegalRequest, Integer> illegalRequests = new ConcurrentHashMap<>();
    private final ExecutorService executorService;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    @Nullable
    private Address peerAddress;
    private boolean isAuthenticated;

    private volatile boolean stopped;
    private volatile boolean shutDownInProgress;
    private volatile boolean inputHandlerStopped;
    private volatile Date lastActivityDate;


    //TODO got java.util.zip.DataFormatException: invalid distance too far back
    // java.util.zip.DataFormatException: invalid literal/lengths set
    // use GZIPInputStream but problems with blocking
    private boolean useCompression = false;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Connection(Socket socket, MessageListener messageListener, ConnectionListener connectionListener) {
        this.socket = socket;
        port = socket.getLocalPort();
        this.messageListener = messageListener;
        this.connectionListener = connectionListener;

        uid = UUID.randomUUID().toString();

        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("Connection-%d")
                .setDaemon(true)
                .build();

        executorService = new ThreadPoolExecutor(5, 50, 10L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(50), threadFactory);

        init();
    }

    private void init() {
        try {
            socket.setSoTimeout(SOCKET_TIMEOUT);
            // Need to access first the ObjectOutputStream otherwise the ObjectInputStream would block
            // See: https://stackoverflow.com/questions/5658089/java-creating-a-new-objectinputstream-blocks/5658109#5658109
            // When you construct an ObjectInputStream, in the constructor the class attempts to read a header that 
            // the associated ObjectOutputStream on the other end of the connection has written.
            // It will not return until that header has been read. 
            if (useCompression) {
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());
            } else {
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());
            }
            executorService.submit(new InputHandler());
        } catch (IOException e) {
            handleConnectionException(e);
        }

        lastActivityDate = new Date();

        log.trace("\nNew connection created " + this.toString());
        connectionListener.onConnection(this);
    }

    public void onAuthenticationComplete(Address peerAddress, Connection connection) {
        isAuthenticated = true;
        this.peerAddress = peerAddress;
        connectionListener.onPeerAddressAuthenticated(peerAddress, connection);
    }

    public boolean isStopped() {
        return stopped;
    }

    public void sendMessage(Message message) {
        if (!stopped) {
            try {
                log.trace("writeObject " + message + " on connection with port " + port);
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
                    out.writeObject(objectToWrite);
                    out.flush();

                    lastActivityDate = new Date();
                }
            } catch (IOException e) {
                handleConnectionException(e);
            }
        } else {
            connectionListener.onDisconnect(ConnectionListener.Reason.ALREADY_CLOSED, Connection.this);
        }
    }

    public void reportIllegalRequest(IllegalRequest illegalRequest) {
        log.warn("We got reported an illegal request " + illegalRequest);
        int prevCounter = illegalRequests.get(illegalRequest);
        if (prevCounter > illegalRequest.limit) {
            log.warn("We close connection as we received too many illegal requests.\n" + illegalRequests.toString());
            shutDown();
        } else {
            illegalRequests.put(illegalRequest, ++prevCounter);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Nullable
    public Address getPeerAddress() {
        return peerAddress;
    }

    public Date getLastActivityDate() {
        return lastActivityDate;
    }

    public boolean isAuthenticated() {
        return isAuthenticated;
    }

    public String getUid() {
        return uid;
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

    private void shutDown(boolean sendCloseConnectionMessage) {
        shutDown(sendCloseConnectionMessage, null);
    }

    private void shutDown(boolean sendCloseConnectionMessage, @Nullable Runnable shutDownCompleteHandler) {
        if (!shutDownInProgress) {
            log.info("\n\nShutDown connection:"
                    + "\npeerAddress=" + peerAddress
                    + "\nobjectId=" + getObjectId()
                    + "\nuid=" + getUid()
                    + "\nisAuthenticated=" + isAuthenticated
                    + "\nsocket.getPort()=" + socket.getPort()
                    + "\n\n");
            log.debug("ShutDown " + this.getObjectId());
            log.debug("ShutDown connection requested. Connection=" + this.toString());

            if (!stopped) {
                stopped = true;
                shutDownInProgress = true;
                inputHandlerStopped = true;
                connectionListener.onDisconnect(ConnectionListener.Reason.SHUT_DOWN, Connection.this);

                if (sendCloseConnectionMessage) {
                    sendMessage(new CloseConnectionMessage());
                    // give a bit of time for closing gracefully
                    Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
                }

                try {
                    socket.close();
                } catch (SocketException e) {
                    log.trace("SocketException at shutdown might be expected " + e.getMessage());
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    Utils.shutDownExecutorService(executorService);

                    log.debug("Connection shutdown complete " + this.toString());
                    // dont use executorService as its shut down but call handler on own thread 
                    // to not get interrupted by caller
                    if (shutDownCompleteHandler != null)
                        new Thread(shutDownCompleteHandler).start();
                }
            }
        }
    }

    private void handleConnectionException(Exception e) {
        if (e instanceof SocketException) {
            if (socket.isClosed())
                connectionListener.onDisconnect(ConnectionListener.Reason.SOCKET_CLOSED, Connection.this);
            else
                connectionListener.onDisconnect(ConnectionListener.Reason.RESET, Connection.this);
        } else if (e instanceof SocketTimeoutException) {
            connectionListener.onDisconnect(ConnectionListener.Reason.TIMEOUT, Connection.this);
        } else if (e instanceof EOFException) {
            connectionListener.onDisconnect(ConnectionListener.Reason.PEER_DISCONNECTED, Connection.this);
        } else {
            log.info("Exception at connection with port " + socket.getLocalPort());
            e.printStackTrace();
            connectionListener.onDisconnect(ConnectionListener.Reason.UNKNOWN, Connection.this);
        }

        shutDown(false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Connection)) return false;

        Connection that = (Connection) o;

        if (port != that.port) return false;
        if (uid != null ? !uid.equals(that.uid) : that.uid != null) return false;
        return !(peerAddress != null ? !peerAddress.equals(that.peerAddress) : that.peerAddress != null);

    }

    @Override
    public int hashCode() {
        int result = port;
        result = 31 * result + (uid != null ? uid.hashCode() : 0);
        result = 31 * result + (peerAddress != null ? peerAddress.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Connection{" +
                "objectId=" + getObjectId() +
                ", uid=" + uid +
                ", port=" + port +
                ", isAuthenticated=" + isAuthenticated +
                ", peerAddress=" + peerAddress +
                ", lastActivityDate=" + lastActivityDate +
                ", stopped=" + stopped +
                ", inputHandlerStopped=" + inputHandlerStopped +
                '}';
    }

    public String getObjectId() {
        return super.toString().split("@")[1].toString();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // InputHandler
    ///////////////////////////////////////////////////////////////////////////////////////////

    private class InputHandler implements Runnable {
        @Override
        public void run() {
            try {
                Thread.currentThread().setName("InputHandler-" + socket.getLocalPort());
                while (!inputHandlerStopped) {
                    try {
                        log.trace("InputHandler waiting for incoming messages connection=" + Connection.this.getObjectId());
                        Object rawInputObject = in.readObject();
                        log.trace("New data arrived at inputHandler of connection=" + Connection.this.toString()
                                + " rawInputObject " + rawInputObject);

                        int size = ByteArrayUtils.objectToByteArray(rawInputObject).length;
                        if (size <= MAX_MSG_SIZE) {
                            Serializable serializable = null;
                            if (useCompression) {
                                if (rawInputObject instanceof byte[]) {
                                    byte[] compressedObjectAsBytes = (byte[]) rawInputObject;
                                    size = compressedObjectAsBytes.length;
                                    //log.trace("Read object compressed data size: " + size);
                                    serializable = Utils.decompress(compressedObjectAsBytes);
                                } else {
                                    reportIllegalRequest(IllegalRequest.InvalidDataType);
                                }
                            } else {
                                if (rawInputObject instanceof Serializable) {
                                    serializable = (Serializable) rawInputObject;
                                } else {
                                    reportIllegalRequest(IllegalRequest.InvalidDataType);
                                }
                            }
                            //log.trace("Read object decompressed data size: " + ByteArrayUtils.objectToByteArray(serializable).length);

                            // compressed size might be bigger theoretically so we check again after decompression
                            if (size <= MAX_MSG_SIZE) {
                                if (serializable instanceof Message) {
                                    lastActivityDate = new Date();
                                    Message message = (Message) serializable;
                                    if (message instanceof CloseConnectionMessage) {
                                        inputHandlerStopped = true;
                                        shutDown(false);
                                    } else {
                                        Task task = new Task() {
                                            @Override
                                            protected Object call() throws Exception {
                                                return null;
                                            }
                                        };
                                        executorService.submit(() -> {
                                            try {
                                                messageListener.onMessage(message, Connection.this);
                                            } catch (Throwable t) {
                                                t.printStackTrace();
                                                log.error("Executing task failed. " + t.getMessage());
                                            }
                                        });
                                    }
                                } else {
                                    reportIllegalRequest(IllegalRequest.InvalidDataType);
                                }
                            } else {
                                log.error("Received decompressed data exceeds max. msg size.");
                                reportIllegalRequest(IllegalRequest.MaxSizeExceeded);
                            }
                        } else {
                            log.error("Received compressed data exceeds max. msg size.");
                            reportIllegalRequest(IllegalRequest.MaxSizeExceeded);
                        }
                    } catch (IOException | ClassNotFoundException e) {
                        inputHandlerStopped = true;
                        handleConnectionException(e);
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
                log.error("Executing task failed. " + t.getMessage());
            }
        }
    }
}