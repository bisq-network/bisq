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

package bisq.core.dao.node.messages;

import bisq.core.dao.node.full.RawBlock;

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
public final class GetBlocksResponse extends NetworkEnvelope implements DirectMessage, ExtendedDataSizePermission {
    private final List<RawBlock> blocks;
    private final int requestNonce;

    public GetBlocksResponse(List<RawBlock> blocks, int requestNonce) {
        this(blocks, requestNonce, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GetBlocksResponse(List<RawBlock> blocks, int requestNonce, int messageVersion) {
        super(messageVersion);
        this.blocks = blocks;
        this.requestNonce = requestNonce;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setGetBlocksResponse(PB.GetBlocksResponse.newBuilder()
                        .addAllRawBlocks(blocks.stream()
                                .map(RawBlock::toProtoMessage)
                                .collect(Collectors.toList()))
                        .setRequestNonce(requestNonce))
                .build();
    }

    public static NetworkEnvelope fromProto(PB.GetBlocksResponse proto, int messageVersion) {
        return new GetBlocksResponse(proto.getRawBlocksList().isEmpty() ?
                new ArrayList<>() :
                proto.getRawBlocksList().stream()
                        .map(RawBlock::fromProto)
                        .collect(Collectors.toList()),
                proto.getRequestNonce(),
                messageVersion);
    }


    @Override
    public String toString() {
        return "GetBlocksResponse{" +
                "\n     blocks=" + blocks +
                ",\n     requestNonce=" + requestNonce +
                "\n} " + super.toString();
    }
}
