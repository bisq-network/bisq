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

public class SUB1XTest extends AbstractAssetTest {

    public SUB1XTest() {
        super(new SUB1X());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("ZDxdoVuyosZ6vY3LZAP1Z4H4eXMq2ZpLH7");
        assertValidAddress("ZKi6EksPCZoMi6EGXS9vWVed4NeSov2ZS4");
        assertValidAddress("ZT29B3yDJq1jzkCTBs4LnraM3E854MAPRm");
        assertValidAddress("ZZeaSimQwza3CkFWTrRPQDamZcbntf2uMG");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("zKi6EksPCZoMi6EGXS9vWVed4NeSov2ZS4");
        assertInvalidAddress("ZDxdoVuyosZ6vY3LZAP1Z4H4eXMq2ZpAC7");
        assertInvalidAddress("ZKi6EksPCZoMi6EGXS9vWVedqwfov2ZS4");
        assertInvalidAddress("ZT29B3yDJq1jzkqwrwBs4LnraM3E854MAPRm");
        assertInvalidAddress("ZZeaSimQwza3CkFWTqwrfQDamZcbntf2uMG");
        assertInvalidAddress("Z23t23f");
        assertInvalidAddress("ZZeaSimQwza3CkFWTrRPQDavZcbnta2uMGA");
    }
}
