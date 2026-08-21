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

import javax.inject.Inject;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

/**
 * Used for PersistableNetworkPayload data which gets appended to a map storage.
 */
@Slf4j
public class AppendOnlyDataStoreService {
    @Getter
    private final List<MapStoreService<? extends PersistableNetworkPayloadStore<? extends PersistableNetworkPayload>, PersistableNetworkPayload>> services = new ArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public AppendOnlyDataStoreService() {
    }

    public void addService(MapStoreService<? extends PersistableNetworkPayloadStore<? extends PersistableNetworkPayload>, PersistableNetworkPayload> service) {
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

    public Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> getMap(PersistableNetworkPayload payload) {
        return findService(payload)
                .map(service -> service instanceof HistoricalDataStoreService ?
                        ((HistoricalDataStoreService<?>) service).getMapOfAllData() :
                        service.getMap())
                .orElse(new HashMap<>());
    }

    public boolean put(P2PDataStorage.ByteArray hashAsByteArray, PersistableNetworkPayload payload) {
        Optional<MapStoreService<? extends PersistableNetworkPayloadStore<? extends PersistableNetworkPayload>, PersistableNetworkPayload>> optionalService = findService(payload);
        optionalService.ifPresent(service -> service.putIfAbsent(hashAsByteArray, payload));
        return optionalService.isPresent();
    }

    @NotNull
    private Optional<MapStoreService<? extends PersistableNetworkPayloadStore<? extends PersistableNetworkPayload>, PersistableNetworkPayload>> findService(
            PersistableNetworkPayload payload) {
        return services.stream()
                .filter(service -> service.canHandle(payload))
                .findAny();
    }
}
