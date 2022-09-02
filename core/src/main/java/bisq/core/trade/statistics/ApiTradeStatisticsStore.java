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


/**
 * We store only the payload in the PB file to save disc space. The hash of the payload
 * can be created anyway and is only used as key in the map. So we have a hybrid data
 * structure which is represented as list in the protobuffer definition and provide a
 * hashMap for the domain access.
 */
public class ApiTradeStatisticsStore extends PersistableNetworkPayloadStore<ApiTradeStatistics> {

    public ApiTradeStatisticsStore() {
    }

    private ApiTradeStatisticsStore(List<ApiTradeStatistics> list) {
        list.forEach(item -> map.put(new P2PDataStorage.ByteArray(item.getHash()), item));
    }

    @Override
    public Message toPersistableMessage() {
        return super.toPersistableMessage();
    }

    @Override
    public String getDefaultStorageFileName() {
        return super.getDefaultStorageFileName();
    }

    public boolean containsKey(P2PDataStorage.ByteArray hash) {
        return map.containsKey(hash);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Message toProtoMessage() {
        return protobuf.PersistableEnvelope.newBuilder()
                .setApiTradeStatisticsStore(getBuilder())
                .build();
    }

    private protobuf.ApiTradeStatisticsStore.Builder getBuilder() {
        List<protobuf.ApiTradeStatistics> protoList = map.values().stream()
                .map(payload -> (ApiTradeStatistics) payload)
                .map(ApiTradeStatistics::toProtoApiTradeStatistics)
                .collect(Collectors.toList());
        return protobuf.ApiTradeStatisticsStore.newBuilder().addAllItems(protoList);
    }


    public static ApiTradeStatisticsStore fromProto(protobuf.ApiTradeStatisticsStore proto) {
        List<ApiTradeStatistics> list = proto.getItemsList().stream()
                .map(ApiTradeStatistics::fromProto)
                .collect(Collectors.toList());
        return new ApiTradeStatisticsStore(list);
    }
}
