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

package bisq.core.dao.burningman;


import com.google.common.primitives.Longs;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BtcFeeReceiverServiceTest {
    @Test
    public void testGetRandomIndex() {
        Random rnd = new Random(456);
        assertEquals(4, BtcFeeReceiverService.getRandomIndex(Longs.asList(0, 0, 0, 3, 3), rnd));
        assertEquals(3, BtcFeeReceiverService.getRandomIndex(Longs.asList(0, 0, 0, 6, 0, 0, 0, 0, 0), rnd));

        assertEquals(-1, BtcFeeReceiverService.getRandomIndex(Longs.asList(), rnd));
        assertEquals(-1, BtcFeeReceiverService.getRandomIndex(Longs.asList(0), rnd));
        assertEquals(-1, BtcFeeReceiverService.getRandomIndex(Longs.asList(0, 0), rnd));

        int[] selections = new int[3];
        for (int i = 0; i < 6000; i++) {
            selections[BtcFeeReceiverService.getRandomIndex(Longs.asList(1, 2, 3), rnd)]++;
        }
        // selections with new Random(456) are: [986, 1981, 3033]
        assertEquals(1000.0, selections[0], 100);
        assertEquals(2000.0, selections[1], 100);
        assertEquals(3000.0, selections[2], 100);
    }

    @Test
    public void testFindIndex() {
        List<Long> weights = Longs.asList(1, 2, 3);
        assertEquals(0, BtcFeeReceiverService.findIndex(weights, 1));
        assertEquals(1, BtcFeeReceiverService.findIndex(weights, 2));
        assertEquals(1, BtcFeeReceiverService.findIndex(weights, 3));
        assertEquals(2, BtcFeeReceiverService.findIndex(weights, 4));
        assertEquals(2, BtcFeeReceiverService.findIndex(weights, 5));
        assertEquals(2, BtcFeeReceiverService.findIndex(weights, 6));

        // invalid values return index 0
        assertEquals(0, BtcFeeReceiverService.findIndex(weights, 0));
        assertEquals(0, BtcFeeReceiverService.findIndex(weights, 7));

        assertEquals(0, BtcFeeReceiverService.findIndex(Longs.asList(0, 1, 2, 3), 0));
        assertEquals(0, BtcFeeReceiverService.findIndex(Longs.asList(1, 2, 3), 0));
        assertEquals(0, BtcFeeReceiverService.findIndex(Longs.asList(1, 2, 3), 1));
        assertEquals(1, BtcFeeReceiverService.findIndex(Longs.asList(0, 1, 2, 3), 1));
        assertEquals(2, BtcFeeReceiverService.findIndex(Longs.asList(0, 1, 2, 3), 2));
        assertEquals(1, BtcFeeReceiverService.findIndex(Longs.asList(0, 1, 0, 2, 3), 1));
        assertEquals(3, BtcFeeReceiverService.findIndex(Longs.asList(0, 1, 0, 2, 3), 2));
        assertEquals(3, BtcFeeReceiverService.findIndex(Longs.asList(0, 0, 0, 1, 2, 3), 1));
        assertEquals(4, BtcFeeReceiverService.findIndex(Longs.asList(0, 0, 0, 1, 2, 3), 2));
        assertEquals(6, BtcFeeReceiverService.findIndex(Longs.asList(0, 0, 0, 1, 0, 0, 2, 3), 2));
    }
}
