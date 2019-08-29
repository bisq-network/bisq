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

public class DarkPayTest extends AbstractAssetTest {

    public DarkPayTest() {
        super(new DarkPay());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("DXSi43hpVRjy1yGF3Vh3nnCK6ydwyWxVAD");
        assertValidAddress("DmHHAyocykozeW8fwJxPbn1o83dT4fDtoR");
        assertValidAddress("RSBxWDDMNxCKtnHvqf8Dsif5sm52ik36rW");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("DXSi43hpVRjy1yGF3Vh3nnCK6ydwyWxVAd");
        assertInvalidAddress("DmHHAyocykozeW888888fwJxPbn1o83dT4fDtoR");
        assertInvalidAddress("RSBxWDDMNxCKtnHvqf8Dsif5sm52ik35rW#");
        assertInvalidAddress("3GyEtTwXhxbjBtmAR3CtzeayAyshtvd44P");
        assertInvalidAddress("1CnXYrivw7pJy3asKftp41wRPgBggF9fBw");
    }
}
