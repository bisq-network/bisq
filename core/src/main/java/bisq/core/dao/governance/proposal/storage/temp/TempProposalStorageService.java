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

package bisq.core.dao.governance.proposal.storage.temp;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.persistence.MapStoreService;

import bisq.common.config.Config;
import bisq.common.persistence.PersistenceManager;

import javax.inject.Inject;
import javax.inject.Named;

import java.io.File;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TempProposalStorageService extends MapStoreService<TempProposalStore, ProtectedStorageEntry> {
    private static final String FILE_NAME = "TempProposalStore";


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TempProposalStorageService(@Named(Config.STORAGE_DIR) File storageDir,
                                      PersistenceManager<TempProposalStore> persistenceManager) {
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
    public Map<P2PDataStorage.ByteArray, ProtectedStorageEntry> getMap() {
        return store.getMap();
    }

    @Override
    public boolean canHandle(ProtectedStorageEntry entry) {
        return entry.getProtectedStoragePayload() instanceof TempProposalPayload;
    }

    @Override
    protected void readFromResources(String postFix, Runnable completeHandler) {
        // We do not have a resource file for that store, so we just call the readStore method instead.
        readStore(persisted -> completeHandler.run());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected TempProposalStore createStore() {
        return new TempProposalStore();
    }
}
