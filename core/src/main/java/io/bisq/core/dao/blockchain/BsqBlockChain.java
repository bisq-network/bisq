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

package io.bisq.core.dao.blockchain;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.Message;
import io.bisq.common.proto.persistable.PersistableEnvelope;
import io.bisq.common.proto.persistable.PersistenceProtoResolver;
import io.bisq.common.storage.Storage;
import io.bisq.common.util.FunctionalReadWriteLock;
import io.bisq.common.util.Tuple2;
import io.bisq.core.dao.DaoOptionKeys;
import io.bisq.core.dao.blockchain.exceptions.BlockNotConnectingException;
import io.bisq.core.dao.blockchain.vo.*;
import io.bisq.generated.protobuffer.PB;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

// Represents mutable state of BSQ blocks
// We get accessed the data from different threads so we need to make sure it is thread safe.
@Slf4j
public class BsqBlockChain implements PersistableEnvelope {

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
    private static final Coin GENESIS_TOTAL_SUPPLY = Coin.parseCoin("2.5");

    //mainnet
    // this tx has a lot of outputs
    // https://blockchain.info/de/tx/ee921650ab3f978881b8fe291e0c025e0da2b7dc684003d7a03d9649dfee2e15
    // BLOCK_HEIGHT 411779
    // 411812 has 693 recursions
    // block 376078 has 2843 recursions and caused once a StackOverflowError, a second run worked. Took 1,2 sec.

    // BTC MAIN NET
    public static final String BTC_GENESIS_TX_ID = "e5c8313c4144d219b5f6b2dacf1d36f2d43a9039bb2fcd1bd57f8352a9c9809a";
    public static final int BTC_GENESIS_BLOCK_HEIGHT = 477865; // 2017-07-28


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
    @Getter
    private Tx genesisTx;

    // not impl in PB yet
    private Set<Tuple2<Long, Integer>> compensationRequestFees;
    private Set<Tuple2<Long, Integer>> votingFees;

