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

public class GrinTest extends AbstractAssetTest {

    public GrinTest() {
        super(new Grin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("0.0.0.0:8080");
        assertValidAddress("173.194.34.134:8080");
        assertValidAddress("127.0.0.1:8080");
        assertValidAddress("192.168.0.1:8080");
        assertValidAddress("18.101.25.153:8080");
        assertValidAddress("173.194.34.134:1");
        assertValidAddress("173.194.34.134:11");
        assertValidAddress("173.194.34.134:1111");
        assertValidAddress("173.194.34.134:65535");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("google.com");
        assertInvalidAddress("100.100.100.100");
        assertInvalidAddress(".100.100.100.100:1222");
        assertInvalidAddress("100..100.100.100:1222.");
        assertInvalidAddress("100.100.100.100.:1222");
        assertInvalidAddress("999.999.999.999:1222");
        assertInvalidAddress("256.256.256.256:1222");
        assertInvalidAddress("256.100.100.100.100:1222");
        assertInvalidAddress("123.123.123:1222");
        assertInvalidAddress("http://123.123.123:1222");
        assertInvalidAddress("1000.2.3.4:1222");
        assertInvalidAddress("999.2.3.4:1222");

        // too large port
        assertInvalidAddress("173.194.34.134:65536");
    }
}
