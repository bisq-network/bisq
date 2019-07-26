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

public class UnobtaniumTest extends AbstractAssetTest {

    public UnobtaniumTest() {
        super(new Unobtanium());
    }

	@Override
	public void testValidAddresses() {
		assertValidAddress("uXN2S9Soj4dSL7fPAuQi9twdaFmtwYndVP");
		assertValidAddress("uZymbhuxhfvxzc5EDdqRWrrZKvabZibBu1");
		assertValidAddress("uKdudT6DwojHYsBE9JWM43hRV28Rmp1Zm1");		
	}

	@Override
	public void testInvalidAddresses() {
		assertInvalidAddress("aHu897ivzmeFuLNB6956X6gyGeVNHUBRgD");
		assertInvalidAddress("a1HwTdCmQV3NspP2QqCGpehoFpi8NY4Zg3");
		assertInvalidAddress("aHu897ivzmeFuLNB6956X6gyGeVNHUBRgD");		
	}
}
