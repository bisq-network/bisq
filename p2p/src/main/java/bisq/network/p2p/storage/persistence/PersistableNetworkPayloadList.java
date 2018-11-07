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

package bisq.network.p2p.storage.persistence;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import bisq.common.proto.persistable.PersistableEnvelope;
import bisq.common.proto.persistable.PersistenceProtoResolver;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.Message;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

// That class wraps a map but is represented in PB as a list to reduce data size (no key).
// PB also does not support a byte array as key and would require some quirks to support such a map (using hex string
// would render our 20 byte keys to 40 bytes as HEX encoded).
// The class name should be map not list but we want to stick with the PB definition name and that cannot be changed
// without breaking backward compatibility.
// TODO at next hard fork we can rename the PB definition and class name.
@Deprecated
@Slf4j
public class PersistableNetworkPayloadList implements PersistableEnvelope {
    @Getter
    private Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> map = new ConcurrentHashMap<>();

    public PersistableNetworkPayloadList() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PersistableNetworkPayloadList(Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> map) {
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
        proto.getItemsList()
                .forEach(e -> {
                    PersistableNetworkPayload payload = PersistableNetworkPayload.fromProto(e, resolver);
                    map.put(new P2PDataStorage.ByteArray(payload.getHash()), payload);
                });
        return new PersistableNetworkPayloadList(map);
    }

    public boolean containsKey(P2PDataStorage.ByteArray hash) {
        return map.containsKey(hash);
    }
}
