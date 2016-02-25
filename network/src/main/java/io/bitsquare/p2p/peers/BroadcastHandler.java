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

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class BroadcastHandler implements PeerManager.Listener {
    private static final Logger log = LoggerFactory.getLogger(BroadcastHandler.class);
    private static final long TIMEOUT_PER_PEER_SEC = Timer.STRESS_TEST ? 2 : 20;
    private static final long DELAY_MS = Timer.STRESS_TEST ? 10 : 300;

    interface ResultHandler {
        void onCompleted(BroadcastHandler broadcastHandler);

        void onFault(BroadcastHandler broadcastHandler);
    }

    public interface Listener {
        void onBroadcasted(BroadcastMessage message, int numOfCompletedBroadcasts);

        void onBroadcastedToFirstPeer(BroadcastMessage message);

        void onBroadcastCompleted(BroadcastMessage message, int numOfCompletedBroadcasts, int numOfFailedBroadcasts);

        void onBroadcastFailed(String errorMessage);
    }

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

    public void broadcast(BroadcastMessage message, @Nullable NodeAddress sender, ResultHandler resultHandler, @Nullable Listener listener) {
        this.message = message;
        this.resultHandler = resultHandler;
        this.listener = listener;

        Log.traceCall("Sender=" + sender + "\n\t" +
                "Message=" + StringUtils.abbreviate(message.toString(), 100));
        Set<Connection> receivers = networkNode.getConfirmedConnections();
        if (!receivers.isEmpty()) {
            timeoutTimer = UserThread.runAfter(() ->
                    onFault("Timeout: Broadcast did not complete after " + TIMEOUT_PER_PEER_SEC + " sec."), TIMEOUT_PER_PEER_SEC * receivers.size());
            numOfPeers = receivers.size();
            numOfCompletedBroadcasts = 0;
            log.info("Broadcast message to {} peers.", numOfPeers);
            receivers.stream()
                    .filter(connection -> !connection.getPeersNodeAddressOptional().get().equals(sender))
                    .forEach(connection -> UserThread.runAfterRandomDelay(() ->
                            sendToPeer(connection, message), 1, DELAY_MS, TimeUnit.MILLISECONDS));
        } else {
            onFault("Message not broadcasted because we have no available peers yet.\n\t" +
                    "That should never happen as broadcast should not be called in such cases.\n" +
                    "message = " + StringUtils.abbreviate(message.toString(), 100));
        }
    }

    private void sendToPeer(Connection connection, BroadcastMessage message) {
        String errorMessage = "Message not broadcasted because we have stopped the handler already.\n\t" +
                "message = " + StringUtils.abbreviate(message.toString(), 100);
        if (!stopped) {
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
                        onFault("stopped at onSuccess: " + errorMessage);
                    }
                }

                @Override
                public void onFailure(@NotNull Throwable throwable) {
                    numOfFailedBroadcasts++;
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
        log.warn(errorMessage);
        if (listener != null)
            listener.onBroadcastFailed(errorMessage);

        if (listener != null && (numOfCompletedBroadcasts + numOfFailedBroadcasts == numOfPeers || stopped))
            listener.onBroadcastCompleted(message, numOfCompletedBroadcasts, numOfFailedBroadcasts);

        cleanup();
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
