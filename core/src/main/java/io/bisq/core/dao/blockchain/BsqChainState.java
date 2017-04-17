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

import io.bisq.common.persistence.Persistable;
import io.bisq.common.util.Utilities;
import io.bisq.core.dao.blockchain.exceptions.BlockNotConnectingException;
import io.bisq.core.dao.blockchain.vo.*;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

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

    @Getter
    private final LinkedList<BsqBlock> blocks = new LinkedList<>();
    private final Map<TxIdIndexTuple, SpentInfo> spentInfoByTxOutputMap = new HashMap<>();
    private final Set<TxOutput> verifiedTxOutputSet = new HashSet<>();
    private final Map<String, Long> burnedFeeByTxIdMap = new HashMap<>();
    private final AtomicReference<Tx> genesisTx = new AtomicReference<>();

    transient private final Map<String, Tx> txMap = new HashMap<>();
    

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public BsqChainState() {
    }

    public void applyPersisted(BsqChainState persistedBsqChainState) {
        synchronized (BsqChainState.this) {
            blocks.addAll(persistedBsqChainState.blocks);
            blocks.stream().flatMap(bsqBlock -> bsqBlock.getTxs().stream()).forEach(this::addTx);
            spentInfoByTxOutputMap.putAll(persistedBsqChainState.spentInfoByTxOutputMap);
            verifiedTxOutputSet.addAll(persistedBsqChainState.verifiedTxOutputSet);
            burnedFeeByTxIdMap.putAll(persistedBsqChainState.burnedFeeByTxIdMap);
            genesisTx.set(persistedBsqChainState.genesisTx.get());

            // printDetails();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Write access
    ///////////////////////////////////////////////////////////////////////////////////////////

    public synchronized void addBlock(BsqBlock block) throws BlockNotConnectingException {
        if (blocks.isEmpty() || (blocks.getLast().getHash().equals(block.getPreviousBlockHash()) &&
                blocks.getLast().getHeight() == block.getHeight() - 1)) {
            blocks.add(block);
            block.getTxs().stream().forEach(this::addTx);
            // printDetails();
        } else {
            log.warn("addBlock called with a not connecting block:\n" +
                            "height()={}, hash()={}, head.height()={}, head.hash()={}",
                    block.getHeight(), block.getHash(), blocks.getLast().getHeight(), blocks.getLast().getHash());
            throw new BlockNotConnectingException(block);
        }
    }

    public void addTx(Tx tx) {
        txMap.put(tx.getId(), tx);
    }

    public void addSpentTxWithSpentInfo(TxOutput spentTxOutput, SpentInfo spentInfo) {
        spentInfoByTxOutputMap.put(spentTxOutput.getTxIdIndexTuple(), spentInfo);
    }

    public void setGenesisTx(Tx tx) {
        genesisTx.set(tx);
    }

    public void addVerifiedTxOutput(TxOutput txOutput) {
        verifiedTxOutputSet.add(txOutput);
    }

    public void addBurnedFee(String txId, long burnedFee) {
        burnedFeeByTxIdMap.put(txId, burnedFee);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Read access
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Optional<Tx> getTx(String txId) {
        return txMap.get(txId) != null ? Optional.of(txMap.get(txId)) : Optional.<Tx>empty();
    }

    private boolean isVerifiedTxOutput(TxOutput txOutput) {
        return verifiedTxOutputSet.contains(txOutput);
    }

    public boolean isTxOutputSpendable(String txId, int index) {
        return getSpendableTxOutput(txId, index).isPresent();
    }

    public boolean hasTxBurnedFee(String txId) {
        return burnedFeeByTxIdMap.containsKey(txId) && burnedFeeByTxIdMap.get(txId) > 0;
    }

    public boolean containsTx(String txId) {
        return getTx(txId).isPresent();
    }

    // TODO use a map for keeping spendable in cache
    public Optional<TxOutput> getSpendableTxOutput(String txId, int index) {
        final Optional<TxOutput> txOutputOptional = findTxOutput(txId, index);
        if (txOutputOptional.isPresent() &&
                isVerifiedTxOutput(txOutputOptional.get()) &&
                !spentInfoByTxOutputMap.containsKey(new TxIdIndexTuple(txId, index))) {
            return txOutputOptional;
        } else {
            return Optional.<TxOutput>empty();
        }
    }

    public int getChainHeadHeight() {
        return !blocks.isEmpty() ? blocks.get(blocks.size() - 1).getHeight() : 0;
    }

    public Optional<BsqBlock> getChainHead() {
        return !blocks.isEmpty() ? Optional.of(blocks.get(blocks.size() - 1)) : Optional.<BsqBlock>empty();
    }

    private Optional<TxOutput> findTxOutput(String txId, int index) {
        return getTx(txId).flatMap(e -> e.getTxOutput(index));
    }

    public boolean isBlockConnecting(String previousBlockHash) {
        if (blocks.isEmpty()) {
            return true;
        } else {
            synchronized (blocks) {
                final String tipHash = blocks.get(blocks.size() - 1).getHash();
                boolean isBlockConnecting = tipHash.equals(previousBlockHash);
                if (!isBlockConnecting)
                    log.error("new block is not connecting: tipHash={}, previousBlockHash={}", tipHash, previousBlockHash);
                return isBlockConnecting;
            }
        }
    }

    public boolean containsBlock(BsqBlock bsqBlock) {
        return blocks.contains(bsqBlock);
    }

    public Set<TxOutput> getVerifiedTxOutputSet() {
        return verifiedTxOutputSet;
    }

    public List<BsqBlock> getBlocksFrom(int fromBlockHeight) {
        return blocks.stream()
                .filter(block -> block.getHeight() >= fromBlockHeight)
                .sorted((o1, o2) -> new Integer(o1.getHeight()).compareTo(o2.getHeight()))
                .collect(Collectors.toList());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

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
}

