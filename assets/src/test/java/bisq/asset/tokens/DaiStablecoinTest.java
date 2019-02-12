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

public class DaiStablecoinTest extends AbstractAssetTest {

    public DaiStablecoinTest() {
        super(new DaiStablecoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("0x2a65Aca4D5fC5B5C859090a6c34d164135398226");
        assertValidAddress("2a65Aca4D5fC5B5C859090a6c34d164135398226");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("0x2a65Aca4D5fC5B5C859090a6c34d1641353982266");
        assertInvalidAddress("0x2a65Aca4D5fC5B5C859090a6c34d16413539822g");
        assertInvalidAddress("2a65Aca4D5fC5B5C859090a6c34d16413539822g");
    }
}
