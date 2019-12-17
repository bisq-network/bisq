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

public class MogwaicoinTest extends AbstractAssetTest {

    public MogwaicoinTest() {
        super(new Mogwaicoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("MFCFYwNnHcyxwBgJkioCRjURU2hjugG99R");
        assertValidAddress("MLh6N4sDZBjbMbQYVDKRiCk8szbuvTrVEt");
        assertValidAddress("M9pS7XLU3rWRmJB6aWxT8SYp66h95H1Q6F");

    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("MXnaJzoAKTNa67Fpt1tLxD5bFMcyN4tCvTT");
        assertInvalidAddress("MnaJzoAKTNa67Fpt1tLxD5bFMcyN4tCvTTd");
        assertInvalidAddress("MnaJzoAKTNa67Fpt1tLxD5bFMcyN4tCvTT#");
    }
}
