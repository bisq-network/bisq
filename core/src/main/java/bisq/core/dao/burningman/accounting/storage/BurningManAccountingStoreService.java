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

package bisq.core.dao.burningman.accounting.storage;

import bisq.core.dao.burningman.accounting.blockchain.AccountingBlock;
import bisq.core.dao.burningman.accounting.exceptions.BlockHashNotConnectingException;
import bisq.core.dao.burningman.accounting.exceptions.BlockHeightNotConnectingException;

import bisq.network.p2p.storage.persistence.ResourceDataStoreService;
import bisq.network.p2p.storage.persistence.StoreService;

import bisq.common.config.Config;
import bisq.common.persistence.PersistenceManager;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.File;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class BurningManAccountingStoreService extends StoreService<BurningManAccountingStore> {
    private static final String FILE_NAME = "BurningManAccountingStore";

    @Inject
    public BurningManAccountingStoreService(ResourceDataStoreService resourceDataStoreService,
                                            @Named(Config.STORAGE_DIR) File storageDir,
                                            PersistenceManager<BurningManAccountingStore> persistenceManager) {
        super(storageDir, persistenceManager);

        resourceDataStoreService.addService(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void requestPersistence() {
        persistenceManager.requestPersistence();
    }

    public void addIfNewBlock(AccountingBlock block) throws BlockHashNotConnectingException, BlockHeightNotConnectingException {
        store.addIfNewBlock(block);
        requestPersistence();
    }

    public void forEachBlock(Consumer<AccountingBlock> consumer) {
        store.forEachBlock(consumer);
    }

    public void purgeLastTenBlocks() {
        store.purgeLastTenBlocks();
        requestPersistence();
    }

    public Optional<AccountingBlock> getLastBlock() {
        return store.getLastBlock();
    }

    public Optional<AccountingBlock> getBlockAtHeight(int height) {
        return store.getBlockAtHeight(height);
    }

    public List<AccountingBlock> getBlocksAtLeastWithHeight(int minHeight) {
        return store.getBlocksAtLeastWithHeight(minHeight);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected BurningManAccountingStore createStore() {
        return new BurningManAccountingStore(new ArrayList<>());
    }

    @Override
    protected void initializePersistenceManager() {
        persistenceManager.initialize(store, PersistenceManager.Source.NETWORK);
    }

    @Override
    public String getFileName() {
        return FILE_NAME;
    }
}
