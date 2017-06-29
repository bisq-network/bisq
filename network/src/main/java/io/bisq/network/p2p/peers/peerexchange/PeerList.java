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

package io.bisq.network.p2p.peers.peerexchange;

import com.google.protobuf.Message;
import io.bisq.common.proto.persistable.PersistableEnvelope;
import io.bisq.common.proto.persistable.PersistableList;
import io.bisq.generated.protobuffer.PB;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PeerList extends PersistableList<Peer> {

    public PeerList(List<Peer> list) {
        super(list);
    }

    @Override
    public Message toProtoMessage() {
        return PB.PersistableEnvelope.newBuilder()
                .setPeerList(PB.PeerList.newBuilder()
                        .addAllPeer(getList().stream().map(Peer::toProtoMessage).collect(Collectors.toList())))
                .build();
    }

    public static PersistableEnvelope fromProto(PB.PeerList proto) {
        return new PeerList(new ArrayList<>(proto.getPeerList().stream()
                .map(Peer::fromProto)
                .collect(Collectors.toList())));
    }
}
