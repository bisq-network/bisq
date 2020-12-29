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

package bisq.core.dao.node.lite.network;

import bisq.core.dao.node.messages.GetBlocksRequest;
import bisq.core.dao.node.messages.GetBlocksResponse;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.CloseConnectionReason;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.PeerManager;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.proto.network.NetworkEnvelope;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Sends a GetBlocksRequest to a full node and listens on corresponding GetBlocksResponse from the full node.
 */
@Slf4j
public class RequestBlocksHandler implements MessageListener {
    private static final long TIMEOUT_MIN = 3;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface Listener {
        void onComplete(GetBlocksResponse getBlocksResponse);

        @SuppressWarnings("UnusedParameters")
        void onFault(String errorMessage, @SuppressWarnings("SameParameterValue") @Nullable Connection connection);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final NetworkNode networkNode;
    private final PeerManager peerManager;
    @Getter
    private final NodeAddress nodeAddress;
    @Getter
    private final int startBlockHeight;
    private final Listener listener;
    private Timer timeoutTimer;
    private final int nonce = new Random().nextInt();
    private boolean stopped;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public RequestBlocksHandler(NetworkNode networkNode,
                                PeerManager peerManager,
                                NodeAddress nodeAddress,
                                int startBlockHeight,
                                Listener listener) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        this.nodeAddress = nodeAddress;
        this.startBlockHeight = startBlockHeight;
        this.listener = listener;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void requestBlocks() {
        if (stopped) {
            log.warn("We have stopped already. We ignore that requestData call.");
            return;
        }

        GetBlocksRequest getBlocksRequest = new GetBlocksRequest(startBlockHeight, nonce, networkNode.getNodeAddress());

        if (timeoutTimer != null) {
            log.warn("We had a timer already running and stop it.");
            timeoutTimer.stop();
        }
        timeoutTimer = UserThread.runAfter(() -> {  // setup before sending to avoid race conditions
                    if (!stopped) {
                        String errorMessage = "A timeout occurred when sending getBlocksRequest:" + getBlocksRequest +
                                " on peersNodeAddress:" + nodeAddress;
                        log.debug("{} / RequestDataHandler={}", errorMessage, RequestBlocksHandler.this);
                        handleFault(errorMessage, nodeAddress, CloseConnectionReason.SEND_MSG_TIMEOUT);
                    } else {
                        log.warn("We have stopped already. We ignore that timeoutTimer.run call. " +
                                "Might be caused by a previous networkNode.sendMessage.onFailure.");
                    }
                },
                TIMEOUT_MIN, TimeUnit.MINUTES);

        log.info("We request blocks from peer {} from block height {}.", nodeAddress, getBlocksRequest.getFromBlockHeight());

        networkNode.addMessageListener(this);

        SettableFuture<Connection> future = networkNode.sendMessage(nodeAddress, getBlocksRequest);
        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(Connection connection) {
                log.info("Sending of GetBlocksRequest message to peer {} succeeded.", nodeAddress.getFullAddress());
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                if (!stopped) {
                    String errorMessage = "Sending getBlocksRequest to " + nodeAddress +
                            " failed. That is expected if the peer is offline.\n\t" +
                            "getBlocksRequest=" + getBlocksRequest + "." +
                            "\n\tException=" + throwable.getMessage();
                    log.error(errorMessage);
                    handleFault(errorMessage, nodeAddress, CloseConnectionReason.SEND_MSG_FAILURE);
                } else {
                    log.warn("We have stopped already. We ignore that networkNode.sendMessage.onFailure call.");
                }
            }
        }, MoreExecutors.directExecutor());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (networkEnvelope instanceof GetBlocksResponse) {
            if (stopped) {
                log.warn("We have stopped already. We ignore that onDataRequest call.");
                return;
            }

            Optional<NodeAddress> optionalNodeAddress = connection.getPeersNodeAddressOptional();
            if (!optionalNodeAddress.isPresent()) {
                log.warn("Peers node address is not present, that is not expected.");
                // We do not return here as in case the connection has been created from the peers side we might not
                // have the address set. As we check the nonce later we do not care that much for the check if the
                // connection address is the same as the one we used.
            } else if (!optionalNodeAddress.get().equals(nodeAddress)) {
                log.warn("Peers node address is not the same we used for the request. This is not expected. We ignore that message.");
                return;
            }

            GetBlocksResponse getBlocksResponse = (GetBlocksResponse) networkEnvelope;
            if (getBlocksResponse.getRequestNonce() != nonce) {
                log.warn("Nonce not matching. That can happen rarely if we get a response after a canceled " +
                                "handshake (timeout causes connection close but peer might have sent a msg before " +
                                "connection was closed).\n\t" +
                                "We drop that message. nonce={} / requestNonce={}",
                        nonce, getBlocksResponse.getRequestNonce());
                return;
            }

            terminate();
            log.info("We received from peer {} a BlocksResponse with {} blocks",
                    nodeAddress.getFullAddress(), getBlocksResponse.getBlocks().size());
            listener.onComplete(getBlocksResponse);
        }
    }

    public void terminate() {
        stopped = true;
        networkNode.removeMessageListener(this);
        stopTimeoutTimer();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////


    @SuppressWarnings("UnusedParameters")
    private void handleFault(String errorMessage,
                             NodeAddress nodeAddress,
                             CloseConnectionReason closeConnectionReason) {
        terminate();
        peerManager.handleConnectionFault(nodeAddress);
        listener.onFault(errorMessage, null);
    }

    private void stopTimeoutTimer() {
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }
}
