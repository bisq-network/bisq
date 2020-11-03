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
public class TradeStatistics2Store extends PersistableNetworkPayloadStore<TradeStatistics2> {

    TradeStatistics2Store() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private TradeStatistics2Store(List<TradeStatistics2> list) {
        super(list);
    }

    public Message toProtoMessage() {
        return protobuf.PersistableEnvelope.newBuilder()
                .setTradeStatistics2Store(getBuilder())
                .build();
    }

    private protobuf.TradeStatistics2Store.Builder getBuilder() {
        final List<protobuf.TradeStatistics2> protoList = map.values().stream()
                .map(payload -> (TradeStatistics2) payload)
                .map(TradeStatistics2::toProtoTradeStatistics2)
                .collect(Collectors.toList());
        return protobuf.TradeStatistics2Store.newBuilder().addAllItems(protoList);
    }

    public static TradeStatistics2Store fromProto(protobuf.TradeStatistics2Store proto) {
        List<TradeStatistics2> list = proto.getItemsList().stream()
                .map(TradeStatistics2::fromProto).collect(Collectors.toList());
        return new TradeStatistics2Store(list);
    }
}
