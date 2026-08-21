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

import bisq.core.dao.monitoring.model.BlindVoteStateHash;
import bisq.core.dao.monitoring.network.messages.GetBlindVoteStateHashesRequest;
import bisq.core.dao.monitoring.network.messages.GetBlindVoteStateHashesResponse;
import bisq.core.dao.monitoring.network.messages.NewBlindVoteStateHashMessage;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.peers.PeerManager;

import bisq.common.proto.network.NetworkEnvelope;

import javax.inject.Inject;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BlindVoteStateNetworkService extends StateNetworkService<NewBlindVoteStateHashMessage,
        GetBlindVoteStateHashesRequest,
        GetBlindVoteStateHashesResponse,
        RequestBlindVoteStateHashesHandler,
        BlindVoteStateHash> {
    @Inject
    public BlindVoteStateNetworkService(NetworkNode networkNode,
                                        PeerManager peerManager,
                                        Broadcaster broadcaster) {
        super(networkNode, peerManager, broadcaster);
    }

    @Override
    protected GetBlindVoteStateHashesRequest castToGetStateHashRequest(NetworkEnvelope networkEnvelope) {
        return (GetBlindVoteStateHashesRequest) networkEnvelope;
    }

    @Override
    protected boolean isGetStateHashesRequest(NetworkEnvelope networkEnvelope) {
        return networkEnvelope instanceof GetBlindVoteStateHashesRequest;
    }

    @Override
    protected NewBlindVoteStateHashMessage castToNewStateHashMessage(NetworkEnvelope networkEnvelope) {
        return (NewBlindVoteStateHashMessage) networkEnvelope;
    }

    @Override
    protected boolean isNewStateHashMessage(NetworkEnvelope networkEnvelope) {
        return networkEnvelope instanceof NewBlindVoteStateHashMessage;
    }

    @Override
    protected GetBlindVoteStateHashesResponse getGetStateHashesResponse(int nonce, List<BlindVoteStateHash> stateHashes) {
        return new GetBlindVoteStateHashesResponse(stateHashes, nonce);
    }

    @Override
    protected NewBlindVoteStateHashMessage getNewStateHashMessage(BlindVoteStateHash myStateHash) {
        return new NewBlindVoteStateHashMessage(myStateHash);
    }

    @Override
    protected RequestBlindVoteStateHashesHandler getRequestStateHashesHandler(NodeAddress nodeAddress, RequestStateHashesHandler.Listener<GetBlindVoteStateHashesResponse> listener) {
        return new RequestBlindVoteStateHashesHandler(networkNode, peerManager, nodeAddress, listener);
    }
}
