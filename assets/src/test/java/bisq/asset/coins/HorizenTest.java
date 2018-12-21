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

public class HorizenTest extends AbstractAssetTest {

    public HorizenTest() {
        super(new Horizen());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("znk62Ey7ptTyHgYLaLDTEwhLF6uN1DXTBfa");
        assertValidAddress("znTqzi5rTXf6KJnX5tLaC5CMGHfeWJwy1c7");
        assertValidAddress("t1V9h2P9n4sYg629Xn4jVDPySJJxGmPb1HK");
        assertValidAddress("t3Ut4KUq2ZSMTPNE67pBU5LqYCi2q36KpXQ");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("zcKffBrza1cirFY47aKvXiV411NZMscf7zUY5bD1HwvkoQvKHgpxLYUHtMCLqBAeif1VwHmMjrMAKNrdCknCVqCzRNizHUq");
        assertInvalidAddress("AFTqzi5rTXf6KJnX5tLaC5CMGHfeWJwy1c7");
        assertInvalidAddress("zig-zag");
        assertInvalidAddress("0123456789");
    }
}
