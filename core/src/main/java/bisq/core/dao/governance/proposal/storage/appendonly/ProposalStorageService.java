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

package bisq.core.dao.governance.proposal.storage.appendonly;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.persistence.MapStoreService;

import bisq.common.config.Config;
import bisq.common.persistence.PersistenceManager;

import javax.inject.Inject;
import javax.inject.Named;

import java.io.File;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProposalStorageService extends MapStoreService<ProposalStore, PersistableNetworkPayload> {
    private static final String FILE_NAME = "ProposalStore";


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ProposalStorageService(@Named(Config.STORAGE_DIR) File storageDir,
                                  PersistenceManager<ProposalStore> persistenceManager) {
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
    public Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> getMap() {
        return store.getMap();
    }

    @Override
    public boolean canHandle(PersistableNetworkPayload payload) {
        return payload instanceof ProposalPayload;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected ProposalStore createStore() {
        return new ProposalStore();
    }
}
