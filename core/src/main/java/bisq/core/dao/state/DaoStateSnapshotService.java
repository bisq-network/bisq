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
public class DaoStateSnapshotService implements DaoStateListener {
    private static final int SNAPSHOT_GRID = 20;

    private final DaoStateService daoStateService;
    private final GenesisTxInfo genesisTxInfo;
    private final CycleService cycleService;
    private final DaoStateStorageService daoStateStorageService;

    private DaoState snapshotCandidate;
    private int chainHeightOfLastApplySnapshot;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DaoStateSnapshotService(DaoStateService daoStateService,
                                   GenesisTxInfo genesisTxInfo,
                                   CycleService cycleService,
                                   DaoStateStorageService daoStateStorageService) {
        this.daoStateService = daoStateService;
        this.genesisTxInfo = genesisTxInfo;
        this.cycleService = cycleService;
        this.daoStateStorageService = daoStateStorageService;

        this.daoStateService.addDaoStateListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We listen to each ParseTxsComplete event even if the batch processing of all blocks at startup is not completed
    // as we need to write snapshots during that batch processing.
    @Override
    public void onParseBlockComplete(Block block) {
        int chainHeight = block.getHeight();

        // Either we don't have a snapshot candidate yet, or if we have one the height at that snapshot candidate must be
        // different to our current height.
        boolean noSnapshotCandidateOrDifferentHeight = snapshotCandidate == null || snapshotCandidate.getChainHeight() != chainHeight;
        if (isSnapshotHeight(chainHeight) &&
                !daoStateService.getBlocks().isEmpty() &&
                isValidHeight(daoStateService.getBlocks().getLast().getHeight()) &&
                noSnapshotCandidateOrDifferentHeight) {
            // At trigger event we store the latest snapshotCandidate to disc
            if (snapshotCandidate != null) {
                // We clone because storage is in a threaded context and we set the snapshotCandidate to our current
                // state in the next step
                DaoState cloned = daoStateService.getClone(snapshotCandidate);
                daoStateStorageService.persist(cloned);
                log.info("Saved snapshotCandidate with height {} to Disc at height {} ",
                        snapshotCandidate.getChainHeight(), chainHeight);
            }

            // Now we clone and keep it in memory for the next trigger event
            snapshotCandidate = daoStateService.getClone();
            log.info("Cloned new snapshotCandidate at height " + chainHeight);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applySnapshot(boolean fromReorg) {
        DaoState persisted = daoStateStorageService.getPersistedBsqState();
        if (persisted != null) {
            LinkedList<Block> blocks = persisted.getBlocks();
            int chainHeightOfPersisted = persisted.getChainHeight();
            if (!blocks.isEmpty()) {
                int heightOfLastBlock = blocks.getLast().getHeight();
                log.info("applySnapshot from persisted daoState with height of last block {}", heightOfLastBlock);
                if (isValidHeight(heightOfLastBlock)) {
                    if (chainHeightOfLastApplySnapshot != chainHeightOfPersisted) {
                        chainHeightOfLastApplySnapshot = chainHeightOfPersisted;
                        daoStateService.applySnapshot(persisted);
                    } else {
                        // The reorg might have been caused by the previous parsing which might contains a range of
                        // blocks.
                        log.warn("We applied already a snapshot with chainHeight {}. We will reset the daoState and " +
                                "start over from the genesis transaction again.", chainHeightOfLastApplySnapshot);
                        persisted = new DaoState();
                        applyEmptySnapshot(persisted);
                    }
                }
            } else if (fromReorg) {
                log.info("We got a reorg and we want to apply the snapshot but it is empty. That is expected in the first blocks until the " +
                        "first snapshot has been created. We use our applySnapshot method and restart from the genesis tx");
                applyEmptySnapshot(persisted);
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

    private void applyEmptySnapshot(DaoState persisted) {
        int genesisBlockHeight = genesisTxInfo.getGenesisBlockHeight();
        persisted.setChainHeight(genesisBlockHeight);
        chainHeightOfLastApplySnapshot = genesisBlockHeight;
        daoStateService.applySnapshot(persisted);
        // In case we apply an empty snapshot we need to trigger the cycleService.addFirstCycle method
        cycleService.addFirstCycle();
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
