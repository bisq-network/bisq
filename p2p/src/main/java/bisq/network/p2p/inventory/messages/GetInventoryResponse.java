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

package bisq.network.p2p.inventory.messages;

import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;

import java.util.Map;

import lombok.Value;

@Value
public class GetInventoryResponse extends NetworkEnvelope {
    private final Map<String, Integer> numPayloadsMap;

    public GetInventoryResponse(Map<String, Integer> numPayloadsMap) {
        this(numPayloadsMap, Version.getP2PMessageVersion());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GetInventoryResponse(Map<String, Integer> numPayloadsMap, int messageVersion) {
        super(messageVersion);

        this.numPayloadsMap = numPayloadsMap;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setGetInventoryResponse(protobuf.GetInventoryResponse.newBuilder()
                        .putAllNumPayloadsMap(numPayloadsMap))
                .build();
    }

    public static GetInventoryResponse fromProto(protobuf.GetInventoryResponse proto, int messageVersion) {
        return new GetInventoryResponse(proto.getNumPayloadsMapMap(), messageVersion);
    }
}
