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

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoSetupService;
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.monitoring.DaoStateMonitoringService;
import bisq.core.dao.monitoring.model.DaoStateHash;
import bisq.core.dao.state.model.DaoState;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.storage.DaoStateStorageService;
import bisq.core.trade.DelayedPayoutAddressProvider;
import bisq.core.user.Preferences;

import bisq.common.UserThread;
import bisq.common.config.Config;
import bisq.common.util.GcUtil;

import javax.inject.Inject;
import javax.inject.Named;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;

import java.util.LinkedList;
import java.util.List;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Manages periodical snapshots of the DaoState.
 * At startup we apply a snapshot if available.
 * At each trigger height we persist the latest snapshot candidate and set the current daoState as new candidate.
 * The trigger height is determined by the SNAPSHOT_GRID. The latest persisted snapshot is min. the height of
 * SNAPSHOT_GRID old not less than 2 times the SNAPSHOT_GRID old.
 */
@Slf4j
public class DaoStateSnapshotService implements DaoSetupService, DaoStateListener {
    private static final int SNAPSHOT_GRID = 20;

    private final DaoStateService daoStateService;
    private final GenesisTxInfo genesisTxInfo;
    private final DaoStateStorageService daoStateStorageService;
    private final DaoStateMonitoringService daoStateMonitoringService;
    private final WalletsSetup walletsSetup;
    private final BsqWalletService bsqWalletService;
    private final Preferences preferences;
    private final Config config;
    private final File storageDir;

    private protobuf.DaoState daoStateCandidate;
    private LinkedList<DaoStateHash> hashChainCandidate = new LinkedList<>();
    private List<Block> blocksCandidate;
    private int snapshotHeight;
    private int chainHeightOfLastApplySnapshot;
    @Setter
    @Nullable
    private Runnable daoRequiresRestartHandler;
    private int daoRequiresRestartHandlerAttempts = 0;
    private boolean readyForPersisting = true;
    private boolean isParseBlockChainComplete;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DaoStateSnapshotService(DaoStateService daoStateService,
                                   GenesisTxInfo genesisTxInfo,
                                   DaoStateStorageService daoStateStorageService,
                                   DaoStateMonitoringService daoStateMonitoringService,
                                   WalletsSetup walletsSetup,
                                   BsqWalletService bsqWalletService,
                                   Preferences preferences,
                                   Config config,
                                   @Named(Config.STORAGE_DIR) File storageDir) {
        this.daoStateService = daoStateService;
        this.genesisTxInfo = genesisTxInfo;
        this.daoStateStorageService = daoStateStorageService;
        this.daoStateMonitoringService = daoStateMonitoringService;
        this.walletsSetup = walletsSetup;
        this.bsqWalletService = bsqWalletService;
        this.preferences = preferences;
        this.config = config;
        this.storageDir = storageDir;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
        daoStateService.addDaoStateListener(this);
    }

    @Override
    public void start() {
    }

    public void shutDown() {
        daoStateStorageService.shutDown();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        if (config.baseCurrencyNetwork.isMainnet() &&
                walletsSetup.isDownloadComplete() &&
                daoStateService.getChainHeight() == bsqWalletService.getBestChainHeight()) {
            // In case the DAO state is invalid we might get an outdated RECIPIENT_BTC_ADDRESS. In that case we trigger
            // a dao resync from resources.
            String address = daoStateService.getParamValue(Param.RECIPIENT_BTC_ADDRESS, daoStateService.getChainHeight());
            if (DelayedPayoutAddressProvider.isOutdatedAddress(address)) {
                log.warn("The RECIPIENT_BTC_ADDRESS is not as expected. The DAO state is probably out of " +
                        "sync and a resync should fix that issue.");
                resyncDaoStateFromResources();
            }
        }
    }

    // We listen onDaoStateChanged to ensure the dao state has been processed from listener clients after parsing.
    // We need to listen during batch processing as well to write snapshots during that process.
    @Override
    public void onDaoStateChanged(Block block) {
        // If we have isUseDaoMonitor activated we apply the hash and snapshots at each new block during initial parsing.
        // Otherwise we do it only after the initial blockchain parsing is completed to not delay the parsing.
        // In that case we get the missing hashes from the seed nodes. At any new block we do the hash calculation
        // ourself and therefore get back confidence that our DAO state is in sync with the network.
        if (preferences.isUseFullModeDaoMonitor() || isParseBlockChainComplete) {
            // We need to execute first the daoStateMonitoringService.createHashFromBlock to get the hash created
            daoStateMonitoringService.createHashFromBlock(block);
            maybeCreateSnapshot(block);
        }
    }

