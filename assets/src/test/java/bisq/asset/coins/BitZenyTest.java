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

public class BitZenyTest extends AbstractAssetTest {

    public BitZenyTest() {
        super(new BitZeny());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("ZryVTPGwpWMrWiqBhcje9NJJku3RgUmVrH");
        assertValidAddress("3CB2kwzn245gaCSfkc7wbe2Myq2kDuQmpu");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("ZryVTPGwpWMrWiqBhcje9NJJku3RgUmVrHH");
        assertInvalidAddress("ZryVTPGwpWMrWiqBhcje9NJJku3RgUmVr");
        assertInvalidAddress("3CB2kwzn245gaCSfkc7wbe2Myq2kDuQmpu#");
    }
}
