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
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;

import bisq.common.proto.persistable.PersistableEnvelope;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProtectedDataStoreService {
    private List<StoreService<? extends PersistableEnvelope, ProtectedStorageEntry>> services = new ArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ProtectedDataStoreService() {
    }

    public void addService(StoreService<? extends PersistableEnvelope, ProtectedStorageEntry> service) {
        services.add(service);
    }

    public void readFromResources(String postFix) {
        services.forEach(service -> service.readFromResources(postFix));
    }

    public Map<P2PDataStorage.ByteArray, ProtectedStorageEntry> getMap() {
        return services.stream()
                .flatMap(service -> service.getMap().entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public void put(P2PDataStorage.ByteArray hash, ProtectedStorageEntry entry) {
        services.stream()
                .filter(service -> service.canHandle(entry))
                .forEach(service -> {
                    service.putIfAbsent(hash, entry);
                });
    }

    public ProtectedStorageEntry putIfAbsent(P2PDataStorage.ByteArray hash, ProtectedStorageEntry entry) {
        Map<P2PDataStorage.ByteArray, ProtectedStorageEntry> map = getMap();
        if (!map.containsKey(hash)) {
            put(hash, entry);
            return null;
        } else {
            return map.get(hash);
        }
    }

    public boolean containsKey(P2PDataStorage.ByteArray hash) {
        return getMap().containsKey(hash);
    }

    public ProtectedStorageEntry remove(P2PDataStorage.ByteArray hash, ProtectedStorageEntry protectedStorageEntry) {
        final ProtectedStorageEntry[] result = new ProtectedStorageEntry[1];
        services.stream()
                .filter(service -> service.canHandle(protectedStorageEntry))
                .forEach(service -> {
                    result[0] = service.remove(hash);
                });
        return result[0];
    }
}
