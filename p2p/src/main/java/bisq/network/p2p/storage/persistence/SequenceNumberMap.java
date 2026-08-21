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

import bisq.common.proto.persistable.PersistableEnvelope;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;

/**
 * This class was not generalized to HashMapPersistable (like we did with #ListPersistable) because
 * in protobuffer the map construct can't be anything, so the straightforward mapping was not possible.
 * Hence this Persistable class.
 */
public class SequenceNumberMap implements PersistableEnvelope {
    @Getter
    @Setter
    private Map<P2PDataStorage.ByteArray, P2PDataStorage.MapValue> map = new ConcurrentHashMap<>();

    public SequenceNumberMap() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private SequenceNumberMap(Map<P2PDataStorage.ByteArray, P2PDataStorage.MapValue> map) {
        this.map.putAll(map);
    }

    @Override
    public protobuf.PersistableEnvelope toProtoMessage() {
        return protobuf.PersistableEnvelope.newBuilder()
                .setSequenceNumberMap(protobuf.SequenceNumberMap.newBuilder()
                        .addAllSequenceNumberEntries(map.entrySet().stream()
                                .map(entry -> protobuf.SequenceNumberEntry.newBuilder()
                                        .setBytes(entry.getKey().toProtoMessage())
                                        .setMapValue(entry.getValue().toProtoMessage())
                                        .build())
                                .collect(Collectors.toList())))
                .build();
    }

    public static SequenceNumberMap fromProto(protobuf.SequenceNumberMap proto) {
        HashMap<P2PDataStorage.ByteArray, P2PDataStorage.MapValue> map = new HashMap<>();
        proto.getSequenceNumberEntriesList()
                .forEach(e -> map.put(P2PDataStorage.ByteArray.fromProto(e.getBytes()), P2PDataStorage.MapValue.fromProto(e.getMapValue())));
        return new SequenceNumberMap(map);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Delegates
    public int size() {
        return map.size();
    }

    public boolean containsKey(P2PDataStorage.ByteArray key) {
        return map.containsKey(key);
    }

    public P2PDataStorage.MapValue get(P2PDataStorage.ByteArray key) {
        return map.get(key);
    }

    public void put(P2PDataStorage.ByteArray key, P2PDataStorage.MapValue value) {
        map.put(key, value);
    }
}
