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

package bisq.core.dao.monitoring.network;

import bisq.core.dao.monitoring.network.messages.GetStateHashesRequest;
import bisq.core.dao.monitoring.network.messages.GetStateHashesResponse;

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

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Slf4j
abstract class RequestStateHashesHandler<Req extends GetStateHashesRequest, Res extends GetStateHashesResponse> implements MessageListener {
    private static final long TIMEOUT = 120;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface Listener<Res extends GetStateHashesResponse> {
        void onComplete(Res getStateHashesResponse, Optional<NodeAddress> peersNodeAddress);

        @SuppressWarnings("UnusedParameters")
        void onFault(String errorMessage, @SuppressWarnings("SameParameterValue") @Nullable Connection connection);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final NetworkNode networkNode;
    private final PeerManager peerManager;
    private final NodeAddress nodeAddress;
    private final Listener<Res> listener;
    private Timer timeoutTimer;
    final int nonce = new Random().nextInt();
    private boolean stopped;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    RequestStateHashesHandler(NetworkNode networkNode,
                              PeerManager peerManager,
                              NodeAddress nodeAddress,
                              Listener<Res> listener) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        this.nodeAddress = nodeAddress;
        this.listener = listener;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Abstract
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected abstract Req getGetStateHashesRequest(int fromHeight);

    protected abstract Res castToGetStateHashesResponse(NetworkEnvelope networkEnvelope);

    protected abstract boolean isGetStateHashesResponse(NetworkEnvelope networkEnvelope);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void requestStateHashes(int fromHeight) {
        if (!stopped) {
            Req getStateHashesRequest = getGetStateHashesRequest(fromHeight);
            if (timeoutTimer == null) {
                timeoutTimer = UserThread.runAfter(() -> {  // setup before sending to avoid race conditions
                            if (!stopped) {
                                String errorMessage = "A timeout occurred at sending getStateHashesRequest:" + getStateHashesRequest +
                                        " on peersNodeAddress:" + nodeAddress;
                                log.debug(errorMessage + " / RequestStateHashesHandler=" + RequestStateHashesHandler.this);
                                handleFault(errorMessage, nodeAddress, CloseConnectionReason.SEND_MSG_TIMEOUT);
                            } else {
                                log.trace("We have stopped already. We ignore that timeoutTimer.run call. " +
                                        "Might be caused by a previous networkNode.sendMessage.onFailure.");
                            }
                        },
                        TIMEOUT);
            }

            log.debug("We send to peer {} a {}.", nodeAddress, getStateHashesRequest);
            networkNode.addMessageListener(this);
            SettableFuture<Connection> future = networkNode.sendMessage(nodeAddress, getStateHashesRequest);
            Futures.addCallback(future, new FutureCallback<>() {
                @Override
                public void onSuccess(Connection connection) {
                    if (!stopped) {
                        log.info("Sending of {} message to peer {} succeeded.",
                                getStateHashesRequest.getClass().getSimpleName(),
                                nodeAddress.getFullAddress());
                    } else {
                        log.trace("We have stopped already. We ignore that networkNode.sendMessage.onSuccess call." +
                                "Might be caused by a previous timeout.");
                    }
                }

                @Override
                public void onFailure(@NotNull Throwable throwable) {
                    if (!stopped) {
                        String errorMessage = "Sending getStateHashesRequest to " + nodeAddress +
                                " failed. That is expected if the peer is offline.\n\t" +
                                "getStateHashesRequest=" + getStateHashesRequest + "." +
                                "\n\tException=" + throwable.getMessage();
                        log.error(errorMessage);
                        handleFault(errorMessage, nodeAddress, CloseConnectionReason.SEND_MSG_FAILURE);
                    } else {
                        log.trace("We have stopped already. We ignore that networkNode.sendMessage.onFailure call. " +
                                "Might be caused by a previous timeout.");
                    }
                }
            }, MoreExecutors.directExecutor());
        } else {
            log.warn("We have stopped already. We ignore that requestProposalsHash call.");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (isGetStateHashesResponse(networkEnvelope)) {
            if (connection.getPeersNodeAddressOptional().isPresent() && connection.getPeersNodeAddressOptional().get().equals(nodeAddress)) {
                if (!stopped) {
                    Res getStateHashesResponse = castToGetStateHashesResponse(networkEnvelope);
                    if (getStateHashesResponse.getRequestNonce() == nonce) {
                        stopTimeoutTimer();
                        cleanup();
                        log.info("We received from peer {} a {} with {} stateHashes",
                                nodeAddress.getFullAddress(), getStateHashesResponse.getClass().getSimpleName(),
                                getStateHashesResponse.getStateHashes().size());
                        listener.onComplete(getStateHashesResponse, connection.getPeersNodeAddressOptional());
                    } else {
                        log.warn("Nonce not matching. That can happen rarely if we get a response after a canceled " +
                                        "handshake (timeout causes connection close but peer might have sent a msg before " +
                                        "connection was closed).\n\t" +
                                        "We drop that message. nonce={} / requestNonce={}",
                                nonce, getStateHashesResponse.getRequestNonce());
                    }
                } else {
                    log.warn("We have stopped already.");
                }
            } else if (connection.getPeersNodeAddressOptional().isPresent()) {
                log.debug("{}: We got a message from another node. We ignore that.",
                        this.getClass().getSimpleName());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("UnusedParameters")
    private void handleFault(String errorMessage, NodeAddress nodeAddress, CloseConnectionReason closeConnectionReason) {
        cleanup();
        peerManager.handleConnectionFault(nodeAddress);
        listener.onFault(errorMessage, null);
    }

    private void cleanup() {
        stopped = true;
        networkNode.removeMessageListener(this);
        stopTimeoutTimer();
    }

    private void stopTimeoutTimer() {
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }
}
