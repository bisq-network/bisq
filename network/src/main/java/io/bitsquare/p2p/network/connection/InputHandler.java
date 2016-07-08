package io.bitsquare.p2p.network.connection;

import io.bitsquare.app.Version;
import io.bitsquare.common.ByteArrayUtils;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.messaging.PrefixedSealedAndSignedMessage;
import io.bitsquare.p2p.network.RuleViolation;
import io.bitsquare.p2p.network.messages.CloseConnectionMessage;
import io.bitsquare.p2p.network.messages.SendersNodeAddressMessage;
import io.bitsquare.p2p.peers.getdata.messages.GetDataResponse;
import io.bitsquare.p2p.peers.keepalive.messages.KeepAliveMessage;
import io.bitsquare.p2p.peers.keepalive.messages.Pong;
import io.bitsquare.p2p.storage.messages.RefreshTTLMessage;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OptionalDataException;
import java.io.Serializable;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;

// Runs in same thread as Connection, receives a message, performs several checks on it
// (including throttling limits, validity and statistics)
// and delivers it to the message listener given in the constructor.
public class InputHandler implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(InputHandler.class);

    private final SharedModel sharedModel;
    private final ObjectInputStream objectInputStream;
    private final String portInfo;
    private final MessageListener messageListener;

    private volatile boolean stopped;
    private long lastReadTimeStamp;

    public InputHandler(SharedModel sharedModel, ObjectInputStream objectInputStream, String portInfo, MessageListener messageListener) {
        this.sharedModel = sharedModel;
        this.objectInputStream = objectInputStream;
        this.portInfo = portInfo;
        this.messageListener = messageListener;
    }

    // TODO should be synchronized? Guaranteed only one thread?
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
            Thread.currentThread().setName("InputHandler-" + portInfo);
            while (!stopped && !Thread.currentThread().isInterrupted()) {
                Connection connection = sharedModel.getConnection();
                log.trace("InputHandler waiting for incoming messages.\n\tConnection=" + connection);
                try {
                    Object rawInputObject = objectInputStream.readObject();

                    // Throttle inbound messages
                    long now = System.currentTimeMillis();
                    long elapsed = now - lastReadTimeStamp;
                    if (elapsed < 10) {
                        log.info("We got 2 messages received in less than 10 ms. We set the thread to sleep " +
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
                                StringUtils.abbreviate(rawInputObject.toString(), 100),
                                size);
                    } else if (rawInputObject instanceof Message) {
                        // We want to log all incoming messages (except Pong and RefreshTTLMessage)
                        // so we log before the data type checks
                        log.info("\n\n<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<\n" +
                                        "New data arrived at inputHandler of connection {}.\n" +
                                        "Received object (truncated)={} / size={}"
                                        + "\n<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<\n",
                                connection,
                                StringUtils.abbreviate(rawInputObject.toString(), 100),
                                size);
                    } else {
                        log.error("\n\n<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<\n" +
                                        "Invalid data arrived at inputHandler of connection {}.\n" +
                                        "Size={}"
                                        + "\n<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<\n",
                                connection,
                                size);
                    }

                    // We want to track the size of each object even if it is invalid data
                    connection.getStatistic().addReceivedBytes(size);

                    // We want to track the messages also before the checks, so do it early...
                    Message message = null;
                    if (rawInputObject instanceof Message) {
                        message = (Message) rawInputObject;
                        connection.getStatistic().addReceivedMessage((Message) rawInputObject);
                    }


                    // First we check the size
                    boolean exceeds;
                    if (rawInputObject instanceof GetDataResponse)
                        exceeds = size > Connection.MAX_MSG_SIZE_GET_DATA;
                    else
                        exceeds = size > Connection.MAX_MSG_SIZE;

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

                    if (message instanceof CloseConnectionMessage) {
                        // If we get a CloseConnectionMessage we shut down
                        log.info("CloseConnectionMessage received. Reason={}\n\t" +
                                "connection={}", ((CloseConnectionMessage) message).reason, connection);
                        stop();
                        sharedModel.shutDown(CloseConnectionReason.CLOSE_REQUESTED_BY_PEER);
                    } else if (!stopped) {
                        // We don't want to get the activity ts updated by ping/pong msg
                        if (!(message instanceof KeepAliveMessage))
                            connection.getStatistic().updateLastActivityTimestamp();

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