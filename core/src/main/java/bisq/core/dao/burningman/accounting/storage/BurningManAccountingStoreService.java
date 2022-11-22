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

import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.persistable.PersistedDataHost;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class BurningManAccountingStoreService implements PersistedDataHost {
    private final PersistenceManager<BurningManAccountingStore> persistenceManager;
    private final BurningManAccountingStore burningManAccountingStore = new BurningManAccountingStore(new ArrayList<>());

    @Inject
    public BurningManAccountingStoreService(PersistenceManager<BurningManAccountingStore> persistenceManager) {
        this.persistenceManager = persistenceManager;

        this.persistenceManager.initialize(burningManAccountingStore, PersistenceManager.Source.PRIVATE_LOW_PRIO);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted(Runnable completeHandler) {
        persistenceManager.readPersisted(persisted -> {
                    burningManAccountingStore.getBlocks().addAll(persisted.getBlocks());
                    completeHandler.run();
                },
                completeHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void requestPersistence() {
        persistenceManager.requestPersistence();
    }

    public List<AccountingBlock> getBlocks() {
        return Collections.unmodifiableList(burningManAccountingStore.getBlocks());
    }

    public void addBlock(AccountingBlock block) {
        burningManAccountingStore.getBlocks().add(block);
        requestPersistence();
    }

    public void purgeLastTenBlocks() {
        List<AccountingBlock> blocks = burningManAccountingStore.getBlocks();
        if (blocks.size() <= 10) {
            blocks.clear();
            requestPersistence();
            return;
        }

        List<AccountingBlock> purged = new ArrayList<>(blocks.subList(0, blocks.size() - 10));
        blocks.clear();
        blocks.addAll(purged);
        requestPersistence();
    }
}
