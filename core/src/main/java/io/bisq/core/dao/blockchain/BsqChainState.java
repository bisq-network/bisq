/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.dao.blockchain;

import com.google.common.annotations.VisibleForTesting;
import io.bisq.common.persistence.Persistable;
import io.bisq.common.storage.Storage;
import io.bisq.common.util.Utilities;
import io.bisq.core.dao.blockchain.exceptions.BlockNotConnectingException;
import io.bisq.core.dao.blockchain.vo.*;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

// Represents mutable state of BSQ chain data
// We get accessed the data from non-UserThread context, so we need to handle threading here.
@Slf4j
@ToString
public class BsqChainState implements Persistable {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Statics
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static BsqChainState getClone(BsqChainState bsqChainState) {
        return Utilities.<BsqChainState>deserialize(Utilities.serialize(bsqChainState));
    }

    // Modulo of blocks for making snapshots of UTXO.
    // We stay also the value behind for safety against reorgs.
    // E.g. for SNAPSHOT_TRIGGER = 30:
    // If we are block 119 and last snapshot was 60 then we get a new trigger for a snapshot at block 120 and
    // new snapshot is block 90. We only persist at the new snapshot, so we always re-parse from latest snapshot after
    // a restart.
    // As we only store snapshots when Txos are added it might be that there are bigger gaps than SNAPSHOT_TRIGGER.
    private static final int SNAPSHOT_GRID = 10;  // set high to deactivate


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Persisted data
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Getter
    private final LinkedList<BsqBlock> blocks = new LinkedList<>();
    private final Map<String, Tx> txMap = new HashMap<>();
    private final Set<TxOutput> verifiedTxOutputSet = new HashSet<>();
    private final Map<TxIdIndexTuple, SpentInfo> spentInfoByTxOutputMap = new HashMap<>();
    private final Map<String, Long> burnedFeeByTxIdMap = new HashMap<>();

    private final AtomicReference<String> genesisTxId = new AtomicReference<>();
    private final AtomicReference<Integer> chainHeadHeight = new AtomicReference<>(0);

    private final AtomicReference<Integer> genesisBlockHeight = new AtomicReference<>(-1);
    private final AtomicReference<Tx> genesisTx = new AtomicReference<>();

    // transient 
    transient private BsqChainState snapshotCandidate;
    transient private Storage<BsqChainState> snapshotBsqChainStateStorage;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public BsqChainState() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Write access
    ///////////////////////////////////////////////////////////////////////////////////////////


    synchronized void init(Storage<BsqChainState> snapshotBsqChainStateStorage, String genesisTxId, int genesisBlockHeight) {
        this.snapshotBsqChainStateStorage = snapshotBsqChainStateStorage;
        this.genesisTxId.set(genesisTxId);
        this.genesisBlockHeight.set(genesisBlockHeight);
    }

    void applySnapshot(@Nullable BsqChainState snapshot) {
        synchronized (BsqChainState.this) {
            blocks.clear();
            txMap.clear();
            verifiedTxOutputSet.clear();
            spentInfoByTxOutputMap.clear();
            burnedFeeByTxIdMap.clear();

            chainHeadHeight.set(0);
            genesisTx.set(null);

            if (snapshot != null) {
                blocks.addAll(snapshot.blocks);
                txMap.putAll(snapshot.txMap);
                verifiedTxOutputSet.addAll(snapshot.verifiedTxOutputSet);
                spentInfoByTxOutputMap.putAll(snapshot.spentInfoByTxOutputMap);
                burnedFeeByTxIdMap.putAll(snapshot.burnedFeeByTxIdMap);

                chainHeadHeight.set(snapshot.chainHeadHeight.get());
                genesisTx.set(snapshot.genesisTx.get());

                genesisTxId.set(snapshot.genesisTxId.get());
                genesisBlockHeight.set(snapshot.genesisBlockHeight.get());
            }


            // printDetails();
        }
    }

    synchronized void addBlock(BsqBlock block) throws BlockNotConnectingException {
        if (!blocks.contains(block)) {
            // TODO
            // final int i = new Random().nextInt(1000);
            if (blocks.isEmpty() || (/*i != 1 &&*/ blocks.getLast().getHash().equals(block.getPreviousBlockHash()) &&
                    block.getHeight() == blocks.getLast().getHeight() + 1)) {
                blocks.add(block);
                block.getTxs().stream().forEach(this::addTx);
                chainHeadHeight.set(block.getHeight());
                //printDetails();
                maybeMakeSnapshot();
            } else if (!blocks.isEmpty()) {
                log.warn("addBlock called with a not connecting block:\n" +
                                "height()={}, hash()={}, head.height()={}, head.hash()={}",
                        block.getHeight(), block.getHash(), blocks.getLast().getHeight(), blocks.getLast().getHash());
                throw new BlockNotConnectingException(block);
            }
        } else {
            log.trace("We got that block already");
        }
    }

