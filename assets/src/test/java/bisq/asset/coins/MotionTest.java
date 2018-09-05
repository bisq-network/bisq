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

public class MotionTest extends AbstractAssetTest {

    public MotionTest() {
        super(new Motion());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("MUQXDHrau4NbDJ93GcTP3q2JEb9dUpUz9n");
        assertValidAddress("MCi4PALzN9U2TL7UAQXJTEorJgYYKwzv7j");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("MidkVmR8GaT3oQthbm6jVxG5R7LnLVAvKwX");
        assertInvalidAddress("MidkVmR8GaT3oQthbm6jVxG5R7LnLVAvK");
        assertInvalidAddress("MidkVmR8GaT3oQthbm6jVxG5R7LnLVAvK#");
        assertInvalidAddress("XidkVmR8GaT3oQthbm6jVxG5R7LnLVAvKwX");
        assertInvalidAddress("yidkVmR8GaT3oQthbm6jVxG5R7LnLVAvKwX");
    }
}
