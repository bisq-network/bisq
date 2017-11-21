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

package io.bisq.network.p2p.storage;

import com.google.protobuf.Message;
import io.bisq.common.proto.persistable.PersistableEnvelope;
import io.bisq.common.proto.persistable.PersistenceProtoResolver;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// That class wraps a map but is represented in PB as a list to reduce data size (no key).
// PB also does not support a byte array as key and would require some quirks to support such a map (using hex string
// would render our 20 byte keys to 40 bytes as HEX encoded).
@Slf4j
public class PersistableNetworkPayloadCollection implements PersistableEnvelope {
    @Getter
    private Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> map = new ConcurrentHashMap<>();

    public PersistableNetworkPayloadCollection() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private PersistableNetworkPayloadCollection(Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> map) {
        this.map.putAll(map);
    }

    public Message toProtoMessage() {
        // Protobuffer maps don't support bytes as key so we use a hex string
        Set<PB.PersistableNetworkPayload> values = map.values().stream()
                .map(PersistableNetworkPayload::toProtoMessage)
                .collect(Collectors.toSet());
        return PB.PersistableEnvelope.newBuilder()
                .setPersistableNetworkPayloadList(PB.PersistableNetworkPayloadList.newBuilder()
                        .addAllItems(values))
                .build();
    }

    public static PersistableEnvelope fromProto(PB.PersistableNetworkPayloadList proto,
                                                PersistenceProtoResolver resolver) {
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> map = new HashMap<>();
        proto.getItemsList().stream()
                .forEach(e -> {
                    PersistableNetworkPayload payload = PersistableNetworkPayload.fromProto(e, resolver);
                    map.put(new P2PDataStorage.ByteArray(payload.getHash()), payload);
                });
        return new PersistableNetworkPayloadCollection(map);
    }
}
