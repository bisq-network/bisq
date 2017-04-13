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

import io.bisq.common.persistance.Persistable;
import io.bisq.common.util.Utilities;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import io.bisq.common.util.Utilities;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Transaction;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

// Map of any TxOutput which was ever used in context of a BSQ TX.
// Outputs can come from various txs :
// - genesis tx
// - spend BSQ tx
// - burn fee
// - voting
// - comp. request
// - sponsoring tx (new genesis)

@Slf4j
public class TxOutputMap implements Persistable {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Statics
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static TxOutputMap getClonedMap(TxOutputMap txOutputMap) {
        return Utilities.<TxOutputMap>deserialize(Utilities.serialize(txOutputMap));
    }


    @Getter
    private HashMap<TxIdIndexTuple, TxOutput> map;
    @Getter
    @Setter
    private int blockHeight;
    @Getter
    @Setter
    private String blockHash;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TxOutputMap() {
        map = new HashMap<>();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isTxOutputUnSpent(String txId, int index) {
        final TxOutput txOutput = get(txId, index);
        return txOutput != null && txOutput.isUnSpend();
    }

    public boolean hasTxBurnedFee(Transaction tx) {
        for (int i = 0; i < tx.getOutputs().size(); i++) {
            final TxOutput txOutput = get(tx.getHashAsString(), i);
            if (txOutput != null && txOutput.hasBurnedFee())
                return true;
        }
        return false;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Delegated map methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Object put(TxOutput txOutput) {
        blockHeight = txOutput.getBlockHeight();
        return map.put(txOutput.getTxIdIndexTuple(), txOutput);
    }

    @Nullable
    public TxOutput get(String txId, int index) {
        return get(new TxIdIndexTuple(txId, index));
    }

    @Nullable
    public TxOutput get(TxIdIndexTuple txIdIndexTuple) {
        return map.get(txIdIndexTuple);
    }

    public boolean contains(String txId, int index) {
        return contains(new TxIdIndexTuple(txId, index));
    }

    public boolean contains(TxIdIndexTuple txIdIndexTuple) {
        return map.containsKey(txIdIndexTuple);
    }

    public Collection<TxOutput> values() {
        return map.values();
    }

    public Set<Map.Entry<TxIdIndexTuple, TxOutput>> entrySet() {
        return map.entrySet();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public int size() {
        return map.size();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String toString() {
        return "TxOutputMap " + map.toString();
    }

    public void printSize() {
        log.info("Nr of entries={}; Size in kb={}", size(), Utilities.serialize(this).length / 1000d);
    }

    public String getTuplesAsString() {
        return map.keySet().stream().map(TxIdIndexTuple::toString).collect(Collectors.joining(","));
    }

    public Set<TxOutput> getUnspentTxOutputs() {
        return map.values().stream().filter(TxOutput::isUnSpend).collect(Collectors.toSet());
    }

    public List<TxOutput> getSortedUnspentTxOutputs() {
        List<TxOutput> list = getUnspentTxOutputs().stream().collect(Collectors.toList());
        Collections.sort(list, (o1, o2) -> o1.getBlockHeightWithTxoId().compareTo(o2.getBlockHeightWithTxoId()));
        return list;
    }

    public void printUnspentTxOutputs(String prefix) {
        final String txoIds = getBlocHeightSortedTxoIds();
        log.info(prefix + " utxo: size={}, blockHeight={}, hashCode={}, txoids={}",
                getSortedUnspentTxOutputs().size(),
                blockHeight,
                getBlockHeightSortedTxoIdsHashCode(),
                txoIds);
    }

    public int getBlockHeightSortedTxoIdsHashCode() {
        return getBlocHeightSortedTxoIds().hashCode();
    }

    private String getBlocHeightSortedTxoIds() {
        return getSortedUnspentTxOutputs().stream()
                .map(e -> e.getBlockHeight() + "/" + e.getTxoId())
                .collect(Collectors.joining("\n"));
    }
}

