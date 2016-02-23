package io.bitsquare.p2p.peers.keepalive;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.app.Log;
import io.bitsquare.common.Timer;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.MessageListener;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.p2p.peers.PeerManager;
import io.bitsquare.p2p.peers.keepalive.messages.Ping;
import io.bitsquare.p2p.peers.keepalive.messages.Pong;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Random;
import java.util.concurrent.TimeUnit;

class KeepAliveHandler implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(KeepAliveHandler.class);
    private Timer delayTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface Listener {
        void onComplete();

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
        delayTimer = UserThread.runAfterRandomDelay(() -> sendPing(connection), 1, 5000, TimeUnit.MILLISECONDS);
    }

    private void sendPing(Connection connection) {
        Log.traceCall("connection=" + connection + " / this=" + this);
        if (!stopped) {
            Ping ping = new Ping(nonce);
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
                        log.info(errorMessage);
                        cleanup();
                        //peerManager.shutDownConnection(connection, CloseConnectionReason.SEND_MSG_FAILURE);
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
    public void onMessage(Message message, Connection connection) {
        if (message instanceof Pong) {
            Log.traceCall(message.toString() + "\n\tconnection=" + connection);
            if (!stopped) {
                Pong pong = (Pong) message;
                if (pong.requestNonce == nonce) {
                    cleanup();
                    listener.onComplete();
                } else {
                    log.warn("Nonce not matching. That should never happen.\n\t" +
                                    "We drop that message. nonce={} / requestNonce={}",
                            nonce, pong.requestNonce);
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
