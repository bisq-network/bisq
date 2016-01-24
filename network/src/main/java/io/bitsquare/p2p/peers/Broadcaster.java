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
        Set<NodeAddress> receivers = networkNode.getNodeAddressesOfSucceededConnections();
        if (!receivers.isEmpty()) {
            log.info("Broadcast message to {} peers. Message: {}", receivers.size(), message);
            receivers.stream()
                    .filter(e -> !e.equals(sender))
                    .forEach(nodeAddress -> {
                        log.trace("Broadcast message from " + networkNode.getNodeAddress() + " to " + nodeAddress + ".");
                        SettableFuture<Connection> future = networkNode.sendMessage(nodeAddress, message);
                        Futures.addCallback(future, new FutureCallback<Connection>() {
                            @Override
                            public void onSuccess(Connection connection) {
                                log.trace("Broadcast from " + networkNode.getNodeAddress() + " to " + nodeAddress + " succeeded.");
                                if (connection != null) {
                                    if (!connection.getPeersNodeAddressOptional().isPresent())
                                        connection.setPeersNodeAddress(nodeAddress);

                                    if (connection.getPeerType() == null)
                                        connection.setPeerType(Connection.PeerType.PEER);
                                }
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
