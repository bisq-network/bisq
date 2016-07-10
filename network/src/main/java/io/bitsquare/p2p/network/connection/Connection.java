package io.bitsquare.p2p.network.connection;

import com.google.common.util.concurrent.CycleDetectingLockFactory;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Uninterruptibles;
import io.bitsquare.app.Log;
import io.bitsquare.common.ByteArrayUtils;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.messaging.PrefixedSealedAndSignedMessage;
import io.bitsquare.p2p.network.RuleViolation;
import io.bitsquare.p2p.network.Statistic;
import io.bitsquare.p2p.network.messages.CloseConnectionMessage;
import io.bitsquare.p2p.peers.keepalive.messages.KeepAliveMessage;
import io.bitsquare.p2p.peers.keepalive.messages.Ping;
import io.bitsquare.p2p.storage.messages.RefreshTTLMessage;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
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
        DIRECT_MSG_PEER
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Leaving some constants package-private for tests to know limits.
    static final int MAX_MSG_SIZE = 200 * 1024;                       // 200 kb
    static final int MAX_MSG_SIZE_GET_DATA = 6 * 1024 * 1024;         // 6 MB (425 offers resulted in about 660 kb, mailbox msg will add more to it) offer has usually 2 kb, mailbox 3kb.
    //TODO decrease limits again after testing
    static final int MSG_THROTTLE_PER_SEC = 200;              // With MAX_MSG_SIZE of 200kb results in bandwidth of 40MB/sec or 5 mbit/sec
    static final int MSG_THROTTLE_PER_10_SEC = 1000;          // With MAX_MSG_SIZE of 200kb results in bandwidth of 20MB/sec or 2.5 mbit/sec
    private static final int SOCKET_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(90);

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
    @Getter
    private final String uid;
    private final ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
    private final ReentrantLock objectOutputStreamLock = cycleDetectingLockFactory.newReentrantLock("objectOutputStreamLock");
    // holder of state shared between InputHandler and Connection
    private final SharedModel sharedModel;
    @Getter
    private final Statistic statistic;

    // set in init
    private InputHandler inputHandler;
    private ObjectOutputStream objectOutputStream;

    // mutable data, set from other threads but not changed internally.
    @Getter
    private Optional<NodeAddress> peersNodeAddressOptional = Optional.empty();
    @Getter
    private volatile boolean stopped;
    @Getter
    private PeerType peerType;
    @Getter
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
            try {
                Log.traceCall();
                // Throttle outbound messages
                long now = System.currentTimeMillis();
                long elapsed = now - lastSendTimeStamp;
                if (elapsed < 20) {
                    log.info("We got 2 sendMessage requests in less than 20 ms. We set the thread to sleep " +
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
                            peersNodeAddress, uid, StringUtils.abbreviate(message.toString(), 100), size);
                } else if (message instanceof PrefixedSealedAndSignedMessage && peersNodeAddressOptional.isPresent()) {
                    setPeerType(Connection.PeerType.DIRECT_MSG_PEER);

                    log.info("\n\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n" +
                                    "Sending direct message to peer" +
                                    "Write object to outputStream to peer: {} (uid={})\ntruncated message={} / size={}" +
                                    "\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n",
                            peersNodeAddress, uid, StringUtils.abbreviate(message.toString(), 100), size);
                } else {
                    log.info("\n\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n" +
                                    "Write object to outputStream to peer: {} (uid={})\ntruncated message={} / size={}" +
                                    "\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n",
                            peersNodeAddress, uid, StringUtils.abbreviate(message.toString(), 100), size);
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
        } else {
            log.debug("called sendMessage but was already stopped");
        }
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

    public boolean violatesThrottleLimit(Serializable serializable) {
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
            log.info("\n\n############################################################\n" +
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

    public boolean hasPeersNodeAddress() {
        return peersNodeAddressOptional.isPresent();
    }

    public RuleViolation getRuleViolation() {
        return sharedModel.getRuleViolation();
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
            log.info("\n\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n" +
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



    ///////////////////////////////////////////////////////////////////////////////////////////
    // InputHandler
    ///////////////////////////////////////////////////////////////////////////////////////////

}
