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

import bisq.core.locale.GlobalSettings;
import bisq.core.monetary.Price;
import bisq.core.provider.price.PriceFeedService;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import org.bitcoinj.core.Coin;

import java.io.File;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TradeStatisticsManagerTest {
    private Locale previousLocale;

    @BeforeEach
    public void setup() {
        previousLocale = GlobalSettings.getLocale();
        GlobalSettings.setLocale(Locale.US);
    }

    @AfterEach
    public void tearDown() {
        GlobalSettings.setLocale(previousLocale);
    }

    @Test
    public void seedsNewestPricePerCurrencyIntoPriceFeed() {
        // Two USD statistics; the newer date carries a different price. The seeding must select
        // the newest price per currency, which relies on the set being ordered by date first.
        long amount = Coin.parseCoin("0.25").getValue();
        TradeStatistics3 older = new TradeStatistics3("USD", Price.parse("USD", "100").getValue(),
                amount, "SEPA", 1_700_000_000_000L, null, null, null, null);
        TradeStatistics3 newer = new TradeStatistics3("USD", Price.parse("USD", "200").getValue(),
                amount, "SEPA", 1_700_000_100_000L, null, null, null, null);

        TradeStatistics3StorageService storageService = mock(TradeStatistics3StorageService.class);
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> mapOfAllData = new HashMap<>();
        mapOfAllData.put(new P2PDataStorage.ByteArray(new byte[]{1}), older);
        mapOfAllData.put(new P2PDataStorage.ByteArray(new byte[]{2}), newer);
        when(storageService.getMapOfAllData()).thenReturn(mapOfAllData);

        P2PService p2PService = mock(P2PService.class);
        when(p2PService.getP2PDataStorage()).thenReturn(mock(P2PDataStorage.class));
        PriceFeedService priceFeedService = mock(PriceFeedService.class);

        TradeStatisticsManager manager = new TradeStatisticsManager(p2PService, priceFeedService,
                storageService, mock(AppendOnlyDataStoreService.class), new File("."), false);

        manager.onAllServicesInitialized();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Price>> captor = ArgumentCaptor.forClass(Map.class);
        verify(priceFeedService).applyInitialBisqMarketPrice(captor.capture());
        assertEquals(newer.getTradePrice().getValue(), captor.getValue().get("USD").getValue());
    }
}
