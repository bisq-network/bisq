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

package io.bisq.core.dao.blockchain.parse;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.Message;
import io.bisq.common.proto.persistable.PersistableEnvelope;
import io.bisq.common.proto.persistable.PersistenceProtoResolver;
import io.bisq.common.storage.Storage;
import io.bisq.common.util.FunctionalReadWriteLock;
import io.bisq.common.util.Tuple2;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.dao.blockchain.exceptions.BlockNotConnectingException;
import io.bisq.core.dao.blockchain.vo.*;
import io.bisq.generated.protobuffer.PB;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

// Represents mutable state of BSQ chain data
// We get accessed the data from different threads so we need to make sure it is thread safe.
@Slf4j
public class BsqChainState implements PersistableEnvelope {

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

    // BTC MAIN NET
    // private static final String BTC_GENESIS_TX_ID = "b26371e2145f52c94b3d30713a9e38305bfc665fc27cd554e794b5e369d99ef5";
    //private static final int BTC_GENESIS_BLOCK_HEIGHT = 461718; // 2017-04-13
    private static final String BTC_GENESIS_TX_ID = "4371a1579bccc672231178cc5fe9fbb9366774d3bcbf21545a82f637f4b61a06";
    private static final int BTC_GENESIS_BLOCK_HEIGHT = 473000; // 2017-06-26

    // LTC MAIN NET
    private static final String LTC_GENESIS_TX_ID = "44074e68c1168d67871b3e9af0e65d6d7c820b03ba15445df2c4089729985fb6";
    private static final int LTC_GENESIS_BLOCK_HEIGHT = 1220170; // 2017-06-11
    // 1186935

    //1220127
    // block 300000 2014-05-10
    // block 350000 2015-03-30
    // block 400000 2016-02-25
    // block 450000 2017-01-25

    // REG TEST
    private static final String BTC_REG_TEST_GENESIS_TX_ID = "da216721fb915da499fe0400d08362f44b672096f37c74501c2f9bcaa7760656";
    private static final int BTC_REG_TEST_GENESIS_BLOCK_HEIGHT = 363;

    // LTC REG TEST
    private static final String LTC_REG_TEST_GENESIS_TX_ID = "3551aa22fbf2e237df3d96d94f286aecc4f3109a7dcd873c5c51e30a6398172c";
    private static final int LTC_REG_TEST_GENESIS_BLOCK_HEIGHT = 105;


    // TEST NET
    // 0.5 BTC to grazcoin ms4ewGfJEv5RTnBD2moDoP5Kp1uJJwDGSX
    // 0.3 BTC to alice: myjn5JVuQLN9S4QwGzY4VrD86819Zc2uhj
    // 0.2BTC to bob: mx3xo655TAjC5r7ScuVEU8b6FMLomnKSeX
    private static final String BTC_TEST_NET_GENESIS_TX_ID = "e360c3c77f43d53cbbf3dc8064c888a10310930a6427770ce4c8ead388edf17c";
    private static final int BTC_TEST_NET_GENESIS_BLOCK_HEIGHT = 1119668;

    private static final String LTC_TEST_NET_GENESIS_TX_ID = "not set";
    private static final int LTC_TEST_NET_GENESIS_BLOCK_HEIGHT = 1;


    // block 376078 has 2843 recursions and caused once a StackOverflowError, a second run worked. Took 1,2 sec.


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Persisted data
    private final LinkedList<BsqBlock> bsqBlocks;
    private final Map<String, Tx> txMap;
    private final Map<TxIdIndexTuple, TxOutput> unspentTxOutputsMap;
    private final String genesisTxId;
    private final int genesisBlockHeight;
    private int chainHeadHeight = 0;
    private Tx genesisTx;

    // not impl in PB yet
    private Set<Tuple2<Long, Integer>> compensationRequestFees;
    private Set<Tuple2<Long, Integer>> votingFees;

