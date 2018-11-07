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

public class DineroTest extends AbstractAssetTest {

    public DineroTest() {
        super(new Dinero());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("DBmvak2TM8GpeiR3ZEVWAHWFZeiw9FG7jK");
        assertValidAddress("DDWit1CcocL2j3CzfmZgz4bx2DE1h8tugv");
        assertValidAddress("DF8D75bjz6i8azUHgmbV3awpn6tni5W43B");
        assertValidAddress("DJquenkkiFVNpF7vVLg2xKnxCjKwnYb6Ay");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("1QbFeFc3iqRYhemqq7VZNX1SN5NtKa8UQFxw");
        assertInvalidAddress("7rrpfJKZC7t1R2FPKrsvfkcE8KBLuSyVYAjt");
        assertInvalidAddress("QFxwQbFeFc3iqRYhek");
    }
}
