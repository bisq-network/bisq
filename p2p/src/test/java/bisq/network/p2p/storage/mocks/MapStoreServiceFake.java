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
import bisq.network.p2p.storage.persistence.MapStoreService;

import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.persistable.PersistableEnvelope;
import bisq.common.proto.persistable.PersistablePayload;

import java.io.File;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;

import static org.mockito.Mockito.mock;

/**
 * Implementation of an in-memory MapStoreService that can be used in tests. Removes overhead
 * involving files, resources, and services for tests that don't need it.
 *
 * @see <a href="https://martinfowler.com/articles/mocksArentStubs.html#TheDifferenceBetweenMocksAndStubs">Reference</a>
 */
public class MapStoreServiceFake extends MapStoreService {
    @Getter
    private final Map<P2PDataStorage.ByteArray, ProtectedStorageEntry> map;

    public MapStoreServiceFake() {
        super(mock(File.class), mock(PersistenceManager.class));
        this.map = new HashMap<>();
    }

    @Override
    public String getFileName() {
        return null;
    }

    @Override
    protected PersistableEnvelope createStore() {
        return null;
    }

    @Override
    public boolean canHandle(PersistablePayload payload) {
        return true;
    }

    protected void readFromResourcesSync(String postFix) {
        // do nothing. This Fake only supports in-memory storage.
    }

    @Override
    protected void initializePersistenceManager() {
    }
}
