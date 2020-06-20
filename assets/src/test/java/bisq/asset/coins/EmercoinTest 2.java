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

public class EmercoinTest extends AbstractAssetTest {

    public EmercoinTest() {
        super(new Emercoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("EedXjU95QcVHLEFAs5EKNT9UWqAWXTyuhD"); // Regular p2pkh address
        assertValidAddress("EHNiED27Un5yKHHsGFDsQipCH4TdsTo5xb"); // Regular p2pkh address
        assertValidAddress("eMERCoinFunera1AddressXXXXXXYDAYke"); // Dummy p2sh address
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("19rem1SSWTphjsFLmcNEAvnfHaBFuDMMae");  // Valid BTC
        assertInvalidAddress("EedXjU95QcVHLEFAs5EKNT9UWqAWXTyuhE");  // Invalid EMC address
        assertInvalidAddress("DDWUYQ3GfMDj8hkx8cbnAMYkTzzAunAQxg");  // Valid DOGE

    }
}
