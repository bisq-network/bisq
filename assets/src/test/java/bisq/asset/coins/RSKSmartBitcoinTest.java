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

public class RSKSmartBitcoinTest extends AbstractAssetTest {

    public RSKSmartBitcoinTest() {
        super(new RSKSmartBitcoin());
    }

    @Override
    public void testValidAddresses() {
        assertValidAddress("0x353c13b940aa5eed75aa97d477954289e7880bb8");
        assertValidAddress("0x9f5304DA62A5408416Ea58A17a92611019bD5ce3");
        assertValidAddress("0x180826b05452ce96E157F0708c43381Fee64a6B8");
    }

    @Override
    public void testInvalidAddresses() {
        assertInvalidAddress("MxmFPEPzF19JFPU3VPrRXvUbPjMQXnQerY");
        assertInvalidAddress("N22FRU9f3fx7Hty641D5cg95kRK6S3sbf3");
        assertInvalidAddress("MxmFPEPzF19JFPU3VPrRXvUbPjMQXnQerY");
    }
}
