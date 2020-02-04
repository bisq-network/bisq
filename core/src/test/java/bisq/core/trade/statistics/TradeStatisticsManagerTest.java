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

import bisq.core.provider.price.PriceFeedService;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreListener;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import org.bitcoinj.core.Coin;

import java.io.File;

import org.mockito.ArgumentCaptor;

import org.junit.Before;
import org.junit.Test;

import static bisq.core.trade.statistics.TradeStatistics2Maker.dayZeroTrade;
import static bisq.core.trade.statistics.TradeStatistics2Maker.depositTxId;
import static bisq.core.trade.statistics.TradeStatistics2Maker.tradeAmount;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static com.natpryce.makeiteasy.MakeItEasy.withNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TradeStatisticsManagerTest {

    private TradeStatisticsManager manager;
    private TradeStatistics2 tradeWithNullDepositTxId;
    private ArgumentCaptor<AppendOnlyDataStoreListener> listenerArgumentCaptor;

    @Before
    public void prepareMocksAndObjects() {
        P2PService p2PService = mock(P2PService.class);
        P2PDataStorage p2PDataStorage = mock(P2PDataStorage.class);
        File storageDir = mock(File.class);
        TradeStatistics2StorageService tradeStatistics2StorageService = mock(TradeStatistics2StorageService.class);
        PriceFeedService priceFeedService = mock(PriceFeedService.class);

        AppendOnlyDataStoreService appendOnlyDataStoreService = mock(AppendOnlyDataStoreService.class);
        when(p2PService.getP2PDataStorage()).thenReturn(p2PDataStorage);

        manager = new TradeStatisticsManager(p2PService, priceFeedService,
                tradeStatistics2StorageService, appendOnlyDataStoreService, storageDir, false);

        tradeWithNullDepositTxId = make(dayZeroTrade.but(withNull(depositTxId)));

        manager.onAllServicesInitialized();
        listenerArgumentCaptor = ArgumentCaptor.forClass(AppendOnlyDataStoreListener.class);
        verify(p2PDataStorage).addAppendOnlyDataStoreListener(listenerArgumentCaptor.capture());

    }

    @Test
    public void addToSet_ObjectWithNullDepositTxId() {
        listenerArgumentCaptor.getValue().onAdded(tradeWithNullDepositTxId);
        assertTrue(manager.getObservableTradeStatisticsSet().contains(tradeWithNullDepositTxId));
    }

    @Test
    public void addToSet_RemoveExistingObjectIfObjectWithNullDepositTxIdIsAdded() {
        TradeStatistics2 tradeWithDepositTxId = make(dayZeroTrade);

        listenerArgumentCaptor.getValue().onAdded(tradeWithDepositTxId);
        listenerArgumentCaptor.getValue().onAdded(tradeWithNullDepositTxId);

        assertFalse(manager.getObservableTradeStatisticsSet().contains(tradeWithDepositTxId));
        assertTrue(manager.getObservableTradeStatisticsSet().contains(tradeWithNullDepositTxId));
    }

    @Test
    public void addToSet_NotRemoveExistingObjectIfObjectsNotEqual() {
        TradeStatistics2 tradeWithDepositTxId = make(dayZeroTrade.but(with(tradeAmount, Coin.FIFTY_COINS)));

        listenerArgumentCaptor.getValue().onAdded(tradeWithDepositTxId);
        listenerArgumentCaptor.getValue().onAdded(tradeWithNullDepositTxId);

        assertTrue(manager.getObservableTradeStatisticsSet().contains(tradeWithDepositTxId));
        assertFalse(manager.getObservableTradeStatisticsSet().contains(tradeWithNullDepositTxId));
    }

    @Test
    public void addToSet_IgnoreObjectIfObjectWithNullDepositTxIdAlreadyExists() {
        TradeStatistics2 tradeWithDepositTxId = make(dayZeroTrade);

        listenerArgumentCaptor.getValue().onAdded(tradeWithNullDepositTxId);
        listenerArgumentCaptor.getValue().onAdded(tradeWithDepositTxId);

        assertTrue(manager.getObservableTradeStatisticsSet().contains(tradeWithNullDepositTxId));
        assertFalse(manager.getObservableTradeStatisticsSet().contains(tradeWithDepositTxId));
    }
}
