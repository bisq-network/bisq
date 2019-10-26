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

package bisq.network.p2p.storage.mocks;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.persistence.ProtectedDataStoreService;

import java.util.HashMap;
import java.util.Map;

public class ProtectedDataStoreServiceFake extends ProtectedDataStoreService {
    private final Map<P2PDataStorage.ByteArray, ProtectedStorageEntry> map;

    public ProtectedDataStoreServiceFake() {
        super();
        map = new HashMap<>();
    }

    public Map<P2PDataStorage.ByteArray, ProtectedStorageEntry> getMap() {
        return map;
    }

    public void put(P2PDataStorage.ByteArray hashAsByteArray, ProtectedStorageEntry entry) {
        map.put(hashAsByteArray, entry);
    }
    public ProtectedStorageEntry remove(P2PDataStorage.ByteArray hash, ProtectedStorageEntry protectedStorageEntry) {
        return map.remove(hash);
    }
}
