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

import bisq.core.network.p2p.inventory.model.InventoryItem;

import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;

import com.google.common.base.Enums;
import com.google.common.base.Optional;

import java.util.HashMap;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode(callSuper = false)
@ToString
public class GetInventoryResponse extends NetworkEnvelope {
    private final Map<InventoryItem, String> inventory;

    public GetInventoryResponse(Map<InventoryItem, String> inventory) {
        this(inventory, Version.getP2PMessageVersion());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GetInventoryResponse(Map<InventoryItem, String> inventory, int messageVersion) {
        super(messageVersion);

        this.inventory = inventory;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        // For protobuf we use a map with a string key
        Map<String, String> map = new HashMap<>();
        inventory.forEach((key, value) -> map.put(key.getKey(), value));
        return getNetworkEnvelopeBuilder()
                .setGetInventoryResponse(protobuf.GetInventoryResponse.newBuilder()
                        .putAllInventory(map))
                .build();
    }

    public static GetInventoryResponse fromProto(protobuf.GetInventoryResponse proto, int messageVersion) {
        // For protobuf we use a map with a string key
        Map<String, String> map = proto.getInventoryMap();
        Map<InventoryItem, String> inventory = new HashMap<>();
        map.forEach((key, value) -> {
            Optional<InventoryItem> optional = Enums.getIfPresent(InventoryItem.class, key);
            if (optional.isPresent()) {
                inventory.put(optional.get(), value);
            }
        });
        return new GetInventoryResponse(inventory, messageVersion);
    }
}
