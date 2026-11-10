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

package bisq.desktop.main.dao.economy.dashboard.volume;

import bisq.core.monetary.Price;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.trade.statistics.TradeStatistics3;
import bisq.core.trade.statistics.TradeStatisticsManager;

import javafx.collections.FXCollections;

import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VolumeChartDataModelTest {
    // Statistics dated before 2021-11-01 have no amount cap. A long sum of these amounts wraps to a negative volume.
    @Test
    void btcVolumeBeyondTheLongRangeSaturates() {
        long date = 1_600_000_000_000L;
        Set<TradeStatistics3> set = Set.of(usdStatistics(Long.MAX_VALUE, date),
                usdStatistics(Long.MAX_VALUE, date + TimeUnit.DAYS.toMillis(100)));
        set.forEach(tradeStatistics -> assertTrue(tradeStatistics.isValid()));

        VolumeChartDataModel model = model(set);

        assertEquals(Set.of(Long.MAX_VALUE), Set.copyOf(model.getBtcVolumeByInterval().values()));
        assertEquals(Long.MAX_VALUE, model.getBtcVolume());
    }

    // Positive control
    @Test
    void btcVolumeIsTheSumOfTheAmounts() {
        long date = 1_600_000_000_000L;
        VolumeChartDataModel model = model(Set.of(usdStatistics(100_000_000, date),
                usdStatistics(50_000_000, date + 1)));

        assertEquals(150_000_000, model.getBtcVolume());
    }

    private static VolumeChartDataModel model(Set<TradeStatistics3> set) {
        TradeStatisticsManager tradeStatisticsManager = mock(TradeStatisticsManager.class);
        when(tradeStatisticsManager.getObservableTradeStatisticsSet()).thenReturn(FXCollections.observableSet(set));
        return new VolumeChartDataModel(tradeStatisticsManager);
    }

    private static TradeStatistics3 usdStatistics(long amount, long date) {
        return new TradeStatistics3("USD", Price.parse("USD", "1").getValue(), amount,
                PaymentMethod.SEPA_ID, date, null, null, (TreeMap<String, String>) null);
    }
}
