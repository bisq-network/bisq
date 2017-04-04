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
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.*;

@Slf4j
public class BsqUTXOMap implements Serializable {
    // We don't use a Lombok delegate here as we want control the access to our map
    @Getter
    private HashMap<TxIdIndexTuple, BsqUTXO> map = new HashMap<>();
    @Getter
    private HashSet<String> txIdSet = new HashSet<>();
    @Getter
    private int lastBlockHeight;

    private transient ObservableMap<TxIdIndexTuple, BsqUTXO> observableMap;
    private transient final Storage<BsqUTXOMap> storage;

    public BsqUTXOMap(File storageDir) {
        storage = new Storage<>(storageDir);
        BsqUTXOMap persisted = storage.initAndGetPersisted(this, "BsqUTXOMap");
        if (persisted != null) {
            map.putAll(persisted.getMap());
            txIdSet = persisted.getTxIdSet();
            lastBlockHeight = persisted.getLastBlockHeight();
        }

        observableMap = FXCollections.observableHashMap();
        observableMap.putAll(map);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            observableMap = FXCollections.observableHashMap();
            observableMap.putAll(map);
        } catch (Throwable t) {
            log.warn("Cannot be deserialized." + t.getMessage());
        }
    }

    public Object add(BsqUTXO bsqUTXO) {
        txIdSet.add(bsqUTXO.getTxId());
        final BsqUTXO result = map.put(new TxIdIndexTuple(bsqUTXO.getTxId(), bsqUTXO.getIndex()), bsqUTXO);
        observableMap.put(new TxIdIndexTuple(bsqUTXO.getTxId(), bsqUTXO.getIndex()), bsqUTXO);
        storage.queueUpForSave();
        return result;
    }

    public BsqUTXO removeByTuple(String txId, int index) {
        txIdSet.remove(txId);
        final BsqUTXO result = map.remove(new TxIdIndexTuple(txId, index));
        observableMap.remove(new TxIdIndexTuple(txId, index));
        storage.queueUpForSave();
        return result;
    }

    public void setLastBlockHeight(int lastBlockHeight) {
        this.lastBlockHeight = lastBlockHeight;
        storage.queueUpForSave();
    }

    public boolean containsTuple(String txId, int index) {
        return map.containsKey(new TxIdIndexTuple(txId, index));
    }

    public BsqUTXO getByTuple(String txId, int index) {
        return observableMap.get(new TxIdIndexTuple(txId, index));
    }

    public void addListener(MapChangeListener<TxIdIndexTuple, BsqUTXO> listener) {
        observableMap.addListener(listener);
    }

    public Collection<BsqUTXO> values() {
        return map.values();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public int size() {
        return map.size();
    }

    public Set<Map.Entry<TxIdIndexTuple, BsqUTXO>> entrySet() {
        return map.entrySet();
    }

    @Override
    public String toString() {
        return "BsqUTXOMap " + map.toString();
    }
}

