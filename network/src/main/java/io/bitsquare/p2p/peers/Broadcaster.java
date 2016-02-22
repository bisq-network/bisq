package io.bitsquare.p2p.peers;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.app.Log;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.CloseConnectionReason;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.ConnectionListener;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.p2p.storage.messages.DataBroadcastMessage;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class Broadcaster implements ConnectionListener, PeerManager.Listener {
    private static final Logger log = LoggerFactory.getLogger(Broadcaster.class);


    public interface Listener {
        void onBroadcasted(DataBroadcastMessage message);
    }

    private final NetworkNode networkNode;
    private PeerManager peerManager;
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private boolean stopped = false;

    private final IntegerProperty numOfBroadcasts = new SimpleIntegerProperty(0);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Broadcaster(NetworkNode networkNode, PeerManager peerManager) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        networkNode.removeConnectionListener(this);
        peerManager.removeListener(this);
    }

    public void shutDown() {
        stopped = true;
        networkNode.removeConnectionListener(this);
        peerManager.removeListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void broadcast(DataBroadcastMessage message, @Nullable NodeAddress sender) {
        Log.traceCall("Sender=" + sender + "\n\t" +
                "Message=" + StringUtils.abbreviate(message.toString(), 100));
        numOfBroadcasts.set(0);
        Set<Connection> receivers = networkNode.getConfirmedConnections();
        if (!receivers.isEmpty()) {
            log.info("Broadcast message to {} peers.", receivers.size());
            receivers.stream()
                    .filter(connection -> !connection.getPeersNodeAddressOptional().get().equals(sender))
                    .forEach(connection -> {
                        NodeAddress nodeAddress = connection.getPeersNodeAddressOptional().get();
                        log.trace("Broadcast message to " + nodeAddress + ".");
                        SettableFuture<Connection> future = networkNode.sendMessage(connection, message);
                        Futures.addCallback(future, new FutureCallback<Connection>() {
                            @Override
                            public void onSuccess(Connection connection) {
                                if (!stopped) {
                                    //log.trace("Broadcast to " + nodeAddress + " succeeded.");
                                    numOfBroadcasts.set(numOfBroadcasts.get() + 1);
                                    listeners.stream().forEach(listener -> listener.onBroadcasted(message));
                                } else {
                                    log.warn("We have stopped already. We ignore that networkNode.sendMessage.onSuccess call.");
                                }
                            }

                            @Override
                            public void onFailure(@NotNull Throwable throwable) {
                                if (!stopped) {
                                    log.info("Broadcast to " + nodeAddress + " failed.\n\t" +
                                            "ErrorMessage=" + throwable.getMessage());
                                } else {
                                    log.warn("We have stopped already. We ignore that networkNode.sendMessage.onFailure call.");
                                }
                            }
                        });
                    });
        } else {
            log.warn("Message not broadcasted because we have no available peers yet.\n\t" +
                    "That should never happen as broadcast should not be called in such cases.\n" +
                    "message = {}", StringUtils.abbreviate(message.toString(), 100));
        }
    }

    public IntegerProperty getNumOfBroadcastsProperty() {
        return numOfBroadcasts;
    }

    // That listener gets immediately removed after the handler is called
    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ConnectionListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnection(Connection connection) {
        stopped = false;
    }

    @Override
    public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
    }

    @Override
    public void onError(Throwable throwable) {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PeerManager.Listener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAllConnectionsLost() {
        stopped = true;
    }

    @Override
    public void onNewConnectionAfterAllConnectionsLost() {
        stopped = false;
    }

    @Override
    public void onAwakeFromStandby() {
        if (!networkNode.getAllConnections().isEmpty())
            stopped = false;
    }
}
