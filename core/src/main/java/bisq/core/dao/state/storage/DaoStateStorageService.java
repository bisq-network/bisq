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

package bisq.core.dao.state.storage;

import bisq.core.dao.monitoring.model.DaoStateHash;
import bisq.core.dao.state.model.DaoState;
import bisq.core.dao.state.model.blockchain.Block;

import bisq.network.p2p.storage.persistence.ResourceDataStoreService;
import bisq.network.p2p.storage.persistence.StoreService;

import bisq.common.UserThread;
import bisq.common.config.Config;
import bisq.common.file.FileUtil;
import bisq.common.persistence.PersistenceManager;
import bisq.common.util.GcUtil;

import javax.inject.Inject;
import javax.inject.Named;

import com.google.common.util.concurrent.MoreExecutors;

import java.io.File;
import java.io.IOException;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages persistence of the daoState.
 */
@Slf4j
public class DaoStateStorageService extends StoreService<DaoStateStore> {
    private static final String FILE_NAME = "DaoStateStore";

    private final BsqBlocksStorageService bsqBlocksStorageService;
    private final File storageDir;
    private final LinkedList<Block> blocks = new LinkedList<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Optional<Future<?>> future = Optional.empty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DaoStateStorageService(ResourceDataStoreService resourceDataStoreService,
                                  BsqBlocksStorageService bsqBlocksStorageService,
                                  @Named(Config.STORAGE_DIR) File storageDir,
                                  PersistenceManager<DaoStateStore> persistenceManager) {
        super(storageDir, persistenceManager);
        this.bsqBlocksStorageService = bsqBlocksStorageService;
        this.storageDir = storageDir;

        resourceDataStoreService.addService(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getFileName() {
        return FILE_NAME;
    }

    public int getChainHeightOfPersistedBlocks() {
        return bsqBlocksStorageService.getChainHeightOfPersistedBlocks();
    }

    public void requestPersistence(protobuf.DaoState daoStateAsProto,
                                   List<Block> blocks,
                                   LinkedList<DaoStateHash> daoStateHashChain,
                                   Runnable completeHandler) {
        if (daoStateAsProto == null) {
            completeHandler.run();
            return;
        }

        if (future.isPresent() && !future.get().isDone()) {
            UserThread.runAfter(() -> requestPersistence(daoStateAsProto, blocks, daoStateHashChain, completeHandler), 2);
            return;
        }

        future = Optional.of(executorService.submit(() -> {
           try {
               Thread.currentThread().setName("Write-blocks-and-DaoState");
               bsqBlocksStorageService.persistBlocks(blocks);
               store.setDaoStateAsProto(daoStateAsProto);
               store.setDaoStateHashChain(daoStateHashChain);
               long ts = System.currentTimeMillis();
               persistenceManager.persistNow(() -> {
                   // After we have written to disk we remove the daoStateAsProto in the store to avoid that it stays in
                   // memory there until the next persist call.
                   log.info("Persist daoState took {} ms", System.currentTimeMillis() - ts);
                   store.releaseMemory();
                   GcUtil.maybeReleaseMemory();
                   UserThread.execute(completeHandler);
               });
           } catch (Exception e) {
               log.error("Exception at persisting BSQ blocks and DaoState", e);
           }
        }));
    }

    public void shutDown() {
        executorService.shutdown();
        // noinspection UnstableApiUsage
        MoreExecutors.shutdownAndAwaitTermination(executorService, 10, TimeUnit.SECONDS);
    }

    @Override
    protected void readFromResources(String postFix, Runnable completeHandler) {
        new Thread(() -> {
            Thread.currentThread().setName("copyBsqBlocksFromResources");
            bsqBlocksStorageService.copyFromResources(postFix);

            super.readFromResources(postFix, () -> {
                // We got mapped back to user thread so we need to create a new thread again as we dont want to
                // execute on user thread
                new Thread(() -> {
                    Thread.currentThread().setName("Read-BsqBlocksStore");
                    protobuf.DaoState daoStateAsProto = store.getDaoStateAsProto();
                    if (daoStateAsProto != null) {
                        LinkedList<Block> list;
                        if (daoStateAsProto.getBlocksList().isEmpty()) {
                            int chainHeight = daoStateAsProto.getChainHeight();
                            list = bsqBlocksStorageService.readBlocks(chainHeight);
                            if (!list.isEmpty()) {
                                int heightOfLastBlock = list.getLast().getHeight();
                                if (heightOfLastBlock != chainHeight) {
                                    log.warn("heightOfLastBlock {} must match chainHeight {}", heightOfLastBlock, chainHeight);
                                    // this error scenario is handled by DaoStateSnapshotService, it will resync from resources & reboot
                                }
                            }
                        } else {
                            list = bsqBlocksStorageService.migrateBlocks(daoStateAsProto.getBlocksList());
                        }
                        blocks.clear();
                        blocks.addAll(list);
                    }
                    UserThread.execute(completeHandler);
                }).start();
            });
        }).start();
    }

    public DaoState getPersistedBsqState() {
        protobuf.DaoState daoStateAsProto = store.getDaoStateAsProto();
        if (daoStateAsProto != null) {
            long ts = System.currentTimeMillis();
            DaoState daoState = DaoState.fromProto(daoStateAsProto, blocks);
            log.info("Deserializing DaoState with {} blocks took {} ms",
                    daoState.getBlocks().size(), System.currentTimeMillis() - ts);
            return daoState;
        }
        return new DaoState();
    }

    public LinkedList<DaoStateHash> getPersistedDaoStateHashChain() {
        return store.getDaoStateHashChain();
    }

    public void releaseMemory() {
        blocks.clear();
        store.releaseMemory();
        GcUtil.maybeReleaseMemory();
    }

    public void resyncDaoStateFromGenesis(Runnable resultHandler) {
        String backupDirName = "out_of_sync_dao_data";
        try {
            removeAndBackupDaoConsensusFiles(storageDir, backupDirName);
        } catch (Throwable t) {
            log.error(t.toString());
        }

        store.setDaoStateAsProto(DaoState.getBsqStateCloneExcludingBlocks(new DaoState()));
        store.setDaoStateHashChain(new LinkedList<>());
        persistenceManager.persistNow(resultHandler);
        bsqBlocksStorageService.removeBlocksInDirectory();
    }

    public void resyncDaoStateFromResources(File storageDir) throws IOException {
        // We delete all DAO consensus data and remove the daoState so it will rebuild from latest
        // resource files.
        String backupDirName = "out_of_sync_dao_data";
        removeAndBackupDaoConsensusFiles(storageDir, backupDirName);

        String newFileName = "DaoStateStore_" + System.currentTimeMillis();
        FileUtil.removeAndBackupFile(storageDir, new File(storageDir, "DaoStateStore"), newFileName, backupDirName);

        bsqBlocksStorageService.removeBlocksDirectory();
    }

    private void removeAndBackupDaoConsensusFiles(File storageDir, String backupDirName) throws IOException {
        // We delete all DAO related data. Some will be rebuild from resources.
        long currentTime = System.currentTimeMillis();
        String newFileName = "BlindVoteStore_" + currentTime;
        FileUtil.removeAndBackupFile(storageDir, new File(storageDir, "BlindVoteStore"), newFileName, backupDirName);

        newFileName = "ProposalStore_" + currentTime;
        FileUtil.removeAndBackupFile(storageDir, new File(storageDir, "ProposalStore"), newFileName, backupDirName);

        // We also need to remove ballot list as it contains the proposals as well. It will be recreated at resync
        newFileName = "BallotList_" + currentTime;
        FileUtil.removeAndBackupFile(storageDir, new File(storageDir, "BallotList"), newFileName, backupDirName);

        newFileName = "UnconfirmedBsqChangeOutputList_" + currentTime;
        FileUtil.removeAndBackupFile(storageDir, new File(storageDir, "UnconfirmedBsqChangeOutputList"), newFileName, backupDirName);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected DaoStateStore createStore() {
        return new DaoStateStore(null, new LinkedList<>());
    }

    @Override
    protected void initializePersistenceManager() {
        persistenceManager.initialize(store, PersistenceManager.Source.NETWORK);
    }
}
