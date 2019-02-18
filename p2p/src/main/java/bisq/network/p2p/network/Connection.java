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
import bisq.common.app.Log;
import bisq.common.app.Version;
import bisq.common.proto.ProtobufferException;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.network.NetworkProtoResolver;
import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import io.bisq.generated.protobuffer.PB;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Uninterruptibles;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import java.lang.ref.WeakReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Connection is created by the server thread or by sendMessage from NetworkNode.
 * All handlers are called on User thread.
 */
@Slf4j
public class Connection implements MessageListener {

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
    static final int PERMITTED_MESSAGE_SIZE = 200 * 1024;                       // 200 kb
    static final int MAX_PERMITTED_MESSAGE_SIZE = 10 * 1024 * 1024;         // 10 MB (425 offers resulted in about 660 kb, mailbox msg will add more to it) offer has usually 2 kb, mailbox 3kb.
    //TODO decrease limits again after testing
    static final int MSG_THROTTLE_PER_SEC = 200;              // With MAX_MSG_SIZE of 200kb results in bandwidth of 40MB/sec or 5 mbit/sec
    static final int MSG_THROTTLE_PER_10_SEC = 1000;          // With MAX_MSG_SIZE of 200kb results in bandwidth of 20MB/sec or 2.5 mbit/sec
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
    private final String portInfo;
    private final String uid;
    private final ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
    // holder of state shared between InputHandler and Connection
    private final SharedModel sharedModel;
    private final Statistic statistic;

    // set in init
    private InputHandler inputHandler;
    private SynchronizedProtoOutputStream protoOutputStream;

    // mutable data, set from other threads but not changed internally.
    private Optional<NodeAddress> peersNodeAddressOptional = Optional.<NodeAddress>empty();
    private volatile boolean stopped;
    private PeerType peerType;
    private final ObjectProperty<NodeAddress> peersNodeAddressProperty = new SimpleObjectProperty<>();
    private final List<Tuple2<Long, NetworkEnvelope>> messageTimeStamps = new ArrayList<>();
    private final CopyOnWriteArraySet<MessageListener> messageListeners = new CopyOnWriteArraySet<>();
    private volatile long lastSendTimeStamp = 0;
    private final CopyOnWriteArraySet<WeakReference<SupportedCapabilitiesListener>> capabilitiesListeners = new CopyOnWriteArraySet<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    Connection(Socket socket, MessageListener messageListener, ConnectionListener connectionListener,
               @Nullable NodeAddress peersNodeAddress, NetworkProtoResolver networkProtoResolver) {
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

        init(peersNodeAddress, networkProtoResolver);
    }

    private void init(@Nullable NodeAddress peersNodeAddress, NetworkProtoResolver networkProtoResolver) {
        try {
            socket.setSoTimeout(SOCKET_TIMEOUT);
            // Need to access first the ObjectOutputStream otherwise the ObjectInputStream would block
            // See: https://stackoverflow.com/questions/5658089/java-creating-a-new-objectinputstream-blocks/5658109#5658109
            // When you construct an ObjectInputStream, in the constructor the class attempts to read a header that
            // the associated ObjectOutputStream on the other end of the connection has written.
            // It will not return until that header has been read.
            protoOutputStream = new SynchronizedProtoOutputStream(socket.getOutputStream(), statistic);
            InputStream protoInputStream = socket.getInputStream();
            // We create a thread for handling inputStream data
            inputHandler = new InputHandler(sharedModel, protoInputStream, portInfo, this, networkProtoResolver);
            singleThreadExecutor.submit(inputHandler);

            // Use Peer as default, in case of other types they will set it as soon as possible.
            peerType = PeerType.PEER;

            if (peersNodeAddress != null)
                setPeersNodeAddress(peersNodeAddress);

            log.trace("New connection created: " + this.toString());

            UserThread.execute(() -> connectionListener.onConnection(this));

        } catch (Throwable e) {
            handleException(e);
        }
    }

