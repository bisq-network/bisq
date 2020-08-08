/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.trade.statistics;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.PrunablePersistableNetworkPayload;
import bisq.network.p2p.storage.persistence.MapStoreService;
import bisq.network.p2p.storage.persistence.PrunableStoreService;

import bisq.common.config.Config;
import bisq.common.storage.Storage;

import javax.inject.Inject;
import javax.inject.Named;

import java.io.File;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TradeStatistics2StorageService extends MapStoreService<TradeStatistics2Store, PersistableNetworkPayload>
        implements PrunableStoreService {
    private static final String FILE_NAME = "TradeStatistics2Store";


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TradeStatistics2StorageService(@Named(Config.STORAGE_DIR) File storageDir,
                                          Storage<TradeStatistics2Store> persistableNetworkPayloadMapStorage) {
        super(storageDir, persistableNetworkPayloadMapStorage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getFileName() {
        return FILE_NAME;
    }

    @Override
    public Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> getMap() {
        return store.getMap();
    }

    @Override
    public boolean canHandle(PersistableNetworkPayload payload) {
        return payload instanceof TradeStatistics2;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected TradeStatistics2Store createStore() {
        return new TradeStatistics2Store();
    }

    // At startup we check our persisted data if it contains too old entries and remove those.
    // This method is called from a non user thread.
    @Override
    public synchronized void prune() {
        AtomicBoolean hasExcludedElements = new AtomicBoolean(false);
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> map = getMap();
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> newMap = map.entrySet().stream()
                .filter(e -> {
                    if (((PrunablePersistableNetworkPayload) e.getValue()).doExclude()) {
                        hasExcludedElements.set(true);
                        return false;
                    } else {
                        return true;
                    }
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (hasExcludedElements.get()) {
            map.clear();
            map.putAll(newMap);
            persist();
        }
    }
}
