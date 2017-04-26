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

import io.bisq.common.persistence.HashMapPersistable;
import io.bisq.common.persistence.Persistable;
import io.bisq.generated.protobuffer.PB;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Delegate;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class SequenceNumberMap implements Persistable {
    @Delegate
    @Getter
    @Setter
    private HashMap<P2PDataStorage.ByteArray, P2PDataStorage.MapValue> hashMap = new HashMap<>();

    public SequenceNumberMap() {
    }

    public SequenceNumberMap(SequenceNumberMap sequenceNumberMap) {
        this.hashMap = sequenceNumberMap.getHashMap();
    }

    public SequenceNumberMap(HashMap<P2PDataStorage.ByteArray, P2PDataStorage.MapValue> hashMap) {
        this.hashMap = hashMap;
    }

    @Override
    public PB.DiskEnvelope toProto() {
        return PB.DiskEnvelope.newBuilder().setSequenceNumberMap(
                PB.SequenceNumberMap.newBuilder().addAllSequenceNumberEntries(
                        hashMap.entrySet().stream()
                                .map(entry ->
                                        PB.SequenceNumberEntry.newBuilder().setBytes(entry.getKey().toProto())
                                                .setMapValue(entry.getValue().toProto()).build())
                                .collect(Collectors.toList()))).build();
    }

    public static SequenceNumberMap fromProto(PB.SequenceNumberMap sequenceNumberMap) {
        List<PB.SequenceNumberEntry> sequenceNumberEntryList = sequenceNumberMap.getSequenceNumberEntriesList();
        HashMap<P2PDataStorage.ByteArray, P2PDataStorage.MapValue> result = new HashMap<>();
        for (final PB.SequenceNumberEntry entry : sequenceNumberEntryList) {
            result.put(P2PDataStorage.ByteArray.fromProto(entry.getBytes()), P2PDataStorage.MapValue.fromProto(entry.getMapValue()));
        }
        return new SequenceNumberMap(result);
    }
}
