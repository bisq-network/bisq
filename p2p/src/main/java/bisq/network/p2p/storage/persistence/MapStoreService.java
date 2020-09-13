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

package bisq.network.p2p.storage.persistence;

import bisq.network.p2p.storage.P2PDataStorage;

import bisq.common.proto.persistable.PersistableEnvelope;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.storage.Storage;

import java.io.File;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * Handles persisted data which is stored in a map.
 *
 * @param <T>
 * @param <R>
 */
@Slf4j
public abstract class MapStoreService<T extends PersistableEnvelope, R extends PersistablePayload> extends StoreService<T> {


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public MapStoreService(File storageDir, Storage<T> storage) {
        super(storageDir, storage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public abstract Map<P2PDataStorage.ByteArray, R> getMap();

    public abstract boolean canHandle(R payload);

    void put(P2PDataStorage.ByteArray hash, R payload) {
        getMap().put(hash, payload);
        persist();
    }

    R putIfAbsent(P2PDataStorage.ByteArray hash, R payload) {
        R previous = getMap().putIfAbsent(hash, payload);
        persist();
        return previous;
    }

    R remove(P2PDataStorage.ByteArray hash) {
        final R result = getMap().remove(hash);
        persist();
        return result;
    }

    boolean containsKey(P2PDataStorage.ByteArray hash) {
        return getMap().containsKey(hash);
    }
}
