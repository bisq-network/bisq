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

@Slf4j
public class AppendOnlyDataStoreService {
    private List<StoreService<? extends PersistableEnvelope, PersistableNetworkPayload>> services = new ArrayList<>();

    // We do not add PersistableNetworkPayloadListService to the services list as it it deprecated and used only to
    // transfer old persisted data to the new data structure.
    private PersistableNetworkPayloadListService persistableNetworkPayloadListService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public AppendOnlyDataStoreService(PersistableNetworkPayloadListService persistableNetworkPayloadListService) {
        this.persistableNetworkPayloadListService = persistableNetworkPayloadListService;
    }

    public void addService(StoreService<? extends PersistableEnvelope, PersistableNetworkPayload> service) {
        services.add(service);
    }

    public void readFromResources(String postFix) {
        services.forEach(service -> service.readFromResources(postFix));

        // transferDeprecatedDataStructure();
    }

    // Only needed for one time converting the old data store to the new ones. Can be removed after next release when we
    // are sure that no issues occurred.
    private void transferDeprecatedDataStructure() {
        // We read the file if it exists in the db folder
        persistableNetworkPayloadListService.readStore();
        // Transfer the content to the new services
        persistableNetworkPayloadListService.getMap().forEach(this::put);
        // We are done with the transfer, now let's remove the file
        persistableNetworkPayloadListService.removeFile();
    }

    public Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> getMap() {
        return services.stream()
                .flatMap(service -> service.getMap().entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public void put(P2PDataStorage.ByteArray hashAsByteArray, PersistableNetworkPayload payload) {
        services.stream()
                .filter(service -> service.canHandle(payload))
                .forEach(service -> {
                    service.putIfAbsent(hashAsByteArray, payload);
                });
    }
}
