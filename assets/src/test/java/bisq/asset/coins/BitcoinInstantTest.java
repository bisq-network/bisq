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

public class BitcoinInstantTest extends AbstractAssetTest {

    public BitcoinInstantTest() {
        super(new BitcoinInstant());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("iG4DCE2RKHH47FcguJsfKXKuF4sdQ519Tt");
        assertValidAddress("i83W3SJH4KVfb94kprXRVXaoLYajFsNeGx");
        assertValidAddress("iDpT25zn3um3kpQCE3ZuMK6gmHviixgFvQ");
        assertValidAddress("iNdhv889Gqp67qdgsTc8K9zBfmqXvGLrtc");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("12c6DSiU4Rq3P4ZxziKxzrL5LmMBrzjrJXX");
        assertInvalidAddress("12c6DSiU4Rq3P4ZxziKxzrL5LmMBrzjrJ");
        assertInvalidAddress("12c6DSiU4Rq3P4ZxziKxzrL5LmMBrzjrJ#");
        assertInvalidAddress("12c6DSiU4Rq3P4ZxziKxzrL5LmMBrzjrJX");
        assertInvalidAddress("iG4DCE2RKHH47FcguJsfKXKuF4sdQ519Ttt");
        assertInvalidAddress("iG4DCE2RKHH47FcguJsfKXKuF4sdQ519T");
        assertInvalidAddress("iG4DCE2RKHH47FcguJsfKXKuF4sdQ519T#");
    }
}
