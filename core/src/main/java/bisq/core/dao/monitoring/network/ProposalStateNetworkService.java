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

import bisq.core.dao.monitoring.model.ProposalStateHash;
import bisq.core.dao.monitoring.network.messages.GetProposalStateHashesRequest;
import bisq.core.dao.monitoring.network.messages.GetProposalStateHashesResponse;
import bisq.core.dao.monitoring.network.messages.NewProposalStateHashMessage;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.peers.PeerManager;

import bisq.common.proto.network.NetworkEnvelope;

import javax.inject.Inject;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProposalStateNetworkService extends StateNetworkService<NewProposalStateHashMessage,
        GetProposalStateHashesRequest,
        GetProposalStateHashesResponse,
        RequestProposalStateHashesHandler,
        ProposalStateHash> {
    @Inject
    public ProposalStateNetworkService(NetworkNode networkNode,
                                       PeerManager peerManager,
                                       Broadcaster broadcaster) {
        super(networkNode, peerManager, broadcaster);
    }

    @Override
    protected GetProposalStateHashesRequest castToGetStateHashRequest(NetworkEnvelope networkEnvelope) {
        return (GetProposalStateHashesRequest) networkEnvelope;
    }

    @Override
    protected boolean isGetStateHashesRequest(NetworkEnvelope networkEnvelope) {
        return networkEnvelope instanceof GetProposalStateHashesRequest;
    }

    @Override
    protected NewProposalStateHashMessage castToNewStateHashMessage(NetworkEnvelope networkEnvelope) {
        return (NewProposalStateHashMessage) networkEnvelope;
    }

    @Override
    protected boolean isNewStateHashMessage(NetworkEnvelope networkEnvelope) {
        return networkEnvelope instanceof NewProposalStateHashMessage;
    }

    @Override
    protected GetProposalStateHashesResponse getGetStateHashesResponse(int nonce, List<ProposalStateHash> stateHashes) {
        return new GetProposalStateHashesResponse(stateHashes, nonce);
    }

    @Override
    protected NewProposalStateHashMessage getNewStateHashMessage(ProposalStateHash myStateHash) {
        return new NewProposalStateHashMessage(myStateHash);
    }

    @Override
    protected RequestProposalStateHashesHandler getRequestStateHashesHandler(NodeAddress nodeAddress, RequestStateHashesHandler.Listener<GetProposalStateHashesResponse> listener) {
        return new RequestProposalStateHashesHandler(networkNode, peerManager, nodeAddress, listener);
    }

}
