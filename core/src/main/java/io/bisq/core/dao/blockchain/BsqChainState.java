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
import io.bisq.core.dao.blockchain.vo.*;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkArgument;

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


    private final List<BsqBlock> blockchain = new CopyOnWriteArrayList<>();
    transient private final Map<String, Tx> txMap = new ConcurrentHashMap<>();
    private final Map<TxIdIndexTuple, SpentInfo> spentInfoByTxOutputMap = new ConcurrentHashMap<>();
    private final Set<TxOutput> verifiedTxOutputSet = new CopyOnWriteArraySet<>();
    private final Map<String, Long> burnedFeeByTxIdMap = new ConcurrentHashMap<>();
    private final AtomicReference<Tx> genesisTx = new AtomicReference<>();
    private final AtomicReference<BsqBlock> chainHeadBlock = new AtomicReference<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BsqChainState() {
    }

    public void applyPersisted(BsqChainState persistedBsqChainState) {
        synchronized (BsqChainState.this) {
            blockchain.addAll(persistedBsqChainState.blockchain);
            blockchain.stream().flatMap(bsqBlock -> bsqBlock.getTxs().stream()).forEach(this::addTx);
            spentInfoByTxOutputMap.putAll(persistedBsqChainState.spentInfoByTxOutputMap);
            verifiedTxOutputSet.addAll(persistedBsqChainState.verifiedTxOutputSet);
            burnedFeeByTxIdMap.putAll(persistedBsqChainState.burnedFeeByTxIdMap);
            genesisTx.set(persistedBsqChainState.genesisTx.get());
            chainHeadBlock.set(persistedBsqChainState.chainHeadBlock.get());
            // printDetails();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Write access
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addBlock(BsqBlock block) {
        checkArgument(getChainHeadHeight() <= block.getHeight(), "chainTip must not be lager than block.getHeight(). chainTip=" +
                getChainHeadHeight() + ": block.getHeight()=" + getChainHeadHeight());
        checkArgument(!blockchain.contains(block), "blockchain must not contain the new block");
        synchronized (this) {
            blockchain.add(block);
            chainHeadBlock.set(block);
            block.getTxs().stream().forEach(this::addTx);
            // printDetails();
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

    public Optional<Tx> getTx(String txId) {
        return txMap.get(txId) != null ? Optional.of(txMap.get(txId)) : Optional.<Tx>empty();
    }

    public boolean isVerifiedTxOutput(TxOutput txOutput) {
        return verifiedTxOutputSet.contains(txOutput);
    }

    public boolean isTxOutputSpendable(String txId, int index) {
        return getSpendableTxOutput(txId, index).isPresent();
    }

    public boolean hasTxBurnedFee(String txId) {
        return burnedFeeByTxIdMap.containsKey(txId) ? burnedFeeByTxIdMap.get(txId) > 0 : false;
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
        final BsqBlock block = chainHeadBlock.get();
        return block != null ? block.getHeight() : 0;
    }

    public Optional<TxOutput> findTxOutput(String txId, int index) {
        return getTx(txId).flatMap(e -> e.getTxOutput(index));
    }

    public boolean isBlockConnecting(String previousBlockHash) {
        if (blockchain.isEmpty()) {
            return true;
        } else {
            synchronized (blockchain) {
                final String tipHash = blockchain.get(blockchain.size() - 1).getHash();
                boolean isBlockConnecting = tipHash.equals(previousBlockHash);
                if (!isBlockConnecting)
                    log.error("new block is not connecting: tipHash={}, previousBlockHash={}", tipHash, previousBlockHash);
                return isBlockConnecting;
            }
        }
    }

    public Set<TxOutput> getVerifiedTxOutputSet() {
        return verifiedTxOutputSet;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void printDetails() {
        log.info("\nchainHeadHeight={}\nblocks.size={}\ntxMap.size={}\nverifiedTxOutputSet.size={}\n" +
                        "spentInfoByTxOutputMap.size={}\nburnedFeeByTxIdMap.size={}\nblocks data size in kb={}\n",
                getChainHeadHeight(),
                blockchain.size(),
                txMap.size(),
                verifiedTxOutputSet.size(),
                spentInfoByTxOutputMap.size(),
                burnedFeeByTxIdMap.size(),
                Utilities.serialize(blockchain.toArray()).length / 1000d);
    }
}

