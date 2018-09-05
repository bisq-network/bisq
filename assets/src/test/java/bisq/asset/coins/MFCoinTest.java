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

public class MFCoinTest extends AbstractAssetTest {

    public MFCoinTest(){
        super(new MFCoin());
    }

    @Test
    public void testValidAddresses(){
        assertValidAddress("Mq7aPKf6xrttnB5so1UVpGMpkmbp7hc47r");
        assertValidAddress("MjQdB9QuDj12Mg5steMNyZzWSTBpSbf7nw");
        assertValidAddress("McFK2Tb4TRqzapbfZnwGGRbjGaRogRS8M6");
    }

    @Test
    public void testInvalidAddresses(){
        assertInvalidAddress("McFK2Tb4TRqzapbfZnwGGRbjGaRogRS8M");
        assertInvalidAddress("McFK2Tb4TRqzapbfZnwGGRbjGaRogRS8Mwqdwqdqwdqwdqwdwd");
        assertInvalidAddress("");
        assertInvalidAddress("McFK2Tb4TRqzapbfZnwGGRbjGaRogRS8MMMMMM");
        assertInvalidAddress("cFK2Tb4TRqzapbfZnwGGRbjGaRogRS8M");
        assertInvalidAddress("cFK2Tb4TRqzapbfZnwGGRbjGaRog8");
        assertInvalidAddress("McFK2Tb4TRqzapbfZnwGGRbjGaRogRS8M6wefweew");
        assertInvalidAddress("cFK2Tb4TRqzapbfZnwGGRbjGaRogRS8M6wefweew");
    }
}
