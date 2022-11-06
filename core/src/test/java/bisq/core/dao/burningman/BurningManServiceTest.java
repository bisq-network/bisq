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


import org.mockito.junit.MockitoJUnitRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class BurningManServiceTest {
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
}
