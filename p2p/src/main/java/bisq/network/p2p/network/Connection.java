/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.network.p2p.network;

import bisq.network.p2p.BundleOfEnvelopes;
import bisq.network.p2p.CloseConnectionMessage;
import bisq.network.p2p.ExtendedDataSizePermission;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.PrefixedSealedAndSignedMessage;
import bisq.network.p2p.SendersNodeAddressMessage;
import bisq.network.p2p.SupportedCapabilitiesMessage;
import bisq.network.p2p.peers.BanList;
import bisq.network.p2p.peers.getdata.messages.GetDataRequest;
import bisq.network.p2p.peers.getdata.messages.GetDataResponse;
import bisq.network.p2p.peers.keepalive.messages.KeepAliveMessage;
import bisq.network.p2p.peers.keepalive.messages.Ping;
import bisq.network.p2p.storage.messages.AddDataMessage;
import bisq.network.p2p.storage.messages.AddPersistableNetworkPayloadMessage;
import bisq.network.p2p.storage.messages.RefreshOfferMessage;
import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.Proto;
import bisq.common.UserThread;
import bisq.common.app.Capabilities;
import bisq.common.app.Capability;
import bisq.common.app.HasCapabilities;
import bisq.common.app.Version;
import bisq.common.proto.ProtobufferException;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.network.NetworkProtoResolver;
import bisq.common.util.Utilities;

import javax.inject.Inject;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Uninterruptibles;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.OptionalDataException;
import java.io.StreamCorruptedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import java.lang.ref.WeakReference;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.Nullable;

import static bisq.network.p2p.network.ConnectionConfig.MSG_THROTTLE_PER_10_SEC;
import static bisq.network.p2p.network.ConnectionConfig.MSG_THROTTLE_PER_SEC;
import static bisq.network.p2p.network.ConnectionConfig.SEND_MSG_THROTTLE_SLEEP;
import static bisq.network.p2p.network.ConnectionConfig.SEND_MSG_THROTTLE_TRIGGER;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Connection is created by the server thread or by sendMessage from NetworkNode.
 * All handlers are called on User thread.
 */
@Slf4j
public class Connection implements HasCapabilities, Runnable, MessageListener {

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

    @Inject
    private static ConnectionConfig connectionConfig;

    // Leaving some constants package-private for tests to know limits.
    private static final int PERMITTED_MESSAGE_SIZE = 200 * 1024;                       // 200 kb
    private static final int MAX_PERMITTED_MESSAGE_SIZE = 10 * 1024 * 1024;             // 10 MB (425 offers resulted in about 660 kb, mailbox msg will add more to it) offer has usually 2 kb, mailbox 3kb.
    //TODO decrease limits again after testing
    private static final int SOCKET_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(120);

