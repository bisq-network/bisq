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

package io.bisq.core.dao.blockchain.parse;

import com.google.common.annotations.VisibleForTesting;
import io.bisq.common.app.Version;
import io.bisq.common.persistence.Persistable;
import io.bisq.common.storage.Storage;
import io.bisq.common.util.Tuple2;
import io.bisq.common.util.Utilities;
import io.bisq.core.dao.blockchain.exceptions.BlockNotConnectingException;
import io.bisq.core.dao.blockchain.vo.*;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

// Represents mutable state of BSQ chain data
// We get accessed the data from non-UserThread context, so we need to handle threading here.
@Slf4j
public class BsqChainState implements Persistable {
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    @VisibleForTesting
    static int getSnapshotHeight(int genesisHeight, int height, int grid) {
        return Math.round(Math.max(genesisHeight + 3 * grid, height) / grid) * grid - grid;
    }

    @VisibleForTesting
    static boolean isSnapshotHeight(int genesisHeight, int height, int grid) {
        return height % grid == 0 && height >= getSnapshotHeight(genesisHeight, height, grid);
    }


    // Modulo of blocks for making snapshots of UTXO.
    // We stay also the value behind for safety against reorgs.
    // E.g. for SNAPSHOT_TRIGGER = 30:
    // If we are block 119 and last snapshot was 60 then we get a new trigger for a snapshot at block 120 and
    // new snapshot is block 90. We only persist at the new snapshot, so we always re-parse from latest snapshot after
    // a restart.
    // As we only store snapshots when Txos are added it might be that there are bigger gaps than SNAPSHOT_TRIGGER.
    private static final int SNAPSHOT_GRID = 100;  // set high to deactivate
    private static final int ISSUANCE_MATURITY = 144 * 30; // 30 days


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Persisted data
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final LinkedList<BsqBlock> blocks = new LinkedList<>();
    private final Map<String, Tx> txMap = new HashMap<>();
    private final Set<TxOutput> unspentTxOutputSet = new HashSet<>();

    // only used for json
    private final Map<TxIdIndexTuple, SpentInfo> spentInfoByTxOutputMap = new HashMap<>();

    private final Map<String, Long> burnedFeeByTxIdMap = new HashMap<>();
    private final List<Tuple2<Long, Integer>> compensationRequestFees = new ArrayList<>();
    private final List<Tuple2<Long, Integer>> votingFees = new ArrayList<>();
    private final List<TxOutput> compensationRequestOpReturnTxOutputs = new ArrayList<>();
    private final List<String> compensationRequestBtcAddresses = new ArrayList<>();
    private final List<TxOutput> votingTxOutputs = new ArrayList<>();
    private final Map<String, Set<TxOutput>> issuanceBtcTxOutputsByBtcAddressMap = new HashMap<>();

    private int chainHeadHeight = 0;
    private Tx genesisTx;
    private String genesisTxId = "";
    private int genesisBlockHeight = -1;

