package io.bitsquare.p2p.network;

import com.google.common.util.concurrent.CycleDetectingLockFactory;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Uninterruptibles;
import io.bitsquare.app.Log;
import io.bitsquare.app.Version;
import io.bitsquare.common.ByteArrayUtils;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.messaging.PrefixedSealedAndSignedMessage;
import io.bitsquare.p2p.messaging.SupportedCapabilitiesMessage;
import io.bitsquare.p2p.network.messages.CloseConnectionMessage;
import io.bitsquare.p2p.network.messages.SendersNodeAddressMessage;
import io.bitsquare.p2p.peers.BanList;
import io.bitsquare.p2p.peers.getdata.messages.GetDataRequest;
import io.bitsquare.p2p.peers.getdata.messages.GetDataResponse;
import io.bitsquare.p2p.peers.keepalive.messages.KeepAliveMessage;
import io.bitsquare.p2p.peers.keepalive.messages.Ping;
import io.bitsquare.p2p.peers.keepalive.messages.Pong;
import io.bitsquare.p2p.storage.messages.AddDataMessage;
import io.bitsquare.p2p.storage.messages.RefreshTTLMessage;
import io.bitsquare.p2p.storage.payload.CapabilityRequiringPayload;
import io.bitsquare.p2p.storage.payload.StoragePayload;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Connection is created by the server thread or by sendMessage from NetworkNode.
 * All handlers are called on User thread.
 */
