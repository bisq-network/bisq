/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.network.p2p.storage;

import io.bisq.common.proto.persistable.PersistableEnvelope;
import io.bisq.generated.protobuffer.PB;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * This class was not generalized to HashMapPersistable (like we did with #ListPersistable) because
 * in protobuffer the map construct can't be anything, so the straightforward mapping was not possible.
 * Hence this Persistable class.
 */
public class SequenceNumberMap implements PersistableEnvelope {
    @Getter
    @Setter
    private HashMap<P2PDataStorage.ByteArray, P2PDataStorage.MapValue> hashMap = new HashMap<>();

    public SequenceNumberMap() {
    }

    public static SequenceNumberMap clone(SequenceNumberMap map) {
        return new SequenceNumberMap(map.getHashMap());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private SequenceNumberMap(HashMap<P2PDataStorage.ByteArray, P2PDataStorage.MapValue> hashMap) {
        this.hashMap = hashMap;
    }

    @Override
    public PB.PersistableEnvelope toProtoMessage() {
        return PB.PersistableEnvelope.newBuilder()
                .setSequenceNumberMap(PB.SequenceNumberMap.newBuilder()
                        .addAllSequenceNumberEntries(hashMap.entrySet().stream()
                                .map(entry -> PB.SequenceNumberEntry.newBuilder()
                                        .setBytes(entry.getKey().toProtoMessage())
                                        .setMapValue(entry.getValue().toProtoMessage())
                                        .build())
                                .collect(Collectors.toList())))
                .build();
    }

    public static SequenceNumberMap fromProto(PB.SequenceNumberMap proto) {
        HashMap<P2PDataStorage.ByteArray, P2PDataStorage.MapValue> map = new HashMap<>();
        proto.getSequenceNumberEntriesList().stream().forEach(entry -> {
            map.put(P2PDataStorage.ByteArray.fromProto(entry.getBytes()), P2PDataStorage.MapValue.fromProto(entry.getMapValue()));
        });
        return new SequenceNumberMap(map);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Delegates
    public int size() {
        return hashMap.size();
    }

    public boolean containsKey(P2PDataStorage.ByteArray key) {
        return hashMap.containsKey(key);
    }

    public P2PDataStorage.MapValue get(P2PDataStorage.ByteArray key) {
        return hashMap.get(key);
    }

    public void put(P2PDataStorage.ByteArray key, P2PDataStorage.MapValue value) {
        hashMap.put(key, value);
    }
}
