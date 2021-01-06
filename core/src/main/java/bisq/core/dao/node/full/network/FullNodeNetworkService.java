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

package bisq.core.dao.node.full.network;

import bisq.core.dao.governance.blindvote.network.messages.RepublishGovernanceDataRequest;
import bisq.core.dao.governance.voteresult.MissingDataRequestService;
import bisq.core.dao.node.full.RawBlock;
import bisq.core.dao.node.messages.GetBlocksRequest;
import bisq.core.dao.node.messages.NewBlockBroadcastMessage;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;

import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.peers.PeerManager;

import bisq.common.UserThread;
import bisq.common.proto.network.NetworkEnvelope;

import javax.inject.Inject;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.Nullable;

/**
 * Responsible for handling requests for BSQ blocks from lite nodes and for broadcasting new blocks to the P2P network.
 */
@Slf4j
public class FullNodeNetworkService implements MessageListener, PeerManager.Listener {

    private static final long CLEANUP_TIMER = 120;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final NetworkNode networkNode;
    private final PeerManager peerManager;
    private final Broadcaster broadcaster;
    private final MissingDataRequestService missingDataRequestService;
    private final DaoStateService daoStateService;

    // Key is connection UID
    private final Map<String, GetBlocksRequestHandler> getBlocksRequestHandlers = new HashMap<>();
    private boolean stopped;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public FullNodeNetworkService(NetworkNode networkNode,
                                  PeerManager peerManager,
                                  Broadcaster broadcaster,
                                  MissingDataRequestService missingDataRequestService,
                                  DaoStateService daoStateService) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        this.broadcaster = broadcaster;
        this.missingDataRequestService = missingDataRequestService;
        this.daoStateService = daoStateService;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        networkNode.addMessageListener(this);
        peerManager.addListener(this);
    }

    @SuppressWarnings("Duplicates")
    public void shutDown() {
        stopped = true;
        networkNode.removeMessageListener(this);
        peerManager.removeListener(this);
    }

    public void publishNewBlock(Block block) {
        log.info("Publish new block at height={} and block hash={}", block.getHeight(), block.getHash());
        RawBlock rawBlock = RawBlock.fromBlock(block);
        NewBlockBroadcastMessage newBlockBroadcastMessage = new NewBlockBroadcastMessage(rawBlock);
        broadcaster.broadcast(newBlockBroadcastMessage, networkNode.getNodeAddress());
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
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (networkEnvelope instanceof GetBlocksRequest) {
            handleGetBlocksRequest((GetBlocksRequest) networkEnvelope, connection);
        } else if (networkEnvelope instanceof RepublishGovernanceDataRequest) {
            handleRepublishGovernanceDataRequest();
        }
    }

    private void handleGetBlocksRequest(GetBlocksRequest getBlocksRequest, Connection connection) {
        if (stopped) {
            log.warn("We have stopped already. We ignore that onMessage call.");
            return;
        }

        String uid = connection.getUid();
        if (getBlocksRequestHandlers.containsKey(uid)) {
            log.warn("We have already a GetDataRequestHandler for that connection started. " +
                    "We start a cleanup timer if the handler has not closed by itself in between 2 minutes.");

            UserThread.runAfter(() -> {
                if (getBlocksRequestHandlers.containsKey(uid)) {
                    GetBlocksRequestHandler handler = getBlocksRequestHandlers.get(uid);
                    handler.stop();
                    getBlocksRequestHandlers.remove(uid);
                }
            }, CLEANUP_TIMER);
            return;
        }

        GetBlocksRequestHandler requestHandler = new GetBlocksRequestHandler(networkNode,
                daoStateService,
                new GetBlocksRequestHandler.Listener() {
                    @Override
                    public void onComplete() {
                        getBlocksRequestHandlers.remove(uid);
                    }

                    @Override
                    public void onFault(String errorMessage, @Nullable Connection connection) {
                        getBlocksRequestHandlers.remove(uid);
                        if (!stopped) {
                            log.trace("GetDataRequestHandler failed.\n\tConnection={}\n\t" +
                                    "ErrorMessage={}", connection, errorMessage);
                            if (connection != null) {
                                peerManager.handleConnectionFault(connection);
                            }
                        } else {
                            log.warn("We have stopped already. We ignore that getDataRequestHandler.handle.onFault call.");
                        }
                    }
                });
        getBlocksRequestHandlers.put(uid, requestHandler);
        requestHandler.onGetBlocksRequest(getBlocksRequest, connection);
    }

    private void handleRepublishGovernanceDataRequest() {
        log.warn("We received a RepublishGovernanceDataRequest and re-published all proposalPayloads and " +
                "blindVotePayloads to the P2P network.");
        missingDataRequestService.reRepublishAllGovernanceData();
    }
}
