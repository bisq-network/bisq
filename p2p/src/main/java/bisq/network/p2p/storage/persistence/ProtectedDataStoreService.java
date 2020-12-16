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

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * Used for data which can be added and removed. ProtectedStorageEntry is used for verifying ownership.
 */
@Slf4j
public class ProtectedDataStoreService {
    private final List<MapStoreService<? extends PersistableEnvelope, ProtectedStorageEntry>> services = new ArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ProtectedDataStoreService() {
    }

    public void addService(MapStoreService<? extends PersistableEnvelope, ProtectedStorageEntry> service) {
        services.add(service);
    }

    public void readFromResources(String postFix, Runnable completeHandler) {
        if (services.isEmpty()) {
            completeHandler.run();
            return;
        }
        AtomicInteger remaining = new AtomicInteger(services.size());
        services.forEach(service -> {
            service.readFromResources(postFix, () -> {
                if (remaining.decrementAndGet() == 0) {
                    completeHandler.run();
                }
            });
        });
    }

    // Uses synchronous execution on the userThread. Only used by tests. The async methods should be used by app code.
    @VisibleForTesting
    public void readFromResourcesSync(String postFix) {
        services.forEach(service -> service.readFromResourcesSync(postFix));
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
                    service.put(hash, entry);
                });
    }

    public ProtectedStorageEntry remove(P2PDataStorage.ByteArray hash, ProtectedStorageEntry protectedStorageEntry) {
        AtomicReference<ProtectedStorageEntry> result = new AtomicReference<>();
        services.stream()
                .filter(service -> service.canHandle(protectedStorageEntry))
                .forEach(service -> result.set(service.remove(hash)));
        return result.get();
    }
}
