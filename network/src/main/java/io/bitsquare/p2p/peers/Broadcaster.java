package io.bitsquare.p2p.peers;

import io.bitsquare.app.Log;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.p2p.storage.messages.BroadcastMessage;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class Broadcaster implements BroadcastHandler.ResultHandler {
    private static final Logger log = LoggerFactory.getLogger(Broadcaster.class);

    private final NetworkNode networkNode;
    private PeerManager peerManager;
    private Set<BroadcastHandler> broadcastHandlers = new CopyOnWriteArraySet<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Broadcaster(NetworkNode networkNode, PeerManager peerManager) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
    }

    public void shutDown() {
        broadcastHandlers.stream().forEach(BroadcastHandler::cancel);
        broadcastHandlers.clear();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void broadcast(BroadcastMessage message, @Nullable NodeAddress sender,
                          @Nullable BroadcastHandler.Listener listener, boolean isDataOwner) {
        Log.traceCall("Sender=" + sender + "\n\t" +
                "Message=" + Utilities.toTruncatedString(message));

        BroadcastHandler broadcastHandler = new BroadcastHandler(networkNode, peerManager);
        broadcastHandler.broadcast(message, sender, this, listener, isDataOwner);
        broadcastHandlers.add(broadcastHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BroadcastHandler.ResultHandler implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onCompleted(BroadcastHandler broadcastHandler) {
        broadcastHandlers.remove(broadcastHandler);
    }

    @Override
    public void onFault(BroadcastHandler broadcastHandler) {
        broadcastHandlers.remove(broadcastHandler);
    }
}
