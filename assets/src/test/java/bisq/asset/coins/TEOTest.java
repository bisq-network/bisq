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

public class TEOTest extends AbstractAssetTest {

    public TEOTest() {
        super(new TEO());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("0x8d1ba0497c3e3db17143604ab7f5e93a3cbac68b");
        assertValidAddress("0x23c9c5ae8c854e9634a610af82924a5366a360a3");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("");
        assertInvalidAddress("8d1ba0497c3e3db17143604ab7f5e93a3cbac68b");
        assertInvalidAddress("0x8d1ba0497c3e3db17143604ab7f5e93a3cbac68");
        assertInvalidAddress("0x8d1ba0497c3e3db17143604ab7f5e93a3cbac68k");
        assertInvalidAddress("098d1ba0497c3e3db17143604ab7f5e93a3cbac68b");
        assertInvalidAddress("098d1ba0497c3e3db17143604ab7f5e93a3cbac68b");
    }
}
