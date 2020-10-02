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

package bisq.network.p2p.peers.peerexchange;

import bisq.common.proto.persistable.PersistableEnvelope;

import com.google.protobuf.Message;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode
public class PeerList implements PersistableEnvelope {
    @Getter
    private final List<Peer> list = new ArrayList<>();

    public PeerList() {
    }

    public PeerList(List<Peer> list) {
        setAll(list);
    }

    public int size() {
        return list.size();
    }

    @Override
    public Message toProtoMessage() {
        return protobuf.PersistableEnvelope.newBuilder()
                .setPeerList(protobuf.PeerList.newBuilder()
                        .addAllPeer(list.stream().map(Peer::toProtoMessage).collect(Collectors.toList())))
                .build();
    }

    public static PeerList fromProto(protobuf.PeerList proto) {
        return new PeerList(new ArrayList<>(proto.getPeerList().stream()
                .map(Peer::fromProto)
                .collect(Collectors.toList())));
    }

    public void setAll(Collection<Peer> collection) {
        this.list.clear();
        this.list.addAll(collection);
    }
}
