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

import bisq.common.storage.Storage;

import javax.inject.Named;

import javax.inject.Inject;

import java.io.File;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TradeStatistics2StorageService extends MapStoreService<TradeStatistics2Store, PersistableNetworkPayload> {
    private static final String FILE_NAME = "TradeStatistics2Store";


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TradeStatistics2StorageService(@Named(Storage.STORAGE_DIR) File storageDir,
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

    @Override
    protected void readStore() {
        super.readStore();
    }
}
