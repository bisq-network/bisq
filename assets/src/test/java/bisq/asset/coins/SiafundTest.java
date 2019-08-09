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

public class SiafundTest extends AbstractAssetTest {

    public SiafundTest() {
        super(new Siafund());
    }

	@Override
	public void testValidAddresses() {
		assertValidAddress("949f35966a9b5f329f7419f91a02301b71b9f776568b2c767842af22b408eb8662203a02ec53");
		assertValidAddress("4daae3005456559972f4902217ee8394a890e2afede6f0b49015e5cfaecdcb13f466f5543346");
		assertValidAddress("da4f7fdc0fa047851a9860b09bc9b1e7424333c977e53a5d8aad74f5843a20b7cfa77a7794ae");		
		
	}

	@Override
	public void testInvalidAddresses() {
		assertInvalidAddress("MxmFPEPzF19JFPU3VPrRXvUbPjMQXnQerY");
		assertInvalidAddress("N22FRU9f3fx7Hty641D5cg95kRK6S3sbf3");
		assertInvalidAddress("MxmFPEPzF19JFPU3VPrRXvUbPjMQXnQerY");		
	}
}
