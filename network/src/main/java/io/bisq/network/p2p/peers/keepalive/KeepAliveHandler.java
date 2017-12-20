package io.bisq.network.p2p.peers.keepalive;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.network.p2p.network.Connection;
import io.bisq.network.p2p.network.MessageListener;
import io.bisq.network.p2p.network.NetworkNode;
import io.bisq.network.p2p.peers.PeerManager;
import io.bisq.network.p2p.peers.keepalive.messages.Ping;
import io.bisq.network.p2p.peers.keepalive.messages.Pong;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Random;
import java.util.concurrent.TimeUnit;

class KeepAliveHandler implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(KeepAliveHandler.class);

    private static final int DELAY_MS = 10_000;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface Listener {
        void onComplete();

        @SuppressWarnings("UnusedParameters")
        void onFault(String errorMessage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final NetworkNode networkNode;
    private final PeerManager peerManager;
    private final Listener listener;
    private final int nonce = new Random().nextInt();
    @Nullable
    private Connection connection;
    private boolean stopped;
    private Timer delayTimer;
    private long sendTs;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public KeepAliveHandler(NetworkNode networkNode, PeerManager peerManager, Listener listener) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        this.listener = listener;
    }

    public void cancel() {
        cleanup();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void sendPingAfterRandomDelay(Connection connection) {
        delayTimer = UserThread.runAfterRandomDelay(() -> sendPing(connection), 1, DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void sendPing(Connection connection) {
        Log.traceCall("connection=" + connection + " / this=" + this);
        if (!stopped) {
            Ping ping = new Ping(nonce, connection.getStatistic().roundTripTimeProperty().get());
            sendTs = System.currentTimeMillis();
            SettableFuture<Connection> future = networkNode.sendMessage(connection, ping);
            Futures.addCallback(future, new FutureCallback<Connection>() {
                @Override
                public void onSuccess(Connection connection) {
                    if (!stopped) {
                        log.trace("Send " + ping + " to " + connection + " succeeded.");
                        KeepAliveHandler.this.connection = connection;
                        connection.addMessageListener(KeepAliveHandler.this);
                    } else {
                        log.trace("We have stopped already. We ignore that networkNode.sendMessage.onSuccess call.");
                    }
                }

                @Override
                public void onFailure(@NotNull Throwable throwable) {
                    if (!stopped) {
                        String errorMessage = "Sending ping to " + connection +
                                " failed. That is expected if the peer is offline.\n\tping=" + ping +
                                ".\n\tException=" + throwable.getMessage();
                        cleanup();
                        //peerManager.shutDownConnection(connection, CloseConnectionReason.SEND_MSG_FAILURE);
                        log.info(errorMessage);
                        peerManager.handleConnectionFault(connection);
                        listener.onFault(errorMessage);
                    } else {
                        log.trace("We have stopped already. We ignore that networkNode.sendMessage.onFailure call.");
                    }
                }
            });
        } else {
            log.trace("We have stopped already. We ignore that sendPing call.");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(NetworkEnvelope networkEnvelop, Connection connection) {
        if (networkEnvelop instanceof Pong) {
            Log.traceCall(networkEnvelop.toString() + "\n\tconnection=" + connection);
            if (!stopped) {
                Pong pong = (Pong) networkEnvelop;
                if (pong.getRequestNonce() == nonce) {
                    int roundTripTime = (int) (System.currentTimeMillis() - sendTs);
                    log.trace("roundTripTime=" + roundTripTime + "\n\tconnection=" + connection);
                    connection.getStatistic().setRoundTripTime(roundTripTime);
                    cleanup();
                    listener.onComplete();
                } else {
                    log.warn("Nonce not matching. That should never happen.\n\t" +
                                    "We drop that message. nonce={} / requestNonce={}",
                            nonce, pong.getRequestNonce());
                }
            } else {
                log.trace("We have stopped already. We ignore that onMessage call.");
            }
        }
    }

    private void cleanup() {
        stopped = true;
        if (connection != null)
            connection.removeMessageListener(this);

        if (delayTimer != null) {
            delayTimer.stop();
            delayTimer = null;
        }
    }
}