    private void handleException(Throwable e) {
        if (sharedModel != null)
            sharedModel.handleConnectionException(e);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Called from various threads
    public void sendMessage(NetworkEnvelope networkEnvelope) {
        log.debug(">> Send networkEnvelope of type: " + networkEnvelope.getClass().getSimpleName());

        if (!stopped) {
            if (noCapabilityRequiredOrCapabilityIsSupported(networkEnvelope)) {
                try {
                    Log.traceCall();

                    // Throttle outbound network_messages
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

                    PB.NetworkEnvelope proto = networkEnvelope.toProtoNetworkEnvelope();
                    log.debug("Sending message: {}", Utilities.toTruncatedString(proto.toString(), 10000));

                    if (networkEnvelope instanceof Ping | networkEnvelope instanceof RefreshOfferMessage) {
                        // pings and offer refresh msg we dont want to log in production
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

                    if (!stopped) {
                        protoOutputStream.writeEnvelope(networkEnvelope);
                    }
                } catch (Throwable t) {
                    handleException(t);
                }
            } else {
                log.info("We did not send the message because the peer does not support our required capabilities. message={}, peers supportedCapabilities={}", networkEnvelope, sharedModel.getSupportedCapabilities());
            }
        } else {
            log.debug("called sendMessage but was already stopped");
        }
    }

    public boolean noCapabilityRequiredOrCapabilityIsSupported(Proto msg) {
        return !isCapabilityRequired(msg) || isCapabilitySupported(msg);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isCapabilityRequired(Proto msg) {
        if (msg instanceof AddDataMessage) {
            final ProtectedStoragePayload protectedStoragePayload = (((AddDataMessage) msg).getProtectedStorageEntry()).getProtectedStoragePayload();
            return protectedStoragePayload instanceof CapabilityRequiringPayload;
        } else if (msg instanceof AddPersistableNetworkPayloadMessage) {
            final PersistableNetworkPayload persistableNetworkPayload = ((AddPersistableNetworkPayloadMessage) msg).getPersistableNetworkPayload();
            return persistableNetworkPayload instanceof CapabilityRequiringPayload;
        } else {
            return msg instanceof CapabilityRequiringPayload;
        }
    }

    private boolean isCapabilitySupported(Proto msg) {
        if (msg instanceof AddDataMessage) {
            final ProtectedStoragePayload protectedStoragePayload = (((AddDataMessage) msg).getProtectedStorageEntry()).getProtectedStoragePayload();
            return protectedStoragePayload instanceof CapabilityRequiringPayload && isCapabilitySupported((CapabilityRequiringPayload) protectedStoragePayload);
        } else if (msg instanceof AddPersistableNetworkPayloadMessage) {
            final PersistableNetworkPayload persistableNetworkPayload = ((AddPersistableNetworkPayloadMessage) msg).getPersistableNetworkPayload();
            return persistableNetworkPayload instanceof CapabilityRequiringPayload && isCapabilitySupported((CapabilityRequiringPayload) persistableNetworkPayload);
        } else {
            return msg instanceof CapabilityRequiringPayload && isCapabilitySupported((CapabilityRequiringPayload) msg);
        }
    }

    private boolean isCapabilitySupported(CapabilityRequiringPayload payload) {
        return isCapabilitySupported(payload, sharedModel.getSupportedCapabilities());
    }

    public static boolean isCapabilitySupported(CapabilityRequiringPayload payload, List<Integer> supportedCapabilities) {
        final List<Integer> requiredCapabilities = payload.getRequiredCapabilities();
        return Capabilities.isCapabilitySupported(requiredCapabilities, supportedCapabilities);
    }

    @Nullable
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

    public void addWeakCapabilitiesListener(SupportedCapabilitiesListener listener) {
        capabilitiesListeners.add(new WeakReference<>(listener));
    }

    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public boolean reportIllegalRequest(RuleViolation ruleViolation) {
        return sharedModel.reportInvalidRequest(ruleViolation);
    }

    // TODO either use the argument or delete it
    private boolean violatesThrottleLimit(NetworkEnvelope networkEnvelope) {
        long now = System.currentTimeMillis();
        boolean violated = false;
        //TODO remove message storage after network is tested stable
        if (messageTimeStamps.size() >= MSG_THROTTLE_PER_SEC) {
            // check if we got more than 200 (MSG_THROTTLE_PER_SEC) msg per sec.
            long compareValue = messageTimeStamps.get(messageTimeStamps.size() - MSG_THROTTLE_PER_SEC).first;
            // if duration < 1 sec we received too much network_messages
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
                // if duration < 10 sec we received too much network_messages
                violated = now - compareValue < TimeUnit.SECONDS.toMillis(10);

                if (violated) {
                    log.error("violatesThrottleLimit MSG_THROTTLE_PER_10_SEC ");
                    log.error("elapsed " + (now - compareValue));
                    log.error("messageTimeStamps: \n\t" + messageTimeStamps.stream()
                            .map(e -> "\n\tts=" + e.first.toString() + " message=" + e.second.getClass().getName())
                            .collect(Collectors.toList()).toString());
                }
            }
            // we limit to max 1000 (MSG_THROTTLE_PER_10SEC) entries
            messageTimeStamps.remove(0);
        }

        messageTimeStamps.add(new Tuple2<>(now, networkEnvelope));
        return violated;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Only receive non - CloseConnectionMessage network_messages
    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        checkArgument(connection.equals(this));
        UserThread.execute(() -> messageListeners.stream().forEach(e -> e.onMessage(networkEnvelope, connection)));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setPeerType(PeerType peerType) {
        log.debug("setPeerType: peerType={}, nodeAddressOpt={}", peerType.toString(), peersNodeAddressOptional);
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

        if (BanList.isBanned(peerNodeAddress)) {
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
        log.debug("shutDown: nodeAddressOpt={}, closeConnectionReason={}", this.peersNodeAddressOptional, closeConnectionReason);
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
            protoOutputStream.onConnectionShutdown();
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
            log.warn("We got reported the ruleViolation {} at connection {}", ruleViolation, connection);
            int numRuleViolations;
            numRuleViolations = ruleViolations.getOrDefault(ruleViolation, 0);

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
                    log.warn("We close connection due RuleViolation.PEER_BANNED. peersNodeAddress={}", connection.getPeersNodeAddressOptional());
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

        @Nullable
        public List<Integer> getSupportedCapabilities() {
            return supportedCapabilities;
        }

        @SuppressWarnings("NullableProblems")
        public void setSupportedCapabilities(List<Integer> supportedCapabilities) {
            this.supportedCapabilities = supportedCapabilities;
            connection.capabilitiesListeners.forEach(l -> {
                SupportedCapabilitiesListener supportedCapabilitiesListener = l.get();
                if (supportedCapabilitiesListener != null)
                    supportedCapabilitiesListener.onChanged(supportedCapabilities);
            });
        }

        public void handleConnectionException(Throwable e) {
            if (e instanceof SocketException) {
                if (socket.isClosed())
                    closeConnectionReason = CloseConnectionReason.SOCKET_CLOSED;
                else
                    closeConnectionReason = CloseConnectionReason.RESET;

                log.info("SocketException (expected if connection lost). closeConnectionReason={}; connection={}", closeConnectionReason, connection);
            } else if (e instanceof SocketTimeoutException || e instanceof TimeoutException) {
                closeConnectionReason = CloseConnectionReason.SOCKET_TIMEOUT;
                log.info("Shut down caused by exception {} on connection={}", e.toString(), connection);
            } else if (e instanceof EOFException) {
                closeConnectionReason = CloseConnectionReason.TERMINATED;
                log.warn("Shut down caused by exception {} on connection={}", e.toString(), connection);
            } else if (e instanceof OptionalDataException || e instanceof StreamCorruptedException) {
                closeConnectionReason = CloseConnectionReason.CORRUPTED_DATA;
                log.warn("Shut down caused by exception {} on connection={}", e.toString(), connection);
            } else {
                // TODO sometimes we get StreamCorruptedException, OptionalDataException, IllegalStateException
                closeConnectionReason = CloseConnectionReason.UNKNOWN_EXCEPTION;
                log.warn("Unknown reason for exception at socket: {}\n\t" +
                                "peer={}\n\t" +
                                "Exception={}",
                        socket.toString(),
                        connection.peersNodeAddressOptional,
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
            stopped = true;
        }

        public RuleViolation getRuleViolation() {
            return ruleViolation;
        }

        @Override
        public String toString() {
            return "SharedModel{" +
                    "\n     connection=" + connection +
                    ",\n     socket=" + socket +
                    ",\n     ruleViolations=" + ruleViolations +
                    ",\n     stopped=" + stopped +
                    ",\n     closeConnectionReason=" + closeConnectionReason +
                    ",\n     ruleViolation=" + ruleViolation +
                    ",\n     supportedCapabilities=" + supportedCapabilities +
                    "\n}";
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
        private final InputStream protoInputStream;
        private final String portInfo;
        private final MessageListener messageListener;
        private final NetworkProtoResolver networkProtoResolver;

        private volatile boolean stopped;
        private long lastReadTimeStamp;
        private boolean threadNameSet;

        public InputHandler(SharedModel sharedModel,
                            InputStream protoInputStream,
                            String portInfo,
                            MessageListener messageListener,
                            NetworkProtoResolver networkProtoResolver) {
            this.sharedModel = sharedModel;
            this.protoInputStream = protoInputStream;
            this.portInfo = portInfo;
            this.messageListener = messageListener;
            this.networkProtoResolver = networkProtoResolver;
        }

        public void stop() {
            if (!stopped) {
                try {
                    protoInputStream.close();
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
                    if (!threadNameSet && sharedModel.connection != null &&
                            sharedModel.connection.getPeersNodeAddressOptional().isPresent()) {
                        Thread.currentThread().setName("InputHandler-" + sharedModel.connection.getPeersNodeAddressOptional().get().getFullAddress());
                        threadNameSet = true;
                    }
                    try {
                        if (sharedModel.getSocket() != null &&
                                sharedModel.getSocket().isClosed()) {
                            log.warn("Socket is null or closed socket={}", sharedModel.getSocket());
                            stopAndShutDown(CloseConnectionReason.SOCKET_CLOSED);
                            return;
                        }

                        Connection connection = checkNotNull(sharedModel.connection, "connection must not be null");
                        log.trace("InputHandler waiting for incoming network_messages.\n\tConnection=" + connection);

                        // Throttle inbound network_messages
                        long now = System.currentTimeMillis();
                        long elapsed = now - lastReadTimeStamp;
                        if (elapsed < 10) {
                            log.debug("We got 2 network_messages received in less than 10 ms. We set the thread to sleep " +
                                            "for 20 ms to avoid getting flooded by our peer. lastReadTimeStamp={}, now={}, elapsed={}",
                                    lastReadTimeStamp, now, elapsed);
                            Thread.sleep(20);
                        }

                        // Reading the protobuffer message from the inputstream
                        PB.NetworkEnvelope proto = PB.NetworkEnvelope.parseDelimitedFrom(protoInputStream);

                        if (proto == null) {
                            if (protoInputStream.read() == -1)
                                log.info("proto is null because protoInputStream.read()=-1 (EOF). That is expected if client got stopped without proper shutdown.");
                            else
                                log.warn("proto is null. protoInputStream.read()=" + protoInputStream.read());
                            stopAndShutDown(CloseConnectionReason.NO_PROTO_BUFFER_ENV);
                            return;
                        }

                        NetworkEnvelope networkEnvelope = networkProtoResolver.fromProto(proto);
                        lastReadTimeStamp = now;
                        log.debug("<< Received networkEnvelope of type: " + networkEnvelope.getClass().getSimpleName());

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
                        connection.statistic.addReceivedBytes(size);

                        // We want to track the network_messages also before the checks, so do it early...
                        connection.statistic.addReceivedMessage(networkEnvelope);

                        // First we check the size
                        boolean exceeds;
                        if (networkEnvelope instanceof ExtendedDataSizePermission) {
                            exceeds = size > MAX_PERMITTED_MESSAGE_SIZE;
                            log.debug("size={}; object={}", size, Utilities.toTruncatedString(proto, 100));
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

                        if (connection.violatesThrottleLimit(networkEnvelope)
                                && reportInvalidRequest(RuleViolation.THROTTLE_LIMIT_EXCEEDED))
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

                        if (sharedModel.getSupportedCapabilities() == null && networkEnvelope instanceof SupportedCapabilitiesMessage)
                            sharedModel.setSupportedCapabilities(((SupportedCapabilitiesMessage) networkEnvelope).getSupportedCapabilities());

                        if (networkEnvelope instanceof CloseConnectionMessage) {
                            // If we get a CloseConnectionMessage we shut down
                            log.info("CloseConnectionMessage received. Reason={}\n\t" +
                                    "connection={}", proto.getCloseConnectionMessage().getReason(), connection);
                            if (CloseConnectionReason.PEER_BANNED.name().equals(proto.getCloseConnectionMessage().getReason())) {
                                log.warn("We got shut down because we are banned by the other peer. (InputHandler.run CloseConnectionMessage)");
                                stopAndShutDown(CloseConnectionReason.PEER_BANNED);
                            } else {
                                stopAndShutDown(CloseConnectionReason.CLOSE_REQUESTED_BY_PEER);
                            }
                            return;
                        } else if (!stopped) {
                            // We don't want to get the activity ts updated by ping/pong msg
                            if (!(networkEnvelope instanceof KeepAliveMessage))
                                connection.statistic.updateLastActivityTimestamp();

                            if (networkEnvelope instanceof GetDataRequest)
                                connection.setPeerType(PeerType.INITIAL_DATA_REQUEST);

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
                                Optional<NodeAddress> peersNodeAddressOptional = connection.getPeersNodeAddressOptional();
                                if (peersNodeAddressOptional.isPresent()) {
                                    // If we have already the peers address we check again if it matches our stored one
                                    checkArgument(peersNodeAddressOptional.get().equals(senderNodeAddress),
                                            "senderNodeAddress not matching connections peer address.\n\t" +
                                                    "message=" + networkEnvelope);
                                } else {
                                    // We must not shut down a banned peer at that moment as it would trigger a connection termination
                                    // and we could not send the CloseConnectionMessage.
                                    // We check for a banned peer inside setPeersNodeAddress() and shut down if banned.
                                    connection.setPeersNodeAddress(senderNodeAddress);
                                }
                            }

                            if (networkEnvelope instanceof PrefixedSealedAndSignedMessage)
                                connection.setPeerType(Connection.PeerType.DIRECT_MSG_PEER);

                            messageListener.onMessage(networkEnvelope, connection);
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

        private void stopAndShutDown(CloseConnectionReason reason) {
            stop();
            sharedModel.shutDown(reason);
        }

        private void handleException(Throwable e) {
            stop();
            if (sharedModel != null)
                sharedModel.handleConnectionException(e);
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
