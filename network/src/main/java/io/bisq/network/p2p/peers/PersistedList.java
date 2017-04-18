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

package io.bisq.network.p2p.peers;

import com.google.protobuf.Message;
import io.bisq.common.Marshaller;
import io.bisq.common.persistence.Persistable;
import io.bisq.common.proto.ProtoHelper;
import io.bisq.generated.protobuffer.PB;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
public class PersistedList<T extends Marshaller> implements Persistable {
    @Getter
    @Setter
    private List<T> list;

    /** convenience ctor */
    public PersistedList(HashSet<T> set) {
        this.list = set.stream().collect(Collectors.toList());
    }

    @Override
    public Message toProto() {
        return PB.DiskEnvelope.newBuilder().setPersistedPeers(
                PB.PersistedPeers.newBuilder().addAllPeers(ProtoHelper.collectionToProto(list))).build();
    }
}
