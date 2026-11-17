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
import bisq.core.provider.PriceFeedNodeAddressProvider;
import bisq.core.provider.PriceHttpClient;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;
import bisq.core.util.PriceUtil;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreListener;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import org.bitcoinj.core.Coin;

import java.io.File;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    // A statistic dated more than a day ahead would stay the newest one of its currency
    @Test
    public void receivedStatisticDatedMoreThanOneDayAheadIsNotUsed() {
        assertNotUsedWhenReceived(usdStatistics(Price.parse("USD", "1").getValue(),
                System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30)));
    }

    @Test
    public void receivedStatisticWithUnusableVolumeIsNotUsed() {
        assertNotUsedWhenReceived(statisticWithUnusableVolume(System.currentTimeMillis()));
    }

    // Positive control for the two tests above
    @Test
    public void receivedValidStatisticIsUsed() {
        P2PDataStorage p2PDataStorage = mock(P2PDataStorage.class);
        PriceFeedService priceFeedService = mock(PriceFeedService.class);
        TradeStatisticsManager manager = manager(p2PDataStorage, priceFeedService, new HashMap<>());
        manager.onAllServicesInitialized();
        TradeStatistics3 valid = usdStatistics(Price.parse("USD", "100").getValue(), System.currentTimeMillis());

        appendOnlyDataStoreListener(p2PDataStorage).onAdded(valid);

        assertEquals(Set.of(valid), manager.getObservableTradeStatisticsSet());
        verify(priceFeedService).setBisqMarketPrice("USD", valid.getTradePrice());
    }

    // A statistic can arrive after newer ones of its currency, for example when a trader republishes it
    @Test
    public void receivedStatisticOlderThanTheNewestOfItsCurrencyKeepsThePriceOfTheNewest() {
        TradeStatistics3 newer = usdStatistics(Price.parse("USD", "200").getValue(), System.currentTimeMillis());
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> mapOfAllData = new HashMap<>();
        mapOfAllData.put(new P2PDataStorage.ByteArray(newer.getHash()), newer);
        P2PDataStorage p2PDataStorage = mock(P2PDataStorage.class);
        PriceFeedService priceFeedService = mock(PriceFeedService.class);
        manager(p2PDataStorage, priceFeedService, mapOfAllData).onAllServicesInitialized();
        TradeStatistics3 older = usdStatistics(Price.parse("USD", "100").getValue(),
                System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30));

        appendOnlyDataStoreListener(p2PDataStorage).onAdded(older);

        verify(priceFeedService).setBisqMarketPrice("USD", newer.getTradePrice());
        verify(priceFeedService, never()).setBisqMarketPrice("USD", older.getTradePrice());
    }

    // A date up to one day ahead is valid. Such a statistic is the newest one of its currency until a statistic with
    // a later date arrives, as after a restart.
    @Test
    public void receivedStatisticDatedAheadStaysTheNewestOfItsCurrency() {
        long now = System.currentTimeMillis();
        P2PDataStorage p2PDataStorage = mock(P2PDataStorage.class);
        PriceFeedService priceFeedService = mock(PriceFeedService.class);
        manager(p2PDataStorage, priceFeedService, new HashMap<>()).onAllServicesInitialized();
        TradeStatistics3 datedAhead = usdStatistics(Price.parse("USD", "1").getValue(),
                now + TimeUnit.HOURS.toMillis(23));
        TradeStatistics3 current = usdStatistics(Price.parse("USD", "100").getValue(), now);
        AppendOnlyDataStoreListener listener = appendOnlyDataStoreListener(p2PDataStorage);

        listener.onAdded(datedAhead);
        listener.onAdded(current);

        verify(priceFeedService, times(2)).setBisqMarketPrice("USD", datedAhead.getTradePrice());
        verify(priceFeedService, never()).setBisqMarketPrice("USD", current.getTradePrice());
    }

    @Test
    public void storedStatisticDatedMoreThanOneDayAheadIsNotUsedAtStartup() {
        assertNotUsedAtStartup(usdStatistics(Price.parse("USD", "1").getValue(),
                System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30)));
    }

    @Test
    public void storedStatisticWithUnusableVolumeIsNotUsedAtStartup() {
        assertNotUsedAtStartup(statisticWithUnusableVolume(System.currentTimeMillis()));
    }

    // Such a price would be seeded as the market price, which cannot be converted back to a long
    @Test
    public void storedStatisticWithAPriceAboveTheCapIsNotUsedAtStartup() {
        assertNotUsedAtStartup(usdStatistics(Long.MAX_VALUE, System.currentTimeMillis()));
    }

    // The consumers of the market price convert it back to a price. For the highest valid price this stays in the long
    // range.
    @Test
    public void marketPriceSeededFromTheHighestValidPriceConvertsBack() {
        TradeStatistics3 highest = usdStatistics(TradeStatistics3.MAX_PRICE, System.currentTimeMillis());
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> mapOfAllData = new HashMap<>();
        mapOfAllData.put(new P2PDataStorage.ByteArray(highest.getHash()), highest);
        PriceFeedService priceFeedService = new PriceFeedService(mock(PriceHttpClient.class), mock(FeeService.class),
                mock(PriceFeedNodeAddressProvider.class), mock(Preferences.class));

        manager(mock(P2PDataStorage.class), priceFeedService, mapOfAllData).onAllServicesInitialized();

        Price converted = assertDoesNotThrow(() ->
                PriceUtil.marketPriceToPrice(priceFeedService.getMarketPrice("USD")));
        assertEquals(TradeStatistics3.MAX_PRICE, converted.getValue());
    }

    private static void assertNotUsedWhenReceived(TradeStatistics3 tradeStatistics) {
        P2PDataStorage p2PDataStorage = mock(P2PDataStorage.class);
        PriceFeedService priceFeedService = mock(PriceFeedService.class);
        TradeStatisticsManager manager = manager(p2PDataStorage, priceFeedService, new HashMap<>());
        manager.onAllServicesInitialized();

        appendOnlyDataStoreListener(p2PDataStorage).onAdded(tradeStatistics);

        assertEquals(Set.of(), manager.getObservableTradeStatisticsSet());
        verify(priceFeedService, never()).setBisqMarketPrice(any(), any());
    }

    // The statistic which is not valid is newer than the valid one, so it would be seeded as the market price
    private static void assertNotUsedAtStartup(TradeStatistics3 tradeStatistics) {
        TradeStatistics3 valid = usdStatistics(Price.parse("USD", "100").getValue(),
                System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> mapOfAllData = new HashMap<>();
        mapOfAllData.put(new P2PDataStorage.ByteArray(valid.getHash()), valid);
        mapOfAllData.put(new P2PDataStorage.ByteArray(tradeStatistics.getHash()), tradeStatistics);
        PriceFeedService priceFeedService = mock(PriceFeedService.class);
        TradeStatisticsManager manager = manager(mock(P2PDataStorage.class), priceFeedService, mapOfAllData);

        manager.onAllServicesInitialized();

        assertEquals(Set.of(valid), manager.getObservableTradeStatisticsSet());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Price>> captor = ArgumentCaptor.forClass(Map.class);
        verify(priceFeedService).applyInitialBisqMarketPrice(captor.capture());
        assertEquals(Map.of("USD", valid.getTradePrice()), captor.getValue());
    }

    private static AppendOnlyDataStoreListener appendOnlyDataStoreListener(P2PDataStorage p2PDataStorage) {
        ArgumentCaptor<AppendOnlyDataStoreListener> captor =
                ArgumentCaptor.forClass(AppendOnlyDataStoreListener.class);
        verify(p2PDataStorage).addAppendOnlyDataStoreListener(captor.capture());
        return captor.getValue();
    }

    private static TradeStatisticsManager manager(
            P2PDataStorage p2PDataStorage,
            PriceFeedService priceFeedService,
            Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> mapOfAllData) {
        TradeStatistics3StorageService storageService = mock(TradeStatistics3StorageService.class);
        when(storageService.getMapOfAllData()).thenReturn(mapOfAllData);
        P2PService p2PService = mock(P2PService.class);
        when(p2PService.getP2PDataStorage()).thenReturn(p2PDataStorage);
        return new TradeStatisticsManager(p2PService, priceFeedService,
                storageService, mock(AppendOnlyDataStoreService.class), new File("."), false);
    }

    private static TradeStatistics3 usdStatistics(long price, long date) {
        return new TradeStatistics3("USD", price, Coin.parseCoin("0.25").getValue(), "SEPA", date, null, null,
                (TreeMap<String, String>) null);
    }

    // The fiat volume amount * price / 10^8 does not fit into a long
    private static TradeStatistics3 statisticWithUnusableVolume(long date) {
        return new TradeStatistics3("USD", Long.MAX_VALUE, TradeStatistics3.HISTORICAL_MAX_TRADE_AMOUNT, "SEPA", date,
                null, null, (TreeMap<String, String>) null);
    }
}
