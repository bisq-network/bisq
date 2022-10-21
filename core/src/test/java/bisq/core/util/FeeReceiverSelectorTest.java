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

package bisq.core.util;

import com.google.common.primitives.Longs;

import java.util.List;
import java.util.Random;

import org.mockito.junit.MockitoJUnitRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class FeeReceiverSelectorTest {
    @Test
    public void testGetRandomIndex() {
        Random rnd = new Random(456);
        int[] selections = new int[3];
        for (int i = 0; i < 6000; i++) {
            selections[FeeReceiverSelector.getRandomIndex(Longs.asList(1, 2, 3), rnd)]++;
        }
        // selections with new Random(456) are: [986, 1981, 3033]
        assertEquals(1000.0, selections[0], 100);
        assertEquals(2000.0, selections[1], 100);
        assertEquals(3000.0, selections[2], 100);
    }

    @Test
    public void testFindIndex() {
        List<Long> weights = Longs.asList(1, 2, 3);
        assertEquals(0, FeeReceiverSelector.findIndex(weights, 1));
        assertEquals(1, FeeReceiverSelector.findIndex(weights, 2));
        assertEquals(1, FeeReceiverSelector.findIndex(weights, 3));
        assertEquals(2, FeeReceiverSelector.findIndex(weights, 4));
        assertEquals(2, FeeReceiverSelector.findIndex(weights, 5));
        assertEquals(2, FeeReceiverSelector.findIndex(weights, 6));

        // invalid values return index 0
        assertEquals(0, FeeReceiverSelector.findIndex(weights, 0));
        assertEquals(0, FeeReceiverSelector.findIndex(weights, 7));
    }
}
