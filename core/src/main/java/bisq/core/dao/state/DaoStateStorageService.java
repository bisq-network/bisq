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

package bisq.core.dao.state;

import bisq.core.dao.state.model.DaoState;

import bisq.network.p2p.storage.persistence.ResourceDataStoreService;
import bisq.network.p2p.storage.persistence.StoreService;

import bisq.common.UserThread;
import bisq.common.storage.Storage;

import javax.inject.Inject;
import javax.inject.Named;

import java.io.File;

import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages persistence of the daoState.
 */
@Slf4j
public class DaoStateStorageService extends StoreService<DaoStateStore> {
    private static final String FILE_NAME = "DaoStateStore";

    private DaoState daoState;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DaoStateStorageService(ResourceDataStoreService resourceDataStoreService,
                                  DaoState daoState,
                                  @Named(Storage.STORAGE_DIR) File storageDir,
                                  Storage<DaoStateStore> daoSnapshotStorage) {
        super(storageDir, daoSnapshotStorage);
        this.daoState = daoState;

        resourceDataStoreService.addService(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getFileName() {
        return FILE_NAME;
    }

    public void persist(DaoState daoState) {
        persist(daoState, 200);
    }

    public void persist(DaoState daoState, long delayInMilli) {
        store.setDaoState(daoState);
        storage.queueUpForSave(store, delayInMilli);
    }

    public DaoState getPersistedBsqState() {
        return store.getDaoState();
    }

    public void resetDaoState(Runnable resultHandler) {
        persist(new DaoState(), 1);
        UserThread.runAfter(resultHandler, 300, TimeUnit.MILLISECONDS);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected DaoStateStore createStore() {
        return new DaoStateStore(DaoState.getClone(daoState));
    }
}
