package io.bitsquare.p2p.peers;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.MessageListener;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.p2p.peers.messages.maintenance.MaintenanceMessage;
import io.bitsquare.p2p.peers.messages.maintenance.PingMessage;
import io.bitsquare.p2p.peers.messages.maintenance.PongMessage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MaintenanceManager implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(MaintenanceManager.class);

    private static final int INACTIVITY_PERIOD_BEFORE_PING = 5 * 60 * 1000;
    
    private final NetworkNode networkNode;
    private final Supplier<Map<Address, Peer>> authenticatedPeersSupplier;
    private final Consumer<Address> removePeerConsumer;

    private Timer sendPingTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public MaintenanceManager(NetworkNode networkNode,
                              Supplier<Map<Address, Peer>> authenticatedPeersSupplier,
                              Consumer<Address> removePeerConsumer) {
        this.networkNode = networkNode;
        this.authenticatedPeersSupplier = authenticatedPeersSupplier;
        this.removePeerConsumer = removePeerConsumer;

        networkNode.addMessageListener(this);
        startMaintenanceTimer();
    }

    public void shutDown() {
        Log.traceCall();
        if (sendPingTimer != null) 
            sendPingTimer.cancel();

        networkNode.removeMessageListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof MaintenanceMessage) {
            Log.traceCall(message.toString());
            if (message instanceof PingMessage) {
                SettableFuture<Connection> future = networkNode.sendMessage(connection, new PongMessage(((PingMessage) message).nonce));
                Futures.addCallback(future, new FutureCallback<Connection>() {
                    @Override
                    public void onSuccess(Connection connection) {
                        log.trace("PongMessage sent successfully");
                    }

                    @Override
                    public void onFailure(@NotNull Throwable throwable) {
                        log.info("PongMessage sending failed " + throwable.getMessage());
                        connection.getPeerAddressOptional().ifPresent(peerAddress -> removePeerConsumer.accept(peerAddress));
                    }
                });
            } else if (message instanceof PongMessage) {
                connection.getPeerAddressOptional().ifPresent(peerAddress -> {
                    Peer peer = authenticatedPeersSupplier.get().get(peerAddress);
                    if (peer != null) {
                        if (((PongMessage) message).nonce != peer.pingNonce) {
                            log.warn("PongMessage invalid: self/peer " + networkNode.getAddress() + "/" + peerAddress);
                            removePeerConsumer.accept(peer.address);
                        }
                    }
                });
            }
        }
    }

    private void startMaintenanceTimer() {
        Log.traceCall();
        if (sendPingTimer != null)
            sendPingTimer.cancel();

        sendPingTimer = UserThread.runAfterRandomDelay(() -> {
            pingPeers();
            startMaintenanceTimer();
        }, 5, 7, TimeUnit.MINUTES);
    }

    private void pingPeers() {
        Set<Peer> connectedPeersList = new HashSet<>(authenticatedPeersSupplier.get().values());
        if (!connectedPeersList.isEmpty()) {
            Log.traceCall();
            connectedPeersList.stream()
                    .filter(e -> (new Date().getTime() - e.connection.getLastActivityDate().getTime()) > INACTIVITY_PERIOD_BEFORE_PING)
                    .forEach(e -> UserThread.runAfterRandomDelay(() -> {
                        SettableFuture<Connection> future = networkNode.sendMessage(e.connection, new PingMessage(e.pingNonce));
                        Futures.addCallback(future, new FutureCallback<Connection>() {
                            @Override
                            public void onSuccess(Connection connection) {
                                log.trace("PingMessage sent successfully");
                            }

                            @Override
                            public void onFailure(@NotNull Throwable throwable) {
                                log.info("PingMessage sending failed " + throwable.getMessage());
                                removePeerConsumer.accept(e.address);
                            }
                        });
                    }, 2, 4, TimeUnit.SECONDS));
        }
    }
}
