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
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import io.bisq.common.proto.persistable.PersistableEnvelope;
import io.bisq.common.proto.persistable.PersistenceProtoResolver;
import io.bisq.common.storage.Storage;
import io.bisq.common.util.FunctionalReadWriteLock;
import io.bisq.common.util.Tuple2;
import io.bisq.common.util.Utilities;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.dao.blockchain.exceptions.BlockNotConnectingException;
import io.bisq.core.dao.blockchain.vo.*;
import io.bisq.generated.protobuffer.PB;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

// Represents mutable state of BSQ chain data
// We get accessed the data from different threads so we need to make sure it is thread safe.
@Slf4j
public class BsqChainState implements PersistableEnvelope, Serializable {
    private static final long serialVersionUID = 1;


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
    private static final String BTC_REG_TEST_GENESIS_TX_ID = "389d631bb48bd2f74fcc88c3506e2b03114b18b4e396c3bd2b8bb7d7ff9ee0d6";
    private static final int BTC_REG_TEST_GENESIS_BLOCK_HEIGHT = 1441;

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
    private final LinkedList<BsqBlock> blocks = new LinkedList<>();
    private final HashMap<String, Tx> txMap = new HashMap<>();
    private final HashMap<TxIdIndexTuple, TxOutput> unspentTxOutputsMap = new HashMap<>();
    private final HashSet<Tuple2<Long, Integer>> compensationRequestFees = new HashSet<>();
    private final HashSet<Tuple2<Long, Integer>> votingFees = new HashSet<>();

    private final String genesisTxId;
    private final int genesisBlockHeight;
    private int chainHeadHeight = 0;
    private Tx genesisTx;

    // transient
    transient private final Storage<BsqChainState> storage;
    transient private BsqChainState snapshotCandidate;
    transient private FunctionalReadWriteLock lock;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public BsqChainState(PersistenceProtoResolver persistenceProtoResolver,
                         @Named(Storage.STORAGE_DIR) File storageDir) {

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

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            lock = new FunctionalReadWriteLock(true);
        } catch (Throwable t) {
            log.warn("Cannot be deserialized." + t.getMessage());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    // TODO only supports serialized data atm
    @Override
    public Message toProtoMessage() {
        PB.BsqChainState.Builder builder = PB.BsqChainState.newBuilder();
        builder.setSerialized(ByteString.copyFrom(Utilities.serialize(this)));
        return PB.PersistableEnvelope.newBuilder().setBsqChainState(builder).build();
    }

    public static PersistableEnvelope fromProto(PB.BsqChainState proto) {
        return Utilities.deserialize(proto.getSerialized().toByteArray());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public write access
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applySnapshot() {
        lock.write(() -> {
            BsqChainState snapshot = storage.initAndGetPersistedWithFileName("BsqChainState");
            blocks.clear();
            txMap.clear();
            unspentTxOutputsMap.clear();
            chainHeadHeight = 0;
            genesisTx = null;

            if (snapshot != null) {
                log.info("applySnapshot snapshot.chainHeadHeight=" + snapshot.chainHeadHeight);
                blocks.addAll(snapshot.blocks);
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
                if (!blocks.contains(block)) {
                    if (blocks.isEmpty() || (blocks.getLast().getHash().equals(block.getPreviousBlockHash()) &&
                            blocks.getLast().getHeight() + 1 == block.getHeight())) {
                        blocks.add(block);
                        block.getTxs().stream().forEach(BsqChainState.this::addTxToMap);
                        chainHeadHeight = block.getHeight();
                        maybeMakeSnapshot();
                        printDetails();
                    } else {
                        log.warn("addBlock called with a not connecting block:\n" +
                                        "height()={}, hash()={}, head.height()={}, head.hash()={}",
                                block.getHeight(), block.getHash(), blocks.getLast().getHeight(), blocks.getLast().getHash());
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
        return lock.read(() -> Utilities.<BsqChainState>cloneObject(this));
    }

    public boolean containsBlock(BsqBlock bsqBlock) {
        return lock.read(() -> blocks.contains(bsqBlock));
    }

    Optional<TxOutput> getUnspentTxOutput(TxIdIndexTuple txIdIndexTuple) {
        return lock.read(() -> unspentTxOutputsMap.entrySet().stream()
                .filter(e -> e.getKey().equals(txIdIndexTuple))
                .map(Map.Entry::getValue).findAny());
    }

    public boolean isTxOutputSpendable(String txId, int index) {
        return lock.read(() -> getSpendableTxOutput(txId, index).isPresent());
    }

    public byte[] getSerializedResettedBlocksFrom(int fromBlockHeight) {
        return lock.read(() -> {
            List<BsqBlock> filtered = blocks.stream()
                    .filter(block -> block.getHeight() >= fromBlockHeight)
                    .collect(Collectors.toList());
            filtered.stream().forEach(BsqBlock::reset);
            return Utilities.<ArrayList<BsqBlock>>serialize(new ArrayList<>(filtered));
        });
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
            // dont access snapshotCandidate.getChainHeadHeight() as locks are transient and woudl give a null pointer!
            if (isSnapshotHeight(getChainHeadHeight()) &&
                    (snapshotCandidate == null ||
                            snapshotCandidate.chainHeadHeight != getChainHeadHeight())) {
                // At trigger event we store the latest snapshotCandidate to disc
                if (snapshotCandidate != null) {
                    // We clone because storage is in a threaded context
                    final BsqChainState cloned = Utilities.<BsqChainState>cloneObject(snapshotCandidate);
                    storage.queueUpForSave(cloned);
                    // dont access cloned anymore with methods as locks are transient!
                    log.info("Saved snapshotCandidate to Disc at height " + cloned.chainHeadHeight);
                }
                // Now we clone and keep it in memory for the next trigger
                snapshotCandidate = Utilities.<BsqChainState>cloneObject(this);
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
                        "    blocks data size in kb={}\n",
                getChainHeadHeight(),
                blocks.size(),
                txMap.size(),
                unspentTxOutputsMap.size(),
                compensationRequestFees.size(),
                votingFees.size(),
                Utilities.serialize(blocks.toArray()).length / 1000d);
    }
}

