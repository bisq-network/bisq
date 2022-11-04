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

import org.mockito.junit.MockitoJUnitRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class BurningManServiceTest {

    @Test
    public void testGetSnapshotHeight() {
        assertEquals(120, BurningManService.getSnapshotHeight(102, 0, 10));
        assertEquals(120, BurningManService.getSnapshotHeight(102, 100, 10));
        assertEquals(120, BurningManService.getSnapshotHeight(102, 102, 10));
        assertEquals(120, BurningManService.getSnapshotHeight(102, 119, 10));
        assertEquals(120, BurningManService.getSnapshotHeight(102, 120, 10));
        assertEquals(120, BurningManService.getSnapshotHeight(102, 121, 10));
        assertEquals(120, BurningManService.getSnapshotHeight(102, 130, 10));
        assertEquals(120, BurningManService.getSnapshotHeight(102, 139, 10));
        assertEquals(130, BurningManService.getSnapshotHeight(102, 140, 10));
        assertEquals(130, BurningManService.getSnapshotHeight(102, 141, 10));
        assertEquals(990, BurningManService.getSnapshotHeight(102, 1000, 10));
    }

    @Test
    public void testGetDecayedAmount() {
        long amount = 100;
        int currentBlockHeight = 1400;
        int genesisBlockHeight = 1000;
        assertEquals(0, BurningManService.getDecayedAmount(amount, 1000, currentBlockHeight, genesisBlockHeight, 0));
        assertEquals(25, BurningManService.getDecayedAmount(amount, 1100, currentBlockHeight, genesisBlockHeight, 0));
        assertEquals(50, BurningManService.getDecayedAmount(amount, 1200, currentBlockHeight, genesisBlockHeight, 0));
        assertEquals(75, BurningManService.getDecayedAmount(amount, 1300, currentBlockHeight, genesisBlockHeight, 0));

        // let genesis have an offset. e.g. 0.5 means an amount at genesis has 50% decay
        assertEquals(50, BurningManService.getDecayedAmount(amount, 1000, currentBlockHeight, genesisBlockHeight, 0.5));
        assertEquals(75, BurningManService.getDecayedAmount(amount, 1200, currentBlockHeight, genesisBlockHeight, 0.5));
        assertEquals(100, BurningManService.getDecayedAmount(amount, 1400, currentBlockHeight, genesisBlockHeight, 0.5));

        assertEquals(50, BurningManService.getDecayedAmount(amount, 1200, currentBlockHeight, genesisBlockHeight, 0));
        assertEquals(75, BurningManService.getDecayedAmount(amount, 1200, currentBlockHeight, genesisBlockHeight, 0.5));
        assertEquals(63, BurningManService.getDecayedAmount(amount, 1200, currentBlockHeight, genesisBlockHeight, 0.25));
        assertEquals(88, BurningManService.getDecayedAmount(amount, 1200, currentBlockHeight, genesisBlockHeight, 0.75));

        assertEquals(100, BurningManService.getDecayedAmount(amount, 1000, currentBlockHeight, genesisBlockHeight, 1));
        assertEquals(100, BurningManService.getDecayedAmount(amount, 1200, currentBlockHeight, genesisBlockHeight, 1));
        assertEquals(100, BurningManService.getDecayedAmount(amount, 1400, currentBlockHeight, genesisBlockHeight, 1));
    }

    @Test
    public void testGetRandomIndex() {
        Random rnd = new Random(456);
        assertEquals(4, BurningManService.getRandomIndex(Longs.asList(0, 0, 0, 3, 3), rnd));
        assertEquals(3, BurningManService.getRandomIndex(Longs.asList(0, 0, 0, 6, 0, 0, 0, 0, 0), rnd));

        assertEquals(-1, BurningManService.getRandomIndex(Longs.asList(), rnd));
        assertEquals(-1, BurningManService.getRandomIndex(Longs.asList(0), rnd));
        assertEquals(-1, BurningManService.getRandomIndex(Longs.asList(0, 0), rnd));

        int[] selections = new int[3];
        for (int i = 0; i < 6000; i++) {
            selections[BurningManService.getRandomIndex(Longs.asList(1, 2, 3), rnd)]++;
        }
        // selections with new Random(456) are: [986, 1981, 3033]
        assertEquals(1000.0, selections[0], 100);
        assertEquals(2000.0, selections[1], 100);
        assertEquals(3000.0, selections[2], 100);
    }

    @Test
    public void testFindIndex() {
        List<Long> weights = Longs.asList(1, 2, 3);
        assertEquals(0, BurningManService.findIndex(weights, 1));
        assertEquals(1, BurningManService.findIndex(weights, 2));
        assertEquals(1, BurningManService.findIndex(weights, 3));
        assertEquals(2, BurningManService.findIndex(weights, 4));
        assertEquals(2, BurningManService.findIndex(weights, 5));
        assertEquals(2, BurningManService.findIndex(weights, 6));

        // invalid values return index 0
        assertEquals(0, BurningManService.findIndex(weights, 0));
        assertEquals(0, BurningManService.findIndex(weights, 7));

        assertEquals(0, BurningManService.findIndex(Longs.asList(0, 1, 2, 3), 0));
        assertEquals(0, BurningManService.findIndex(Longs.asList(1, 2, 3), 0));
        assertEquals(0, BurningManService.findIndex(Longs.asList(1, 2, 3), 1));
        assertEquals(1, BurningManService.findIndex(Longs.asList(0, 1, 2, 3), 1));
        assertEquals(2, BurningManService.findIndex(Longs.asList(0, 1, 2, 3), 2));
        assertEquals(1, BurningManService.findIndex(Longs.asList(0, 1, 0, 2, 3), 1));
        assertEquals(3, BurningManService.findIndex(Longs.asList(0, 1, 0, 2, 3), 2));
        assertEquals(3, BurningManService.findIndex(Longs.asList(0, 0, 0, 1, 2, 3), 1));
        assertEquals(4, BurningManService.findIndex(Longs.asList(0, 0, 0, 1, 2, 3), 2));
        assertEquals(6, BurningManService.findIndex(Longs.asList(0, 0, 0, 1, 0, 0, 2, 3), 2));
    }
}
