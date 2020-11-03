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
import bisq.network.p2p.storage.persistence.MapStoreService;

import bisq.common.config.Config;
import bisq.common.persistence.PersistenceManager;

import javax.inject.Inject;
import javax.inject.Named;

import java.io.File;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TradeStatistics2StorageService extends MapStoreService<TradeStatistics2Store, PersistableNetworkPayload> {
    private static final String FILE_NAME = "TradeStatistics2Store";


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TradeStatistics2StorageService(@Named(Config.STORAGE_DIR) File storageDir,
                                          PersistenceManager<TradeStatistics2Store> persistenceManager) {
        super(storageDir, persistenceManager);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void initializePersistenceManager() {
        persistenceManager.initialize(store, PersistenceManager.Source.NETWORK);
    }

    @Override
    public String getFileName() {
        return FILE_NAME;
    }

    @Override
    public Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> getMap() {
        // As it is used for data request and response and we do not want to send any old trade stat data anymore.
        return new HashMap<>();
    }

    // We overwrite that method to receive old trade stats from the network. As we deactivated getMap to not deliver
    // hashes we needed to use the getMapOfAllData method to actually store the data.
    // That's a bit of a hack but it's just for transition and can be removed after a few months anyway.
    // Alternatively we could create a new interface to handle it differently on the other client classes but that
    // seems to be not justified as it is needed only temporarily.
    @Override
    protected PersistableNetworkPayload putIfAbsent(P2PDataStorage.ByteArray hash, PersistableNetworkPayload payload) {
        return getMapOfAllData().putIfAbsent(hash, payload);
    }

    @Override
    protected void readFromResources(String postFix, Runnable completeHandler) {
        // We do not attempt to read from resources as that file is not provided anymore
        readStore(persisted -> completeHandler.run());
    }

    public Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> getMapOfAllData() {
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
}
