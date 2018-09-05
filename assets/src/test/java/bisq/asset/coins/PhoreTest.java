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

package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;

import org.junit.Test;

public class PhoreTest extends AbstractAssetTest {

    public PhoreTest() {
        super(new Phore());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("PJCKDPyvfbf1yV7mYNeJ8Zb47hKRwVPYDj");
        assertValidAddress("PJPmiib7JzMDiMQBBFCz92erB8iUvJqBqt");
        assertValidAddress("PS6yeJnJUD2pe9fpDQvtm4KkLDwCWpa8ub");
        assertValidAddress("PKfuRcjwzKFq3dbqE9gq8Ztxn922W4GZhm");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("pGXsgFjSMzh1dSqggRvHjPvE3cnwvuXC7s");
        assertInvalidAddress("PKfRRcjwzKFq3dbqE9gq8Ztxn922W4GZhm");
        assertInvalidAddress("PXP75NnwDryYswQb9RaPFBchqLRSvBmDP");
        assertInvalidAddress("PKr3vQ7SkqLELsYGM6qeRumyfPx3366uyU9");
        assertInvalidAddress("PKr3vQ7S");
        assertInvalidAddress("P0r3vQ7SkqLELsYGM6qeRumyfPx3366uyU9");
    }
}
