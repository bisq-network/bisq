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

public class SiaPrimeCoinTest extends AbstractAssetTest {

    public SiaPrimeCoinTest() {
        super(new SiaPrimeCoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("d9fe1331ed2ae1bbdfe0e2942e84d74b7310648e5a5f14c4980ec2c6a19f08af6894b9060e83");
        assertValidAddress("205cf3be0f04397ee6cc1f52d8ae47f589a4ef5936949c158b2555df291efb87db2bbbca2031");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("");
        assertInvalidAddress("205cf3be0f04397ee6cc1f52d8ae47f589a4ef5936949c158b2555df291efb87db2bbbca20311");
        assertInvalidAddress("205cf3be0f04397ee6cc1f52d8ae47f589a4ef5936949c158b2555df291efb87db2bbbca203");
        assertInvalidAddress("205cf3be0f04397ee6cc1f52d8ae47f589a4ef5936949c158b2555df291efb87db2bbbca2031#");
        assertInvalidAddress("bvQpKvb1SswwxVTuyZocHWCVsUeGq7MwoR");
        assertInvalidAddress("d9fe1331ed2ae1bbdfe0e2942e84d74b7310648e5a5f14c4980ec2c6a19f08af6894b9060E83");
    }
}
