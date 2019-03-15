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

import bisq.core.dao.monitoring.model.DaoStateHash;
import bisq.core.dao.monitoring.network.messages.GetDaoStateHashesRequest;
import bisq.core.dao.monitoring.network.messages.GetDaoStateHashesResponse;
import bisq.core.dao.monitoring.network.messages.NewDaoStateHashMessage;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.peers.PeerManager;

import bisq.common.proto.network.NetworkEnvelope;

import javax.inject.Inject;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

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
public class DaoStateNetworkService extends StateNetworkService<NewDaoStateHashMessage,
        GetDaoStateHashesRequest,
        GetDaoStateHashesResponse,
        RequestDaoStateHashesHandler,
        DaoStateHash> {
    @Inject
    public DaoStateNetworkService(NetworkNode networkNode,
                                  PeerManager peerManager,
                                  Broadcaster broadcaster) {
        super(networkNode, peerManager, broadcaster);
    }

    @Override
    protected GetDaoStateHashesRequest castToGetStateHashRequest(NetworkEnvelope networkEnvelope) {
        return (GetDaoStateHashesRequest) networkEnvelope;
    }

    @Override
    protected boolean isGetStateHashesRequest(NetworkEnvelope networkEnvelope) {
        return networkEnvelope instanceof GetDaoStateHashesRequest;
    }

    @Override
    protected NewDaoStateHashMessage castToNewStateHashMessage(NetworkEnvelope networkEnvelope) {
        return (NewDaoStateHashMessage) networkEnvelope;
    }

    @Override
    protected boolean isNewStateHashMessage(NetworkEnvelope networkEnvelope) {
        return networkEnvelope instanceof NewDaoStateHashMessage;
    }

    @Override
    protected GetDaoStateHashesResponse getGetStateHashesResponse(int nonce, List<DaoStateHash> stateHashes) {
        return new GetDaoStateHashesResponse(stateHashes, nonce);
    }

    @Override
    protected NewDaoStateHashMessage getNewStateHashMessage(DaoStateHash myStateHash) {
        return new NewDaoStateHashMessage(myStateHash);
    }

    @Override
    protected RequestDaoStateHashesHandler getRequestStateHashesHandler(NodeAddress nodeAddress, RequestStateHashesHandler.Listener<GetDaoStateHashesResponse> listener) {
        return new RequestDaoStateHashesHandler(networkNode, peerManager, nodeAddress, listener);
    }

}