    // transient 
    transient private BsqChainState snapshotCandidate;
    transient private Storage<BsqChainState> snapshotBsqChainStateStorage;
    transient private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    transient private final ReentrantReadWriteLock.WriteLock writeLock;
    transient private final ReentrantReadWriteLock.ReadLock readLock;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public BsqChainState() {
        readLock = lock.readLock();
        writeLock = lock.writeLock();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public write access
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void init(Storage<BsqChainState> snapshotBsqChainStateStorage, String genesisTxId, int genesisBlockHeight) {
        try {
            writeLock.lock();

            this.snapshotBsqChainStateStorage = snapshotBsqChainStateStorage;
            this.genesisTxId = genesisTxId;
            this.genesisBlockHeight = genesisBlockHeight;
        } finally {
            writeLock.unlock();
        }
    }

    public void applySnapshot(@Nullable BsqChainState snapshot) {
        try {
            writeLock.lock();

            blocks.clear();
            txMap.clear();
            unspentTxOutputSet.clear();
            spentInfoByTxOutputMap.clear();
            burnedFeeByTxIdMap.clear();

            chainHeadHeight = 0;
            genesisTx = null;

            if (snapshot != null) {
                blocks.addAll(snapshot.blocks);
                txMap.putAll(snapshot.txMap);
                unspentTxOutputSet.addAll(snapshot.unspentTxOutputSet);
                spentInfoByTxOutputMap.putAll(snapshot.spentInfoByTxOutputMap);
                burnedFeeByTxIdMap.putAll(snapshot.burnedFeeByTxIdMap);
                chainHeadHeight = snapshot.chainHeadHeight;
                genesisTx = snapshot.genesisTx;
            }

            // printDetails();
        } finally {
            writeLock.unlock();
        }
    }

    public void setCreateCompensationRequestFee(long fee, int blockHeight) {
        try {
            writeLock.lock();
            compensationRequestFees.add(new Tuple2<>(fee, blockHeight));
        } finally {
            writeLock.unlock();
        }
    }

    public void setVotingFee(long fee, int blockHeight) {
        try {
            writeLock.lock();
            votingFees.add(new Tuple2<>(fee, blockHeight));
        } finally {
            writeLock.unlock();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package scope write access
    ///////////////////////////////////////////////////////////////////////////////////////////

    void addBlock(BsqBlock block) throws BlockNotConnectingException {
        try {
            writeLock.lock();

            if (!blocks.contains(block)) {
                if (blocks.isEmpty() || (blocks.getLast().getHash().equals(block.getPreviousBlockHash()) &&
                        block.getHeight() == blocks.getLast().getHeight() + 1)) {
                    blocks.add(block);
                    block.getTxs().stream().forEach(this::addTx);
                    chainHeadHeight = block.getHeight();
                    maybeMakeSnapshot();
                    //printDetails();
                } else if (!blocks.isEmpty()) {
                    log.warn("addBlock called with a not connecting block:\n" +
                                    "height()={}, hash()={}, head.height()={}, head.hash()={}",
                            block.getHeight(), block.getHash(), blocks.getLast().getHeight(), blocks.getLast().getHash());
                    throw new BlockNotConnectingException(block);
                }
            } else {
                log.trace("We got that block already");
            }
        } finally {
            writeLock.unlock();
        }
    }

    void addTx(Tx tx) {
        try {
            writeLock.lock();
            txMap.put(tx.getId(), tx);
        } finally {
            writeLock.unlock();
        }
    }

    void addSpentTxWithSpentInfo(TxOutput spentTxOutput, SpentInfo spentInfo) {
        try {
            writeLock.lock();
            spentInfoByTxOutputMap.put(spentTxOutput.getTxIdIndexTuple(), spentInfo);
        } finally {
            writeLock.unlock();
        }
    }

    void setGenesisTx(Tx tx) {
        try {
            writeLock.lock();
            genesisTx = tx;
        } finally {
            writeLock.unlock();
        }
    }

    void addUnspentTxOutput(TxOutput txOutput) {
        try {
            writeLock.lock();
            unspentTxOutputSet.add(txOutput);
        } finally {
            writeLock.unlock();
        }
    }

    void removeUnspentTxOutput(TxOutput txOutput) {
        try {
            writeLock.lock();
            unspentTxOutputSet.remove(txOutput);
        } finally {
            writeLock.unlock();
        }
    }

    void addBurnedFee(String txId, long burnedFee) {
        try {
            writeLock.lock();
            burnedFeeByTxIdMap.put(txId, burnedFee);
        } finally {
            writeLock.unlock();
        }
    }

    void addCompensationRequestOpReturnOutput(TxOutput opReturnTxOutput) {
        try {
            writeLock.lock();
            compensationRequestOpReturnTxOutputs.add(opReturnTxOutput);
        } finally {
            writeLock.unlock();
        }
    }

    void adCompensationRequestBtcTxOutputs(String btcAddress) {
        try {
            writeLock.lock();
            compensationRequestBtcAddresses.add(btcAddress);
        } finally {
            writeLock.unlock();
        }
    }


    void addVotingOpReturnOutput(TxOutput opReturnTxOutput) {
        try {
            writeLock.lock();
            votingTxOutputs.add(opReturnTxOutput);
        } finally {
            writeLock.unlock();
        }
    }

    void addIssuanceBtcTxOutput(TxOutput btcTxOutput) {
        try {
            writeLock.lock();

            if (!issuanceBtcTxOutputsByBtcAddressMap.containsKey(btcTxOutput.getAddress()))
                issuanceBtcTxOutputsByBtcAddressMap.put(btcTxOutput.getAddress(), new HashSet<>());

            issuanceBtcTxOutputsByBtcAddressMap.get(btcTxOutput.getAddress()).add(btcTxOutput);
        } finally {
            writeLock.unlock();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public read access
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BsqChainState getClone() {
        final byte[] serialize;
        try {
            readLock.lock();
            serialize = Utilities.serialize(this);
        } finally {
            readLock.unlock();
        }
        return Utilities.<BsqChainState>deserialize(serialize);
    }

    public boolean containsBlock(BsqBlock bsqBlock) {
        try {
            readLock.lock();
            return blocks.contains(bsqBlock);
        } finally {
            readLock.unlock();
        }
    }

    public boolean isTxOutputSpendable(String txId, int index) {
        try {
            readLock.lock();
            return getSpendableTxOutput(txId, index).isPresent();
        } finally {
            readLock.unlock();
        }
    }

    public List<BsqBlock> getBlocksFrom(int fromBlockHeight) {
        try {
            readLock.lock();
            return blocks.stream()
                    .filter(block -> block.getHeight() >= fromBlockHeight)
                    .sorted((o1, o2) -> new Integer(o1.getHeight()).compareTo(o2.getHeight()))
                    .collect(Collectors.toList());
        } finally {
            readLock.unlock();
        }
    }

    public boolean hasTxBurnedFee(String txId) {
        try {
            readLock.lock();
            return burnedFeeByTxIdMap.containsKey(txId) && burnedFeeByTxIdMap.get(txId) > 0;
        } finally {
            readLock.unlock();
        }
    }

    public boolean containsTx(String txId) {
        try {
            readLock.lock();
            return getTx(txId).isPresent();
        } finally {
            readLock.unlock();
        }
    }

    public int getChainHeadHeight() {
        try {
            readLock.lock();
            return chainHeadHeight;
        } finally {
            readLock.unlock();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package scope read access
    ///////////////////////////////////////////////////////////////////////////////////////////

    Optional<TxOutput> getSpendableTxOutput(String txId, int index) {
        try {
            readLock.lock();
            final Optional<TxOutput> spendingTxOutputOptional = getTx(txId).flatMap(e -> e.getTxOutput(index));
            if (spendingTxOutputOptional.isPresent() &&
                    unspentTxOutputSet.contains(spendingTxOutputOptional.get()) &&
                    isTxOutputMature(spendingTxOutputOptional.get())) {
                return spendingTxOutputOptional;
            } else {
                return Optional.<TxOutput>empty();
            }
        } finally {
            readLock.unlock();
        }
    }

    long getCreateCompensationRequestFee(int blockHeight) {
        try {
            readLock.lock();
            long fee = -1;
            for (Tuple2<Long, Integer> feeAtHeight : compensationRequestFees) {
                if (feeAtHeight.second <= blockHeight)
                    fee = feeAtHeight.first;
            }
            checkArgument(fee > -1, "compensationRequestFees must be set");
            return fee;
        } finally {
            readLock.unlock();
        }
    }

    //TODO not impl yet
    boolean isCompensationRequestPeriodValid(int blockHeight) {
        try {
            readLock.lock();
            return true;
        } finally {
            readLock.unlock();
        }

    }

    long getVotingFee(int blockHeight) {
        try {
            readLock.lock();
            long fee = -1;
            for (Tuple2<Long, Integer> feeAtHeight : votingFees) {
                if (feeAtHeight.second <= blockHeight)
                    fee = feeAtHeight.first;
            }
            checkArgument(fee > -1, "compensationRequestFees must be set");
            return fee;
        } finally {
            readLock.unlock();
        }
    }

    //TODO not impl yet
    boolean isVotingPeriodValid(int blockHeight) {
        try {
            readLock.lock();
            return true;
        } finally {
            readLock.unlock();
        }
    }

    boolean containsCompensationRequestBtcAddress(String btcAddress) {
        try {
            readLock.lock();
            return compensationRequestBtcAddresses.contains(btcAddress);
        } finally {
            readLock.unlock();
        }
    }

    Set<TxOutput> containsIssuanceTxOutputsByBtcAddress(String btcAddress) {
        try {
            readLock.lock();
            return issuanceBtcTxOutputsByBtcAddressMap.get(btcAddress);
        } finally {
            readLock.unlock();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BsqChainState getClone(BsqChainState bsqChainState) {
        final byte[] serialize = Utilities.serialize(bsqChainState);
        return Utilities.<BsqChainState>deserialize(serialize);
    }


    //TODO
    // for genesis we dont need it and for issuance we need more implemented first
    private boolean isTxOutputMature(TxOutput spendingTxOutput) {
        try {
            readLock.lock();
            return true;
        } finally {
            readLock.unlock();
        }
    }

    private Optional<Tx> getTx(String txId) {
        try {
            readLock.lock();
            return txMap.get(txId) != null ? Optional.of(txMap.get(txId)) : Optional.<Tx>empty();
        } finally {
            readLock.unlock();
        }
    }

    private int getSnapshotHeight(int height) {
        return getSnapshotHeight(genesisBlockHeight, height, SNAPSHOT_GRID);
    }

    private boolean isSnapshotHeight(int height) {
        return isSnapshotHeight(genesisBlockHeight, height, SNAPSHOT_GRID);
    }

    private void maybeMakeSnapshot() {
        try {
            readLock.lock();
            // dont access snapshotCandidate.getChainHeadHeight() as locks are transient and woudl give a null pointer!
            if (isSnapshotHeight(getChainHeadHeight()) &&
                    (snapshotCandidate == null ||
                            snapshotCandidate.chainHeadHeight != getChainHeadHeight())) {
                // At trigger event we store the latest snapshotCandidate to disc
                if (snapshotCandidate != null) {
                    // We clone because storage is in a threaded context
                    final BsqChainState cloned = getClone(snapshotCandidate);
                    snapshotBsqChainStateStorage.queueUpForSave(cloned);
                    // dont access cloned anymore with methods as locks are transient!
                    log.info("Saved snapshotCandidate to Disc at height " + cloned.chainHeadHeight);
                }
                // Now we clone and keep it in memory for the next trigger
                snapshotCandidate = getClone(this);
                // dont access cloned anymore with methods as locks are transient!
                log.debug("Cloned new snapshotCandidate at height " + snapshotCandidate.chainHeadHeight);
            }
        } finally {
            readLock.unlock();
        }
    }

    private void printDetails() {
        log.info("\nchainHeadHeight={}\nblocks.size={}\ntxMap.size={}\nunspentTxOutputSet.size={}\n" +
                        "spentInfoByTxOutputMap.size={}\nburnedFeeByTxIdMap.size={}\nblocks data size in kb={}\n",
                getChainHeadHeight(),
                blocks.size(),
                txMap.size(),
                unspentTxOutputSet.size(),
                spentInfoByTxOutputMap.size(),
                burnedFeeByTxIdMap.size(),
                Utilities.serialize(blocks.toArray()).length / 1000d);
    }
}