    void addTx(Tx tx) {
        txMap.put(tx.getId(), tx);
    }

    void addSpentTxWithSpentInfo(TxOutput spentTxOutput, SpentInfo spentInfo) {
        spentInfoByTxOutputMap.put(spentTxOutput.getTxIdIndexTuple(), spentInfo);
    }

    void setGenesisTx(Tx tx) {
        genesisTx.set(tx);
    }

    void addVerifiedTxOutput(TxOutput txOutput) {
        verifiedTxOutputSet.add(txOutput);
    }

    void addBurnedFee(String txId, long burnedFee) {
        burnedFeeByTxIdMap.put(txId, burnedFee);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Read access
    ///////////////////////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isTxOutputSpendable(String txId, int index) {
        return getSpendableTxOutput(txId, index).isPresent();
    }

    public boolean hasTxBurnedFee(String txId) {
        return burnedFeeByTxIdMap.containsKey(txId) && burnedFeeByTxIdMap.get(txId) > 0;
    }

    public boolean containsTx(String txId) {
        return getTx(txId).isPresent();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    // TODO use a map for keeping spendable in cache
    Optional<TxOutput> getSpendableTxOutput(String txId, int index) {
        final Optional<TxOutput> txOutputOptional = findTxOutput(txId, index);
        if (txOutputOptional.isPresent() &&
                isVerifiedTxOutput(txOutputOptional.get()) &&
                !spentInfoByTxOutputMap.containsKey(new TxIdIndexTuple(txId, index))) {
            return txOutputOptional;
        } else {
            return Optional.<TxOutput>empty();
        }
    }

    int getChainHeadHeight() {
        return chainHeadHeight.get();
    }

    boolean containsBlock(BsqBlock bsqBlock) {
        return blocks.contains(bsqBlock);
    }

    List<BsqBlock> getBlocksFrom(int fromBlockHeight) {
        return blocks.stream()
                .filter(block -> block.getHeight() >= fromBlockHeight)
                .sorted((o1, o2) -> new Integer(o1.getHeight()).compareTo(o2.getHeight()))
                .collect(Collectors.toList());
    }

    int getGenesisBlockHeight() {
        return genesisBlockHeight.get();
    }

    String getGenesisTxId() {
        return genesisTxId.get();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void maybeMakeSnapshot() {
        if (isSnapshotHeight(chainHeadHeight.get()) &&
                (snapshotCandidate == null ||
                        snapshotCandidate.getChainHeadHeight() != chainHeadHeight.get())) {
            // At trigger event we store the latest snapshotCandidate to disc
            if (snapshotCandidate != null) {
                // We clone because storage is in a threaded context
                final BsqChainState cloned = BsqChainState.getClone(snapshotCandidate);
                snapshotBsqChainStateStorage.queueUpForSave(cloned);
                log.info("Saved snapshotCandidate to Disc at height " + cloned.getChainHeadHeight());
            }

            // Now we clone and keep it in memory for the next trigger
            snapshotCandidate = BsqChainState.getClone(this);
            log.debug("Cloned new snapshotCandidate at height " + snapshotCandidate.getChainHeadHeight());
        }
    }

    private Optional<Tx> getTx(String txId) {
        return txMap.get(txId) != null ? Optional.of(txMap.get(txId)) : Optional.<Tx>empty();
    }

    private boolean isVerifiedTxOutput(TxOutput txOutput) {
        return verifiedTxOutputSet.contains(txOutput);
    }

    private Optional<TxOutput> findTxOutput(String txId, int index) {
        return getTx(txId).flatMap(e -> e.getTxOutput(index));
    }

    private void printDetails() {
        log.info("\nchainHeadHeight={}\nblocks.size={}\ntxMap.size={}\nverifiedTxOutputSet.size={}\n" +
                        "spentInfoByTxOutputMap.size={}\nburnedFeeByTxIdMap.size={}\nblocks data size in kb={}\n",
                getChainHeadHeight(),
                blocks.size(),
                txMap.size(),
                verifiedTxOutputSet.size(),
                spentInfoByTxOutputMap.size(),
                burnedFeeByTxIdMap.size(),
                Utilities.serialize(blocks.toArray()).length / 1000d);
    }

    @VisibleForTesting
    static int getSnapshotHeight(int genesisHeight, int height, int grid) {
        return Math.round(Math.max(genesisHeight + 3 * grid, height) / grid) * grid - grid;
    }

    protected int getSnapshotHeight(int height) {
        return getSnapshotHeight(getGenesisBlockHeight(), height, SNAPSHOT_GRID);
    }

    @VisibleForTesting
    static boolean isSnapshotHeight(int genesisHeight, int height, int grid) {
        return height % grid == 0 && height >= getSnapshotHeight(genesisHeight, height, grid);
    }

    private boolean isSnapshotHeight(int height) {
        return isSnapshotHeight(getGenesisBlockHeight(), height, SNAPSHOT_GRID);
    }


}

