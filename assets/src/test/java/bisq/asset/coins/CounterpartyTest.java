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

public class CounterpartyTest extends AbstractAssetTest {

    public CounterpartyTest() {
        super(new Counterparty());
    }

	@Override
	public void testValidAddresses() {
		assertValidAddress("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa");
		assertValidAddress("1KBbojKRf1YnJKp1YK5eEz9TWlS4pFEbwS");
		assertValidAddress("1AtLN6BMlW0Rwj800LNcBBR2o0k0sYVuIN");		
	}

	@Override
	public void testInvalidAddresses() {
		assertInvalidAddress("MxmFPEPzF19JFPU3VPrRXvUbPjMQXnQerY");
		assertInvalidAddress("122FRU9f3fx7Hty641DRK6S3sbf3");
		assertInvalidAddress("MxmFPEPzF19JFPU3VPrRXvUbPjMQXnQerY");		
	}
}
