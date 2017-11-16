package io.bisq.network.p2p.peers;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.common.util.Utilities;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.network.Connection;
import io.bisq.network.p2p.network.NetworkNode;
import io.bisq.network.p2p.storage.messages.BroadcastMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class BroadcastHandler implements PeerManager.Listener {
    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static final Logger log = LoggerFactory.getLogger(BroadcastHandler.class);
    private static final long TIMEOUT_PER_PEER_SEC = 30;

    interface ResultHandler {
        void onCompleted(BroadcastHandler broadcastHandler);

        void onFault(BroadcastHandler broadcastHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface Listener {
        @SuppressWarnings({"EmptyMethod", "UnusedParameters"})
        void onBroadcasted(BroadcastMessage message, int numOfCompletedBroadcasts);

        void onBroadcastedToFirstPeer(BroadcastMessage message);

        void onBroadcastCompleted(BroadcastMessage message, int numOfCompletedBroadcasts, int numOfFailedBroadcasts);

        @SuppressWarnings({"EmptyMethod", "UnusedParameters"})
        void onBroadcastFailed(String errorMessage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final NetworkNode networkNode;
    public final String uid;
    private final PeerManager peerManager;
    private boolean stopped = false;
    private int numOfCompletedBroadcasts = 0;
    private int numOfFailedBroadcasts = 0;
    private BroadcastMessage message;
    private ResultHandler resultHandler;
    @Nullable
    private Listener listener;
    private int numOfPeers;
    private Timer timeoutTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BroadcastHandler(NetworkNode networkNode, PeerManager peerManager) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        peerManager.addListener(this);
        uid = UUID.randomUUID().toString();
    }

    public void cancel() {
        stopped = true;
        onFault("Broadcast canceled.", false);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void broadcast(BroadcastMessage message, @Nullable NodeAddress sender, ResultHandler resultHandler,
                          @Nullable Listener listener, boolean isDataOwner) {
        this.message = message;
        this.resultHandler = resultHandler;
        this.listener = listener;

        Log.traceCall("Sender=" + sender + "\n\t" +
                "Message=" + Utilities.toTruncatedString(message));
        Set<Connection> connectedPeersSet = networkNode.getConfirmedConnections()
                .stream()
                .filter(connection -> !connection.getPeersNodeAddressOptional().get().equals(sender))
                .collect(Collectors.toSet());
        if (!connectedPeersSet.isEmpty()) {
            numOfCompletedBroadcasts = 0;

            List<Connection> connectedPeersList = new ArrayList<>(connectedPeersSet);
            Collections.shuffle(connectedPeersList);
            numOfPeers = connectedPeersList.size();
            int delay = 50;
            if (!isDataOwner) {
                // for not data owner (relay nodes) we send to max. 7 nodes and use a longer delay
                numOfPeers = Math.min(7, connectedPeersList.size());
                delay = 100;
            }

            long timeoutDelay = TIMEOUT_PER_PEER_SEC * numOfPeers;
            timeoutTimer = UserThread.runAfter(() -> {  // setup before sending to avoid race conditions
                String errorMessage = "Timeout: Broadcast did not complete after " + timeoutDelay + " sec.";

                log.debug(errorMessage + "\n\t" +
                        "numOfPeers=" + numOfPeers + "\n\t" +
                        "numOfCompletedBroadcasts=" + numOfCompletedBroadcasts + "\n\t" +
                        "numOfCompletedBroadcasts=" + numOfCompletedBroadcasts + "\n\t" +
                        "numOfFailedBroadcasts=" + numOfFailedBroadcasts);
                onFault(errorMessage, false);
            }, timeoutDelay);

            log.debug("Broadcast message to {} peers out of {} total connected peers.", numOfPeers, connectedPeersSet.size());
            for (int i = 0; i < numOfPeers; i++) {
                if (stopped)
                    break;  // do not continue sending after a timeout or a cancellation

                final long minDelay = (i + 1) * delay;
                final long maxDelay = (i + 2) * delay;
                final Connection connection = connectedPeersList.get(i);
                UserThread.runAfterRandomDelay(() -> sendToPeer(connection, message), minDelay, maxDelay, TimeUnit.MILLISECONDS);
            }
        } else {
            onFault("Message not broadcasted because we have no available peers yet.\n\t" +
                    "message = " + Utilities.toTruncatedString(message), false);
        }
    }

    private void sendToPeer(Connection connection, BroadcastMessage message) {
        String errorMessage = "Message not broadcasted because we have stopped the handler already.\n\t" +
                "message = " + Utilities.toTruncatedString(message);
        if (!stopped) {
            if (!connection.isStopped()) {
                if (!connection.isCapabilityRequired(message) || connection.isCapabilitySupported(message)) {
                    NodeAddress nodeAddress = connection.getPeersNodeAddressOptional().get();
                    log.trace("Broadcast message to " + nodeAddress + ".");
                    SettableFuture<Connection> future = networkNode.sendMessage(connection, message);
                    Futures.addCallback(future, new FutureCallback<Connection>() {
                        @Override
                        public void onSuccess(Connection connection) {
                            numOfCompletedBroadcasts++;
                            if (!stopped) {
                                log.trace("Broadcast to " + nodeAddress + " succeeded.");

                                if (listener != null)
                                    listener.onBroadcasted(message, numOfCompletedBroadcasts);

                                if (listener != null && numOfCompletedBroadcasts == 1)
                                    listener.onBroadcastedToFirstPeer(message);

                                if (numOfCompletedBroadcasts + numOfFailedBroadcasts == numOfPeers) {
                                    if (listener != null)
                                        listener.onBroadcastCompleted(message, numOfCompletedBroadcasts, numOfFailedBroadcasts);

                                    cleanup();
                                    resultHandler.onCompleted(BroadcastHandler.this);
                                }
                            } else {
                                // TODO investigate why that is called very often at seed nodes
                                onFault("stopped at onSuccess: " + errorMessage, false);
                            }
                        }

                        @Override
                        public void onFailure(@NotNull Throwable throwable) {
                            numOfFailedBroadcasts++;
                            if (!stopped) {
                                log.debug("Broadcast to " + nodeAddress + " failed.\n\t" +
                                        "ErrorMessage=" + throwable.getMessage());
                                if (numOfCompletedBroadcasts + numOfFailedBroadcasts == numOfPeers)
                                    onFault("stopped at onFailure: " + errorMessage);
                            } else {
                                onFault("stopped at onFailure: " + errorMessage);
                            }
                        }
                    });
                } else {
                    log.info("We did not send the message because the peer does not support our required capabilities. message={}, peers supportedCapabilities={}", Utilities.toTruncatedString(message, 200), connection.getSupportedCapabilities());
                }
            } else {
                onFault("Connection stopped already", false);
            }
        } else {
            onFault("stopped at sendToPeer: " + errorMessage, false);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PeerManager.Listener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAllConnectionsLost() {
        onFault("All connections lost", false);
    }

    @Override
    public void onNewConnectionAfterAllConnectionsLost() {
    }

    @Override
    public void onAwakeFromStandby() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void cleanup() {
        stopped = true;
        peerManager.removeListener(this);
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }

    private void onFault(String errorMessage) {
        onFault(errorMessage, true);
    }

    private void onFault(String errorMessage, boolean logWarning) {
        cleanup();

        if (logWarning)
            log.warn(errorMessage);
        else
            log.debug(errorMessage);

        if (listener != null)
            listener.onBroadcastFailed(errorMessage);

        if (listener != null && (numOfCompletedBroadcasts + numOfFailedBroadcasts == numOfPeers || stopped))
            listener.onBroadcastCompleted(message, numOfCompletedBroadcasts, numOfFailedBroadcasts);

        resultHandler.onFault(this);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BroadcastHandler)) return false;

        BroadcastHandler that = (BroadcastHandler) o;

        return !(uid != null ? !uid.equals(that.uid) : that.uid != null);
    }

    @Override
    public int hashCode() {
        return uid != null ? uid.hashCode() : 0;
    }
}
