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
import io.bisq.common.proto.network.NetworkProtoResolver;
import io.bisq.common.proto.persistable.PersistableEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// Only used for converting old TradeStatistic data to new TradeStatistic2 at the moment. But might be used for
// CompensationRequests and voteItems in future
@Slf4j
public class PersistableEntryMap implements PersistableEnvelope {
    @Getter
    private Map<P2PDataStorage.ByteArray, ProtectedStorageEntry> map = new ConcurrentHashMap<>();

    public PersistableEntryMap() {
    }

    public PersistableEntryMap(Map<P2PDataStorage.ByteArray, ProtectedStorageEntry> map) {
        this.map.putAll(map);
    }

    public Message toProtoMessage() {
        // Protobuffer maps don't support bytes as key so we use a hex string
        Map<String, PB.ProtectedStorageEntry> values = map.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().getHex(),
                        e -> (PB.ProtectedStorageEntry) e.getValue().toProtoMessage()));
        return PB.PersistableEnvelope.newBuilder()
                .setPersistedEntryMap(PB.PersistedEntryMap.newBuilder()
                        .putAllPersistedEntryMap(values))
                .build();
    }

    public static PersistableEnvelope fromProto(Map<String, PB.ProtectedStorageEntry> proto,
                                                NetworkProtoResolver networkProtoResolver) {
        // Protobuffer maps don't support bytes as key so we use a hex string

        // Takes about 4 sec for 4000 items ;-( Java serialisation was 500 ms
        log.info("PersistedEntryMap.fromProto size: " + proto.entrySet().size());
        Map<P2PDataStorage.ByteArray, ProtectedStorageEntry> map = proto.entrySet().stream()
                .collect(Collectors.<Map.Entry<String, PB.ProtectedStorageEntry>, P2PDataStorage.ByteArray, ProtectedStorageEntry>toMap(
                        e -> new P2PDataStorage.ByteArray(e.getKey()),
                        e -> ProtectedStorageEntry.fromProto(e.getValue(), networkProtoResolver)
                ));
        return new PersistableEntryMap(new HashMap<>(map));
    }

    public void put(P2PDataStorage.ByteArray key, ProtectedStorageEntry value) {
        map.put(key, value);
    }
}
