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

public class NxtTest extends AbstractAssetTest {

    public NxtTest() {
        super(new Nxt());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("NXT-JM2U-U4AE-G7WF-3NP9F");
        assertValidAddress("NXT-6UNJ-UMFM-Z525-4S24M");
        assertValidAddress("NXT-2223-2222-KB8Y-22222");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("abcde");
        assertInvalidAddress("NXT-");
        assertInvalidAddress("NXT-JM2U-U4AE-G7WF-3ND9F");
        assertInvalidAddress("NXT-JM2U-U4AE-G7WF-3Np9F");
        assertInvalidAddress("NXT-2222-2222-2222-22222");
    }
}
