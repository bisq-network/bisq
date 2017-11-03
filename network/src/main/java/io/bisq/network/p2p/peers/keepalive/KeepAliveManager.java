package io.bisq.network.p2p.peers.keepalive;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.network.p2p.network.*;
import io.bisq.network.p2p.peers.PeerManager;
import io.bisq.network.p2p.peers.keepalive.messages.Ping;
import io.bisq.network.p2p.peers.keepalive.messages.Pong;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class KeepAliveManager implements MessageListener, ConnectionListener, PeerManager.Listener {
    private static final Logger log = LoggerFactory.getLogger(KeepAliveManager.class);

    private static final int INTERVAL_SEC = new Random().nextInt(5) + 30;
    private static final long LAST_ACTIVITY_AGE_MS = INTERVAL_SEC / 2;

    private final NetworkNode networkNode;
    private final PeerManager peerManager;
    private final Map<String, KeepAliveHandler> handlerMap = new HashMap<>();

    private boolean stopped;
    private Timer keepAliveTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public KeepAliveManager(NetworkNode networkNode,
                            PeerManager peerManager) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;

        this.networkNode.addMessageListener(this);
        this.networkNode.addConnectionListener(this);
        this.peerManager.addListener(this);
    }

    public void shutDown() {
        Log.traceCall();
        stopped = true;
        networkNode.removeMessageListener(this);
        networkNode.removeConnectionListener(this);
        peerManager.removeListener(this);
        closeAllHandlers();
        stopKeepAliveTimer();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        restart();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(NetworkEnvelope networkEnvelop, Connection connection) {
        if (networkEnvelop instanceof Ping) {
            Log.traceCall(networkEnvelop.toString() + "\n\tconnection=" + connection);
            if (!stopped) {
                Ping ping = (Ping) networkEnvelop;

                // We get from peer last measured rrt
                connection.getStatistic().setRoundTripTime(ping.getLastRoundTripTime());

                Pong pong = new Pong(ping.getNonce());
                SettableFuture<Connection> future = networkNode.sendMessage(connection, pong);
                Futures.addCallback(future, new FutureCallback<Connection>() {
                    @Override
                    public void onSuccess(Connection connection) {
                        log.trace("Pong sent successfully");
                    }

                    @Override
                    public void onFailure(@NotNull Throwable throwable) {
                        if (!stopped) {
                            String errorMessage = "Sending pong to " + connection +
                                " failed. That is expected if the peer is offline. pong=" + pong + "." +
                                "Exception: " + throwable.getMessage();
                            log.debug(errorMessage);
                            peerManager.handleConnectionFault(connection);
                        } else {
                            log.warn("We have stopped already. We ignore that  networkNode.sendMessage.onFailure call.");
                        }
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
    }

    @Override
    public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
        Log.traceCall();
        closeHandler(connection);
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
        closeAllHandlers();
        stopKeepAliveTimer();
        stopped = true;
        restart();
    }

    @Override
    public void onNewConnectionAfterAllConnectionsLost() {
        Log.traceCall();
        closeAllHandlers();
        stopped = false;
        restart();
    }

    @Override
    public void onAwakeFromStandby() {
        Log.traceCall();
        closeAllHandlers();
        stopped = false;
        if (!networkNode.getAllConnections().isEmpty())
            restart();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void restart() {
        if (keepAliveTimer == null)
            keepAliveTimer = UserThread.runPeriodically(() -> {
                stopped = false;
                keepAlive();
            }, INTERVAL_SEC);
    }

    private void keepAlive() {
        if (!stopped) {
            Log.traceCall();
            networkNode.getConfirmedConnections().stream()
                .filter(connection -> connection instanceof OutboundConnection &&
                    connection.getStatistic().getLastActivityAge() > LAST_ACTIVITY_AGE_MS)
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
                        keepAliveHandler.sendPingAfterRandomDelay(connection);
                    } else {
                        // TODO check if this situation causes any issues
                        log.debug("Connection with id {} has not completed and is still in our map. " +
                            "We will try to ping that peer at the next schedule.", uid);
                    }
                });

            int size = handlerMap.size();
            log.debug("handlerMap size=" + size);
            if (size > peerManager.getMaxConnections())
                log.warn("Seems we didn't clean up out map correctly.\n" +
                    "handlerMap size={}, peerManager.getMaxConnections()={}", size, peerManager.getMaxConnections());
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

    private void closeHandler(Connection connection) {
        String uid = connection.getUid();
        if (handlerMap.containsKey(uid)) {
            handlerMap.get(uid).cancel();
            handlerMap.remove(uid);
        }
    }

    private void closeAllHandlers() {
        handlerMap.values().stream().forEach(KeepAliveHandler::cancel);
        handlerMap.clear();
    }

}
