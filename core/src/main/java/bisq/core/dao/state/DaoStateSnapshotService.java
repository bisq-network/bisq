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

import bisq.core.dao.state.blockchain.Block;

import javax.inject.Inject;

import com.google.common.annotations.VisibleForTesting;

import java.util.LinkedList;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages periodical snapshots of the BsqState.
 * At startup we apply a snapshot if available.
 * At each trigger height we persist the latest snapshot candidate and set the current bsqState as new candidate.
 * The trigger height is determined by the SNAPSHOT_GRID. The latest persisted snapshot is min. the height of
 * SNAPSHOT_GRID old not less than 2 times the SNAPSHOT_GRID old.
 */
@Slf4j
public class DaoStateSnapshotService implements BsqStateListener {
    private static final int SNAPSHOT_GRID = 10;

    private final BsqStateService bsqStateService;
    private final GenesisTxInfo genesisTxInfo;
    private final DaoStateStorageService daoStateStorageService;

    private BsqState snapshotCandidate;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DaoStateSnapshotService(BsqStateService bsqStateService,
                                   GenesisTxInfo genesisTxInfo,
                                   DaoStateStorageService daoStateStorageService) {
        this.bsqStateService = bsqStateService;
        this.genesisTxInfo = genesisTxInfo;
        this.daoStateStorageService = daoStateStorageService;

        this.bsqStateService.addBsqStateListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNewBlockHeight(int blockHeight) {
    }

    @Override
    public void onParseTxsComplete(Block block) {
        int chainHeight = block.getHeight();

        // Either we don't have a snapshot candidate yet, or if we have one the height at that snapshot candidate must be
        // different to our current height.
        boolean noSnapshotCandidateOrDifferentHeight = snapshotCandidate == null || snapshotCandidate.getChainHeight() != chainHeight;
        if (isSnapshotHeight(chainHeight) &&
                isValidHeight(bsqStateService.getBlocks().getLast().getHeight()) &&
                noSnapshotCandidateOrDifferentHeight) {
            // At trigger event we store the latest snapshotCandidate to disc
            if (snapshotCandidate != null) {
                // We clone because storage is in a threaded context and we set the snapshotCandidate to our current
                // state in the next step
                BsqState cloned = bsqStateService.getClone(snapshotCandidate);
                daoStateStorageService.persist(cloned);
                log.info("Saved snapshotCandidate with height {} to Disc at height {} ",
                        snapshotCandidate.getChainHeight(), chainHeight);
            }

            // Now we clone and keep it in memory for the next trigger event
            snapshotCandidate = bsqStateService.getClone();
            log.info("Cloned new snapshotCandidate at height " + chainHeight);
        }
    }

    private boolean isValidHeight(int heightOfLastBlock) {
        return heightOfLastBlock >= genesisTxInfo.getGenesisBlockHeight();
    }

    @Override
    public void onParseBlockChainComplete() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applySnapshot() {
        BsqState persisted = daoStateStorageService.getPersistedBsqState();
        if (persisted != null) {
            LinkedList<Block> blocks = persisted.getBlocks();
            if (!blocks.isEmpty()) {
                int heightOfLastBlock = blocks.getLast().getHeight();
                log.info("applySnapshot from persisted bsqState with height of last block {}", heightOfLastBlock);
                if (isValidHeight(heightOfLastBlock))
                    bsqStateService.applySnapshot(persisted);
            }
        } else {
            log.info("Try to apply snapshot but no stored snapshot available. That is expected at first blocks.");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

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