    // transient
    @Nullable
    transient private Storage<BsqChainState> storage;
    @Nullable
    transient private BsqChainState snapshotCandidate;
    transient private final FunctionalReadWriteLock lock;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public BsqChainState(PersistenceProtoResolver persistenceProtoResolver,
                         @Named(Storage.STORAGE_DIR) File storageDir) {

        bsqBlocks = new LinkedList<>();
        txMap = new HashMap<>();
        unspentTxOutputsMap = new HashMap<>();
        compensationRequestFees = new HashSet<>();
        votingFees = new HashSet<>();

        storage = new Storage<>(storageDir, persistenceProtoResolver);

        switch (BisqEnvironment.getBaseCurrencyNetwork()) {
            case BTC_MAINNET:
                genesisTxId = BTC_GENESIS_TX_ID;
                genesisBlockHeight = BTC_GENESIS_BLOCK_HEIGHT;
                break;
            case BTC_TESTNET:
                genesisTxId = BTC_TEST_NET_GENESIS_TX_ID;
                genesisBlockHeight = BTC_TEST_NET_GENESIS_BLOCK_HEIGHT;
                break;
            case BTC_REGTEST:
                genesisTxId = BTC_REG_TEST_GENESIS_TX_ID;
                genesisBlockHeight = BTC_REG_TEST_GENESIS_BLOCK_HEIGHT;
                break;

            case LTC_TESTNET:
                genesisTxId = LTC_TEST_NET_GENESIS_TX_ID;
                genesisBlockHeight = LTC_TEST_NET_GENESIS_BLOCK_HEIGHT;
                break;
            case LTC_REGTEST:
                genesisTxId = LTC_REG_TEST_GENESIS_TX_ID;
                genesisBlockHeight = LTC_REG_TEST_GENESIS_BLOCK_HEIGHT;
                break;
            case LTC_MAINNET:
            default:
                genesisTxId = LTC_GENESIS_TX_ID;
                genesisBlockHeight = LTC_GENESIS_BLOCK_HEIGHT;
                break;
        }

        lock = new FunctionalReadWriteLock(true);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BsqChainState(LinkedList<BsqBlock> bsqBlocks,
                          Map<String, Tx> txMap,
                          Map<TxIdIndexTuple, TxOutput> unspentTxOutputsMap,
                          String genesisTxId,
                          int genesisBlockHeight,
                          int chainHeadHeight,
                          Tx genesisTx) {
        this.bsqBlocks = bsqBlocks;
        this.txMap = txMap;
        this.unspentTxOutputsMap = unspentTxOutputsMap;
        this.genesisTxId = genesisTxId;
        this.genesisBlockHeight = genesisBlockHeight;
        this.chainHeadHeight = chainHeadHeight;
        this.genesisTx = genesisTx;

        lock = new FunctionalReadWriteLock(true);

        // not impl yet in PB
        compensationRequestFees = new HashSet<>();
        votingFees = new HashSet<>();
    }

    @Override
    public Message toProtoMessage() {
        return PB.PersistableEnvelope.newBuilder().setBsqChainState(getBsqChainStateBuilder()).build();
    }

    private PB.BsqChainState.Builder getBsqChainStateBuilder() {
        return PB.BsqChainState.newBuilder()
                .addAllBsqBlocks(bsqBlocks.stream()
                        .map(BsqBlock::toProtoMessage)
                        .collect(Collectors.toList()))
                .putAllTxMap(txMap.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey,
                                v -> v.getValue().toProtoMessage())))
                .putAllUnspentTxOutputsMap(unspentTxOutputsMap.entrySet().stream()
                        .collect(Collectors.toMap(k -> k.getKey().getAsString(),
                                v -> v.getValue().toProtoMessage())))
                .setGenesisTxId(genesisTxId)
                .setGenesisBlockHeight(genesisBlockHeight)
                .setChainHeadHeight(chainHeadHeight)
                .setGenesisTx(genesisTx.toProtoMessage());
    }

    public static PersistableEnvelope fromProto(PB.BsqChainState proto) {
        return new BsqChainState(new LinkedList<>(proto.getBsqBlocksList().stream()
                .map(BsqBlock::fromProto)
                .collect(Collectors.toList())),
                new HashMap<>(proto.getTxMapMap().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, v -> Tx.fromProto(v.getValue())))),
                new HashMap<>(proto.getUnspentTxOutputsMapMap().entrySet().stream()
                        .collect(Collectors.toMap(k -> new TxIdIndexTuple(k.getKey()), v -> TxOutput.fromProto(v.getValue())))),
                proto.getGenesisTxId(),
                proto.getGenesisBlockHeight(),
                proto.getChainHeadHeight(),
                Tx.fromProto(proto.getGenesisTx())
        );
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public write access
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applySnapshot() {
        lock.write(() -> {
            checkNotNull(storage, "storage must not be null");
            BsqChainState snapshot = storage.initAndGetPersistedWithFileName("BsqChainState");
            bsqBlocks.clear();
            txMap.clear();
            unspentTxOutputsMap.clear();
            chainHeadHeight = 0;
            genesisTx = null;

            if (snapshot != null) {
                log.info("applySnapshot snapshot.chainHeadHeight=" + snapshot.chainHeadHeight);
                bsqBlocks.addAll(snapshot.bsqBlocks);
                txMap.putAll(snapshot.txMap);
                unspentTxOutputsMap.putAll(snapshot.unspentTxOutputsMap);
                chainHeadHeight = snapshot.chainHeadHeight;
                genesisTx = snapshot.genesisTx;
            } else {
                log.info("Try to apply snapshot but no stored snapshot available");
            }

            printDetails();
        });
    }

    public void setCreateCompensationRequestFee(long fee, int blockHeight) {
        lock.write(() -> compensationRequestFees.add(new Tuple2<>(fee, blockHeight)));
    }

    public void setVotingFee(long fee, int blockHeight) {
        lock.write(() -> votingFees.add(new Tuple2<>(fee, blockHeight)));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package scope write access
    ///////////////////////////////////////////////////////////////////////////////////////////

    void addBlock(BsqBlock block) throws BlockNotConnectingException {
        try {
            lock.write2(() -> {
                if (!bsqBlocks.contains(block)) {
                    if (bsqBlocks.isEmpty() || (bsqBlocks.getLast().getHash().equals(block.getPreviousBlockHash()) &&
                            bsqBlocks.getLast().getHeight() + 1 == block.getHeight())) {
                        bsqBlocks.add(block);
                        block.getTxs().stream().forEach(BsqChainState.this::addTxToMap);
                        chainHeadHeight = block.getHeight();
                        maybeMakeSnapshot();
                        printDetails();
                    } else {
                        log.warn("addBlock called with a not connecting block:\n" +
                                        "height()={}, hash()={}, head.height()={}, head.hash()={}",
                                block.getHeight(), block.getHash(), bsqBlocks.getLast().getHeight(), bsqBlocks.getLast().getHash());
                        throw new BlockNotConnectingException(block);
                    }
                } else {
                    log.trace("We got that block already");
                }
                return null;
            });
        } catch (Exception e) {
            throw new BlockNotConnectingException(block);
        } catch (Throwable e) {
            log.error(e.toString());
            e.printStackTrace();
            throw e;
        }
    }

    void addTxToMap(Tx tx) {
        lock.write(() -> txMap.put(tx.getId(), tx));
    }

    void addUnspentTxOutput(TxOutput txOutput) {
        lock.write(() -> {
            checkArgument(txOutput.isVerified(), "txOutput must be verified at addUnspentTxOutput");
            unspentTxOutputsMap.put(txOutput.getTxIdIndexTuple(), txOutput);
        });
    }

    void removeUnspentTxOutput(TxOutput txOutput) {
        lock.write(() -> unspentTxOutputsMap.remove(txOutput.getTxIdIndexTuple()));
    }

    void setGenesisTx(Tx tx) {
        lock.write(() -> genesisTx = tx);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public read access
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getGenesisTxId() {
        return genesisTxId;
    }

    public int getGenesisBlockHeight() {
        return lock.read(() -> genesisBlockHeight);
    }

    public BsqChainState getClone() {
        return getClone(this);
    }

    public BsqChainState getClone(BsqChainState bsqChainState) {
        return lock.read(() -> (BsqChainState) BsqChainState.fromProto(bsqChainState.getBsqChainStateBuilder().build()));
    }

    public boolean containsBlock(BsqBlock bsqBlock) {
        return lock.read(() -> bsqBlocks.contains(bsqBlock));
    }

    Optional<TxOutput> getUnspentTxOutput(TxIdIndexTuple txIdIndexTuple) {
        return lock.read(() -> unspentTxOutputsMap.entrySet().stream()
                .filter(e -> e.getKey().equals(txIdIndexTuple))
                .map(Map.Entry::getValue).findAny());
    }

    public boolean isTxOutputSpendable(String txId, int index) {
        return lock.read(() -> getSpendableTxOutput(txId, index).isPresent());
    }

    public boolean hasTxBurntFee(String txId) {
        return lock.read(() -> getTx(txId).map(Tx::getBurntFee).filter(fee -> fee > 0).isPresent());
    }

    public Optional<TxType> getTxType(String txId) {
        return lock.read(() -> getTx(txId).map(Tx::getTxType));
    }

    public boolean containsTx(String txId) {
        return lock.read(() -> getTx(txId).isPresent());
    }

    public int getChainHeadHeight() {
        return lock.read(() -> chainHeadHeight);
    }

    // Only used for Json Exporter
    public Map<String, Tx> getTxMap() {
        return lock.read(() -> txMap);
    }

    public List<BsqBlock> getResettedBlocksFrom(int fromBlockHeight) {
        return lock.read(() -> {
            BsqChainState clone = getClone();
            List<BsqBlock> filtered = clone.bsqBlocks.stream()
                    .filter(block -> block.getHeight() >= fromBlockHeight)
                    .collect(Collectors.toList());
            filtered.stream().forEach(BsqBlock::reset);
            return filtered;
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package scope read access
    ///////////////////////////////////////////////////////////////////////////////////////////

    Optional<TxOutput> getSpendableTxOutput(String txId, int index) {
        return lock.read(() -> getSpendableTxOutput(new TxIdIndexTuple(txId, index)));
    }

    Optional<TxOutput> getSpendableTxOutput(TxIdIndexTuple txIdIndexTuple) {
        return lock.read(() -> getUnspentTxOutput(txIdIndexTuple)
                .filter(this::isTxOutputMature));
    }

    long getCreateCompensationRequestFee(int blockHeight) {
        return lock.read(() -> {
            long fee = -1;
            for (Tuple2<Long, Integer> feeAtHeight : compensationRequestFees) {
                if (feeAtHeight.second <= blockHeight)
                    fee = feeAtHeight.first;
            }
            checkArgument(fee > -1, "compensationRequestFees must be set");
            return fee;
        });
    }

    //TODO not impl yet
    boolean isCompensationRequestPeriodValid(int blockHeight) {
        return lock.read(() -> true);

    }

    long getVotingFee(int blockHeight) {
        return lock.read(() -> {
            long fee = -1;
            for (Tuple2<Long, Integer> feeAtHeight : votingFees) {
                if (feeAtHeight.second <= blockHeight)
                    fee = feeAtHeight.first;
            }
            checkArgument(fee > -1, "compensationRequestFees must be set");
            return fee;
        });
    }

    //TODO not impl yet
    boolean isVotingPeriodValid(int blockHeight) {
        return lock.read(() -> true);
    }

    boolean existsCompensationRequestBtcAddress(String btcAddress) {
        return lock.read(() -> getAllTxOutputs().stream()
                .filter(txOutput -> txOutput.isCompensationRequestBtcOutput() &&
                        txOutput.getAddress().equals(btcAddress))
                .findAny()
                .isPresent());
    }

    Set<TxOutput> findSponsoringBtcOutputsWithSameBtcAddress(String btcAddress) {
        return lock.read(() -> getAllTxOutputs().stream()
                .filter(txOutput -> txOutput.isSponsoringBtcOutput() &&
                        txOutput.getAddress().equals(btcAddress))
                .collect(Collectors.toSet()));
    }

    //TODO
    // for genesis we dont need it and for issuance we need more implemented first
    boolean isTxOutputMature(TxOutput spendingTxOutput) {
        return lock.read(() -> true);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Optional<Tx> getTx(String txId) {
        return lock.read(() -> txMap.get(txId) != null ? Optional.of(txMap.get(txId)) : Optional.<Tx>empty());
    }

    private boolean isSnapshotHeight(int height) {
        return isSnapshotHeight(genesisBlockHeight, height, SNAPSHOT_GRID);
    }

    private void maybeMakeSnapshot() {
        lock.read(() -> {
            if (isSnapshotHeight(getChainHeadHeight()) &&
                    (snapshotCandidate == null ||
                            snapshotCandidate.chainHeadHeight != getChainHeadHeight())) {
                // At trigger event we store the latest snapshotCandidate to disc
                if (snapshotCandidate != null) {
                    // We clone because storage is in a threaded context
                    final BsqChainState cloned = getClone(snapshotCandidate);
                    checkNotNull(storage, "storage must nto be null");
                    storage.queueUpForSave(cloned);
                    // dont access cloned anymore with methods as locks are transient!
                    log.info("Saved snapshotCandidate to Disc at height " + cloned.chainHeadHeight);
                }
                // Now we clone and keep it in memory for the next trigger
                snapshotCandidate = getClone(this);
                // dont access cloned anymore with methods as locks are transient!
                log.debug("Cloned new snapshotCandidate at height " + snapshotCandidate.chainHeadHeight);
            }
        });
    }

    private Set<TxOutput> getAllTxOutputs() {
        return txMap.values().stream()
                .flatMap(tx -> tx.getOutputs().stream())
                .collect(Collectors.toSet());
    }

    private void printDetails() {
        log.debug("\nchainHeadHeight={}\n" +
                        "    blocks.size={}\n" +
                        "    txMap.size={}\n" +
                        "    unspentTxOutputsMap.size={}\n" +
                        "    compensationRequestFees.size={}\n" +
                        "    votingFees.size={}\n" +
                getChainHeadHeight(),
                bsqBlocks.size(),
                txMap.size(),
                unspentTxOutputsMap.size(),
                compensationRequestFees.size(),
                votingFees.size());
    }
}

