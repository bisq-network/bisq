package io.bisq.core.dao.node.full.network;

import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.core.dao.blockchain.ReadModel;
import io.bisq.core.dao.blockchain.vo.BsqBlock;
import io.bisq.core.dao.node.messages.GetBsqBlocksRequest;
import io.bisq.core.dao.node.messages.NewBsqBlockBroadcastMessage;
import io.bisq.network.p2p.network.Connection;
import io.bisq.network.p2p.network.MessageListener;
import io.bisq.network.p2p.network.NetworkNode;
import io.bisq.network.p2p.peers.Broadcaster;
import io.bisq.network.p2p.peers.PeerManager;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/**
 * Responsible for handling requests for BSQ blocks from lite nodes and for broadcasting new blocks to the P2P network.
 */
@Slf4j
public class FullNodeNetworkManager implements MessageListener, PeerManager.Listener {

    private static final long CLEANUP_TIMER = 120;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final NetworkNode networkNode;
    private final PeerManager peerManager;
    private final Broadcaster broadcaster;
    private final ReadModel readModel;

    // Key is connection UID
    private final Map<String, GetBsqBlocksRequestHandler> getBlocksRequestHandlers = new HashMap<>();
    private boolean stopped;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public FullNodeNetworkManager(NetworkNode networkNode,
                                  PeerManager peerManager,
                                  Broadcaster broadcaster,
                                  ReadModel readModel) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        this.broadcaster = broadcaster;
        this.readModel = readModel;
        // seedNodeAddresses can be empty (in case there is only 1 seed node, the seed node starting up has no other seed nodes)

        networkNode.addMessageListener(this);
        peerManager.addListener(this);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("Duplicates")
    public void shutDown() {
        Log.traceCall();
        stopped = true;
        networkNode.removeMessageListener(this);
        peerManager.removeListener(this);
    }

    public void publishNewBlock(BsqBlock bsqBlock) {
        log.info("Publish new block at height={} and block hash={}", bsqBlock.getHeight(), bsqBlock.getHash());
        final NewBsqBlockBroadcastMessage newBsqBlockBroadcastMessage = new NewBsqBlockBroadcastMessage(bsqBlock);
        broadcaster.broadcast(newBsqBlockBroadcastMessage, networkNode.getNodeAddress(), null, true);
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
        stopped = false;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(NetworkEnvelope networkEnvelop, Connection connection) {
        if (networkEnvelop instanceof GetBsqBlocksRequest) {
            // We received a GetBsqBlocksRequest from a liteNode
            Log.traceCall(networkEnvelop.toString() + "\n\tconnection=" + connection);
            if (!stopped) {
                final String uid = connection.getUid();
                if (!getBlocksRequestHandlers.containsKey(uid)) {
                    GetBsqBlocksRequestHandler requestHandler = new GetBsqBlocksRequestHandler(networkNode,
                            readModel,
                            new GetBsqBlocksRequestHandler.Listener() {
                                @Override
                                public void onComplete() {
                                    getBlocksRequestHandlers.remove(uid);
                                    log.trace("requestDataHandshake completed.\n\tConnection={}", connection);
                                }

                                @Override
                                public void onFault(String errorMessage, @Nullable Connection connection) {
                                    getBlocksRequestHandlers.remove(uid);
                                    if (!stopped) {
                                        log.trace("GetDataRequestHandler failed.\n\tConnection={}\n\t" +
                                                "ErrorMessage={}", connection, errorMessage);
                                        peerManager.handleConnectionFault(connection);
                                    } else {
                                        log.warn("We have stopped already. We ignore that getDataRequestHandler.handle.onFault call.");
                                    }
                                }
                            });
                    getBlocksRequestHandlers.put(uid, requestHandler);
                    requestHandler.onGetBsqBlocksRequest((GetBsqBlocksRequest) networkEnvelop, connection);
                } else {
                    log.warn("We have already a GetDataRequestHandler for that connection started. " +
                            "We start a cleanup timer if the handler has not closed by itself in between 2 minutes.");

                    UserThread.runAfter(() -> {
                        if (getBlocksRequestHandlers.containsKey(uid)) {
                            GetBsqBlocksRequestHandler handler = getBlocksRequestHandlers.get(uid);
                            handler.stop();
                            getBlocksRequestHandlers.remove(uid);
                        }
                    }, CLEANUP_TIMER);
                }
            } else {
                log.warn("We have stopped already. We ignore that onMessage call.");
            }
        }
    }
}
