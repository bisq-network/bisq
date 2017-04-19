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
import io.bisq.common.proto.PersistenceProtoResolver;
import io.bisq.common.storage.Storage;
import io.bisq.common.util.FunctionalReadWriteLock;
import io.bisq.common.util.Tuple2;
import io.bisq.common.util.Utilities;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.btc.BitcoinNetwork;
import io.bisq.core.dao.DaoOptionKeys;
import io.bisq.core.dao.blockchain.exceptions.BlockNotConnectingException;
import io.bisq.core.dao.blockchain.vo.*;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

// Represents mutable state of BSQ chain data
// We get accessed the data from different threads so we need to make sure it is thread safe.
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

    private static final int SNAPSHOT_GRID = 100;  // set high to deactivate
    private static final int ISSUANCE_MATURITY = 144 * 30; // 30 days

    //mainnet
    // this tx has a lot of outputs
    // https://blockchain.info/de/tx/ee921650ab3f978881b8fe291e0c025e0da2b7dc684003d7a03d9649dfee2e15
    // BLOCK_HEIGHT 411779
    // 411812 has 693 recursions
    // MAIN NET
    private static final String GENESIS_TX_ID = "b26371e2145f52c94b3d30713a9e38305bfc665fc27cd554e794b5e369d99ef5";
    private static final int GENESIS_BLOCK_HEIGHT = 461718; // 2017-04-13
    // block 300000 2014-05-10
    // block 350000 2015-03-30
    // block 400000 2016-02-25
    // block 450000 2017-01-25

    // REG TEST
    private static final String REG_TEST_GENESIS_TX_ID = "3bc7bc9484e112ec8ddd1a1c984379819245ac463b9ce40fa8b5bf771c0f9236";
    private static final int REG_TEST_GENESIS_BLOCK_HEIGHT = 102;
    // TEST NET
    // https://testnet.blockexplorer.com/block/00000000f1cd94c6ccc458a922f2a42c975c3447180f0db1e56322a26ab3f0ec
    private static final String TEST_NET_GENESIS_TX_ID = "8853756990acfc1784aac1ee1a50d331c915a46876bb4ad98f260ef2d35da845";
    private static final int TEST_NET_GENESIS_BLOCK_HEIGHT = 327626; //Mar 16, 2015 
    // block 376078 has 2843 recursions and caused once a StackOverflowError, a second run worked. Took 1,2 sec.
    

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Persisted data
    private final LinkedList<BsqBlock> blocks = new LinkedList<>();
    private final Map<String, Tx> txMap = new HashMap<>();
    private final Set<TxOutput> unspentTxOutputSet = new HashSet<>();
    private final Map<TxIdIndexTuple, SpentInfo> spentInfoByTxOutputMap = new HashMap<>();
    private final Map<String, Long> burnedFeeByTxIdMap = new HashMap<>();
    private final Set<Tuple2<Long, Integer>> compensationRequestFees = new HashSet<>();
    private final Set<Tuple2<Long, Integer>> votingFees = new HashSet<>();
    private final Set<TxOutput> compensationRequestOpReturnTxOutputs = new HashSet<>();
    private final Set<String> compensationRequestBtcAddresses = new HashSet<>();
    private final Set<TxOutput> votingTxOutputs = new HashSet<>();
    private final Map<String, Set<TxOutput>> issuanceBtcTxOutputsByBtcAddressMap = new HashMap<>();

    private final String genesisTxId;
    private final int genesisBlockHeight;
    private int chainHeadHeight = 0;
    private Tx genesisTx;

    // transient
    transient private final boolean dumpBlockchainData;
    transient private final Storage<BsqChainState> snapshotBsqChainStateStorage;
    transient private BsqChainState snapshotCandidate;
    transient private final FunctionalReadWriteLock lock;
    transient private final ReentrantReadWriteLock.WriteLock writeLock;
    transient private final ReentrantReadWriteLock.ReadLock readLock;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public BsqChainState(BisqEnvironment bisqEnvironment,
                         PersistenceProtoResolver persistenceProtoResolver,
                         @Named(Storage.STORAGE_DIR) File storageDir,
                         @Named(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA) boolean dumpBlockchainData) {
        this.dumpBlockchainData = dumpBlockchainData;

        snapshotBsqChainStateStorage = new Storage<>(storageDir, persistenceProtoResolver);

        if (bisqEnvironment.getBitcoinNetwork() == BitcoinNetwork.MAINNET) {
            genesisTxId = GENESIS_TX_ID;
            genesisBlockHeight = GENESIS_BLOCK_HEIGHT;
        } else if (bisqEnvironment.getBitcoinNetwork() == BitcoinNetwork.REGTEST) {
            genesisTxId = REG_TEST_GENESIS_TX_ID;
            genesisBlockHeight = REG_TEST_GENESIS_BLOCK_HEIGHT;
        } else {
            genesisTxId = TEST_NET_GENESIS_TX_ID;
            genesisBlockHeight = TEST_NET_GENESIS_BLOCK_HEIGHT;
        }

        // TODO choose between two styles + consider using fair so that threads don't have to wait too long?
        lock = new FunctionalReadWriteLock(true);
        ReentrantReadWriteLock lock2 = new ReentrantReadWriteLock(true);
        readLock = lock2.readLock();
        writeLock = lock2.writeLock();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public write access
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applySnapshot() {
        lock.write(() -> {
            BsqChainState snapshot = snapshotBsqChainStateStorage.initAndGetPersistedWithFileName("BsqChainState");

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

            printDetails();
        });
    }

    public void setCreateCompensationRequestFee(long fee, int blockHeight) {
        lock.write(() -> {
            compensationRequestFees.add(new Tuple2<>(fee, blockHeight));
        });
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
                        blocks.getLast().getHeight() + 1 == block.getHeight())) {
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
        // we only use spentInfoByTxOutputMap for json export
        if (dumpBlockchainData) {
            try {
                writeLock.lock();
                spentInfoByTxOutputMap.put(spentTxOutput.getTxIdIndexTuple(), spentInfo);
            } finally {
                writeLock.unlock();
            }
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

    // TODO doesn't need a lock, it's a final String variable so should be thread safe
    public String getGenesisTxId() {
        try {
            readLock.lock();
            return genesisTxId;
        } finally {
            readLock.unlock();
        }
    }

    public int getGenesisBlockHeight() {
        try {
            readLock.lock();
            return genesisBlockHeight;
        } finally {
            readLock.unlock();
        }
    }

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

    public byte[] getSerializedBlocksFrom(int fromBlockHeight) {
        try {
            readLock.lock();
            List<BsqBlock> filtered = blocks.stream()
                    .filter(block -> block.getHeight() >= fromBlockHeight)
                    .collect(Collectors.toList());
            return Utilities.<ArrayList<BsqBlock>>serialize(new ArrayList<>(filtered));
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

    Set<TxOutput> issuanceTxOutputsByBtcAddress(String btcAddress) {
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
        return lock.read(() -> {
            readLock.lock();
            return txMap.get(txId) != null ? Optional.of(txMap.get(txId)) : Optional.<Tx>empty();
        });
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
        log.info("\nchainHeadHeight={}\n" +
                        "    blocks.size={}\n" +
                        "    txMap.size={}\n" +
                        "    unspentTxOutputSet.size={}\n" +
                        "    spentInfoByTxOutputMap.size={}\n" +
                        "    burnedFeeByTxIdMap.size={}\n" +
                        "    compensationRequestFees.size={}\n" +
                        "    votingFees.size={}\n" +
                        "    compensationRequestOpReturnTxOutputs.size={}\n" +
                        "    compensationRequestBtcAddresses.size={}\n" +
                        "    votingTxOutputs.size={}\n" +
                        "    issuanceBtcTxOutputsByBtcAddressMap.size={}\n" +
                        "    blocks data size in kb={}\n",
                getChainHeadHeight(),
                blocks.size(),
                txMap.size(),
                unspentTxOutputSet.size(),
                spentInfoByTxOutputMap.size(),
                burnedFeeByTxIdMap.size(),
                compensationRequestFees.size(),
                votingFees.size(),
                compensationRequestOpReturnTxOutputs.size(),
                compensationRequestBtcAddresses.size(),
                votingTxOutputs.size(),
                issuanceBtcTxOutputsByBtcAddressMap.size(),
                Utilities.serialize(blocks.toArray()).length / 1000d);
    }
}

