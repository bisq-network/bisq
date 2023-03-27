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


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BurningManServiceTest {
    @Test
    public void testGetDecayedAmount() {
        long amount = 100;
        int currentBlockHeight = 1400;
        int fromBlockHeight = 1000;
        assertEquals(0, BurningManService.getDecayedAmount(amount, 1000, currentBlockHeight, fromBlockHeight));
        assertEquals(25, BurningManService.getDecayedAmount(amount, 1100, currentBlockHeight, fromBlockHeight));
        assertEquals(50, BurningManService.getDecayedAmount(amount, 1200, currentBlockHeight, fromBlockHeight));
        assertEquals(75, BurningManService.getDecayedAmount(amount, 1300, currentBlockHeight, fromBlockHeight));

        // cycles with 100 blocks, issuance at block 20, look-back period 3 cycles
        assertEquals(40, BurningManService.getDecayedAmount(amount, 120, 300, 0));
        assertEquals(33, BurningManService.getDecayedAmount(amount, 120, 320, 20));
        assertEquals(27, BurningManService.getDecayedAmount(amount, 120, 340, 40));
        assertEquals(20, BurningManService.getDecayedAmount(amount, 120, 360, 60));
        assertEquals(13, BurningManService.getDecayedAmount(amount, 120, 380, 80));
        assertEquals(7, BurningManService.getDecayedAmount(amount, 120, 399, 99));
        assertEquals(7, BurningManService.getDecayedAmount(amount, 120, 400, 100));
        assertEquals(3, BurningManService.getDecayedAmount(amount, 120, 410, 110));
        assertEquals(40, BurningManService.getDecayedAmount(amount, 220, 400, 100));

    }
}
