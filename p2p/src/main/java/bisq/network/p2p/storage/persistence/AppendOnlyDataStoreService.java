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
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import bisq.common.proto.persistable.PersistableEnvelope;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * Used for PersistableNetworkPayload data which gets appended to a map storage.
 */
@Slf4j
public class AppendOnlyDataStoreService {
    private List<MapStoreService<? extends PersistableEnvelope, PersistableNetworkPayload>> services = new ArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public AppendOnlyDataStoreService() {
    }

    public void addService(MapStoreService<? extends PersistableEnvelope, PersistableNetworkPayload> service) {
        services.add(service);
    }

    public void readFromResources(String postFix) {
        services.forEach(service -> service.readFromResources(postFix));
    }

    public Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> getMap() {
        return services.stream()
                .flatMap(service -> service.getMap().entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public void put(P2PDataStorage.ByteArray hashAsByteArray, PersistableNetworkPayload payload) {
        services.stream()
                .filter(service -> service.canHandle(payload))
                .forEach(service -> service.putIfAbsent(hashAsByteArray, payload));
    }
}
