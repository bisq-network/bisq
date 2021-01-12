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
import bisq.common.util.Utilities;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RemovedPayloadsMap implements PersistableEnvelope {
    @Getter
    private final Map<P2PDataStorage.ByteArray, Long> dateByHashes;

    public RemovedPayloadsMap() {
        this.dateByHashes = new HashMap<>();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private RemovedPayloadsMap(Map<P2PDataStorage.ByteArray, Long> dateByHashes) {
        this.dateByHashes = dateByHashes;
    }

    // Protobuf map only supports strings or integers as key, but no bytes or complex object so we convert the
    // bytes to a hex string, otherwise we would need to make a extra value object to wrap it.
    @Override
    public protobuf.PersistableEnvelope toProtoMessage() {
        protobuf.RemovedPayloadsMap.Builder builder = protobuf.RemovedPayloadsMap.newBuilder()
                .putAllDateByHashes(dateByHashes.entrySet().stream()
                        .collect(Collectors.toMap(e -> Utilities.encodeToHex(e.getKey().bytes),
                                Map.Entry::getValue)));
        return protobuf.PersistableEnvelope.newBuilder()
                .setRemovedPayloadsMap(builder)
                .build();
    }

    public static RemovedPayloadsMap fromProto(protobuf.RemovedPayloadsMap proto) {
        Map<P2PDataStorage.ByteArray, Long> dateByHashes = proto.getDateByHashesMap().entrySet().stream()
                .collect(Collectors.toMap(e -> new P2PDataStorage.ByteArray(Utilities.decodeFromHex(e.getKey())),
                        Map.Entry::getValue));
        return new RemovedPayloadsMap(dateByHashes);
    }

    @Override
    public String toString() {
        return "RemovedPayloadsMap{" +
                "\n     dateByHashes=" + dateByHashes +
                "\n}";
    }
}