    // transient
    @Nullable
    transient private Storage<BsqBlockChain> storage;
    @Nullable
    transient private BsqBlockChain snapshotCandidate;
    transient private final FunctionalReadWriteLock lock;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public BsqBlockChain(PersistenceProtoResolver persistenceProtoResolver,
                         @Named(Storage.STORAGE_DIR) File storageDir,
                         @Named(DaoOptionKeys.GENESIS_TX_ID) String genesisTxId,
                         @Named(DaoOptionKeys.GENESIS_BLOCK_HEIGHT) int genesisBlockHeight) {
        this.genesisTxId = genesisTxId;
        this.genesisBlockHeight = genesisBlockHeight;

        storage = new Storage<>(storageDir, persistenceProtoResolver);

        bsqBlocks = new LinkedList<>();
        txMap = new HashMap<>();
        unspentTxOutputsMap = new HashMap<>();
        compensationRequestFees = new HashSet<>();
        votingFees = new HashSet<>();

        lock = new FunctionalReadWriteLock(true);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BsqBlockChain(LinkedList<BsqBlock> bsqBlocks,
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
        return PB.PersistableEnvelope.newBuilder().setBsqBlockChain(getBsqBlockChainBuilder()).build();
    }

    private PB.BsqBlockChain.Builder getBsqBlockChainBuilder() {
        final PB.BsqBlockChain.Builder builder = PB.BsqBlockChain.newBuilder()
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

    public static PersistableEnvelope fromProto(PB.BsqBlockChain proto) {
        return new BsqBlockChain(new LinkedList<>(proto.getBsqBlocksList().stream()
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
    // Atomic access
    ///////////////////////////////////////////////////////////////////////////////////////////

    public <T> T callFunctionWithWriteLock(Supplier<T> supplier) {
        return lock.write(supplier);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public write access
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applySnapshot() {
        lock.write(() -> {
            checkNotNull(storage, "storage must not be null");
            BsqBlockChain snapshot = storage.initAndGetPersistedWithFileName("BsqBlockChain", 100);
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

    public void addBlock(BsqBlock block) throws BlockNotConnectingException {
        try {
            lock.write2(() -> {
                if (!bsqBlocks.contains(block)) {
                    if (bsqBlocks.isEmpty() || (bsqBlocks.getLast().getHash().equals(block.getPreviousBlockHash()) &&
                            bsqBlocks.getLast().getHeight() + 1 == block.getHeight())) {
                        bsqBlocks.add(block);
                        block.getTxs().forEach(BsqBlockChain.this::addTxToMap);
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

    public void addTxToMap(Tx tx) {
        lock.write(() -> txMap.put(tx.getId(), tx));
    }

    public void addUnspentTxOutput(TxOutput txOutput) {
        lock.write(() -> {
            checkArgument(txOutput.isVerified(), "txOutput must be verified at addUnspentTxOutput");
            unspentTxOutputsMap.put(txOutput.getTxIdIndexTuple(), txOutput);
        });
    }

    public void removeUnspentTxOutput(TxOutput txOutput) {
        lock.write(() -> unspentTxOutputsMap.remove(txOutput.getTxIdIndexTuple()));
    }

    public void setGenesisTx(Tx tx) {
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

    public BsqBlockChain getClone() {
        return getClone(this);
    }

    private BsqBlockChain getClone(BsqBlockChain bsqBlockChain) {
        return lock.read(() -> (BsqBlockChain) BsqBlockChain.fromProto(bsqBlockChain.getBsqBlockChainBuilder().build()));
    }

    public boolean containsBlock(BsqBlock bsqBlock) {
        return lock.read(() -> bsqBlocks.contains(bsqBlock));
    }

    private Optional<TxOutput> getUnspentTxOutput(TxIdIndexTuple txIdIndexTuple) {
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

    public Optional<Tx> findTx(String txId) {
        Tx tx = getTxMap().get(txId);
        if (tx != null)
            return Optional.of(tx);
        else
            return Optional.empty();
    }

    public int getChainHeadHeight() {
        return lock.read(() -> chainHeadHeight);
    }

    public Map<String, Tx> getTxMap() {
        return lock.read(() -> txMap);
    }

    public List<BsqBlock> getResetBlocksFrom(int fromBlockHeight) {
        return lock.read(() -> {
            BsqBlockChain clone = getClone();
            List<BsqBlock> filtered = clone.bsqBlocks.stream()
                    .filter(block -> block.getHeight() >= fromBlockHeight)
                    .collect(Collectors.toList());
            filtered.forEach(BsqBlock::reset);
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
        return lock.read(() -> BsqBlockChain.GENESIS_TOTAL_SUPPLY);
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

    public Optional<TxOutput> getSpendableTxOutput(String txId, int index) {
        return lock.read(() -> getSpendableTxOutput(new TxIdIndexTuple(txId, index)));
    }

    public Optional<TxOutput> getSpendableTxOutput(TxIdIndexTuple txIdIndexTuple) {
        return lock.read(() -> getUnspentTxOutput(txIdIndexTuple)
                .filter(this::isTxOutputMature));
    }

    public long getCreateCompensationRequestFee(int blockHeight) {
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
    public boolean isCompensationRequestPeriodValid(int blockHeight) {
        return lock.read(() -> true);

    }

    public long getVotingFee(int blockHeight) {
        return lock.read(() -> {
            long fee = -1;
            for (Tuple2<Long, Integer> feeAtHeight : votingFees) {
                if (feeAtHeight.second <= blockHeight)
                    fee = feeAtHeight.first;
            }
            checkArgument(fee > -1, "votingFee must be set");
            return fee;
        });
    }

    //TODO not impl yet
    public boolean isVotingPeriodValid(int blockHeight) {
        return lock.read(() -> true);
    }

    public boolean existsCompensationRequestBtcAddress(String btcAddress) {
        return lock.read(() -> getAllTxOutputs().stream()
                .anyMatch(txOutput -> txOutput.isCompensationRequestBtcOutput() &&
                        btcAddress.equals(txOutput.getAddress())));
    }

    public Set<TxOutput> findSponsoringBtcOutputsWithSameBtcAddress(String btcAddress) {
        return lock.read(() -> getAllTxOutputs().stream()
                .filter(txOutput -> txOutput.isSponsoringBtcOutput() &&
                        btcAddress.equals(txOutput.getAddress()))
                .collect(Collectors.toSet()));
    }

    //TODO
    // for genesis we don't need it and for issuance we need more implemented first
    private boolean isTxOutputMature(TxOutput spendingTxOutput) {
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
                    final BsqBlockChain cloned = getClone(snapshotCandidate);
                    checkNotNull(storage, "storage must not be null");
                    storage.queueUpForSave(cloned);
                    // don't access cloned anymore with methods as locks are transient!
                    log.info("Saved snapshotCandidate to Disc at height " + cloned.chainHeadHeight);
                }
                // Now we clone and keep it in memory for the next trigger
                snapshotCandidate = getClone(this);
                // don't access cloned anymore with methods as locks are transient!
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

