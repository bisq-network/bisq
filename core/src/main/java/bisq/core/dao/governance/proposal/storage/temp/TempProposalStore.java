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

package bisq.core.dao.governance.proposal.storage.temp;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;

import bisq.common.proto.network.NetworkProtoResolver;
import bisq.common.proto.persistable.PersistableEnvelope;

import com.google.protobuf.Message;

import javax.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;


/**
 * We store only the payload in the PB file to save disc space. The hash of the payload can be created anyway and
 * is only used as key in the map. So we have a hybrid data structure which is represented as list in the protobuffer
 * definition and provide a hashMap for the domain access.
 */
@Slf4j
public class TempProposalStore implements PersistableEnvelope {
    @Getter
    private final Map<P2PDataStorage.ByteArray, ProtectedStorageEntry> map = new ConcurrentHashMap<>();

    @Inject
    TempProposalStore() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private TempProposalStore(List<ProtectedStorageEntry> list) {
        list.forEach(entry -> map.put(P2PDataStorage.get32ByteHashAsByteArray(entry.getProtectedStoragePayload()), entry));
    }

    public Message toProtoMessage() {
        return protobuf.PersistableEnvelope.newBuilder()
                .setTempProposalStore(getBuilder())
                .build();
    }

    private protobuf.TempProposalStore.Builder getBuilder() {
        final List<protobuf.ProtectedStorageEntry> protoList = map.values().stream()
                .map(ProtectedStorageEntry::toProtectedStorageEntry)
                .collect(Collectors.toList());
        return protobuf.TempProposalStore.newBuilder().addAllItems(protoList);
    }

    public static TempProposalStore fromProto(protobuf.TempProposalStore proto, NetworkProtoResolver networkProtoResolver) {
        List<ProtectedStorageEntry> list = proto.getItemsList().stream()
                .map(entry -> getProtectedStorageEntryOrNull(entry, networkProtoResolver))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return new TempProposalStore(list);
    }

    // The entries come from the network, so one of them failing to deserialize must not cost us the whole store.
    // PersistenceManager treats a throw here as a corrupted file and moves it aside.
    @Nullable
    private static ProtectedStorageEntry getProtectedStorageEntryOrNull(protobuf.ProtectedStorageEntry proto,
                                                                        NetworkProtoResolver networkProtoResolver) {
        try {
            return ProtectedStorageEntry.fromProto(proto, networkProtoResolver);
        } catch (RuntimeException e) {
            log.warn("Ignoring invalid ProtectedStorageEntry from TempProposalStore. {}", e.toString());
            return null;
        }
    }

    public boolean containsKey(P2PDataStorage.ByteArray hash) {
        return map.containsKey(hash);
    }
}
