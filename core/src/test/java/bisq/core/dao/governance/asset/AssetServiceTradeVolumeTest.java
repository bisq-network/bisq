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

package bisq.core.dao.governance.asset;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssetServiceTradeVolumeTest {
    @Test
    void sumsTheAmountsAfterTheLookupDate() {
        List<AssetService.TradeAmountDateTuple> list = List.of(
                new AssetService.TradeAmountDateTuple(1_000, 10),
                new AssetService.TradeAmountDateTuple(2_000, 20),
                new AssetService.TradeAmountDateTuple(4_000, 30));

        assertEquals(6_000, AssetService.getTradeVolume(15, list));
        assertEquals(0, AssetService.getTradeVolume(15, null));
    }

    // Statistics dated before 2021-11-01 have no amount cap. A long sum of these amounts wraps to a negative volume,
    // which would show the asset as delisted.
    @Test
    void volumeBeyondTheLongRangeSaturates() {
        List<AssetService.TradeAmountDateTuple> list = List.of(
                new AssetService.TradeAmountDateTuple(Long.MAX_VALUE, 10),
                new AssetService.TradeAmountDateTuple(Long.MAX_VALUE, 20));

        assertEquals(Long.MAX_VALUE, AssetService.getTradeVolume(0, list));
    }
}
