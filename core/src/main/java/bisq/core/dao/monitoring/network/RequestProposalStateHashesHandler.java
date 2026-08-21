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

import bisq.core.dao.monitoring.network.messages.GetProposalStateHashesRequest;
import bisq.core.dao.monitoring.network.messages.GetProposalStateHashesResponse;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.PeerManager;

import bisq.common.proto.network.NetworkEnvelope;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RequestProposalStateHashesHandler extends RequestStateHashesHandler<GetProposalStateHashesRequest, GetProposalStateHashesResponse> {
    RequestProposalStateHashesHandler(NetworkNode networkNode,
                                      PeerManager peerManager,
                                      NodeAddress nodeAddress,
                                      Listener<GetProposalStateHashesResponse> listener) {
        super(networkNode, peerManager, nodeAddress, listener);
    }

    @Override
    protected GetProposalStateHashesRequest getGetStateHashesRequest(int fromHeight) {
        return new GetProposalStateHashesRequest(fromHeight, nonce);
    }

    @Override
    protected GetProposalStateHashesResponse castToGetStateHashesResponse(NetworkEnvelope networkEnvelope) {
        return (GetProposalStateHashesResponse) networkEnvelope;
    }

    @Override
    protected boolean isGetStateHashesResponse(NetworkEnvelope networkEnvelope) {
        return networkEnvelope instanceof GetProposalStateHashesResponse;
    }
}
