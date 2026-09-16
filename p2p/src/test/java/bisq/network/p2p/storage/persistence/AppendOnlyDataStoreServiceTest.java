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
import bisq.network.p2p.storage.mocks.MapStoreServiceFake;
import bisq.network.p2p.storage.mocks.PersistableNetworkPayloadStub;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Message;

import java.io.File;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

public class AppendOnlyDataStoreServiceTest {
    private HistoricalDataStoreServiceStub historicalService;
    private AppendOnlyDataStoreService appendOnlyDataStoreService;
    private PersistableNetworkPayload livePayload;
    private PersistableNetworkPayload historicalPayload;
    private PersistableNetworkPayload unknownPayload;

    @BeforeEach
    public void setUp() {
        livePayload = new PersistableNetworkPayloadStub(new byte[]{1});
        historicalPayload = new PersistableNetworkPayloadStub(new byte[]{2});
        unknownPayload = new PersistableNetworkPayloadStub(new byte[]{3});

        historicalService = new HistoricalDataStoreServiceStub();
        historicalService.getMapOfLiveData().put(hashOf(livePayload), livePayload);
        historicalService.allHistoricalPayloads = ImmutableMap.of(hashOf(historicalPayload), historicalPayload);

        appendOnlyDataStoreService = new AppendOnlyDataStoreService();
        appendOnlyDataStoreService.addService(historicalService);
    }

    @Test
    public void testContainsKeyFindsLiveAndHistoricalDataWithoutCopyingTheStore() {
        assertTrue(appendOnlyDataStoreService.containsKey(livePayload, hashOf(livePayload)));
        assertTrue(appendOnlyDataStoreService.containsKey(historicalPayload, hashOf(historicalPayload)));
        assertFalse(appendOnlyDataStoreService.containsKey(unknownPayload, hashOf(unknownPayload)));

        assertEquals(0, historicalService.numMapOfAllDataRequests);
    }

    @Test
    public void testContainsKeyMatchesTheMergedMap() {
        // The merged map defines the expected answers. Each request for it copies the whole store, which is
        // why the membership test must not be routed through it.
        for (PersistableNetworkPayload payload : List.of(livePayload, historicalPayload, unknownPayload)) {
            assertEquals(appendOnlyDataStoreService.getMap(payload).containsKey(hashOf(payload)),
                    appendOnlyDataStoreService.containsKey(payload, hashOf(payload)));
        }
        assertEquals(3, historicalService.numMapOfAllDataRequests);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testContainsKeyOnPlainMapStoreService() {
        AppendOnlyDataStoreService service = new AppendOnlyDataStoreService();
        service.addService(new MapStoreServiceFake());

        assertFalse(service.containsKey(unknownPayload, hashOf(unknownPayload)));
        service.put(hashOf(unknownPayload), unknownPayload);
        assertTrue(service.containsKey(unknownPayload, hashOf(unknownPayload)));
    }

    @Test
    public void testContainsKeyWithoutMatchingService() {
        assertFalse(new AppendOnlyDataStoreService().containsKey(unknownPayload, hashOf(unknownPayload)));
    }

    private static P2PDataStorage.ByteArray hashOf(PersistableNetworkPayload payload) {
        return new P2PDataStorage.ByteArray(payload.getHash());
    }

    private static class HistoricalDataStoreServiceStub
            extends HistoricalDataStoreService<PersistableNetworkPayloadStore<PersistableNetworkPayload>> {
        int numMapOfAllDataRequests;

        HistoricalDataStoreServiceStub() {
            super(mock(File.class), null);
            store = new PersistableNetworkPayloadStore<>() {
                @Override
                public Message toProtoMessage() {
                    return null;
                }
            };
        }

        @Override
        public Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> getMapOfAllData() {
            numMapOfAllDataRequests++;
            return super.getMapOfAllData();
        }

        @Override
        public boolean canHandle(PersistableNetworkPayload payload) {
            return true;
        }

        @Override
        public String getFileName() {
            return "HistoricalDataStoreServiceStub";
        }

        @Override
        protected void initializePersistenceManager() {
        }

        @Override
        protected PersistableNetworkPayloadStore<PersistableNetworkPayload> createStore() {
            return null;
        }
    }
}
