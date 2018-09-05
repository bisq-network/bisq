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

public class ByteballTest extends AbstractAssetTest {

    public ByteballTest() {
        super(new Byteball());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("BN7JXKXWEG4BVJ7NW6Q3Z7SMJNZJYM3G");
        assertValidAddress("XGKZODTTTRXIUA75TKONWHFDCU6634DE");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("XGKZODTGTRXIUA75TKONWHFDCU6634DE");
        assertInvalidAddress("XGKZODTTTRXIUA75TKONWHFDCU6634D");
        assertInvalidAddress("XGKZODTTTRXIUA75TKONWHFDCU6634DZ");
    }
}
