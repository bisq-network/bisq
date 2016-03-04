package io.bitsquare.p2p.peers;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.app.Log;
import io.bitsquare.common.Timer;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.p2p.storage.messages.BroadcastMessage;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class BroadcastHandler implements PeerManager.Listener {
    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static final Logger log = LoggerFactory.getLogger(BroadcastHandler.class);
    private static final long TIMEOUT_PER_PEER_SEC = Timer.STRESS_TEST ? 5 : 30;
    private static final long DELAY_MS = Timer.STRESS_TEST ? 100 : 500;

    interface ResultHandler {
        void onCompleted(BroadcastHandler broadcastHandler);

        void onFault(BroadcastHandler broadcastHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface Listener {
        void onBroadcasted(BroadcastMessage message, int numOfCompletedBroadcasts);

        void onBroadcastedToFirstPeer(BroadcastMessage message);

        void onBroadcastCompleted(BroadcastMessage message, int numOfCompletedBroadcasts, int numOfFailedBroadcasts);

        void onBroadcastFailed(String errorMessage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final NetworkNode networkNode;
    public final String uid;
    private PeerManager peerManager;
    private boolean stopped = false;
    private int numOfCompletedBroadcasts = 0;
    private int numOfFailedBroadcasts = 0;
    private BroadcastMessage message;
    private ResultHandler resultHandler;
    @Nullable
    private Listener listener;
    private int numOfPeers;
    private Timer timeoutTimer;
    private Set<String> broadcastQueue = new CopyOnWriteArraySet<>();


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
        onFault("Broadcast canceled.");
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
                "Message=" + StringUtils.abbreviate(message.toString(), 100));
        Set<Connection> connectedPeersSet = networkNode.getConfirmedConnections()
                .stream()
                .filter(connection -> !connection.getPeersNodeAddressOptional().get().equals(sender))
                .collect(Collectors.toSet());
        if (!connectedPeersSet.isEmpty()) {
            numOfCompletedBroadcasts = 0;

            List<Connection> connectedPeersList = new ArrayList<>(connectedPeersSet);
            Collections.shuffle(connectedPeersList);
            numOfPeers = connectedPeersList.size();
            int factor = 1;
            if (!isDataOwner) {
                // for not data owner (relay nodes) we send to max. 4 nodes and use a longer delay
                numOfPeers = Math.min(4, connectedPeersList.size());
                factor = 2;
            }
            log.info("Broadcast message to {} peers out of {} total connected peers.", numOfPeers, connectedPeersSet.size());
            for (int i = 0; i < numOfPeers; i++) {
                final long minDelay = i * 50 * factor + 1;
                final long maxDelay = minDelay * 2 + 50 * factor;
                final Connection connection = connectedPeersList.get(i);
                UserThread.runAfterRandomDelay(() -> sendToPeer(connection, message), minDelay, maxDelay, TimeUnit.MILLISECONDS);
            }

            long timeoutDelay = TIMEOUT_PER_PEER_SEC * numOfPeers;
            timeoutTimer = UserThread.runAfter(() -> {
                String errorMessage = "Timeout: Broadcast did not complete after " + timeoutDelay + " sec.";

                log.warn(errorMessage + "\n\t" +
                        "numOfPeers=" + numOfPeers + "\n\t" +
                        "numOfCompletedBroadcasts=" + numOfCompletedBroadcasts + "\n\t" +
                        "numOfCompletedBroadcasts=" + numOfCompletedBroadcasts + "\n\t" +
                        "numOfFailedBroadcasts=" + numOfFailedBroadcasts + "\n\t" +
                        "broadcastQueue.size()=" + broadcastQueue.size() + "\n\t" +
                        "broadcastQueue=" + broadcastQueue);
                onFault(errorMessage);
            }, timeoutDelay);
        } else {
            onFault("Message not broadcasted because we have no available peers yet.\n\t" +
                    "message = " + StringUtils.abbreviate(message.toString(), 100), false);
        }
    }

    private void sendToPeer(Connection connection, BroadcastMessage message) {
        String errorMessage = "Message not broadcasted because we have stopped the handler already.\n\t" +
                "message = " + StringUtils.abbreviate(message.toString(), 100);
        if (!stopped) {
            if (!connection.isStopped()) {
                NodeAddress nodeAddress = connection.getPeersNodeAddressOptional().get();
                log.trace("Broadcast message to " + nodeAddress + ".");
                broadcastQueue.add(nodeAddress.getFullAddress());
                SettableFuture<Connection> future = networkNode.sendMessage(connection, message);
                Futures.addCallback(future, new FutureCallback<Connection>() {
                    @Override
                    public void onSuccess(Connection connection) {
                        numOfCompletedBroadcasts++;
                        broadcastQueue.remove(nodeAddress.getFullAddress());
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
                            onFault("stopped at onSuccess: " + errorMessage);
                        }
                    }

                    @Override
                    public void onFailure(@NotNull Throwable throwable) {
                        numOfFailedBroadcasts++;
                        broadcastQueue.remove(nodeAddress.getFullAddress());
                        if (!stopped) {
                            log.info("Broadcast to " + nodeAddress + " failed.\n\t" +
                                    "ErrorMessage=" + throwable.getMessage());
                            if (numOfCompletedBroadcasts + numOfFailedBroadcasts == numOfPeers)
                                onFault("stopped at onFailure: " + errorMessage);
                        } else {
                            onFault("stopped at onFailure: " + errorMessage);
                        }
                    }
                });
            } else {
                onFault("Connection stopped already");
            }
        } else {
            onFault("stopped at sendToPeer: " + errorMessage);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PeerManager.Listener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAllConnectionsLost() {
        onFault("All connections lost");
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
            log.trace(errorMessage);

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
