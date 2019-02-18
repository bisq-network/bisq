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

public class AskcoinTest extends AbstractAssetTest {

    public AskcoinTest() {
        super(new Askcoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("1");
        assertValidAddress("123");
        assertValidAddress("876982302333");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("0");
        assertInvalidAddress("038292");
        assertInvalidAddress("");
        assertInvalidAddress("000232320382");
        assertInvalidAddress("1298934567890");
        assertInvalidAddress("123abc5ab");
        assertInvalidAddress("null");
        assertInvalidAddress("xidjfwi23ii0");
    }
}