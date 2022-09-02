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


import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.persistence.HistoricalDataStoreService;

import bisq.common.config.Config;
import bisq.common.persistence.PersistenceManager;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.File;

import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class ApiTradeStatisticsStorageService extends HistoricalDataStoreService<ApiTradeStatisticsStore> {
    private static final String FILE_NAME = "ApiTradeStatisticsStore";

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ApiTradeStatisticsStorageService(@Named(Config.STORAGE_DIR) File storageDir,
                                            PersistenceManager<ApiTradeStatisticsStore> persistenceManager) {
        super(storageDir, persistenceManager);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getFileName() {
        return FILE_NAME;
    }

    @Override
    protected void initializePersistenceManager() {
        persistenceManager.initialize(store, PersistenceManager.Source.NETWORK);
    }

    @Override
    public boolean canHandle(PersistableNetworkPayload payload) {
        return payload instanceof ApiTradeStatistics;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected ApiTradeStatisticsStore createStore() {
        return new ApiTradeStatisticsStore();
    }

    public void persistNow() {
        persistenceManager.persistNow(() -> {
        });
    }
}
