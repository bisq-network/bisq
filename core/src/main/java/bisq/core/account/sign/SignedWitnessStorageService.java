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

package bisq.core.account.sign;

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
public class SignedWitnessStorageService extends MapStoreService<SignedWitnessStore, PersistableNetworkPayload> {
    private static final String FILE_NAME = "SignedWitnessStore";


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public SignedWitnessStorageService(@Named(Config.STORAGE_DIR) File storageDir,
                                       PersistenceManager<SignedWitnessStore> persistenceManager) {
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
        return store.getMap();
    }

    @Override
    public boolean canHandle(PersistableNetworkPayload payload) {
        return payload instanceof SignedWitness;
    }

    // A witness which the store refuses now, with an empty field or an unknown method, could be stored before. It would
    // stay in the store and go to peers in the initial data, which nodes without that check store and fail on, so it
    // is removed when the store is read.
    @Override
    protected void readFromResources(String postFix, Runnable completeHandler) {
        super.readFromResources(postFix, () -> {
            removeMalformedWitnesses();
            completeHandler.run();
        });
    }

    @Override
    protected void readFromResourcesSync(String postFix) {
        super.readFromResourcesSync(postFix);
        removeMalformedWitnesses();
    }

    private void removeMalformedWitnesses() {
        int sizeBefore = getMap().size();
        getMap().values().removeIf(payload -> !payload.verifyHashSize());
        int removed = sizeBefore - getMap().size();
        if (removed > 0) {
            log.warn("We removed {} malformed signed witnesses from the store", removed);
            requestPersistence();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected SignedWitnessStore createStore() {
        return new SignedWitnessStore();
    }
}
