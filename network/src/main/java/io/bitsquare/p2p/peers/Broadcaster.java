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

public class Broadcaster {
    private static final Logger log = LoggerFactory.getLogger(Broadcaster.class);
    private final NetworkNode networkNode;

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
                        log.trace("Broadcast message from " + networkNode.getNodeAddress() + " to " +
                                connection.getPeersNodeAddressOptional().get() + ".");
                        SettableFuture<Connection> future = networkNode.sendMessage(connection, message);
                        Futures.addCallback(future, new FutureCallback<Connection>() {
                            @Override
                            public void onSuccess(Connection connection) {
                                log.trace("Broadcast from " + networkNode.getNodeAddress() + " to " + connection + " succeeded.");
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
}
