package io.bitsquare.p2p.peers.keepalive;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Utilities;
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
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class KeepAliveManager implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(KeepAliveManager.class);

    //private static final int INTERVAL_SEC = new Random().nextInt(10) + 10;
    //TODO
    private static final int INTERVAL_SEC = 3;

    private final NetworkNode networkNode;
    private final PeerManager peerManager;
    private ScheduledThreadPoolExecutor executor;
    private final Map<String, KeepAliveHandler> maintenanceHandlerMap = new HashMap<>();
    private boolean shutDownInProgress;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public KeepAliveManager(NetworkNode networkNode, PeerManager peerManager) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;

        networkNode.addMessageListener(this);
    }

    public void shutDown() {
        Log.traceCall();
        shutDownInProgress = true;

        networkNode.removeMessageListener(this);
        maintenanceHandlerMap.values().stream().forEach(KeepAliveHandler::cleanup);

        if (executor != null)
            MoreExecutors.shutdownAndAwaitTermination(executor, 100, TimeUnit.MILLISECONDS);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        if (executor == null) {
            executor = Utilities.getScheduledThreadPoolExecutor("KeepAliveManager", 1, 2, 5);
            executor.scheduleAtFixedRate(() -> UserThread.execute(this::keepAlive),
                    INTERVAL_SEC, INTERVAL_SEC, TimeUnit.SECONDS);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof Ping) {
            Log.traceCall(message.toString() + "\n\tconnection=" + connection);

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
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////


    private void keepAlive() {
        Log.traceCall();

        if (!shutDownInProgress) {
            networkNode.getConfirmedConnections().stream()
                    .filter(connection -> connection instanceof OutboundConnection)
                    .forEach(connection -> {
                        if (!maintenanceHandlerMap.containsKey(connection.getUid())) {
                            KeepAliveHandler keepAliveHandler = new KeepAliveHandler(networkNode, peerManager, new KeepAliveHandler.Listener() {
                                @Override
                                public void onComplete() {
                                    maintenanceHandlerMap.remove(connection.getUid());
                                }

                                @Override
                                public void onFault(String errorMessage, Connection connection) {
                                    maintenanceHandlerMap.remove(connection.getUid());
                                }
                            });
                            maintenanceHandlerMap.put(connection.getUid(), keepAliveHandler);
                            keepAliveHandler.sendPing(connection);
                        } else {
                            log.warn("Connection with id {} has not completed and is still in our map. " +
                                    "We will try to ping that peer at the next schedule.", connection.getUid());
                        }
                    });

            int size = maintenanceHandlerMap.size();
            log.info("maintenanceHandlerMap size=" + size);
            if (size > peerManager.getMaxConnections())
                log.warn("Seems we don't clean up out map correctly.\n" +
                        "maintenanceHandlerMap size={}, peerManager.getMaxConnections()={}", size, peerManager.getMaxConnections());
        }
    }
}
