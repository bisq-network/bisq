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

public class DubaicoinTest extends AbstractAssetTest {

    public DubaicoinTest() {
        super(new Dubaicoin());
    }

	@Test
	public void testValidAddresses() {
		assertValidAddress("0xb0fbfba46458d04d1a9c1ad4943477cd4a6d5379");
		assertValidAddress("0x66d434D922516177aD0B2F4623F5D876cA1b5A0b");
		assertValidAddress("0x45394189EB1aB713338Fc0693342042f137C6DCc");		
		
	}

	@Test
	public void testInvalidAddresses() {
		assertInvalidAddress("MxmFPEPzF19JFPU3VPrRXvUbPjMQXnQerY");
		assertInvalidAddress("N22FRU9f3fx7Hty641D5cg95kRK6S3sbf3");
		assertInvalidAddress("MxmFPEPzF19JFPU3VPrRXvUbPjMQXnQerY");		
	}
}
