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

package bisq.core.dao.state.monitoring.messages;

import bisq.core.dao.state.monitoring.DaoStateHash;

import bisq.network.p2p.DirectMessage;
import bisq.network.p2p.ExtendedDataSizePermission;

import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;

import io.bisq.generated.protobuffer.PB;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)
@Getter
public final class GetDaoStateHashResponse extends NetworkEnvelope implements DirectMessage, ExtendedDataSizePermission {
    private final List<DaoStateHash> daoStateHashes;
    private final int requestNonce;

    public GetDaoStateHashResponse(List<DaoStateHash> daoStateHashes, int requestNonce) {
        this(daoStateHashes, requestNonce, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GetDaoStateHashResponse(List<DaoStateHash> daoStateHashes,
                                    int requestNonce,
                                    int messageVersion) {
        super(messageVersion);
        this.daoStateHashes = daoStateHashes;
        this.requestNonce = requestNonce;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setGetDaoStateHashResponse(PB.GetDaoStateHashResponse.newBuilder()
                        .addAllDaoStateHashes(daoStateHashes.stream()
                                .map(DaoStateHash::toProtoMessage)
                                .collect(Collectors.toList()))
                        .setRequestNonce(requestNonce))
                .build();
    }

    public static NetworkEnvelope fromProto(PB.GetDaoStateHashResponse proto, int messageVersion) {
        return new GetDaoStateHashResponse(proto.getDaoStateHashesList().isEmpty() ?
                new ArrayList<>() :
                proto.getDaoStateHashesList().stream()
                        .map(DaoStateHash::fromProto)
                        .collect(Collectors.toList()),
                proto.getRequestNonce(),
                messageVersion);
    }

    @Override
    public String toString() {
        return "GetDaoStateHashResponse{" +
                "\n     daoStateHashes=" + daoStateHashes +
                ",\n     requestNonce=" + requestNonce +
                "\n} " + super.toString();
    }
}
