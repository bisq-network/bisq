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

public class FujicoinTest extends AbstractAssetTest {

    public FujicoinTest() {
        super(new Fujicoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("FpEbvwxhmer2zSvqh61JtLiffu8Tk2abdo");
        assertValidAddress("7gcLWi78MFJ9akMzTAiug3uArvPch5LB6q");
        assertValidAddress("FrjN1LLWJj1DWVooBCdybBvmaEAqxMuuq8");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhem");
        assertInvalidAddress("FpEbvwxhmer2zSvqh61JtLiffu8Tk2abda");
        assertInvalidAddress("7gcLWi78MFJ9akMzTAiug3uArvPch5LB6a");
        assertInvalidAddress("fc1q3s2fc2xqgush29urtfdj0vhcj96h8424zyl6wa");
    }
}
