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

public class ZcoinTest extends AbstractAssetTest {

    public ZcoinTest() {
        super(new Zcoin());
    }

	@Override
	public void testValidAddresses() {
		assertValidAddress("aHu897ivzmeFuLNB6956X6gyGeVNHUBRgD");
		assertValidAddress("a1HwTdCmQV3NspP2QqCGpehoFpi8NY4Zg3");
		assertValidAddress("aHu897ivzmeFuLNB6956X6gyGeVNHUBRgD");		
	}

	@Override
	public void testInvalidAddresses() {
		assertInvalidAddress("MxmFPEPzF19JFPU3VPrRXvUbPjMQXnQerY");
		assertInvalidAddress("N22FRU9f3fx7Hty641D5cg95kRK6S3sbf3");
		assertInvalidAddress("MxmFPEPzF19JFPU3VPrRXvUbPjMQXnQerY");		
	}
}
