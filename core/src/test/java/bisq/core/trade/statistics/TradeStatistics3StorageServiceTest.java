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

package bisq.core.trade.statistics;

import bisq.network.p2p.storage.P2PDataStorage.ByteArray;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.persistence.PersistenceManager;

import com.google.common.collect.ImmutableMap;

import java.io.File;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeStatistics3StorageServiceTest {
    @TempDir
    File directory;

    @Test
    @SuppressWarnings("unchecked")
    void onlyAcceptedStatisticsReachPersistence() {
        PersistenceManager<TradeStatistics3Store> persistence = mock(PersistenceManager.class);
        TradeStatistics3StorageService service = new TradeStatistics3StorageService(directory, persistence) {
            {
                store = new TradeStatistics3Store();
                allHistoricalPayloads = ImmutableMap.of();
            }
        };
        AppendOnlyDataStoreService stores = new AppendOnlyDataStoreService();
        stores.addService(service);
        TradeStatistics3 invalid = statistic(0);
        assertFalse(stores.put(new ByteArray(invalid.getHash()), invalid));
        verify(persistence, never()).requestPersistence();

        TradeStatistics3 valid = statistic(50_000_000);
        ByteArray key = new ByteArray(valid.getHash());
        assertTrue(stores.put(key, valid));
        assertFalse(stores.put(key, valid));
        assertTrue(stores.containsKey(valid, key));
        verify(persistence).requestPersistence();
    }

    // A later version can add a currency. Nodes of this version do not use its statistics, but they store and
    // relay them, as nodes of earlier versions do.
    @Test
    @SuppressWarnings("unchecked")
    void statisticOfACurrencyUnknownToThisVersionIsStoredButNotUsed() {
        PersistenceManager<TradeStatistics3Store> persistence = mock(PersistenceManager.class);
        TradeStatistics3StorageService service = new TradeStatistics3StorageService(directory, persistence) {
            {
                store = new TradeStatistics3Store();
                allHistoricalPayloads = ImmutableMap.of();
            }
        };
        AppendOnlyDataStoreService stores = new AppendOnlyDataStoreService();
        stores.addService(service);
        TradeStatistics3 statistic = new TradeStatistics3("NEWCOIN", 500_000, 25_000_000, "BLOCK_CHAINS",
                System.currentTimeMillis(), null, null, null);

        assertFalse(statistic.isValid());
        assertTrue(stores.put(new ByteArray(statistic.getHash()), statistic));
        verify(persistence).requestPersistence();
    }

    @Test
    @SuppressWarnings("unchecked")
    void fullLiveStoreRefusesANewStatistic() {
        PersistenceManager<TradeStatistics3Store> persistence = mock(PersistenceManager.class);
        TradeStatistics3StorageService service = spy(new TradeStatistics3StorageService(directory, persistence));
        Map<ByteArray, PersistableNetworkPayload> full = mock(Map.class);
        when(full.size()).thenReturn(TradeStatistics3Store.MAX_ITEMS);
        doReturn(full).when(service).getMapOfLiveData();
        AppendOnlyDataStoreService stores = new AppendOnlyDataStoreService();
        stores.addService(service);

        TradeStatistics3 valid = statistic(50_000_000);
        assertFalse(stores.put(new ByteArray(valid.getHash()), valid));
        verify(persistence, never()).requestPersistence();
    }

    private static TradeStatistics3 statistic(long price) {
        return new TradeStatistics3("USD", price, 25_000_000, "SEPA", System.currentTimeMillis(), null, null, null);
    }
}