    @Override
    public void onParseBlockChainComplete() {
        isParseBlockChainComplete = true;

        // In case we have dao monitoring deactivated we create the snapshot after we are completed with parsing
        // and we got called back from daoStateMonitoringService once the hashes are created from peers data.
        if (!preferences.isUseFullModeDaoMonitor()) {
            // We register a callback handler once the daoStateMonitoringService has received the missing hashes from
            // the seed node and applied the latest hash. After that we are ready to make a snapshot and persist it.
            daoStateMonitoringService.setCreateSnapshotHandler(() -> {
                // As we did not have created any snapshots during initial parsing we create it now. We cannot use the past
                // snapshot height as we have not cloned a candidate (that would cause quite some delay during parsing).
                // The next snapshots will be created again according to the snapshot height grid (each 20 blocks).
                // This also comes with the improvement that the user does not need to load the past blocks back to the last
                // snapshot height. Though it comes also with the small risk that in case of re-orgs the user need to do
                // a resync in case the dao state would have been affected by that reorg.
                long ts = System.currentTimeMillis();
                // We do not keep a copy of the clone as we use it immediately for persistence.
                GcUtil.maybeReleaseMemory();
                int chainHeight = daoStateService.getChainHeight();
                log.info("Create snapshot at height {}", chainHeight);
                // We do not keep the data in our fields to enable gc as soon its released in the store

                protobuf.DaoState daoStateForSnapshot = getDaoStateForSnapshot();
                List<Block> blocksForSnapshot = getBlocksForSnapshot();
                LinkedList<DaoStateHash> hashChainForSnapshot = getHashChainForSnapshot();
                daoStateStorageService.requestPersistence(daoStateForSnapshot,
                        blocksForSnapshot,
                        hashChainForSnapshot,
                        () -> {
                            GcUtil.maybeReleaseMemory();
                            log.info("Persisted daoState after parsing completed at height {}. Took {} ms",
                                    chainHeight, System.currentTimeMillis() - ts);
                        });
                GcUtil.maybeReleaseMemory();
            });
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We need to process during batch processing as well to write snapshots during that process.
    public void maybeCreateSnapshot(Block block) {
        int chainHeight = block.getHeight();

        // Either we don't have a snapshot candidate yet, or if we have one the height at that snapshot candidate must be
        // different to our current height.
        boolean noSnapshotCandidateOrDifferentHeight = daoStateCandidate == null ||
                snapshotHeight != chainHeight;
        if (isSnapshotHeight(chainHeight) &&
                !daoStateService.getBlocks().isEmpty() &&
                isValidHeight(daoStateService.getBlockHeightOfLastBlock()) &&
                noSnapshotCandidateOrDifferentHeight) {

            // We protect to get called while we are not completed with persisting the daoState. This can take about
            // 20 seconds and it is not expected that we get triggered another snapshot event in that period, but this
            // check guards that we would skip such calls..
            if (!readyForPersisting) {
                if (preferences.isUseFullModeDaoMonitor()) {
                    // In case we dont use isUseFullModeDaoMonitor we might called here too often as the parsing is much
                    // faster than the persistence and we likely create only 1 snapshot during initial parsing, so
                    // we log only if isUseFullModeDaoMonitor is true as then parsing is likely slower and we would
                    // expect that we do a snapshot at each trigger block.
                    log.info("We try to persist a daoState but the previous call has not completed yet. " +
                            "We ignore that call and skip that snapshot. " +
                            "Snapshot will be created at next snapshot height again. This is not to be expected with live " +
                            "blockchain data.");
                }
                return;
            }

            if (daoStateCandidate != null) {
                persist();
            } else {
                createSnapshot();
            }
        }
    }

    private void persist() {
        long ts = System.currentTimeMillis();
        readyForPersisting = false;
        daoStateStorageService.requestPersistence(daoStateCandidate,
                blocksCandidate,
                hashChainCandidate,
                () -> {
                    log.info("Serializing daoStateCandidate for writing to Disc at chainHeight {} took {} ms.",
                            snapshotHeight, System.currentTimeMillis() - ts);

                    createSnapshot();
                    readyForPersisting = true;
                });
    }

    private void createSnapshot() {
        long ts = System.currentTimeMillis();
        // Now we clone and keep it in memory for the next trigger event
        // We do not fit into the target grid of 20 blocks as we get called here once persistence is
        // done from the write thread (mapped back to user thread).
        // As we want to prevent to maintain 2 clones we prefer that strategy. If we would do the clone
        // after the persist call we would keep an additional copy in memory.
        daoStateCandidate = getDaoStateForSnapshot();
        blocksCandidate = getBlocksForSnapshot();
        hashChainCandidate = getHashChainForSnapshot();
        snapshotHeight = daoStateService.getChainHeight();
        GcUtil.maybeReleaseMemory();

        log.info("Cloned new daoStateCandidate at height {} took {} ms.", snapshotHeight, System.currentTimeMillis() - ts);
    }

    public void applySnapshot(boolean fromReorg) {
        DaoState persistedBsqState = daoStateStorageService.getPersistedBsqState();
        LinkedList<DaoStateHash> persistedDaoStateHashChain = daoStateStorageService.getPersistedDaoStateHashChain();
        if (persistedBsqState != null) {
            int chainHeightOfPersisted = persistedBsqState.getChainHeight();
            if (!persistedBsqState.getBlocks().isEmpty()) {
                int heightOfLastBlock = persistedBsqState.getLastBlock().getHeight();
                if (heightOfLastBlock != chainHeightOfPersisted) {
                    log.warn("chainHeightOfPersisted must be same as heightOfLastBlock");
                    resyncDaoStateFromResources();
                    return;
                }
                if (isValidHeight(heightOfLastBlock)) {
                    if (chainHeightOfLastApplySnapshot != chainHeightOfPersisted) {
                        chainHeightOfLastApplySnapshot = chainHeightOfPersisted;
                        daoStateService.applySnapshot(persistedBsqState);
                        daoStateMonitoringService.applySnapshot(persistedDaoStateHashChain);
                        daoStateStorageService.releaseMemory();
                    } else {
                        // The reorg might have been caused by the previous parsing which might contains a range of
                        // blocks.
                        log.warn("We applied already a snapshot with chainHeight {}. " +
                                        "We remove all dao store files and shutdown. After a restart resource files will " +
                                        "be applied if available.",
                                chainHeightOfLastApplySnapshot);
                        resyncDaoStateFromResources();
                    }
                }
            } else if (fromReorg) {
                log.info("We got a reorg and we want to apply the snapshot but it is empty. " +
                        "That is expected in the first blocks until the first snapshot has been created. " +
                        "We remove all dao store files and shutdown. " +
                        "After a restart resource files will be applied if available.");
                resyncDaoStateFromResources();
            } else {
                log.info("No Bsq blocks in DaoState. Expected if no data are provided yet from resources or persisted data.");
            }
        } else {
            log.info("Try to apply snapshot but no stored snapshot available. That is expected at first blocks.");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean isValidHeight(int heightOfLastBlock) {
        return heightOfLastBlock >= genesisTxInfo.getGenesisBlockHeight();
    }

    private void resyncDaoStateFromResources() {
        log.info("resyncDaoStateFromResources called");
        if (daoRequiresRestartHandler == null && ++daoRequiresRestartHandlerAttempts <= 3) {
            log.warn("daoRequiresRestartHandler has not been initialized yet, will try again in 10 seconds");
            UserThread.runAfter(this::resyncDaoStateFromResources, 10);  // a delay for the app to init
            return;
        }
        try {
            daoStateStorageService.resyncDaoStateFromResources(storageDir);
            // the restart handler informs the user of the need to restart bisq (in desktop mode)
            if (daoRequiresRestartHandler == null) {
                log.error("daoRequiresRestartHandler COULD NOT be called as it has not been initialized yet");
            } else {
                log.info("calling daoRequiresRestartHandler...");
                daoRequiresRestartHandler.run();
            }
        } catch (IOException e) {
            log.error("Error at resyncDaoStateFromResources: {}", e.toString());
        }
    }

    @VisibleForTesting
    int getSnapshotHeight(int genesisHeight, int height, int grid) {
        return Math.round(Math.max(genesisHeight + 3 * grid, height) / grid) * grid - grid;
    }

    @VisibleForTesting
    boolean isSnapshotHeight(int genesisHeight, int height, int grid) {
        return height % grid == 0 && height >= getSnapshotHeight(genesisHeight, height, grid);
    }

    private boolean isSnapshotHeight(int height) {
        return isSnapshotHeight(genesisTxInfo.getGenesisBlockHeight(), height, SNAPSHOT_GRID);
    }

    private protobuf.DaoState getDaoStateForSnapshot() {
        return daoStateService.getBsqStateCloneExcludingBlocks();
    }

    private List<Block> getBlocksForSnapshot() {
        int fromBlockHeight = daoStateStorageService.getChainHeightOfPersistedBlocks() + 1;
        return daoStateService.getBlocksFromBlockHeight(fromBlockHeight);
    }

    private LinkedList<DaoStateHash> getHashChainForSnapshot() {
        return new LinkedList<>(daoStateMonitoringService.getDaoStateHashChain());
    }
}
