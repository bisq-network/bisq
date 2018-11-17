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

package bisq.asset.tokens;

import bisq.asset.AbstractAssetTest;

import org.junit.Test;

public class GoDigitTest extends AbstractAssetTest {

    public GoDigitTest() {
        super(new GoDigit());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("0x1d0582fa759e4b9beab4f6f82cc539ac62c49250");
        assertValidAddress("1d0582fa759e4b9beab4f6f82cc539ac62c49250");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("0x1d0582fa759e4b9beab4f6f82cc539ac62c492500");
        assertInvalidAddress("0x1d0582fa759e4b9beab4f6f82cc539ac62c4925g");
        assertInvalidAddress("1d0582fa759e4b9beab4f6f82cc539ac62c4925g");
    }
}
