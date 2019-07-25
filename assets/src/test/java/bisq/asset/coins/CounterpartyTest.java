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
        super(new Counterparty.Mainnet());
    }

	@Override
	public void testValidAddresses() {
		// TODO Auto-generated method stub
		assertValidAddress("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa");
		assertValidAddress("111111111112ZkE8UvCp8JCNRhfuBoo");
		assertValidAddress("1234567891234567891234567891wBd7RE");		
	}

	@Override
	public void testInvalidAddresses() {
		assertValidAddress("MxmFPEPzF19JFPU3VPrRXvUbPjMQXnQerY");
		assertValidAddress("N22FRU9f3fx7Hty641D5cg95kRK6S3sbf3");
		assertValidAddress("MxmFPEPzF19JFPU3VPrRXvUbPjMQXnQerY");		
	}
}
