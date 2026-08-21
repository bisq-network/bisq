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

package bisq.core.dao.monitoring.network.messages;

import bisq.core.dao.monitoring.model.BlindVoteStateHash;

import bisq.network.p2p.InitialDataRequest;

import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)
@Getter
public final class GetBlindVoteStateHashesResponse extends GetStateHashesResponse<BlindVoteStateHash> {
    public GetBlindVoteStateHashesResponse(List<BlindVoteStateHash> stateHashes, int requestNonce) {
        super(stateHashes, requestNonce, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GetBlindVoteStateHashesResponse(List<BlindVoteStateHash> stateHashes,
                                            int requestNonce,
                                            int messageVersion) {
        super(stateHashes, requestNonce, messageVersion);
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setGetBlindVoteStateHashesResponse(protobuf.GetBlindVoteStateHashesResponse.newBuilder()
                        .addAllStateHashes(stateHashes.stream()
                                .map(BlindVoteStateHash::toProtoMessage)
                                .collect(Collectors.toList()))
                        .setRequestNonce(requestNonce))
                .build();
    }

    public static NetworkEnvelope fromProto(protobuf.GetBlindVoteStateHashesResponse proto, int messageVersion) {
        return new GetBlindVoteStateHashesResponse(proto.getStateHashesList().isEmpty() ?
                new ArrayList<>() :
                proto.getStateHashesList().stream()
                        .map(BlindVoteStateHash::fromProto)
                        .collect(Collectors.toList()),
                proto.getRequestNonce(),
                messageVersion);
    }

    @Override
    public Class<? extends InitialDataRequest> associatedRequest() {
        return GetBlindVoteStateHashesRequest.class;
    }
}
