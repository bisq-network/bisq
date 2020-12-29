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

import bisq.core.dao.node.full.RawBlock;
import bisq.core.dao.node.messages.GetBlocksRequest;
import bisq.core.dao.node.messages.GetBlocksResponse;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;

import bisq.network.p2p.network.CloseConnectionReason;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.NetworkNode;

import bisq.common.Timer;
import bisq.common.UserThread;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

/**
 * Accepts a GetBlocksRequest from a lite node and sends back a corresponding GetBlocksResponse.
 */
@Slf4j
class GetBlocksRequestHandler {
    private static final long TIMEOUT_MIN = 3;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface Listener {
        void onComplete();

        void onFault(String errorMessage, Connection connection);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final NetworkNode networkNode;
    private final DaoStateService daoStateService;
    private final Listener listener;
    private Timer timeoutTimer;
    private boolean stopped;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public GetBlocksRequestHandler(NetworkNode networkNode, DaoStateService daoStateService, Listener listener) {
        this.networkNode = networkNode;
        this.daoStateService = daoStateService;
        this.listener = listener;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onGetBlocksRequest(GetBlocksRequest getBlocksRequest, Connection connection) {
        long ts = System.currentTimeMillis();
        // We limit number of blocks to 6000 which is about 1.5 month.
        List<Block> blocks = new LinkedList<>(daoStateService.getBlocksFromBlockHeight(getBlocksRequest.getFromBlockHeight(), 6000));
        List<RawBlock> rawBlocks = blocks.stream().map(RawBlock::fromBlock).collect(Collectors.toList());
        GetBlocksResponse getBlocksResponse = new GetBlocksResponse(rawBlocks, getBlocksRequest.getNonce());
        log.info("Received GetBlocksRequest from {} for blocks from height {}. " +
                        "Building GetBlocksResponse with {} blocks took {} ms.",
                connection.getPeersNodeAddressOptional(), getBlocksRequest.getFromBlockHeight(),
                rawBlocks.size(), System.currentTimeMillis() - ts);

        if (timeoutTimer != null) {
            timeoutTimer.stop();
            log.warn("Timeout was already running. We stopped it.");
        }
        timeoutTimer = UserThread.runAfter(() -> {  // setup before sending to avoid race conditions
                    String errorMessage = "A timeout occurred for getBlocksResponse.requestNonce:" +
                            getBlocksResponse.getRequestNonce() +
                            " on connection: " + connection;
                    handleFault(errorMessage, CloseConnectionReason.SEND_MSG_TIMEOUT, connection);
                },
                TIMEOUT_MIN, TimeUnit.MINUTES);

        SettableFuture<Connection> future = networkNode.sendMessage(connection, getBlocksResponse);
        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(Connection connection) {
                if (!stopped) {
                    log.info("Send DataResponse to {} succeeded. getBlocksResponse.getBlocks().size()={}",
                            connection.getPeersNodeAddressOptional(), getBlocksResponse.getBlocks().size());
                    cleanup();
                    listener.onComplete();
                } else {
                    log.trace("We have stopped already. We ignore that networkNode.sendMessage.onSuccess call.");
                }
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                if (!stopped) {
                    String errorMessage = "Sending getBlocksResponse to " + connection +
                            " failed. That is expected if the peer is offline. getBlocksResponse=" + getBlocksResponse + "." +
                            "Exception: " + throwable.getMessage();
                    handleFault(errorMessage, CloseConnectionReason.SEND_MSG_FAILURE, connection);
                } else {
                    log.trace("We have stopped already. We ignore that networkNode.sendMessage.onFailure call.");
                }
            }
        }, MoreExecutors.directExecutor());
    }

    public void stop() {
        cleanup();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handleFault(String errorMessage, CloseConnectionReason closeConnectionReason, Connection connection) {
        if (!stopped) {
            log.warn("{}, closeConnectionReason={}", errorMessage, closeConnectionReason);
            cleanup();
            listener.onFault(errorMessage, connection);
        } else {
            log.warn("We have already stopped (handleFault)");
        }
    }

    private void cleanup() {
        stopped = true;
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }
}
