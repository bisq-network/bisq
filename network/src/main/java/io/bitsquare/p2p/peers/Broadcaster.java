package io.bitsquare.p2p.peers;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.app.Log;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.p2p.storage.messages.DataBroadcastMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class Broadcaster {
    private static final Logger log = LoggerFactory.getLogger(Broadcaster.class);


    public interface Listener {
        void onBroadcasted(DataBroadcastMessage message);
    }

    private final NetworkNode networkNode;
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();

    public Broadcaster(NetworkNode networkNode) {
        this.networkNode = networkNode;
    }

    public void broadcast(DataBroadcastMessage message, @Nullable NodeAddress sender) {
        Log.traceCall("Sender " + sender + ". Message " + message.toString());
        Set<Connection> receivers = networkNode.getConfirmedConnections();
        if (!receivers.isEmpty()) {
            log.info("Broadcast message to {} peers. Message: {}", receivers.size(), message);
            receivers.stream()
                    .filter(connection -> !connection.getPeersNodeAddressOptional().get().equals(sender))
                    .forEach(connection -> {
                        log.trace("Broadcast message to " +
                                connection.getPeersNodeAddressOptional().get() + ".");
                        SettableFuture<Connection> future = networkNode.sendMessage(connection, message);
                        Futures.addCallback(future, new FutureCallback<Connection>() {
                            @Override
                            public void onSuccess(Connection connection) {
                                log.trace("Broadcast to " + connection + " succeeded.");
                                listeners.stream().forEach(listener -> {
                                    listener.onBroadcasted(message);
                                    listeners.remove(listener);
                                });
                            }

                            @Override
                            public void onFailure(@NotNull Throwable throwable) {
                                log.info("Broadcast failed. " + throwable.getMessage());
                            }
                        });
                    });
        } else {
            log.info("Message not broadcasted because we have no available peers yet. " +
                    "message = {}", message);
        }
    }

    // That listener gets immediately removed after the handler is called
    public void addOneTimeListener(Listener listener) {
        listeners.add(listener);
    }

}
