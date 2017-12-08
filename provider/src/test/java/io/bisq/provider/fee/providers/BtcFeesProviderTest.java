/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.provider.fee.providers;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BtcFeesProviderTest {

    @Test
    public void testGetAverage() {
        BtcFeesProvider btcFeesProvider = new BtcFeesProvider(BtcFeesProvider.CAPACITY, BtcFeesProvider.MAX_BLOCKS);

        assertEquals(0, btcFeesProvider.getAverage(0));

        btcFeesProvider = new BtcFeesProvider(BtcFeesProvider.CAPACITY, BtcFeesProvider.MAX_BLOCKS);
        assertEquals(1, btcFeesProvider.getAverage(1));

        btcFeesProvider = new BtcFeesProvider(BtcFeesProvider.CAPACITY, BtcFeesProvider.MAX_BLOCKS);
        assertEquals(0, btcFeesProvider.getAverage(0));
        assertEquals(0, btcFeesProvider.getAverage(1));

        btcFeesProvider = new BtcFeesProvider(BtcFeesProvider.CAPACITY, BtcFeesProvider.MAX_BLOCKS);
        assertEquals(0, btcFeesProvider.getAverage(0));
        assertEquals(1, btcFeesProvider.getAverage(2));

        BtcFeesProvider.CAPACITY = 5;
        btcFeesProvider = new BtcFeesProvider(BtcFeesProvider.CAPACITY, BtcFeesProvider.MAX_BLOCKS);
        assertEquals(10, btcFeesProvider.getAverage(10));
        assertEquals(15, btcFeesProvider.getAverage(20));
        assertEquals(20, btcFeesProvider.getAverage(30));
        assertEquals(20, btcFeesProvider.getAverage(20));
        assertEquals(20, btcFeesProvider.getAverage(20)); //5th

        assertEquals(22, btcFeesProvider.getAverage(20)); // first removed
        assertEquals(28, btcFeesProvider.getAverage(50)); // second removed
        assertEquals(30, btcFeesProvider.getAverage(40)); // third removed
    }
}
