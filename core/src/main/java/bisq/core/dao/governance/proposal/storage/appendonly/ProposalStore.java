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

package bisq.core.dao.governance.proposal.storage.appendonly;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import bisq.common.proto.persistable.PersistableEnvelope;

import com.google.protobuf.Message;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;


/**
 * We store only the payload in the PB file to save disc space. The hash of the payload can be created anyway and
 * is only used as key in the map. So we have a hybrid data structure which is represented as list in the protobuffer
 * definition and provide a hashMap for the domain access.
 */
@Slf4j
public class ProposalStore implements PersistableEnvelope {
    @Getter
    private Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> map = new ConcurrentHashMap<>();

    ProposalStore() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private ProposalStore(List<ProposalPayload> list) {
        list.forEach(item -> map.put(new P2PDataStorage.ByteArray(item.getHash()), item));
    }

    public Message toProtoMessage() {
        return protobuf.PersistableEnvelope.newBuilder()
                .setProposalStore(getBuilder())
                .build();
    }

    private protobuf.ProposalStore.Builder getBuilder() {
        final List<protobuf.ProposalPayload> protoList = map.values().stream()
                .map(payload -> (ProposalPayload) payload)
                .map(ProposalPayload::toProtoProposalPayload)
                .collect(Collectors.toList());
        return protobuf.ProposalStore.newBuilder().addAllItems(protoList);
    }

    public static PersistableEnvelope fromProto(protobuf.ProposalStore proto) {
        List<ProposalPayload> list = proto.getItemsList().stream()
                .map(ProposalPayload::fromProto).collect(Collectors.toList());
        return new ProposalStore(list);
    }

    public boolean containsKey(P2PDataStorage.ByteArray hash) {
        return map.containsKey(hash);
    }
}
