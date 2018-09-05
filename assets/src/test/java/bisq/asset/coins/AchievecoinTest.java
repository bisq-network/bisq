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

public class AchievecoinTest extends AbstractAssetTest {

    public AchievecoinTest() {
        super(new Achievecoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("AciV7ZyJDpCg7kGGmbo97VjgjpVZkXRTMD");
        assertValidAddress("ARhW8anWaZtExdK2cQkBwsvsQZ9TkC9bwH");
        assertValidAddress("AcxpGTWX4zFiD8p8hfYw99RXV7MY2y8hs9");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("GWaSW6PHfQKBv8EXV3xiqGG2zxKZh4XYNu");
        assertInvalidAddress("AcxpGTWX4zFiD8p8hfY099RXV7MY2y8hs9");
        assertInvalidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhem");
    }
}