public class Connection implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(Connection.class);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enums
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum PeerType {
        SEED_NODE,
        PEER,
        DIRECT_MSG_PEER,
        INITIAL_DATA_REQUEST
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Leaving some constants package-private for tests to know limits.
    static final int MAX_MSG_SIZE = 200 * 1024;                       // 200 kb
    static final int MAX_MSG_SIZE_GET_DATA = 10 * 1024 * 1024;         // 10 MB (425 offers resulted in about 660 kb, mailbox msg will add more to it) offer has usually 2 kb, mailbox 3kb.
    //TODO decrease limits again after testing
    static final int MSG_THROTTLE_PER_SEC = 200;              // With MAX_MSG_SIZE of 200kb results in bandwidth of 40MB/sec or 5 mbit/sec
    static final int MSG_THROTTLE_PER_10_SEC = 1000;          // With MAX_MSG_SIZE of 200kb results in bandwidth of 20MB/sec or 2.5 mbit/sec
    private static final int SOCKET_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(60);

    public static int getMaxMsgSize() {
        return MAX_MSG_SIZE;
    }

    private static final CycleDetectingLockFactory cycleDetectingLockFactory = CycleDetectingLockFactory.newInstance(CycleDetectingLockFactory.Policies.THROW);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final Socket socket;
    // private final MessageListener messageListener;
    private final ConnectionListener connectionListener;
    private final String portInfo;
    private final String uid;
    private final ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
    private final ReentrantLock objectOutputStreamLock = cycleDetectingLockFactory.newReentrantLock("objectOutputStreamLock");
    // holder of state shared between InputHandler and Connection
    private final SharedModel sharedModel;
    private final Statistic statistic;

    // set in init
    private InputHandler inputHandler;
    private ObjectOutputStream objectOutputStream;

    // mutable data, set from other threads but not changed internally.
    private Optional<NodeAddress> peersNodeAddressOptional = Optional.empty();
    private volatile boolean stopped;
    private PeerType peerType;
    private final ObjectProperty<NodeAddress> peersNodeAddressProperty = new SimpleObjectProperty<>();
    private final List<Tuple2<Long, Serializable>> messageTimeStamps = new ArrayList<>();
    private final CopyOnWriteArraySet<MessageListener> messageListeners = new CopyOnWriteArraySet<>();
    private volatile long lastSendTimeStamp = 0;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    Connection(Socket socket, MessageListener messageListener, ConnectionListener connectionListener,
               @Nullable NodeAddress peersNodeAddress) {
        this.socket = socket;
        this.connectionListener = connectionListener;
        uid = UUID.randomUUID().toString();
        statistic = new Statistic();

        addMessageListener(messageListener);

        sharedModel = new SharedModel(this, socket);

        if (socket.getLocalPort() == 0)
            portInfo = "port=" + socket.getPort();
        else
            portInfo = "localPort=" + socket.getLocalPort() + "/port=" + socket.getPort();

        init(peersNodeAddress);
    }

    private void init(@Nullable NodeAddress peersNodeAddress) {
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
            inputHandler = new InputHandler(sharedModel, objectInputStream, portInfo, this);
            singleThreadExecutor.submit(inputHandler);
        } catch (IOException e) {
            sharedModel.handleConnectionException(e);
        }

        // Use Peer as default, in case of other types they will set it as soon as possible.
        peerType = PeerType.PEER;

        if (peersNodeAddress != null)
            setPeersNodeAddress(peersNodeAddress);

        log.trace("New connection created: " + this.toString());

        UserThread.execute(() -> connectionListener.onConnection(this));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Called form various threads
    public void sendMessage(Message message) {
        if (!stopped) {
            if (!isCapabilityRequired(message) || isCapabilitySupported(message)) {
                try {
                    Log.traceCall();

                    // Throttle outbound messages
                    long now = System.currentTimeMillis();
                    long elapsed = now - lastSendTimeStamp;
                    if (elapsed < 20) {
                        log.debug("We got 2 sendMessage requests in less than 20 ms. We set the thread to sleep " +
                                        "for 50 ms to avoid flooding our peer. lastSendTimeStamp={}, now={}, elapsed={}",
                                lastSendTimeStamp, now, elapsed);
                        Thread.sleep(50);
                    }

                    lastSendTimeStamp = now;
                    String peersNodeAddress = peersNodeAddressOptional.isPresent() ? peersNodeAddressOptional.get().toString() : "null";
                    int size = ByteArrayUtils.objectToByteArray(message).length;

                    if (message instanceof Ping || message instanceof RefreshTTLMessage) {
                        // pings and offer refresh msg we dont want to log in production
                        log.trace("\n\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n" +
                                        "Sending direct message to peer" +
                                        "Write object to outputStream to peer: {} (uid={})\ntruncated message={} / size={}" +
                                        "\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n",
                                peersNodeAddress, uid, Utilities.toTruncatedString(message), size);
                    } else if (message instanceof PrefixedSealedAndSignedMessage && peersNodeAddressOptional.isPresent()) {
                        setPeerType(Connection.PeerType.DIRECT_MSG_PEER);

                        log.debug("\n\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n" +
                                        "Sending direct message to peer" +
                                        "Write object to outputStream to peer: {} (uid={})\ntruncated message={} / size={}" +
                                        "\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n",
                                peersNodeAddress, uid, Utilities.toTruncatedString(message), size);
                    } else if (message instanceof GetDataResponse && ((GetDataResponse) message).isGetUpdatedDataResponse) {
                        setPeerType(Connection.PeerType.PEER);
                    } else {
                        log.debug("\n\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n" +
                                        "Write object to outputStream to peer: {} (uid={})\ntruncated message={} / size={}" +
                                        "\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n",
                                peersNodeAddress, uid, Utilities.toTruncatedString(message), size);
                    }

                    if (!stopped) {
                        objectOutputStreamLock.lock();
                        objectOutputStream.writeObject(message);
                        objectOutputStream.flush();

                        statistic.addSentBytes(size);
                        statistic.addSentMessage(message);

                        // We don't want to get the activity ts updated by ping/pong msg
                        if (!(message instanceof KeepAliveMessage))
                            statistic.updateLastActivityTimestamp();
                    }
                } catch (IOException e) {
                    // an exception lead to a shutdown
                    sharedModel.handleConnectionException(e);
                } catch (Throwable t) {
                    log.error(t.getMessage());
                    t.printStackTrace();
                    sharedModel.handleConnectionException(t);
                } finally {
                    if (objectOutputStreamLock.isLocked())
                        objectOutputStreamLock.unlock();
                }
            }
        } else {
            log.debug("called sendMessage but was already stopped");
        }
    }

    public boolean isCapabilitySupported(Message message) {
        if (message instanceof AddDataMessage) {
            final StoragePayload storagePayload = (((AddDataMessage) message).protectedStorageEntry).getStoragePayload();
            if (storagePayload instanceof CapabilityRequiringPayload) {
                final List<Integer> requiredCapabilities = ((CapabilityRequiringPayload) storagePayload).getRequiredCapabilities();
                final List<Integer> supportedCapabilities = sharedModel.getSupportedCapabilities();
                if (supportedCapabilities != null) {
                    for (int messageCapability : requiredCapabilities) {
                        for (int connectionCapability : supportedCapabilities) {
                            if (messageCapability == connectionCapability)
                                return true;
                        }
                    }
                    log.debug("We do not send the message to the peer because he does not support the required capability for that message type.\n" +
                            "Required capabilities is: " + requiredCapabilities.toString() + "\n" +
                            "Supported capabilities is: " + supportedCapabilities.toString() + "\n" +
                            "connection: " + this.toString() + "\n" +
                            "storagePayload is: " + Utilities.toTruncatedString(storagePayload));
                    return false;
                } else {
                    log.debug("We do not send the message to the peer because he uses an old version which does not support capabilities.\n" +
                            "Required capabilities is: " + requiredCapabilities.toString() + "\n" +
                            "connection: " + this.toString() + "\n" +
                            "storagePayload is: " + Utilities.toTruncatedString(storagePayload));
                    return false;
                }
            } else {
                return true;
            }
        } else {
            return true;
        }
    }

    public boolean isCapabilityRequired(Message message) {
        return message instanceof AddDataMessage && (((AddDataMessage) message).protectedStorageEntry).getStoragePayload() instanceof CapabilityRequiringPayload;
    }

    public List<Integer> getSupportedCapabilities() {
        return sharedModel.getSupportedCapabilities();
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

    @SuppressWarnings("unused")
    public boolean reportIllegalRequest(RuleViolation ruleViolation) {
        return sharedModel.reportInvalidRequest(ruleViolation);
    }

    private boolean violatesThrottleLimit(Serializable serializable) {
        long now = System.currentTimeMillis();
        boolean violated = false;
        //TODO remove serializable storage after network is tested stable
        if (messageTimeStamps.size() >= MSG_THROTTLE_PER_SEC) {
            // check if we got more than 70 (MSG_THROTTLE_PER_SEC) msg per sec.
            long compareValue = messageTimeStamps.get(messageTimeStamps.size() - MSG_THROTTLE_PER_SEC).first;
            // if duration < 1 sec we received too much messages
            violated = now - compareValue < TimeUnit.SECONDS.toMillis(1);
            if (violated) {
                log.error("violatesThrottleLimit MSG_THROTTLE_PER_SEC ");
                log.error("elapsed " + (now - compareValue));
                log.error("messageTimeStamps: \n\t" + messageTimeStamps.stream()
                        .map(e -> "\n\tts=" + e.first.toString() + " message=" + e.second.getClass().getName())
                        .collect(Collectors.toList()).toString());
            }
        }

        if (messageTimeStamps.size() >= MSG_THROTTLE_PER_10_SEC) {
            if (!violated) {
                // check if we got more than 50 msg per 10 sec.
                long compareValue = messageTimeStamps.get(messageTimeStamps.size() - MSG_THROTTLE_PER_10_SEC).first;
                // if duration < 10 sec we received too much messages
                violated = now - compareValue < TimeUnit.SECONDS.toMillis(10);

                if (violated) {
                    log.error("violatesThrottleLimit MSG_THROTTLE_PER_10_SEC ");
                    log.error("elapsed " + (now - compareValue));
                    log.error("messageTimeStamps: \n\t" + messageTimeStamps.stream()
                            .map(e -> "\n\tts=" + e.first.toString() + " message=" + e.second.getClass().getName())
                            .collect(Collectors.toList()).toString());
                }
            }
            // we limit to max 50 (MSG_THROTTLE_PER_10SEC) entries
            messageTimeStamps.remove(0);
        }

        messageTimeStamps.add(new Tuple2<>(now, serializable));
        return violated;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Only receive non - CloseConnectionMessage messages
    @Override
    public void onMessage(Message message, Connection connection) {
        checkArgument(connection.equals(this));
        UserThread.execute(() -> messageListeners.stream().forEach(e -> e.onMessage(message, connection)));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setPeerType(PeerType peerType) {
        Log.traceCall(peerType.toString());
        this.peerType = peerType;
    }

    public void setPeersNodeAddress(NodeAddress peerNodeAddress) {
        checkNotNull(peerNodeAddress, "peerAddress must not be null");
        peersNodeAddressOptional = Optional.of(peerNodeAddress);

        String peersNodeAddress = getPeersNodeAddressOptional().isPresent() ? getPeersNodeAddressOptional().get().getFullAddress() : "";
        if (this instanceof InboundConnection) {
            log.debug("\n\n############################################################\n" +
                    "We got the peers node address set.\n" +
                    "peersNodeAddress= " + peersNodeAddress +
                    "\nconnection.uid=" + getUid() +
                    "\n############################################################\n");
        }

        peersNodeAddressProperty.set(peerNodeAddress);

        if (BanList.contains(peerNodeAddress)) {
            log.warn("We detected a connection to a banned peer. We will close that connection. (setPeersNodeAddress)");
            sharedModel.reportInvalidRequest(RuleViolation.PEER_BANNED);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<NodeAddress> getPeersNodeAddressOptional() {
        return peersNodeAddressOptional;
    }

    public String getUid() {
        return uid;
    }

    public boolean hasPeersNodeAddress() {
        return peersNodeAddressOptional.isPresent();
    }

    public boolean isStopped() {
        return stopped;
    }

    public PeerType getPeerType() {
        return peerType;
    }

    public ReadOnlyObjectProperty<NodeAddress> peersNodeAddressProperty() {
        return peersNodeAddressProperty;
    }

    public RuleViolation getRuleViolation() {
        return sharedModel.getRuleViolation();
    }

    public Statistic getStatistic() {
        return statistic;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ShutDown
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void shutDown(CloseConnectionReason closeConnectionReason) {
        shutDown(closeConnectionReason, null);
    }

    public void shutDown(CloseConnectionReason closeConnectionReason, @Nullable Runnable shutDownCompleteHandler) {
        Log.traceCall(this.toString());
        if (!stopped) {
            String peersNodeAddress = peersNodeAddressOptional.isPresent() ? peersNodeAddressOptional.get().toString() : "null";
            log.debug("\n\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n" +
                    "ShutDown connection:"
                    + "\npeersNodeAddress=" + peersNodeAddress
                    + "\ncloseConnectionReason=" + closeConnectionReason
                    + "\nuid=" + uid
                    + "\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n");

            if (closeConnectionReason.sendCloseMessage) {
                new Thread(() -> {
                    Thread.currentThread().setName("Connection:SendCloseConnectionMessage-" + this.uid);
                    Log.traceCall("sendCloseConnectionMessage");
                    try {
                        String reason = closeConnectionReason == CloseConnectionReason.RULE_VIOLATION ?
                                sharedModel.getRuleViolation().name() : closeConnectionReason.name();
                        sendMessage(new CloseConnectionMessage(reason));

                        setStopFlags();

                        Uninterruptibles.sleepUninterruptibly(200, TimeUnit.MILLISECONDS);
                    } catch (Throwable t) {
                        log.error(t.getMessage());
                        t.printStackTrace();
                    } finally {
                        setStopFlags();
                        UserThread.execute(() -> doShutDown(closeConnectionReason, shutDownCompleteHandler));
                    }
                }).start();
            } else {
                setStopFlags();
                doShutDown(closeConnectionReason, shutDownCompleteHandler);
            }
        } else {
            //TODO find out why we get called that
            log.debug("stopped was already at shutDown call");
            UserThread.execute(() -> doShutDown(closeConnectionReason, shutDownCompleteHandler));
        }
    }

    private void setStopFlags() {
        stopped = true;
        sharedModel.stop();
        if (inputHandler != null)
            inputHandler.stop();
    }

    private void doShutDown(CloseConnectionReason closeConnectionReason, @Nullable Runnable shutDownCompleteHandler) {
        // Use UserThread.execute as its not clear if that is called from a non-UserThread
        UserThread.execute(() -> connectionListener.onDisconnect(closeConnectionReason, this));
        try {
            sharedModel.getSocket().close();
        } catch (SocketException e) {
            log.trace("SocketException at shutdown might be expected " + e.getMessage());
        } catch (IOException e) {
            log.error("Exception at shutdown. " + e.getMessage());
            e.printStackTrace();
        } finally {
            MoreExecutors.shutdownAndAwaitTermination(singleThreadExecutor, 500, TimeUnit.MILLISECONDS);

            log.debug("Connection shutdown complete " + this.toString());
            // Use UserThread.execute as its not clear if that is called from a non-UserThread
            if (shutDownCompleteHandler != null)
                UserThread.execute(shutDownCompleteHandler);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Connection)) return false;

        Connection that = (Connection) o;

        return !(uid != null ? !uid.equals(that.uid) : that.uid != null);

    }

    @Override
    public int hashCode() {
        return uid != null ? uid.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Connection{" +
                "peerAddress=" + peersNodeAddressOptional +
                ", peerType=" + peerType +
                ", uid='" + uid + '\'' +
                '}';
    }

    @SuppressWarnings("unused")
    public String printDetails() {
        return "Connection{" +
                "peerAddress=" + peersNodeAddressOptional +
                ", peerType=" + peerType +
                ", portInfo=" + portInfo +
                ", uid='" + uid + '\'' +
                ", sharedSpace=" + sharedModel.toString() +
                ", stopped=" + stopped +
                '}';
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // SharedSpace
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Holds all shared data between Connection and InputHandler
     * Runs in same thread as Connection
     */
    private static class SharedModel {
        private static final Logger log = LoggerFactory.getLogger(SharedModel.class);

        private final Connection connection;
        private final Socket socket;
        private final ConcurrentHashMap<RuleViolation, Integer> ruleViolations = new ConcurrentHashMap<>();

        // mutable
        private volatile boolean stopped;
        private CloseConnectionReason closeConnectionReason;
        private RuleViolation ruleViolation;
        @Nullable
        private List<Integer> supportedCapabilities;


        public SharedModel(Connection connection, Socket socket) {
            this.connection = connection;
            this.socket = socket;
        }

        public boolean reportInvalidRequest(RuleViolation ruleViolation) {
            log.warn("We got reported an corrupt request " + ruleViolation + "\n\tconnection=" + this);
            int numRuleViolations;
            if (ruleViolations.contains(ruleViolation))
                numRuleViolations = ruleViolations.get(ruleViolation);
            else
                numRuleViolations = 0;

            numRuleViolations++;
            ruleViolations.put(ruleViolation, numRuleViolations);

            if (numRuleViolations >= ruleViolation.maxTolerance) {
                log.warn("We close connection as we received too many corrupt requests.\n" +
                        "numRuleViolations={}\n\t" +
                        "corruptRequest={}\n\t" +
                        "corruptRequests={}\n\t" +
                        "connection={}", numRuleViolations, ruleViolation, ruleViolations.toString(), connection);
                this.ruleViolation = ruleViolation;
                if (ruleViolation == RuleViolation.PEER_BANNED) {
                    log.warn("We detected a connection to a banned peer. We will close that connection. (reportInvalidRequest)");
                    shutDown(CloseConnectionReason.PEER_BANNED);
                } else {
                    shutDown(CloseConnectionReason.RULE_VIOLATION);
                }

                return true;
            } else {
                return false;
            }
        }

        @Nullable
        public List<Integer> getSupportedCapabilities() {
            return supportedCapabilities;
        }

        public void setSupportedCapabilities(List<Integer> supportedCapabilities) {
            this.supportedCapabilities = supportedCapabilities;
        }

        public void handleConnectionException(Throwable e) {
            Log.traceCall(e.toString());
            if (e instanceof SocketException) {
                if (socket.isClosed())
                    closeConnectionReason = CloseConnectionReason.SOCKET_CLOSED;
                else
                    closeConnectionReason = CloseConnectionReason.RESET;
            } else if (e instanceof SocketTimeoutException || e instanceof TimeoutException) {
                closeConnectionReason = CloseConnectionReason.SOCKET_TIMEOUT;
                log.debug("SocketTimeoutException at socket " + socket.toString() + "\n\tconnection={}" + this);
            } else if (e instanceof EOFException) {
                closeConnectionReason = CloseConnectionReason.TERMINATED;
            } else if (e instanceof OptionalDataException || e instanceof StreamCorruptedException) {
                closeConnectionReason = CloseConnectionReason.CORRUPTED_DATA;
            } else {
                // TODO sometimes we get StreamCorruptedException, OptionalDataException, IllegalStateException
                closeConnectionReason = CloseConnectionReason.UNKNOWN_EXCEPTION;
                log.warn("Unknown reason for exception at socket {}\n\t" +
                                "connection={}\n\t" +
                                "Exception=",
                        socket.toString(),
                        this,
                        e.toString());
                e.printStackTrace();
            }

            shutDown(closeConnectionReason);
        }

        public void shutDown(CloseConnectionReason closeConnectionReason) {
            if (!stopped) {
                stopped = true;
                connection.shutDown(closeConnectionReason);
            }
        }

        public Socket getSocket() {
            return socket;
        }

        public void stop() {
            this.stopped = true;
        }

        public RuleViolation getRuleViolation() {
            return ruleViolation;
        }

        @Override
        public String toString() {
            return "SharedSpace{" +
                    "socket=" + socket +
                    ", ruleViolations=" + ruleViolations +
                    '}';
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // InputHandler
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Runs in same thread as Connection, receives a message, performs several checks on it
    // (including throttling limits, validity and statistics)
    // and delivers it to the message listener given in the constructor.
    private static class InputHandler implements Runnable {
        private static final Logger log = LoggerFactory.getLogger(InputHandler.class);

        private final SharedModel sharedModel;
        private final ObjectInputStream objectInputStream;
        private final String portInfo;
        private final MessageListener messageListener;

        private volatile boolean stopped;
        private long lastReadTimeStamp;
        private boolean threadNameSet;

        public InputHandler(SharedModel sharedModel, ObjectInputStream objectInputStream, String portInfo, MessageListener messageListener) {
            this.sharedModel = sharedModel;
            this.objectInputStream = objectInputStream;
            this.portInfo = portInfo;
            this.messageListener = messageListener;
        }

        public void stop() {
            if (!stopped) {
                try {
                    objectInputStream.close();
                } catch (IOException e) {
                    log.error("IOException at InputHandler.stop\n" + e.getMessage());
                    e.printStackTrace();
                } finally {
                    stopped = true;
                }
            }
        }

        @Override
        public void run() {
            try {
                Thread.currentThread().setName("InputHandler");
                while (!stopped && !Thread.currentThread().isInterrupted()) {
                    if (!threadNameSet && sharedModel.connection.getPeersNodeAddressOptional().isPresent()) {
                        Thread.currentThread().setName("InputHandler-" + sharedModel.connection.getPeersNodeAddressOptional().get().getFullAddress());
                        threadNameSet = true;
                    }
                    try {
                        if (sharedModel.getSocket().isClosed() || objectInputStream.available() < 0) {
                            log.warn("Shutdown because objectInputStream.available() < 0. objectInputStream.available()=" + objectInputStream.available());
                            sharedModel.shutDown(CloseConnectionReason.TERMINATED);
                            return;
                        }

                        Connection connection = sharedModel.connection;
                        log.trace("InputHandler waiting for incoming messages.\n\tConnection=" + connection);

                        Object rawInputObject = objectInputStream.readObject();

                        // Throttle inbound messages
                        long now = System.currentTimeMillis();
                        long elapsed = now - lastReadTimeStamp;
                        if (elapsed < 10) {
                            log.debug("We got 2 messages received in less than 10 ms. We set the thread to sleep " +
                                            "for 20 ms to avoid getting flooded by our peer. lastReadTimeStamp={}, now={}, elapsed={}",
                                    lastReadTimeStamp, now, elapsed);
                            Thread.sleep(20);
                        }

                        lastReadTimeStamp = now;
                        int size = ByteArrayUtils.objectToByteArray(rawInputObject).length;

                        if (rawInputObject instanceof Pong || rawInputObject instanceof RefreshTTLMessage) {
                            // We only log Pong and RefreshTTLMessage when in dev environment (trace)
                            log.trace("\n\n<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<\n" +
                                            "New data arrived at inputHandler of connection {}.\n" +
                                            "Received object (truncated)={} / size={}"
                                            + "\n<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<\n",
                                    connection,
                                    Utilities.toTruncatedString(rawInputObject),
                                    size);
                        } else if (rawInputObject instanceof Message) {
                            // We want to log all incoming messages (except Pong and RefreshTTLMessage) 
                            // so we log before the data type checks
                            //log.info("size={}; object={}", size, Utilities.toTruncatedString(rawInputObject.toString(), 100));
                            log.debug("\n\n<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<\n" +
                                            "New data arrived at inputHandler of connection {}.\n" +
                                            "Received object (truncated)={} / size={}"
                                            + "\n<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<\n",
                                    connection,
                                    Utilities.toTruncatedString(rawInputObject),
                                    size);
                        } else {
                            log.error("Invalid data arrived at inputHandler of connection {} Size={}", connection, size);
                            try {
                                // Don't call toString on rawInputObject
                                log.error("rawInputObject.className=" + rawInputObject.getClass().getName());
                            } catch (Throwable ignore) {
                            }
                        }

                        // We want to track the size of each object even if it is invalid data
                        connection.statistic.addReceivedBytes(size);

                        // We want to track the messages also before the checks, so do it early...
                        Message message = null;
                        if (rawInputObject instanceof Message) {
                            message = (Message) rawInputObject;
                            connection.statistic.addReceivedMessage((Message) rawInputObject);
                        }


                        // First we check the size
                        boolean exceeds;
                        if (rawInputObject instanceof GetDataResponse) {
                            exceeds = size > MAX_MSG_SIZE_GET_DATA;
                            log.info("size={}; object={}", size, Utilities.toTruncatedString(rawInputObject.toString(), 100));
                        } else {
                            exceeds = size > MAX_MSG_SIZE;
                        }
                        if (exceeds)
                            log.warn("size > MAX_MSG_SIZE. size={}; object={}", size, message);

                        if (exceeds && reportInvalidRequest(RuleViolation.MAX_MSG_SIZE_EXCEEDED))
                            return;


                        // Then we check if data is of type Serializable (objectInputStream supports  
                        // Externalizable objects as well)
                        Serializable serializable;
                        if (rawInputObject instanceof Serializable) {
                            serializable = (Serializable) rawInputObject;
                        } else {
                            reportInvalidRequest(RuleViolation.INVALID_DATA_TYPE);
                            // We return anyway here independent of the return value of reportInvalidRequest
                            return;
                        }

                        // Then check data throttle limit. Do that for non-message type objects as well, 
                        // so that's why we use serializable here.
                        if (connection.violatesThrottleLimit(serializable) && reportInvalidRequest(RuleViolation.THROTTLE_LIMIT_EXCEEDED))
                            return;

                        // We do the message type check after the size/throttle checks. 
                        // The type check was done already earlier so we only check if message is not null.
                        if (message == null) {
                            reportInvalidRequest(RuleViolation.INVALID_DATA_TYPE);
                            // We return anyway here independent of the return value of reportInvalidRequest
                            return;
                        }

                        // Check P2P network ID
                        int messageVersion = message.getMessageVersion();
                        int p2PMessageVersion = Version.getP2PMessageVersion();
                        if (messageVersion != p2PMessageVersion) {
                            log.warn("message.getMessageVersion()=" + messageVersion);
                            log.warn("Version.getP2PMessageVersion()=" + p2PMessageVersion);
                            log.warn("message=" + message);
                            reportInvalidRequest(RuleViolation.WRONG_NETWORK_ID);
                            // We return anyway here independent of the return value of reportInvalidRequest
                            return;
                        }

                        if (sharedModel.getSupportedCapabilities() == null && message instanceof SupportedCapabilitiesMessage)
                            sharedModel.setSupportedCapabilities(((SupportedCapabilitiesMessage) message).getSupportedCapabilities());

                        if (message instanceof CloseConnectionMessage) {
                            // If we get a CloseConnectionMessage we shut down
                            log.debug("CloseConnectionMessage received. Reason={}\n\t" +
                                    "connection={}", ((CloseConnectionMessage) message).reason, connection);
                            stop();
                            if (CloseConnectionReason.PEER_BANNED.name().equals(((CloseConnectionMessage) message).reason)) {
                                log.warn("We got shut down because we are banned by the other peer. (InputHandler.run CloseConnectionMessage)");
                                sharedModel.shutDown(CloseConnectionReason.PEER_BANNED);
                            } else {
                                sharedModel.shutDown(CloseConnectionReason.CLOSE_REQUESTED_BY_PEER);
                            }
                        } else if (!stopped) {
                            // We don't want to get the activity ts updated by ping/pong msg
                            if (!(message instanceof KeepAliveMessage))
                                connection.statistic.updateLastActivityTimestamp();

                            if (message instanceof GetDataRequest)
                                connection.setPeerType(PeerType.INITIAL_DATA_REQUEST);

                            // First a seed node gets a message from a peer (PreliminaryDataRequest using
                            // AnonymousMessage interface) which does not have its hidden service
                            // published, so it does not know its address. As the IncomingConnection does not have the
                            // peersNodeAddress set that connection cannot be used for outgoing messages until we 
                            // get the address set.
                            // At the data update message (DataRequest using SendersNodeAddressMessage interface) 
                            // after the HS is published we get the peer's address set.

                            // There are only those messages used for new connections to a peer:
                            // 1. PreliminaryDataRequest
                            // 2. DataRequest (implements SendersNodeAddressMessage)
                            // 3. GetPeersRequest (implements SendersNodeAddressMessage)
                            // 4. DirectMessage (implements SendersNodeAddressMessage)
                            if (message instanceof SendersNodeAddressMessage) {
                                NodeAddress senderNodeAddress = ((SendersNodeAddressMessage) message).getSenderNodeAddress();
                                // We must not shut down a banned peer at that moment as it would trigger a connection termination 
                                // and we could not send the CloseConnectionMessage.
                                // We shut down a banned peer at the next step at setPeersNodeAddress().

                                Optional<NodeAddress> peersNodeAddressOptional = connection.getPeersNodeAddressOptional();
                                if (peersNodeAddressOptional.isPresent()) {
                                    // If we have already the peers address we check again if it matches our stored one
                                    checkArgument(peersNodeAddressOptional.get().equals(senderNodeAddress),
                                            "senderNodeAddress not matching connections peer address.\n\t" +
                                                    "message=" + message);
                                } else {
                                    connection.setPeersNodeAddress(senderNodeAddress);
                                }
                            }

                            if (message instanceof PrefixedSealedAndSignedMessage)
                                connection.setPeerType(Connection.PeerType.DIRECT_MSG_PEER);

                            messageListener.onMessage(message, connection);
                        }
                    } catch (ClassNotFoundException | NoClassDefFoundError e) {
                        log.warn(e.getMessage());
                        e.printStackTrace();
                        reportInvalidRequest(RuleViolation.INVALID_DATA_TYPE);
                        return;
                    } catch (IOException e) {
                        stop();
                        sharedModel.handleConnectionException(e);
                    } catch (Throwable t) {
                        t.printStackTrace();
                        stop();
                        if (sharedModel != null)
                            sharedModel.handleConnectionException(new Exception(t));
                    }
                }
            } catch (Throwable t) {
                if (!(t instanceof OptionalDataException))
                    t.printStackTrace();
                stop();
                sharedModel.handleConnectionException(new Exception(t));
            }
        }

        private boolean reportInvalidRequest(RuleViolation ruleViolation) {
            boolean causedShutDown = sharedModel.reportInvalidRequest(ruleViolation);
            if (causedShutDown)
                stop();
            return causedShutDown;
        }

        @Override
        public String toString() {
            return "InputHandler{" +
                    "sharedSpace=" + sharedModel +
                    ", port=" + portInfo +
                    ", stopped=" + stopped +
                    '}';
        }
    }
}
