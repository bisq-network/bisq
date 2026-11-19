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
import bisq.network.p2p.storage.payload.InvalidPersistableNetworkPayloadException;
import bisq.network.p2p.storage.persistence.PersistableNetworkPayloadStore;

import com.google.common.annotations.VisibleForTesting;
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

    // Bounds the writable store between releases. Each bundled historical file is loaded separately.
    static final int MAX_ITEMS = 100_000;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

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
        return fromProto(proto, MAX_ITEMS);
    }

    @VisibleForTesting
    static TradeStatistics3Store fromProto(protobuf.TradeStatistics3Store proto, int maxItems) {
        TradeStatistics3Store store = new TradeStatistics3Store();
        int numAboveSizeLimit = 0;
        for (protobuf.TradeStatistics3 record : proto.getItemsList()) {
            if (store.map.size() >= maxItems) {
                log.warn("Trade statistics store reached its limit of {} entries on load", maxItems);
                break;
            }
            // A store written by v1.10.11 or earlier can hold such records. We skip them before we hash them.
            if (record.getSerializedSize() > TradeStatistics3.MAX_SERIALIZED_BYTES) {
                numAboveSizeLimit++;
                continue;
            }
            try {
                TradeStatistics3 item = TradeStatistics3.fromProto(record);
                store.map.put(new P2PDataStorage.ByteArray(item.getHash()), item);
            } catch (InvalidPersistableNetworkPayloadException e) {
                log.warn("Ignoring invalid TradeStatistics3 from TradeStatistics3Store. {}", e.getMessage());
            }
        }
        if (numAboveSizeLimit > 0) {
            log.warn("Ignoring {} TradeStatistics3 above the size limit of {} bytes from TradeStatistics3Store",
                    numAboveSizeLimit, TradeStatistics3.MAX_SERIALIZED_BYTES);
        }
        return store;
    }

    public boolean containsKey(P2PDataStorage.ByteArray hash) {
        return map.containsKey(hash);
    }
}
