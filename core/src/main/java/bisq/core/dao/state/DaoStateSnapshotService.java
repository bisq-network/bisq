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

import bisq.core.dao.governance.period.CycleService;
import bisq.core.dao.monitoring.DaoStateMonitoringService;
import bisq.core.dao.monitoring.model.DaoStateHash;
import bisq.core.dao.state.model.DaoState;
import bisq.core.dao.state.model.blockchain.Block;

import javax.inject.Inject;

import com.google.common.annotations.VisibleForTesting;

import java.util.LinkedList;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages periodical snapshots of the DaoState.
 * At startup we apply a snapshot if available.
 * At each trigger height we persist the latest snapshot candidate and set the current daoState as new candidate.
 * The trigger height is determined by the SNAPSHOT_GRID. The latest persisted snapshot is min. the height of
 * SNAPSHOT_GRID old not less than 2 times the SNAPSHOT_GRID old.
 */
@Slf4j
public class DaoStateSnapshotService {
    private static final int SNAPSHOT_GRID = 20;

    private final DaoStateService daoStateService;
    private final GenesisTxInfo genesisTxInfo;
    private final CycleService cycleService;
    private final DaoStateStorageService daoStateStorageService;
    private final DaoStateMonitoringService daoStateMonitoringService;

    private DaoState daoStateSnapshotCandidate;
    private LinkedList<DaoStateHash> daoStateHashChainSnapshotCandidate = new LinkedList<>();
    private int chainHeightOfLastApplySnapshot;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DaoStateSnapshotService(DaoStateService daoStateService,
                                   GenesisTxInfo genesisTxInfo,
                                   CycleService cycleService,
                                   DaoStateStorageService daoStateStorageService,
                                   DaoStateMonitoringService daoStateMonitoringService) {
        this.daoStateService = daoStateService;
        this.genesisTxInfo = genesisTxInfo;
        this.cycleService = cycleService;
        this.daoStateStorageService = daoStateStorageService;
        this.daoStateMonitoringService = daoStateMonitoringService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We do not use DaoStateListener.onDaoStateChanged but let the DaoEventCoordinator call maybeCreateSnapshot to ensure the
    // correct order of execution.
    // We need to process during batch processing as well to write snapshots during that process.
    public void maybeCreateSnapshot(Block block) {
        int chainHeight = block.getHeight();

        // Either we don't have a snapshot candidate yet, or if we have one the height at that snapshot candidate must be
        // different to our current height.
        boolean noSnapshotCandidateOrDifferentHeight = daoStateSnapshotCandidate == null ||
                daoStateSnapshotCandidate.getChainHeight() != chainHeight;
        if (isSnapshotHeight(chainHeight) &&
                !daoStateService.getBlocks().isEmpty() &&
                isValidHeight(daoStateService.getBlocks().getLast().getHeight()) &&
                noSnapshotCandidateOrDifferentHeight) {
            // At trigger event we store the latest snapshotCandidate to disc
            if (daoStateSnapshotCandidate != null) {
                // We clone because storage is in a threaded context and we set the snapshotCandidate to our current
                // state in the next step
                DaoState clonedDaoState = daoStateService.getClone(daoStateSnapshotCandidate);
                LinkedList<DaoStateHash> clonedDaoStateHashChain = new LinkedList<>(daoStateHashChainSnapshotCandidate);
                daoStateStorageService.persist(clonedDaoState, clonedDaoStateHashChain);

                log.info("Saved snapshotCandidate with height {} to Disc at height {} ",
                        daoStateSnapshotCandidate.getChainHeight(), chainHeight);
            }

            // Now we clone and keep it in memory for the next trigger event
            daoStateSnapshotCandidate = daoStateService.getClone();
            daoStateHashChainSnapshotCandidate = new LinkedList<>(daoStateMonitoringService.getDaoStateHashChain());

            log.info("Cloned new snapshotCandidate at height " + chainHeight);
        }
    }

    public void applySnapshot(boolean fromReorg) {
        DaoState persistedBsqState = daoStateStorageService.getPersistedBsqState();
        LinkedList<DaoStateHash> persistedDaoStateHashChain = daoStateStorageService.getPersistedDaoStateHashChain();
        if (persistedBsqState != null) {
            LinkedList<Block> blocks = persistedBsqState.getBlocks();
            int chainHeightOfPersisted = persistedBsqState.getChainHeight();
            if (!blocks.isEmpty()) {
                int heightOfLastBlock = blocks.getLast().getHeight();
                log.info("applySnapshot from persistedBsqState daoState with height of last block {}", heightOfLastBlock);
                if (isValidHeight(heightOfLastBlock)) {
                    if (chainHeightOfLastApplySnapshot != chainHeightOfPersisted) {
                        chainHeightOfLastApplySnapshot = chainHeightOfPersisted;
                        daoStateService.applySnapshot(persistedBsqState);
                        daoStateMonitoringService.applySnapshot(persistedDaoStateHashChain);
                    } else {
                        // The reorg might have been caused by the previous parsing which might contains a range of
                        // blocks.
                        log.warn("We applied already a snapshot with chainHeight {}. We will reset the daoState and " +
                                "start over from the genesis transaction again.", chainHeightOfLastApplySnapshot);
                        applyEmptySnapshot();
                    }
                }
            } else if (fromReorg) {
                log.info("We got a reorg and we want to apply the snapshot but it is empty. That is expected in the first blocks until the " +
                        "first snapshot has been created. We use our applySnapshot method and restart from the genesis tx");
                applyEmptySnapshot();
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

    private void applyEmptySnapshot() {
        DaoState emptyDaoState = new DaoState();
        int genesisBlockHeight = genesisTxInfo.getGenesisBlockHeight();
        emptyDaoState.setChainHeight(genesisBlockHeight);
        chainHeightOfLastApplySnapshot = genesisBlockHeight;
        daoStateService.applySnapshot(emptyDaoState);
        // In case we apply an empty snapshot we need to trigger the cycleService.addFirstCycle method
        cycleService.addFirstCycle();

        daoStateMonitoringService.applySnapshot(new LinkedList<>());
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
}
