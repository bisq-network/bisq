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

package bisq.core.trade.statistics;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.persistence.PersistableNetworkPayloadStore;

import com.google.protobuf.Message;

import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * We store only the payload in the PB file to save disc space. The hash of the payload can be created anyway and
 * is only used as key in the map. So we have a hybrid data structure which is represented as list in the protobuffer
 * definition and provide a hashMap for the domain access.
 */
@Slf4j
public class TradeStatistics3Store extends PersistableNetworkPayloadStore<TradeStatistics3> {

    public TradeStatistics3Store() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private TradeStatistics3Store(List<TradeStatistics3> list) {
        list.forEach(item -> map.put(new P2PDataStorage.ByteArray(item.getHash()), item));
    }

    public Message toProtoMessage() {
        return protobuf.PersistableEnvelope.newBuilder()
                .setTradeStatistics3Store(getBuilder())
                .build();
    }

    private protobuf.TradeStatistics3Store.Builder getBuilder() {
        List<protobuf.TradeStatistics3> protoList = map.values().stream()
                .map(payload -> (TradeStatistics3) payload)
                .map(TradeStatistics3::toProtoTradeStatistics3)
                .collect(Collectors.toList());
        return protobuf.TradeStatistics3Store.newBuilder().addAllItems(protoList);
    }

    public static TradeStatistics3Store fromProto(protobuf.TradeStatistics3Store proto) {
        List<TradeStatistics3> list = proto.getItemsList().stream()
                .map(TradeStatistics3::fromProto).collect(Collectors.toList());
        return new TradeStatistics3Store(list);
    }

    public boolean containsKey(P2PDataStorage.ByteArray hash) {
        return map.containsKey(hash);
    }
}
