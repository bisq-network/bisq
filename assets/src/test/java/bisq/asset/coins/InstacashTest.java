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

public class InstacashTest extends AbstractAssetTest {

    public InstacashTest() {
        super(new Instacash());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("AYx4EqKhomeMu2CTMx1AHdNMkjv6ygnvji");
        assertValidAddress("AcWyvE7texXcCsPLvW1btXhLimrDMpNdAu");
        assertValidAddress("AMfLeLotcvgaHQW374NmHZgs1qXF8P6kjc");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("aYzyJYqhnxF738QjqMqTku5Wft7x4GhVCr");
        assertInvalidAddress("DYzyJYqhnxF738QjqMqTku5Wft7x4GhVCr");
        assertInvalidAddress("xYzyJYqhnxF738QjqMqTku5Wft7x4GhVCr");
        assertInvalidAddress("1YzyJYqhnxF738QjqMqTku5Wft7x4GhVCr");
        assertInvalidAddress(
                "AYzyJYqhnxF738QjqMqTku5Wft7x4GhVCr5vcz2NZLUDsoXGp5rAFUjKnb7DdkFbLp7aSpejCcC4FTxsVvDxq9YKSprzf");
    }
}
