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

import io.bisq.common.storage.Storage;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

// Map of any ever existing TxOutput which was a valid BSQ
@Slf4j
public class TxOutputMap implements Serializable {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface Listener {
        void onMapChanged(TxOutputMap txOutputMap);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////


    // We don't use a Lombok delegate here as we want control the access to our map
    @Getter
    private HashMap<TxIdIndexTuple, TxOutput> map = new HashMap<>();
    @Getter
    @Setter
    private int snapshotHeight = 0;

    private transient final Storage<TxOutputMap> storage;
    private final List<Listener> listeners = new ArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TxOutputMap(File storageDir) {
        storage = new Storage<>(storageDir);
        TxOutputMap persisted = storage.initAndGetPersisted(this, "BsqTxOutputMap");
        if (persisted != null) {
            map.putAll(persisted.getMap());
            snapshotHeight = persisted.getSnapshotHeight();
        }
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

    public void persist() {
        storage.queueUpForSave();
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Delegated map methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Object put(TxOutput txOutput) {
        final TxOutput result = map.put(new TxIdIndexTuple(txOutput.getTxId(), txOutput.getIndex()), txOutput);
        listeners.stream().forEach(l -> l.onMapChanged(this));
        return result;
    }

    @Nullable
    public TxOutput get(String txId, int index) {
        return map.get(new TxIdIndexTuple(txId, index));
    }

    public boolean contains(String txId, int index) {
        return map.containsKey(new TxIdIndexTuple(txId, index));
    }

    public Collection<TxOutput> values() {
        return map.values();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public int size() {
        return map.size();
    }

    @Override
    public String toString() {
        return "BsqTxOutputMap " + map.toString();
    }
}

