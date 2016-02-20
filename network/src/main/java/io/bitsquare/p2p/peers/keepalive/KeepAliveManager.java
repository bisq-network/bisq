package io.bitsquare.p2p.peers.keepalive;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.app.Log;
import io.bitsquare.common.Timer;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.network.*;
import io.bitsquare.p2p.peers.PeerManager;
import io.bitsquare.p2p.peers.keepalive.messages.Ping;
import io.bitsquare.p2p.peers.keepalive.messages.Pong;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class KeepAliveManager implements MessageListener, ConnectionListener, PeerManager.Listener {
    private static final Logger log = LoggerFactory.getLogger(KeepAliveManager.class);

    //private static final int INTERVAL_SEC = new Random().nextInt(10) + 10;
    //TODO
    private static final int INTERVAL_SEC = 5;

    private final NetworkNode networkNode;
    private final PeerManager peerManager;
    private final Map<String, KeepAliveHandler> handlerMap = new HashMap<>();
    private boolean stopped;
    private Timer keepAliveTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public KeepAliveManager(NetworkNode networkNode, PeerManager peerManager) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;

        networkNode.addMessageListener(this);
        networkNode.addConnectionListener(this);
        peerManager.addListener(this);
    }

    public void shutDown() {
        Log.traceCall();
        stopped = true;

        networkNode.removeMessageListener(this);
        networkNode.removeConnectionListener(this);
        peerManager.removeListener(this);

        closeAllMaintenanceHandlers();

        stopKeepAliveTimer();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        stopped = false;
        if (keepAliveTimer == null)
            keepAliveTimer = UserThread.runPeriodically(this::keepAlive, INTERVAL_SEC);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof Ping) {
            Log.traceCall(message.toString() + "\n\tconnection=" + connection);
            if (!stopped) {
                Ping ping = (Ping) message;
                Pong pong = new Pong(ping.nonce);
                SettableFuture<Connection> future = networkNode.sendMessage(connection, pong);
                Futures.addCallback(future, new FutureCallback<Connection>() {
                    @Override
                    public void onSuccess(Connection connection) {
                        log.trace("Pong sent successfully");
                    }

                    @Override
                    public void onFailure(@NotNull Throwable throwable) {
                        String errorMessage = "Sending pong to " + connection +
                                " failed. That is expected if the peer is offline. pong=" + pong + "." +
                                "Exception: " + throwable.getMessage();
                        log.info(errorMessage);
                        peerManager.handleConnectionFault(connection);
                        peerManager.shutDownConnection(connection, CloseConnectionReason.SEND_MSG_FAILURE);
                    }
                });
            } else {
                log.warn("We have stopped already. We ignore that onMessage call.");
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ConnectionListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnection(Connection connection) {
        Log.traceCall();
        // clean up in case we could not clean up at disconnect
        closeMaintenanceHandler(connection);
    }

    @Override
    public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
        Log.traceCall();
        closeMaintenanceHandler(connection);
    }

    @Override
    public void onError(Throwable throwable) {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PeerManager.Listener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAllConnectionsLost() {
        Log.traceCall();
        closeAllMaintenanceHandlers();
        stopKeepAliveTimer();
    }

    @Override
    public void onNewConnectionAfterAllConnectionsLost() {
        Log.traceCall();
        closeAllMaintenanceHandlers();
        start();
    }

    @Override
    public void onAwakeFromStandby() {
        Log.traceCall();
        closeAllMaintenanceHandlers();
        if (!networkNode.getAllConnections().isEmpty())
            start();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void keepAlive() {
        if (!stopped) {
            Log.traceCall();
            networkNode.getConfirmedConnections().stream()
                    .filter(connection -> connection instanceof OutboundConnection)
                    .forEach(connection -> {
                        final String uid = connection.getUid();
                        if (!handlerMap.containsKey(uid)) {
                            KeepAliveHandler keepAliveHandler = new KeepAliveHandler(networkNode, peerManager, new KeepAliveHandler.Listener() {
                                @Override
                                public void onComplete() {
                                    handlerMap.remove(uid);
                                }

                                @Override
                                public void onFault(String errorMessage) {
                                    handlerMap.remove(uid);
                                }
                            });
                            handlerMap.put(uid, keepAliveHandler);
                            keepAliveHandler.sendPing(connection);
                        } else {
                            log.warn("Connection with id {} has not completed and is still in our map. " +
                                    "We will try to ping that peer at the next schedule.", uid);
                        }
                    });

            int size = handlerMap.size();
            log.info("maintenanceHandlerMap size=" + size);
            if (size > peerManager.getMaxConnections())
                log.warn("Seems we didn't clean up out map correctly.\n" +
                        "maintenanceHandlerMap size={}, peerManager.getMaxConnections()={}", size, peerManager.getMaxConnections());
        } else {
            log.warn("We have stopped already. We ignore that keepAlive call.");
        }
    }

    private void stopKeepAliveTimer() {
        stopped = true;
        if (keepAliveTimer != null) {
            keepAliveTimer.stop();
            keepAliveTimer = null;
        }
    }

    private void closeMaintenanceHandler(Connection connection) {
        String uid = connection.getUid();
        if (handlerMap.containsKey(uid)) {
            handlerMap.get(uid).cleanup();
            handlerMap.remove(uid);
        }
    }

    private void closeAllMaintenanceHandlers() {
        handlerMap.values().stream().forEach(KeepAliveHandler::cleanup);
        handlerMap.clear();
    }

}
