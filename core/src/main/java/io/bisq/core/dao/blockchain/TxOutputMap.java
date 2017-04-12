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

import io.bisq.common.util.Utilities;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
public class TxOutputMap implements Serializable {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Statics
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static TxOutputMap getClonedMap(TxOutputMap txOutputMap) {
        return new TxOutputMap(txOutputMap);
    }

    public static TxOutputMap getClonedMapUpToHeight(TxOutputMap txOutputMap, int snapshotHeight) {
        final TxOutputMap txOutputMapClone = new TxOutputMap();
        txOutputMapClone.setBlockHeight(txOutputMap.getBlockHeight());
        txOutputMapClone.setSnapshotHeight(txOutputMap.getSnapshotHeight());

        Map<TxIdIndexTuple, TxOutput> map = txOutputMap.entrySet().stream()
                .filter(entry -> entry.getValue().getBlockHeight() <= snapshotHeight)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        txOutputMapClone.putAll(map);

        return txOutputMapClone;
    }
    
    
    @Getter
    private HashMap<TxIdIndexTuple, TxOutput> map = new HashMap<>();
    @Getter
    @Setter
    private int snapshotHeight = 0;
    @Getter
    @Setter
    private int blockHeight;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TxOutputMap() {
    }

    private TxOutputMap(TxOutputMap txOutputMap) {
        map = txOutputMap.getMap();
        snapshotHeight = txOutputMap.getSnapshotHeight();
        blockHeight = txOutputMap.getBlockHeight();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isTxOutputUnSpent(String txId, int index) {
        final TxOutput txOutput = get(txId, index);
        return txOutput != null && txOutput.isUnSpend();
    }

    public boolean hasTxBurnedFee(String txId) {
        final TxOutput txOutput = get(txId, 0);
        return txOutput != null && txOutput.hasBurnedFee();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Delegated map methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Object put(TxOutput txOutput) {
        return map.put(txOutput.getTxIdIndexTuple(), txOutput);
    }

    public void putAll(Map<TxIdIndexTuple, TxOutput> txOutputs) {
        map.putAll(txOutputs);
    }

    public void putAll(TxOutputMap txOutputMap) {
        map.putAll(txOutputMap.getMap());
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

    @Override
    public String toString() {
        return "TxOutputMap " + map.toString();
    }

    public void printSize() {
        log.info("Nr of entries={}; Size in kb={}", size(), Utilities.serialize(this).length / 1000);
    }
}

