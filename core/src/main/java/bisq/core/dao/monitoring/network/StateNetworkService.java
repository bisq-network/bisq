/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.monitoring.network;

import bisq.core.dao.monitoring.model.StateHash;
import bisq.core.dao.monitoring.network.messages.GetStateHashesRequest;
import bisq.core.dao.monitoring.network.messages.GetStateHashesResponse;
import bisq.core.dao.monitoring.network.messages.NewStateHashMessage;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.peers.PeerManager;

import bisq.common.proto.network.NetworkEnvelope;

import javax.inject.Inject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Monitors the DaoState with using a hash fo the complete daoState and make it accessible to the network for
 * so we can detect quickly if any consensus issue arise. The data does not contain any private user
 * data so sharing it on demand has no privacy concerns.
 *
 * We request the state from the connected seed nodes after batch processing of BSQ is complete as well as we start
 * to listen for broadcast messages from our peers about dao state of new blocks. It could be that the received dao
 * state from the peers is already covering the next block we have not received yet. So we only take data in account
 * which are inside the block height we have already. To avoid such race conditions we delay the broadcasting of our
 * state to the peers to not get ignored it in case they have not received the block yet.
 *
 * We do not persist that chain of hashes and we only create it from the blocks we parse, so we start from the height
 * of the latest block in the snapshot.
 *
 * TODO maybe request full state?
 * TODO add p2p network data for monitoring
 * TODO auto recovery
 */
@Slf4j
public abstract class StateNetworkService<Msg extends NewStateHashMessage,
        Req extends GetStateHashesRequest,
        Res extends GetStateHashesResponse<StH>,
        Han extends RequestStateHashesHandler,
        StH extends StateHash> implements MessageListener {

    public interface Listener<Msg extends NewStateHashMessage, Req extends GetStateHashesRequest, StH extends StateHash> {
        void onNewStateHashMessage(Msg newStateHashMessage, Connection connection);

        void onGetStateHashRequest(Connection connection, Req getStateHashRequest);

        void onPeersStateHashes(List<StH> stateHashes, Optional<NodeAddress> peersNodeAddress);
    }

    //TODO testing
    final NetworkNode networkNode;
    final PeerManager peerManager;
    private final Broadcaster broadcaster;

    @Getter
    private final Map<NodeAddress, Han> requestStateHashHandlerMap = new HashMap<>();
    private final List<Listener<Msg, Req, StH>> listeners = new CopyOnWriteArrayList<>();
    private boolean messageListenerAdded;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public StateNetworkService(NetworkNode networkNode,
                               PeerManager peerManager,
                               Broadcaster broadcaster) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        this.broadcaster = broadcaster;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Abstract
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected abstract Req castToGetStateHashRequest(NetworkEnvelope networkEnvelope);


    protected abstract boolean isGetStateHashesRequest(NetworkEnvelope networkEnvelope);


    protected abstract Msg castToNewStateHashMessage(NetworkEnvelope networkEnvelope);


    protected abstract boolean isNewStateHashMessage(NetworkEnvelope networkEnvelope);

    protected abstract Res getGetStateHashesResponse(int nonce, List<StH> stateHashes);

    protected abstract Msg getNewStateHashMessage(StH myStateHash);

    protected abstract Han getRequestStateHashesHandler(NodeAddress nodeAddress, RequestStateHashesHandler.Listener<Res> listener);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (isNewStateHashMessage(networkEnvelope)) {
            Msg newStateHashMessage = castToNewStateHashMessage(networkEnvelope);
            log.info("We received a {} from peer {} with stateHash={} ",
                    newStateHashMessage.getClass().getSimpleName(),
                    connection.getPeersNodeAddressOptional(),
                    newStateHashMessage.getStateHash());
            listeners.forEach(e -> e.onNewStateHashMessage(newStateHashMessage, connection));
        } else if (isGetStateHashesRequest(networkEnvelope)) {
            Req getStateHashRequest = castToGetStateHashRequest(networkEnvelope);
            log.info("We received a {} from peer {} for height={} ",
                    getStateHashRequest.getClass().getSimpleName(),
                    connection.getPeersNodeAddressOptional(),
                    getStateHashRequest.getHeight());
            listeners.forEach(e -> e.onGetStateHashRequest(connection, getStateHashRequest));
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addListeners() {
        if (!messageListenerAdded) {
            networkNode.addMessageListener(this);
            messageListenerAdded = true;
        }
    }

    public void sendGetStateHashesResponse(Connection connection, int nonce, List<StH> stateHashes) {
        Res getStateHashesResponse = getGetStateHashesResponse(nonce, stateHashes);
        log.info("Send {} with {} stateHashes to peer {}", getStateHashesResponse.getClass().getSimpleName(),
                stateHashes.size(), connection.getPeersNodeAddressOptional());
        connection.sendMessage(getStateHashesResponse);
    }

    public void requestHashesFromAllConnectedSeedNodes(int fromHeight) {
        networkNode.getConfirmedConnections().stream()
                .filter(peerManager::isSeedNode)
                .forEach(connection -> connection.getPeersNodeAddressOptional()
                        .ifPresent(e -> requestHashesFromSeedNode(fromHeight, e)));
    }

    public void broadcastMyStateHash(StH myStateHash) {
        NewStateHashMessage newStateHashMessage = getNewStateHashMessage(myStateHash);
        broadcaster.broadcast(newStateHashMessage, networkNode.getNodeAddress(), null, true);
    }

    public void requestHashes(int fromHeight, String peersAddress) {
        requestHashesFromSeedNode(fromHeight, new NodeAddress(peersAddress));
    }

    public void reset() {
        requestStateHashHandlerMap.clear();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addListener(Listener<Msg, Req, StH> listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener<Msg, Req, StH> listener) {
        listeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void requestHashesFromSeedNode(int fromHeight, NodeAddress nodeAddress) {
        RequestStateHashesHandler.Listener<Res> listener = new RequestStateHashesHandler.Listener<>() {
            @Override
            public void onComplete(Res getStateHashesResponse, Optional<NodeAddress> peersNodeAddress) {
                requestStateHashHandlerMap.remove(nodeAddress);
                List<StH> stateHashes = getStateHashesResponse.getStateHashes();
                listeners.forEach(e -> e.onPeersStateHashes(stateHashes, peersNodeAddress));
            }

            @Override
            public void onFault(String errorMessage, @Nullable Connection connection) {
                log.warn("requestDaoStateHashesHandler with outbound connection failed.\n\tnodeAddress={}\n\t" +
                        "ErrorMessage={}", nodeAddress, errorMessage);
                requestStateHashHandlerMap.remove(nodeAddress);
            }
        };
        Han requestStateHashesHandler = getRequestStateHashesHandler(nodeAddress, listener);
        requestStateHashHandlerMap.put(nodeAddress, requestStateHashesHandler);
        requestStateHashesHandler.requestStateHashes(fromHeight);
    }
}
