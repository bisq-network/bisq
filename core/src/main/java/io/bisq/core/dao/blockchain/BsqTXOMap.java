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
import io.bisq.common.persistence.PersistenceProtoResolver;
import io.bisq.common.storage.Storage;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

// Map of any ever existing TxOutput which was a valid BSQ
@Slf4j
public class BsqTXOMap implements Persistable {
    // We don't use a Lombok delegate here as we want control the access to our map
    @Getter
    private HashMap<TxIdIndexTuple, TxOutput> map = new HashMap<>();
    @Getter
    private HashSet<String> txIdSet = new HashSet<>();
    @Getter
    private HashMap<String, Tx> burnedBSQTxMap = new HashMap<>();
    @Getter
    @Setter
    private int snapshotHeight;

    private transient ObservableMap<TxIdIndexTuple, TxOutput> observableMap;
    private transient ObservableMap<String, Tx> observableBurnedBSQTxMap;
    private transient final Storage<BsqTXOMap> storage;

    public BsqTXOMap(File storageDir, PersistenceProtoResolver persistenceProtoResolver) {
        storage = new Storage<>(storageDir, persistenceProtoResolver);
        BsqTXOMap persisted = storage.initAndGetPersisted(this, "BsqTXOMap");
        if (persisted != null) {
            map.putAll(persisted.getMap());
            snapshotHeight = persisted.getSnapshotHeight();
            txIdSet = persisted.getTxIdSet();
        }

        observableMap = FXCollections.observableHashMap();
        observableMap.putAll(map);
        observableBurnedBSQTxMap = FXCollections.observableHashMap();
        observableBurnedBSQTxMap.putAll(burnedBSQTxMap);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            observableMap = FXCollections.observableHashMap();
            observableMap.putAll(map);
            observableBurnedBSQTxMap = FXCollections.observableHashMap();
            observableBurnedBSQTxMap.putAll(burnedBSQTxMap);
        } catch (Throwable t) {
            log.warn("Cannot be deserialized." + t.getMessage());
        }
    }

    public Object add(TxOutput txOutput) {
        txIdSet.add(txOutput.getTxId());
        final TxOutput result = map.put(new TxIdIndexTuple(txOutput.getTxId(), txOutput.getIndex()), txOutput);
        observableMap.put(new TxIdIndexTuple(txOutput.getTxId(), txOutput.getIndex()), txOutput);
        return result;
    }

    public Object addBurnedBSQTx(Tx tx) {
        Tx result = burnedBSQTxMap.put(tx.getId(), tx);
        observableBurnedBSQTxMap.put(tx.getId(), tx);
        return result;
    }

    public void persist() {
        storage.queueUpForSave();
    }

    public boolean containsTuple(String txId, int index) {
        return map.containsKey(new TxIdIndexTuple(txId, index));
    }

    public TxOutput getByTuple(String txId, int index) {
        return map.get(new TxIdIndexTuple(txId, index));
    }

    public void addListener(MapChangeListener<TxIdIndexTuple, TxOutput> listener) {
        observableMap.addListener(listener);
    }

    public void addBurnedBSQTxMapListener(MapChangeListener<String, Tx> listener) {
        observableBurnedBSQTxMap.addListener(listener);
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

    public Set<Map.Entry<TxIdIndexTuple, TxOutput>> entrySet() {
        return map.entrySet();
    }

    @Override
    public String toString() {
        return "BsqUTXOMap " + map.toString();
    }
}

