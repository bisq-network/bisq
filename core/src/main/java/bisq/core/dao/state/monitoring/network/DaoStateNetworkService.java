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

package bisq.core.dao.state.monitoring.network;

import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.monitoring.DaoStateHash;
import bisq.core.dao.state.monitoring.messages.GetDaoStateHashRequest;
import bisq.core.dao.state.monitoring.messages.GetDaoStateHashResponse;
import bisq.core.dao.state.monitoring.messages.NewDaoStateHashMessage;

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
public class DaoStateNetworkService implements DaoStateListener, MessageListener {

    public interface Listener {
        void onNewDaoStateHashMessage(NewDaoStateHashMessage newDaoStateHashMessage, Connection connection);

        void onGetDaoStateHashRequest(Connection connection, GetDaoStateHashRequest getDaoStateHashRequest);

        void onPeersDaoStateHash(DaoStateHash daoStateHash, Optional<NodeAddress> peersNodeAddress);
    }

    //TODO testing
    public final NetworkNode networkNode;
    private final PeerManager peerManager;
    private final Broadcaster broadcaster;

    @Getter
    private final Map<NodeAddress, RequestDaoStateHashHandler> requestDaoStateHashHandlerMap = new HashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DaoStateNetworkService(NetworkNode networkNode,
                                  PeerManager peerManager,
                                  Broadcaster broadcaster) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        this.broadcaster = broadcaster;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (networkEnvelope instanceof NewDaoStateHashMessage) {
            NewDaoStateHashMessage newDaoStateHashMessage = (NewDaoStateHashMessage) networkEnvelope;
            log.info("We received a NewDaoStateHashMessage {} from peer {}",
                    newDaoStateHashMessage, connection.getPeersNodeAddressOptional());

            listeners.forEach(e -> e.onNewDaoStateHashMessage(newDaoStateHashMessage, connection));
        } else if (networkEnvelope instanceof GetDaoStateHashRequest) {
            GetDaoStateHashRequest getDaoStateHashRequest = (GetDaoStateHashRequest) networkEnvelope;
            listeners.forEach(e -> e.onGetDaoStateHashRequest(connection, getDaoStateHashRequest));
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addListeners() {
        networkNode.addMessageListener(this);
    }

    public void sendGetDaoStateHashResponse(Connection connection, int nonce, List<DaoStateHash> daoStateHashes) {
        connection.sendMessage(new GetDaoStateHashResponse(daoStateHashes, nonce));
    }

    public void requestHashesFromAllConnectedSeedNodes(int fromBlockHeight) {
        networkNode.getConfirmedConnections().stream()
                .filter(peerManager::isSeedNode)
                .forEach(connection -> connection.getPeersNodeAddressOptional()
                        .ifPresent(e -> requestHashFromSeedNode(fromBlockHeight, e)));
    }

    public void broadcastMyDaoStateHash(DaoStateHash myDaoStateHash) {
        broadcaster.broadcast(new NewDaoStateHashMessage(myDaoStateHash),
                networkNode.getNodeAddress(),
                null,
                true);
    }

    public void requestHashes(int fromBlockHeight, String peersAddress) {
        requestHashFromSeedNode(fromBlockHeight, new NodeAddress(peersAddress));
    }

    public void reset() {
        requestDaoStateHashHandlerMap.clear();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void requestHashFromSeedNode(int fromBlockHeight, NodeAddress nodeAddress) {
        RequestDaoStateHashHandler requestDaoStateHashHandler = new RequestDaoStateHashHandler(networkNode,
                peerManager,
                nodeAddress,
                new RequestDaoStateHashHandler.Listener() {
                    @Override
                    public void onComplete(GetDaoStateHashResponse getDaoStateHashResponse, Optional<NodeAddress> peersNodeAddress) {
                        log.debug("requestDaoStateHashHandler of outbound connection complete. nodeAddress={}", nodeAddress);
                        requestDaoStateHashHandlerMap.remove(nodeAddress);
                        getDaoStateHashResponse.getDaoStateHashes()
                                .forEach(daoStateHash -> listeners.forEach(e -> e.onPeersDaoStateHash(daoStateHash, peersNodeAddress)));
                    }

                    @Override
                    public void onFault(String errorMessage, @Nullable Connection connection) {
                        log.warn("requestDaoStateHashHandler with outbound connection failed.\n\tnodeAddress={}\n\t" +
                                "ErrorMessage={}", nodeAddress, errorMessage);
                        requestDaoStateHashHandlerMap.remove(nodeAddress);
                    }
                });
        requestDaoStateHashHandlerMap.put(nodeAddress, requestDaoStateHashHandler);
        requestDaoStateHashHandler.requestDaoStateHash(fromBlockHeight);
    }
}