    public static int getPermittedMessageSize() {
        return PERMITTED_MESSAGE_SIZE;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final Socket socket;
    // private final MessageListener messageListener;
    private final ConnectionListener connectionListener;
    @Getter
    private final String uid;
    private final ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "Connection.java executor-service"));
    // holder of state shared between InputHandler and Connection
    @Getter
    private final Statistic statistic;
    private final int msgThrottlePer10Sec;
    private final int msgThrottlePerSec;
    private final int sendMsgThrottleTrigger;
    private final int sendMsgThrottleSleep;

    // set in init
    private SynchronizedProtoOutputStream protoOutputStream;

    // mutable data, set from other threads but not changed internally.
    @Getter
    private Optional<NodeAddress> peersNodeAddressOptional = Optional.empty();
    @Getter
    private volatile boolean stopped;

    // Use Peer as default, in case of other types they will set it as soon as possible.
    @Getter
    private PeerType peerType = PeerType.PEER;
    @Getter
    private final ObjectProperty<NodeAddress> peersNodeAddressProperty = new SimpleObjectProperty<>();
    private final List<Long> messageTimeStamps = new ArrayList<>();
    private final CopyOnWriteArraySet<MessageListener> messageListeners = new CopyOnWriteArraySet<>();
    private volatile long lastSendTimeStamp = 0;
    private final CopyOnWriteArraySet<WeakReference<SupportedCapabilitiesListener>> capabilitiesListeners = new CopyOnWriteArraySet<>();

    @Getter
    private RuleViolation ruleViolation;
    private final ConcurrentHashMap<RuleViolation, Integer> ruleViolations = new ConcurrentHashMap<>();

    private final Capabilities capabilities = new Capabilities();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    Connection(Socket socket,
               MessageListener messageListener,
               ConnectionListener connectionListener,
               @Nullable NodeAddress peersNodeAddress,
               NetworkProtoResolver networkProtoResolver) {
        this.socket = socket;
        this.connectionListener = connectionListener;
        uid = UUID.randomUUID().toString();
        statistic = new Statistic();

        if (connectionConfig == null)
            connectionConfig = new ConnectionConfig(MSG_THROTTLE_PER_SEC, MSG_THROTTLE_PER_10_SEC, SEND_MSG_THROTTLE_TRIGGER, SEND_MSG_THROTTLE_SLEEP);
        msgThrottlePerSec = connectionConfig.getMsgThrottlePerSec();
        msgThrottlePer10Sec = connectionConfig.getMsgThrottlePer10Sec();
        sendMsgThrottleTrigger = connectionConfig.getSendMsgThrottleTrigger();
        sendMsgThrottleSleep = connectionConfig.getSendMsgThrottleSleep();

        addMessageListener(messageListener);

        this.networkProtoResolver = networkProtoResolver;
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
            protoOutputStream = new SynchronizedProtoOutputStream(socket.getOutputStream(), statistic);
            protoInputStream = socket.getInputStream();
            // We create a thread for handling inputStream data
            singleThreadExecutor.submit(this);

            if (peersNodeAddress != null)
                setPeersNodeAddress(peersNodeAddress);

            UserThread.execute(() -> connectionListener.onConnection(this));
        } catch (Throwable e) {
            handleException(e);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Capabilities getCapabilities() {
        return capabilities;
    }

    private final Object lock = new Object();
    private final Queue<BundleOfEnvelopes> queueOfBundles = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService bundleSender = Executors.newSingleThreadScheduledExecutor();

    // Called from various threads
    public void sendMessage(NetworkEnvelope networkEnvelope) {
        log.debug(">> Send networkEnvelope of type: " + networkEnvelope.getClass().getSimpleName());

        if (!stopped) {
            if (noCapabilityRequiredOrCapabilityIsSupported(networkEnvelope)) {
                try {
                    String peersNodeAddress = peersNodeAddressOptional.map(NodeAddress::toString).orElse("null");

                    protobuf.NetworkEnvelope proto = networkEnvelope.toProtoNetworkEnvelope();
                    log.trace("Sending message: {}", Utilities.toTruncatedString(proto.toString(), 10000));

                    if (networkEnvelope instanceof Ping | networkEnvelope instanceof RefreshOfferMessage) {
                        // pings and offer refresh msg we don't want to log in production
                        log.trace("\n\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n" +
                                        "Sending direct message to peer" +
                                        "Write object to outputStream to peer: {} (uid={})\ntruncated message={} / size={}" +
                                        "\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n",
                                peersNodeAddress, uid, proto.toString(), proto.getSerializedSize());
                    } else if (networkEnvelope instanceof PrefixedSealedAndSignedMessage && peersNodeAddressOptional.isPresent()) {
                        setPeerType(Connection.PeerType.DIRECT_MSG_PEER);

                        log.debug("\n\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n" +
                                        "Sending direct message to peer" +
                                        "Write object to outputStream to peer: {} (uid={})\ntruncated message={} / size={}" +
                                        "\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n",
                                peersNodeAddress, uid, Utilities.toTruncatedString(networkEnvelope), -1);
                    } else if (networkEnvelope instanceof GetDataResponse && ((GetDataResponse) networkEnvelope).isGetUpdatedDataResponse()) {
                        setPeerType(Connection.PeerType.PEER);
                    } else {
                        log.debug("\n\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n" +
                                        "Write object to outputStream to peer: {} (uid={})\ntruncated message={} / size={}" +
                                        "\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n",
                                peersNodeAddress, uid, Utilities.toTruncatedString(networkEnvelope), proto.getSerializedSize());
                    }

                    // Throttle outbound network_messages
                    long now = System.currentTimeMillis();
                    long elapsed = now - lastSendTimeStamp;
                    if (elapsed < sendMsgThrottleTrigger) {
                        log.debug("We got 2 sendMessage requests in less than {} ms. We set the thread to sleep " +
                                        "for {} ms to avoid flooding our peer. lastSendTimeStamp={}, now={}, elapsed={}, networkEnvelope={}",
                                sendMsgThrottleTrigger, sendMsgThrottleSleep, lastSendTimeStamp, now, elapsed,
                                networkEnvelope.getClass().getSimpleName());

                        // check if BundleOfEnvelopes is supported
                        if (getCapabilities().containsAll(new Capabilities(Capability.BUNDLE_OF_ENVELOPES))) {
                            synchronized (lock) {
                                // check if current envelope fits size
                                // - no? create new envelope
                                if (queueOfBundles.isEmpty() || queueOfBundles.element().toProtoNetworkEnvelope().getSerializedSize() + networkEnvelope.toProtoNetworkEnvelope().getSerializedSize() > MAX_PERMITTED_MESSAGE_SIZE * 0.9) {
                                    // - no? create a bucket
                                    queueOfBundles.add(new BundleOfEnvelopes());

                                    // - and schedule it for sending
                                    lastSendTimeStamp += sendMsgThrottleSleep;

                                    bundleSender.schedule(() -> {
                                        if (!stopped) {
                                            synchronized (lock) {
                                                BundleOfEnvelopes current = queueOfBundles.poll();
                                                if (current != null) {
                                                    if (current.getEnvelopes().size() == 1) {
                                                        protoOutputStream.writeEnvelope(current.getEnvelopes().get(0));
                                                    } else {
                                                        protoOutputStream.writeEnvelope(current);
                                                    }
                                                }
                                            }
                                        }
                                    }, lastSendTimeStamp - now, TimeUnit.MILLISECONDS);
                                }

                                // - yes? add to bucket
                                queueOfBundles.element().add(networkEnvelope);
                            }
                            return;
                        }

                        Thread.sleep(sendMsgThrottleSleep);
                    }

                    lastSendTimeStamp = now;

                    if (!stopped) {
                        protoOutputStream.writeEnvelope(networkEnvelope);
                    }
                } catch (Throwable t) {
                    handleException(t);
                }
            }
        } else {
            log.debug("called sendMessage but was already stopped");
        }
    }

    public boolean noCapabilityRequiredOrCapabilityIsSupported(Proto msg) {
        boolean result;
        if (msg instanceof AddDataMessage) {
            final ProtectedStoragePayload protectedStoragePayload = (((AddDataMessage) msg).getProtectedStorageEntry()).getProtectedStoragePayload();
            result = !(protectedStoragePayload instanceof CapabilityRequiringPayload);
            if (!result)
                result = capabilities.containsAll(((CapabilityRequiringPayload) protectedStoragePayload).getRequiredCapabilities());
        } else if (msg instanceof AddPersistableNetworkPayloadMessage) {
            final PersistableNetworkPayload persistableNetworkPayload = ((AddPersistableNetworkPayloadMessage) msg).getPersistableNetworkPayload();
            result = !(persistableNetworkPayload instanceof CapabilityRequiringPayload);
            if (!result)
                result = capabilities.containsAll(((CapabilityRequiringPayload) persistableNetworkPayload).getRequiredCapabilities());
        } else if (msg instanceof CapabilityRequiringPayload) {
            result = capabilities.containsAll(((CapabilityRequiringPayload) msg).getRequiredCapabilities());
        } else {
            result = true;
        }

        if (!result) {
            if (capabilities.size() > 1) {
                Proto data = msg;
                if (msg instanceof AddDataMessage) {
                    data = ((AddDataMessage) msg).getProtectedStorageEntry().getProtectedStoragePayload();
                }
                // Monitoring nodes have only one capability set, we don't want to log those
                log.debug("We did not send the message because the peer does not support our required capabilities. " +
                                "messageClass={}, peer={}, peers supportedCapabilities={}",
                        data.getClass().getSimpleName(), peersNodeAddressOptional, capabilities);
            }
        }
        return result;
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

    public void addWeakCapabilitiesListener(SupportedCapabilitiesListener listener) {
        capabilitiesListeners.add(new WeakReference<>(listener));
    }

    private boolean violatesThrottleLimit() {
        long now = System.currentTimeMillis();

        messageTimeStamps.add(now);

        // clean list
        while (messageTimeStamps.size() > msgThrottlePer10Sec)
            messageTimeStamps.remove(0);

        return violatesThrottleLimit(now, 1, msgThrottlePerSec) || violatesThrottleLimit(now, 10, msgThrottlePer10Sec);
    }

    private boolean violatesThrottleLimit(long now, int seconds, int messageCountLimit) {
        if (messageTimeStamps.size() >= messageCountLimit) {

            // find the entry in the message timestamp history which determines whether we overshot the limit or not
            long compareValue = messageTimeStamps.get(messageTimeStamps.size() - messageCountLimit);

            // if duration < seconds sec we received too much network_messages
            if (now - compareValue < TimeUnit.SECONDS.toMillis(seconds)) {
                log.error("violatesThrottleLimit {}/{} second(s)", messageCountLimit, seconds);

                return true;
            }
        }

        return false;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Only receive non - CloseConnectionMessage network_messages
    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        checkArgument(connection.equals(this));

        if (networkEnvelope instanceof BundleOfEnvelopes)
            for (NetworkEnvelope current : ((BundleOfEnvelopes) networkEnvelope).getEnvelopes()) {
                UserThread.execute(() -> messageListeners.forEach(e -> e.onMessage(current, connection)));
            }
        else
            UserThread.execute(() -> messageListeners.forEach(e -> e.onMessage(networkEnvelope, connection)));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setPeerType(PeerType peerType) {
        log.debug("setPeerType: peerType={}, nodeAddressOpt={}", peerType.toString(), peersNodeAddressOptional);
        this.peerType = peerType;
    }

    private void setPeersNodeAddress(NodeAddress peerNodeAddress) {
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

        if (BanList.isBanned(peerNodeAddress)) {
            log.warn("We detected a connection to a banned peer. We will close that connection. (setPeersNodeAddress)");
            reportInvalidRequest(RuleViolation.PEER_BANNED);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean hasPeersNodeAddress() {
        return peersNodeAddressOptional.isPresent();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // ShutDown
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void shutDown(CloseConnectionReason closeConnectionReason) {
        shutDown(closeConnectionReason, null);
    }

    public void shutDown(CloseConnectionReason closeConnectionReason, @Nullable Runnable shutDownCompleteHandler) {
        log.debug("shutDown: nodeAddressOpt={}, closeConnectionReason={}", this.peersNodeAddressOptional.orElse(null), closeConnectionReason);
        if (!stopped) {
            String peersNodeAddress = peersNodeAddressOptional.map(NodeAddress::toString).orElse("null");
            log.debug("\n\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n" +
                    "ShutDown connection:"
                    + "\npeersNodeAddress=" + peersNodeAddress
                    + "\ncloseConnectionReason=" + closeConnectionReason
                    + "\nuid=" + uid
                    + "\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n");

            if (closeConnectionReason.sendCloseMessage) {
                new Thread(() -> {
                    try {
                        String reason = closeConnectionReason == CloseConnectionReason.RULE_VIOLATION ?
                                getRuleViolation().name() : closeConnectionReason.name();
                        sendMessage(new CloseConnectionMessage(reason));

                        stopped = true;

                        //noinspection UnstableApiUsage
                        Uninterruptibles.sleepUninterruptibly(200, TimeUnit.MILLISECONDS);
                    } catch (Throwable t) {
                        log.error(t.getMessage());
                        t.printStackTrace();
                    } finally {
                        stopped = true;
                        UserThread.execute(() -> doShutDown(closeConnectionReason, shutDownCompleteHandler));
                    }
                }, "Connection:SendCloseConnectionMessage-" + this.uid).start();
            } else {
                stopped = true;
                doShutDown(closeConnectionReason, shutDownCompleteHandler);
            }
        } else {
            //TODO find out why we get called that
            log.debug("stopped was already at shutDown call");
            UserThread.execute(() -> doShutDown(closeConnectionReason, shutDownCompleteHandler));
        }
    }

    private void doShutDown(CloseConnectionReason closeConnectionReason, @Nullable Runnable shutDownCompleteHandler) {
        // Use UserThread.execute as its not clear if that is called from a non-UserThread
        UserThread.execute(() -> connectionListener.onDisconnect(closeConnectionReason, this));
        try {
            socket.close();
        } catch (SocketException e) {
            log.trace("SocketException at shutdown might be expected " + e.getMessage());
        } catch (IOException e) {
            log.error("Exception at shutdown. " + e.getMessage());
            e.printStackTrace();
        } finally {
            protoOutputStream.onConnectionShutdown();

            try {
                protoInputStream.close();
            } catch (IOException e) {
                log.error(e.getMessage());
                e.printStackTrace();
            }

            //noinspection UnstableApiUsage
            MoreExecutors.shutdownAndAwaitTermination(singleThreadExecutor, 500, TimeUnit.MILLISECONDS);
            MoreExecutors.shutdownAndAwaitTermination(bundleSender, 500, TimeUnit.MILLISECONDS);

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

        return uid.equals(that.uid);

    }

    @Override
    public int hashCode() {
        return uid.hashCode();
    }

    @Override
    public String toString() {
        return "Connection{" +
                "peerAddress=" + peersNodeAddressOptional +
                ", peerType=" + peerType +
                ", connectionType=" + (this instanceof InboundConnection ? "InboundConnection" : "OutboundConnection") +
                ", uid='" + uid + '\'' +
                '}';
    }

    @SuppressWarnings("unused")
    public String printDetails() {
        String portInfo;
        if (socket.getLocalPort() == 0)
            portInfo = "port=" + socket.getPort();
        else
            portInfo = "localPort=" + socket.getLocalPort() + "/port=" + socket.getPort();

        return "Connection{" +
                "peerAddress=" + peersNodeAddressOptional +
                ", peerType=" + peerType +
                ", portInfo=" + portInfo +
                ", uid='" + uid + '\'' +
                ", ruleViolation=" + ruleViolation +
                ", ruleViolations=" + ruleViolations +
                ", supportedCapabilities=" + capabilities +
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


    public boolean reportInvalidRequest(RuleViolation ruleViolation) {
        log.warn("We got reported the ruleViolation {} at connection {}", ruleViolation, this);
        int numRuleViolations;
        numRuleViolations = ruleViolations.getOrDefault(ruleViolation, 0);

        numRuleViolations++;
        ruleViolations.put(ruleViolation, numRuleViolations);

        if (numRuleViolations >= ruleViolation.maxTolerance) {
            log.warn("We close connection as we received too many corrupt requests.\n" +
                    "numRuleViolations={}\n\t" +
                    "corruptRequest={}\n\t" +
                    "corruptRequests={}\n\t" +
                    "connection={}", numRuleViolations, ruleViolation, ruleViolations.toString(), this);
            this.ruleViolation = ruleViolation;
            if (ruleViolation == RuleViolation.PEER_BANNED) {
                log.warn("We close connection due RuleViolation.PEER_BANNED. peersNodeAddress={}", getPeersNodeAddressOptional());
                shutDown(CloseConnectionReason.PEER_BANNED);
            } else if (ruleViolation == RuleViolation.INVALID_CLASS) {
                log.warn("We close connection due RuleViolation.INVALID_CLASS");
                shutDown(CloseConnectionReason.INVALID_CLASS_RECEIVED);
            } else {
                log.warn("We close connection due RuleViolation.RULE_VIOLATION");
                shutDown(CloseConnectionReason.RULE_VIOLATION);
            }

            return true;
        } else {
            return false;
        }
    }

    private void handleException(Throwable e) {
        CloseConnectionReason closeConnectionReason;

        if (e instanceof SocketException) {
            if (socket.isClosed())
                closeConnectionReason = CloseConnectionReason.SOCKET_CLOSED;
            else
                closeConnectionReason = CloseConnectionReason.RESET;

            log.info("SocketException (expected if connection lost). closeConnectionReason={}; connection={}", closeConnectionReason, this);
        } else if (e instanceof SocketTimeoutException || e instanceof TimeoutException) {
            closeConnectionReason = CloseConnectionReason.SOCKET_TIMEOUT;
            log.info("Shut down caused by exception {} on connection={}", e.toString(), this);
        } else if (e instanceof EOFException) {
            closeConnectionReason = CloseConnectionReason.TERMINATED;
            log.warn("Shut down caused by exception {} on connection={}", e.toString(), this);
        } else if (e instanceof OptionalDataException || e instanceof StreamCorruptedException) {
            closeConnectionReason = CloseConnectionReason.CORRUPTED_DATA;
            log.warn("Shut down caused by exception {} on connection={}", e.toString(), this);
        } else {
            // TODO sometimes we get StreamCorruptedException, OptionalDataException, IllegalStateException
            closeConnectionReason = CloseConnectionReason.UNKNOWN_EXCEPTION;
            log.warn("Unknown reason for exception at socket: {}\n\t" +
                            "peer={}\n\t" +
                            "Exception={}",
                    socket.toString(),
                    this.peersNodeAddressOptional,
                    e.toString());
            e.printStackTrace();
        }
        shutDown(closeConnectionReason);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // InputHandler
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Runs in same thread as Connection, receives a message, performs several checks on it
    // (including throttling limits, validity and statistics)
    // and delivers it to the message listener given in the constructor.
    private InputStream protoInputStream;
    private final NetworkProtoResolver networkProtoResolver;

    private long lastReadTimeStamp;
    private boolean threadNameSet;

    @Override
    public void run() {
        try {
            Thread.currentThread().setName("InputHandler");
            while (!stopped && !Thread.currentThread().isInterrupted()) {
                if (!threadNameSet && getPeersNodeAddressOptional().isPresent()) {
                    Thread.currentThread().setName("InputHandler-" + getPeersNodeAddressOptional().get().getFullAddress());
                    threadNameSet = true;
                }
                try {
                    if (socket != null &&
                            socket.isClosed()) {
                        log.warn("Socket is null or closed socket={}", socket);
                        shutDown(CloseConnectionReason.SOCKET_CLOSED);
                        return;
                    }

                    // Throttle inbound network_messages
                    long now = System.currentTimeMillis();
                    long elapsed = now - lastReadTimeStamp;
                    if (elapsed < 10) {
                        log.debug("We got 2 network_messages received in less than 10 ms. We set the thread to sleep " +
                                        "for 20 ms to avoid getting flooded by our peer. lastReadTimeStamp={}, now={}, elapsed={}",
                                lastReadTimeStamp, now, elapsed);
                        Thread.sleep(20);
                    }

                    // Reading the protobuffer message from the inputStream
                    protobuf.NetworkEnvelope proto = protobuf.NetworkEnvelope.parseDelimitedFrom(protoInputStream);

                    if (proto == null) {
                        if (protoInputStream.read() == -1)
                            log.debug("proto is null because protoInputStream.read()=-1 (EOF). That is expected if client got stopped without proper shutdown.");
                        else
                            log.warn("proto is null. protoInputStream.read()=" + protoInputStream.read());
                        shutDown(CloseConnectionReason.NO_PROTO_BUFFER_ENV);
                        return;
                    }

                    NetworkEnvelope networkEnvelope = networkProtoResolver.fromProto(proto);
                    lastReadTimeStamp = now;
                    log.debug("<< Received networkEnvelope of type: {}", networkEnvelope.getClass().getSimpleName());
                    int size = proto.getSerializedSize();
                    // We comment out that part as only debug and trace log level is used. For debugging purposes
                    // we leave the code though.
                        /*if (networkEnvelope instanceof Pong || networkEnvelope instanceof RefreshOfferMessage) {
                            // We only log Pong and RefreshOfferMsg when in dev environment (trace)
                            log.trace("\n\n<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<\n" +
                                            "New data arrived at inputHandler of connection {}.\n" +
                                            "Received object (truncated)={} / size={}"
                                            + "\n<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<\n",
                                    connection,
                                    Utilities.toTruncatedString(proto.toString()),
                                    size);
                        } else {
                            // We want to log all incoming network_messages (except Pong and RefreshOfferMsg)
                            // so we log before the data type checks
                            //log.info("size={}; object={}", size, Utilities.toTruncatedString(rawInputObject.toString(), 100));
                            log.debug("\n\n<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<\n" +
                                            "New data arrived at inputHandler of connection {}.\n" +
                                            "Received object (truncated)={} / size={}"
                                            + "\n<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<\n",
                                    connection,
                                    Utilities.toTruncatedString(proto.toString()),
                                    size);
                        }*/

                    // We want to track the size of each object even if it is invalid data
                    statistic.addReceivedBytes(size);

                    // We want to track the network_messages also before the checks, so do it early...
                    statistic.addReceivedMessage(networkEnvelope);

                    // First we check the size
                    boolean exceeds;
                    if (networkEnvelope instanceof ExtendedDataSizePermission) {
                        exceeds = size > MAX_PERMITTED_MESSAGE_SIZE;
                    } else {
                        exceeds = size > PERMITTED_MESSAGE_SIZE;
                    }

                    if (networkEnvelope instanceof AddPersistableNetworkPayloadMessage &&
                            !((AddPersistableNetworkPayloadMessage) networkEnvelope).getPersistableNetworkPayload().verifyHashSize()) {
                        log.warn("PersistableNetworkPayload.verifyHashSize failed. hashSize={}; object={}",
                                ((AddPersistableNetworkPayloadMessage) networkEnvelope).getPersistableNetworkPayload().getHash().length,
                                Utilities.toTruncatedString(proto));
                        if (reportInvalidRequest(RuleViolation.MAX_MSG_SIZE_EXCEEDED))
                            return;
                    }

                    if (exceeds) {
                        log.warn("size > MAX_MSG_SIZE. size={}; object={}", size, Utilities.toTruncatedString(proto));

                        if (reportInvalidRequest(RuleViolation.MAX_MSG_SIZE_EXCEEDED))
                            return;
                    }

                    if (violatesThrottleLimit() && reportInvalidRequest(RuleViolation.THROTTLE_LIMIT_EXCEEDED))
                        return;

                    // Check P2P network ID
                    if (proto.getMessageVersion() != Version.getP2PMessageVersion()
                            && reportInvalidRequest(RuleViolation.WRONG_NETWORK_ID)) {
                        log.warn("RuleViolation.WRONG_NETWORK_ID. version of message={}, app version={}, " +
                                        "proto.toTruncatedString={}", proto.getMessageVersion(),
                                Version.getP2PMessageVersion(),
                                Utilities.toTruncatedString(proto.toString()));
                        return;
                    }

                    if (networkEnvelope instanceof SupportedCapabilitiesMessage) {
                        Capabilities supportedCapabilities = ((SupportedCapabilitiesMessage) networkEnvelope).getSupportedCapabilities();
                        if (supportedCapabilities != null) {
                            if (!capabilities.equals(supportedCapabilities)) {
                                capabilities.set(supportedCapabilities);

                                // Capabilities can be empty. We only check for mandatory if we get some capabilities.
                                if (!capabilities.isEmpty() && !Capabilities.hasMandatoryCapability(capabilities)) {
                                    String senderNodeAddress = networkEnvelope instanceof SendersNodeAddressMessage ?
                                            ((SendersNodeAddressMessage) networkEnvelope).getSenderNodeAddress().getFullAddress() :
                                            "[unknown address]";
                                    log.info("We close a connection to old node {}. " +
                                                    "Capabilities of old node: {}, networkEnvelope class name={}",
                                            senderNodeAddress, capabilities.prettyPrint(), networkEnvelope.getClass().getSimpleName());
                                    shutDown(CloseConnectionReason.MANDATORY_CAPABILITIES_NOT_SUPPORTED);
                                    return;
                                }

                                capabilitiesListeners.forEach(weakListener -> {
                                    SupportedCapabilitiesListener supportedCapabilitiesListener = weakListener.get();
                                    if (supportedCapabilitiesListener != null) {
                                        UserThread.execute(() -> supportedCapabilitiesListener.onChanged(supportedCapabilities));
                                    }
                                });
                            }
                        }
                    }

                    if (networkEnvelope instanceof CloseConnectionMessage) {
                        // If we get a CloseConnectionMessage we shut down
                        if (log.isDebugEnabled()) {
                            log.debug("CloseConnectionMessage received. Reason={}\n\t" +
                                    "connection={}", proto.getCloseConnectionMessage().getReason(), this);
                        }
                        if (CloseConnectionReason.PEER_BANNED.name().equals(proto.getCloseConnectionMessage().getReason())) {
                            log.warn("We got shut down because we are banned by the other peer. (InputHandler.run CloseConnectionMessage)");
                            shutDown(CloseConnectionReason.PEER_BANNED);
                        } else {
                            shutDown(CloseConnectionReason.CLOSE_REQUESTED_BY_PEER);
                        }
                        return;
                    } else if (!stopped) {
                        // We don't want to get the activity ts updated by ping/pong msg
                        if (!(networkEnvelope instanceof KeepAliveMessage))
                            statistic.updateLastActivityTimestamp();

                        if (networkEnvelope instanceof GetDataRequest)
                            setPeerType(PeerType.INITIAL_DATA_REQUEST);

                        // First a seed node gets a message from a peer (PreliminaryDataRequest using
                        // AnonymousMessage interface) which does not have its hidden service
                        // published, so it does not know its address. As the IncomingConnection does not have the
                        // peersNodeAddress set that connection cannot be used for outgoing network_messages until we
                        // get the address set.
                        // At the data update message (DataRequest using SendersNodeAddressMessage interface)
                        // after the HS is published we get the peer's address set.

                        // There are only those network_messages used for new connections to a peer:
                        // 1. PreliminaryDataRequest
                        // 2. DataRequest (implements SendersNodeAddressMessage)
                        // 3. GetPeersRequest (implements SendersNodeAddressMessage)
                        // 4. DirectMessage (implements SendersNodeAddressMessage)
                        if (networkEnvelope instanceof SendersNodeAddressMessage) {
                            NodeAddress senderNodeAddress = ((SendersNodeAddressMessage) networkEnvelope).getSenderNodeAddress();
                            if (senderNodeAddress != null) {
                                Optional<NodeAddress> peersNodeAddressOptional = getPeersNodeAddressOptional();
                                if (peersNodeAddressOptional.isPresent()) {
                                    // If we have already the peers address we check again if it matches our stored one
                                    checkArgument(peersNodeAddressOptional.get().equals(senderNodeAddress),
                                            "senderNodeAddress not matching connections peer address.\n\t" +
                                                    "message=" + networkEnvelope);
                                } else {
                                    // We must not shut down a banned peer at that moment as it would trigger a connection termination
                                    // and we could not send the CloseConnectionMessage.
                                    // We check for a banned peer inside setPeersNodeAddress() and shut down if banned.
                                    setPeersNodeAddress(senderNodeAddress);
                                }
                            }
                        }

                        if (networkEnvelope instanceof PrefixedSealedAndSignedMessage)
                            setPeerType(Connection.PeerType.DIRECT_MSG_PEER);

                        onMessage(networkEnvelope, this);
                    }
                } catch (InvalidClassException e) {
                    log.error(e.getMessage());
                    e.printStackTrace();
                    reportInvalidRequest(RuleViolation.INVALID_CLASS);
                } catch (ProtobufferException | NoClassDefFoundError e) {
                    log.error(e.getMessage());
                    e.printStackTrace();
                    reportInvalidRequest(RuleViolation.INVALID_DATA_TYPE);
                } catch (Throwable t) {
                    handleException(t);
                }
            }
        } catch (Throwable t) {
            handleException(t);
        }
    }
}
