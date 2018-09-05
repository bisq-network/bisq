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

public class NimiqTest extends AbstractAssetTest {
    public NimiqTest() {
        super(new Nimiq());
    }

    @Override
    public void testValidAddresses() {
        assertValidAddress("NQ09 VF5Y 1PKV MRM4 5LE1 55KV P6R2 GXYJ XYQF");
        assertValidAddress("NQ19YG5446TXEHGQD2R2V8XAJX84UFG0S0MC");

        // Null address (burn)
        assertValidAddress("NQ07 0000 0000 0000 0000 0000 0000 0000 0000");
    }

    @Override
    public void testInvalidAddresses() {
        // plain wrong
        assertInvalidAddress("0xcfb98637bcae43c13323eaa1731ced2b716962fd");
        assertInvalidAddress("12c6DSiU4Rq3P4ZxziKxzrL5LmMBrzjrJX");

        // invalid chars
        assertInvalidAddress("NQ62 VF5Y 1PKV MRM4 5LE1 55KV P6R2 GXYI XYQF");

        // too short
        assertInvalidAddress("NQ07 0000 0000 0000 0000 0000 0000 0000 000");
        assertInvalidAddress("FR76 3000 4003 2000 0101 9471 656");

        // not NQ
        assertInvalidAddress("US35 0000 0000 0000 0000 0000 0000 0000 0000");
        assertInvalidAddress("US37VF5Y1PKVMRM45LE155KVP6R2GXYJXYQF");

        // invalid checksum
        assertInvalidAddress("NQ08 0000 0000 0000 0000 0000 0000 0000 0000");
        assertInvalidAddress("NQ08 VF5Y 1PKV MRM4 5LE1 55KV P6R2 GXYJ XYQF");
        assertInvalidAddress("NQ18YG5446TXEHGQD2R2V8XAJX84UFG0S0MC");
    }
}
