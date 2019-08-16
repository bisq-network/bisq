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

public class NoirTest extends AbstractAssetTest {

    public NoirTest() {
        super(new Noir());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("ZMZ6M64FiFjPjmzXRf7xBuyarorUmT8uyG");
        assertValidAddress("ZHoMM3vccwGrAQocmmp9ZHA7Gjg9Uqkok7");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("");
        assertInvalidAddress("21HQQgsvLTgN9xD9hNmAgAreakzVzQUSLSHa");
        assertInvalidAddress("ZHoMM3vccwGrAQocmmp9ZHA7Gjg9Uqkok7*");
        assertInvalidAddress("ZHoMM3vccwGrAQocmmp9ZHA7Gjg9Uqkok7#jHt5jtP");
    }
}
