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

public class WaviTest extends AbstractAssetTest {

    public WaviTest() {
        super(new Wavi());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("WbdqBhmbkhYSYpjzKFuWRKhw7k1tN1oNrr");
        assertValidAddress("WU7HUe2RjxjKFWXmNVWyeYvByfWyefp8tZ");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("69dqBhmbkhYSYpjzKFuWRKhw7k1tN1oNrr");
        assertInvalidAddress("WbdqBhmbkhYSYpjzKFuWRKhw7k1tN1oNrr37");
        assertInvalidAddress("WU7HUe2RjxjKFWXmNVWyeYvByfWyefp869");
        assertInvalidAddress("Qc7HUe2RjxjKFWXmNVWyeYvByfWyefp8tZ");
    }
}
