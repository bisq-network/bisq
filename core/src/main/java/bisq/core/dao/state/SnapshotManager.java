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

import bisq.common.proto.persistable.PersistenceProtoResolver;
import bisq.common.storage.Storage;

import javax.inject.Inject;
import javax.inject.Named;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Manages snapshots of BsqState.
 * // FIXME not working correctly anymore
 */
@Slf4j
public class SnapshotManager implements BsqStateListener {
    private static final int SNAPSHOT_GRID = 11000;

    private final BsqState bsqState;
    private final BsqStateService bsqStateService;
    private final GenesisTxInfo genesisTxInfo;
    private final Storage<BsqState> storage;

    private BsqState snapshotCandidate;

    @Inject
    public SnapshotManager(BsqState bsqState,
                           BsqStateService bsqStateService,
                           PersistenceProtoResolver persistenceProtoResolver,
                           GenesisTxInfo genesisTxInfo,
                           @Named(Storage.STORAGE_DIR) File storageDir) {
        this.bsqState = bsqState;
        this.bsqStateService = bsqStateService;
        this.genesisTxInfo = genesisTxInfo;
        storage = new Storage<>(storageDir, persistenceProtoResolver);

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
        final int chainHeadHeight = block.getHeight();
        if (isSnapshotHeight(chainHeadHeight) &&
                (snapshotCandidate == null ||
                        snapshotCandidate.getChainHeight() != chainHeadHeight)) {
            // At trigger event we store the latest snapshotCandidate to disc
            if (snapshotCandidate != null) {
                // We clone because storage is in a threaded context
                final BsqState cloned = bsqState.getClone(snapshotCandidate);
                if (cloned.getBlocks().getLast().getHeight() >= genesisTxInfo.getGenesisBlockHeight())
                    storage.queueUpForSave(cloned);
                log.info("Saved snapshotCandidate to Disc at height " + chainHeadHeight);
            }
            // Now we clone and keep it in memory for the next trigger
            snapshotCandidate = bsqState.getClone();
            // don't access cloned anymore with methods as locks are transient!
            log.debug("Cloned new snapshotCandidate at height " + chainHeadHeight);
        }
    }

    @Override
    public void onParseBlockChainComplete() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applySnapshot() {
        checkNotNull(storage, "storage must not be null");
        BsqState persisted = storage.initAndGetPersisted(bsqState, 100);
        if (persisted != null) {
            log.info("applySnapshot persisted.chainHeadHeight=" + bsqStateService.getBlocksFromState(persisted).getLast().getHeight());
            if (persisted.getBlocks().getLast().getHeight() >= genesisTxInfo.getGenesisBlockHeight())
                bsqStateService.applySnapshot(persisted);
        } else {
            log.info("Try to apply snapshot but no stored snapshot available");
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
        return isSnapshotHeight(bsqStateService.getGenesisBlockHeight(), height, SNAPSHOT_GRID);
    }
}
