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

public class BurningManPresentationServiceTest {
    @Test
    public void testGetRandomIndex() {
        long total = 100;
        long myAmount = 40;
        double myTargetShare = 0.75;
        // Initial state:
        // Mine: 40
        // Others: 60
        // Total: 100
        // Current myShare: 0.4

        // Target state:
        // Mine: 40 + 140 = 180
        // Others: 60
        // Total: 240
        // Target myShare: 0.75

        assertEquals(140, BurningManPresentationService.getMissingAmountToReachTargetShare(total, myAmount, myTargetShare));

        total = 60;
        myAmount = 0;
        myTargetShare = 0.4;
        // Initial state:
        // Mine: 0
        // Others: 60
        // Total: 60
        // Current myShare: 0

        // Target state:
        // Mine: 0 + 40 = 40
        // Others: 60
        // Total: 100
        // Target myShare: 0.4

        assertEquals(40, BurningManPresentationService.getMissingAmountToReachTargetShare(total, myAmount, myTargetShare));

        total = 40;
        myAmount = 40;
        myTargetShare = 1;
        // Initial state:
        // Mine: 40
        // Others: 0
        // Total: 40
        // Current myShare: 1

        // Target state:
        // Mine: 40 -40 = 0
        // Others: 0
        // Total: 0
        // Target myShare: 1

        assertEquals(-40, BurningManPresentationService.getMissingAmountToReachTargetShare(total, myAmount, myTargetShare));

        total = 100;
        myAmount = 99;
        myTargetShare = 0;
        // Initial state:
        // Mine: 99
        // Others: 1
        // Total: 100
        // Current myShare: 0.99

        // Target state:
        // Mine: 99 - 99 = 0
        // Others: 1
        // Total: 100
        // Target myShare: 0

        assertEquals(-99, BurningManPresentationService.getMissingAmountToReachTargetShare(total, myAmount, myTargetShare));

        total = 100;
        myAmount = 1;
        myTargetShare = 0.5;
        // Initial state:
        // Mine: 1
        // Others: 99
        // Total: 100
        // Current myShare: 0.01

        // Target state:
        // Mine: 1 + 98 = 99
        // Others: 99
        // Total: 198
        // Target myShare: 0.5

        assertEquals(98, BurningManPresentationService.getMissingAmountToReachTargetShare(total, myAmount, myTargetShare));


        total = 110;
        myAmount = 0;
        myTargetShare = 0.6;
        // Initial state:
        // Mine: 0
        // Others: 110
        // Total: 110
        // Current myShare: 0

        // Target state:
        // Mine: 165
        // Others: 110
        // Total: 275
        // Target myShare: 0.6

        assertEquals(165, BurningManPresentationService.getMissingAmountToReachTargetShare(total, myAmount, myTargetShare));
    }
}
