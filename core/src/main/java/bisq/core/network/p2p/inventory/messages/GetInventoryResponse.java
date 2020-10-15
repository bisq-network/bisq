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

package bisq.core.network.p2p.inventory.messages;

import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;

import java.util.Map;

import lombok.Value;

@Value
public class GetInventoryResponse extends NetworkEnvelope {
    private final Map<String, String> inventory;

    public GetInventoryResponse(Map<String, String> inventory) {
        this(inventory, Version.getP2PMessageVersion());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GetInventoryResponse(Map<String, String> inventory, int messageVersion) {
        super(messageVersion);

        this.inventory = inventory;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setGetInventoryResponse(protobuf.GetInventoryResponse.newBuilder()
                        .putAllInventory(inventory))
                .build();
    }

    public static GetInventoryResponse fromProto(protobuf.GetInventoryResponse proto, int messageVersion) {
        return new GetInventoryResponse(proto.getInventoryMap(), messageVersion);
    }
}
