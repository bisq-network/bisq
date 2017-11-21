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
import org.bitcoinj.core.Coin;

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
    public static final Coin GENESIS_TOTAL_SUPPLY = Coin.COIN.multiply(25);

    //mainnet
    // this tx has a lot of outputs
    // https://blockchain.info/de/tx/ee921650ab3f978881b8fe291e0c025e0da2b7dc684003d7a03d9649dfee2e15
    // BLOCK_HEIGHT 411779
    // 411812 has 693 recursions
    // block 376078 has 2843 recursions and caused once a StackOverflowError, a second run worked. Took 1,2 sec.

    // BTC MAIN NET
    private static final String BTC_GENESIS_TX_ID = "e5c8313c4144d219b5f6b2dacf1d36f2d43a9039bb2fcd1bd57f8352a9c9809a";
    private static final int BTC_GENESIS_BLOCK_HEIGHT = 477865; // 2017-07-28

    // TEST NET
    // Phase 0 initial genesis tx 6.10.2017: 2f194230e23459a9211322c4b1c182cf3f367086e8059aca2f8f44e20dac527a
   // private static final String BTC_TEST_NET_GENESIS_TX_ID = "2f194230e23459a9211322c4b1c182cf3f367086e8059aca2f8f44e20dac527a";
   // private static final int BTC_TEST_NET_GENESIS_BLOCK_HEIGHT = 1209140;

    // Rebased genesis tx 9th november 2017
    private static final String BTC_TEST_NET_GENESIS_TX_ID = "f8b65c65624bd822f92480c39959f8ae4a6f94a9841c1625464ec6353cfba1d9";
    private static final int BTC_TEST_NET_GENESIS_BLOCK_HEIGHT =  1227630;

    // REG TEST
    private static final String BTC_REG_TEST_GENESIS_TX_ID = "321a2156d6cac631d3e574caf54a5a401e51971280c14b18b5f5877026a94d47";
    private static final int BTC_REG_TEST_GENESIS_BLOCK_HEIGHT = 111;


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
    @Nullable
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
            case BTC_TESTNET:
                genesisTxId = BTC_TEST_NET_GENESIS_TX_ID;
                genesisBlockHeight = BTC_TEST_NET_GENESIS_BLOCK_HEIGHT;
                break;
            case BTC_REGTEST:
                genesisTxId = BTC_REG_TEST_GENESIS_TX_ID;
                genesisBlockHeight = BTC_REG_TEST_GENESIS_BLOCK_HEIGHT;
                break;
            case BTC_MAINNET:
            default:
                genesisTxId = BTC_GENESIS_TX_ID;
                genesisBlockHeight = BTC_GENESIS_BLOCK_HEIGHT;
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
                          @Nullable Tx genesisTx) {
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
        final PB.BsqChainState.Builder builder = PB.BsqChainState.newBuilder()
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
                .setChainHeadHeight(chainHeadHeight);

        Optional.ofNullable(genesisTx).ifPresent(e -> builder.setGenesisTx(genesisTx.toProtoMessage()));

        return builder;
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
                proto.hasGenesisTx() ? Tx.fromProto(proto.getGenesisTx()) : null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public write access
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applySnapshot() {
        lock.write(() -> {
            checkNotNull(storage, "storage must not be null");
            BsqChainState snapshot = storage.initAndGetPersistedWithFileName("BsqChainState", 100);
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

    public Coin getTotalBurntFee() {
        return lock.read(() -> Coin.valueOf(getTxMap().entrySet().stream().mapToLong(e -> e.getValue().getBurntFee()).sum()));
    }

    public Set<Tx> getFeeTransactions() {
        return lock.read(() -> getTxMap().entrySet().stream().filter(e -> e.getValue().getBurntFee() > 0).map(Map.Entry::getValue).collect(Collectors.toSet()));
    }

    public Coin getIssuedAmount() {
        return lock.read(() -> BsqChainState.GENESIS_TOTAL_SUPPLY);
    }

    public Set<TxOutput> getUnspentTxOutputs() {
        return lock.read(() -> getAllTxOutputs().stream().filter(e -> e.isVerified() && e.isUnspent()).collect(Collectors.toSet()));
    }

    public Set<TxOutput> getSpentTxOutputs() {
        return lock.read(() -> getAllTxOutputs().stream().filter(e -> e.isVerified() && !e.isUnspent()).collect(Collectors.toSet()));
    }

    public Set<Tx> getTransactions() {
        return lock.read(() -> getTxMap().entrySet().stream().map(Map.Entry::getValue).collect(Collectors.toSet()));
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

