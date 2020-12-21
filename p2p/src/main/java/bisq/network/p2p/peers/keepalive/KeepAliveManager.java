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

package bisq.network.p2p.peers.keepalive;

import bisq.network.p2p.network.CloseConnectionReason;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.ConnectionListener;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.network.OutboundConnection;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.peers.keepalive.messages.Ping;
import bisq.network.p2p.peers.keepalive.messages.Pong;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.util.Utilities;

import javax.inject.Inject;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jetbrains.annotations.NotNull;

public class KeepAliveManager implements MessageListener, ConnectionListener, PeerManager.Listener {
    private static final Logger log = LoggerFactory.getLogger(KeepAliveManager.class);

    private static final int INTERVAL_SEC = new Random().nextInt(30) + 30;
    private static final long LAST_ACTIVITY_AGE_MS = INTERVAL_SEC * 1000 / 2;

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
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (networkEnvelope instanceof Ping) {
            if (!stopped) {
                Ping ping = (Ping) networkEnvelope;

                // We get from peer last measured rrt
                connection.getStatistic().setRoundTripTime(ping.getLastRoundTripTime());

                Pong pong = new Pong(ping.getNonce());
                SettableFuture<Connection> future = networkNode.sendMessage(connection, pong);
                Futures.addCallback(future, Utilities.failureCallback(throwable -> {
                    if (!stopped) {
                        String errorMessage = "Sending pong to " + connection +
                                " failed. That is expected if the peer is offline. " +
                                "Exception: " + throwable.getMessage();
                        log.info(errorMessage);
                        peerManager.handleConnectionFault(connection);
                    } else {
                        log.warn("We have stopped already. We ignore that  networkNode.sendMessage.onFailure call.");
                    }
                }), MoreExecutors.directExecutor());
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
    }

    @Override
    public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
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
        closeAllHandlers();
        stopKeepAliveTimer();
        stopped = true;
        restart();
    }

    @Override
    public void onNewConnectionAfterAllConnectionsLost() {
        closeAllHandlers();
        stopped = false;
        restart();
    }

    @Override
    public void onAwakeFromStandby() {
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
