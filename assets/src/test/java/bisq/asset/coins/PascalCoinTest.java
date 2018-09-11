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

import org.junit.Test;
import bisq.asset.AbstractAssetTest;

public class PascalCoinTest extends AbstractAssetTest {

    public PascalCoinTest() {
        super(new PascalCoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("0-10");
        assertValidAddress("1-22");

        assertValidAddress("1");
        assertValidAddress("0");

        assertValidAddress("3532-30");
        assertValidAddress("3532");

        assertValidAddress("507932-29");
        assertValidAddress("1189514-91");
        assertValidAddress("1189514");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("-1");
        assertInvalidAddress("AS-56");
        assertInvalidAddress("0-1");
        assertInvalidAddress("-1");
        assertInvalidAddress("0-100");
    }
}
