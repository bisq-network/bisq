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

public class OctocoinTest extends AbstractAssetTest {

    public OctocoinTest() {
        super(new Octocoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("8TP9rh3SH6n9cSLmV22vnSNNw56LKGpLra");
        assertValidAddress("37NwrYsD1HxQW5zfLTuQcUUXGMPvQgzTSn");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("1ANNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62i");
        assertInvalidAddress("38NwrYsD1HxQW5zfLT0QcUUXGMPvQgzTSn");
        assertInvalidAddress("8tP9rh3SH6n9cSLmV22vnSNNw56LKGpLrB");
        assertInvalidAddress("8Zbvjr");
    }
}
