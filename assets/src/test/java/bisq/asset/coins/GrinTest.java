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
        // valid slatepack addresses
        assertValidAddress("grin1ephxt0u33rz9zpl7exer2awfr9s9ae28qsx7908q2zq03uv3sj7suqdule");
        assertValidAddress("grin1wwg5k80qje0lw32ldttgl52lew0ucmv64zux27pzanl0a2ku85ps5gxafa");
        assertValidAddress("grin1mdxxaz8g5zc4fhqcvcu79c0sp3md9j2f6tt5cxde78scjatkh3zqzrgl9r");
        assertValidAddress("grin17whxsfzj3su0rtpd3hkcjt3hlatvc89dpc9syvrmq2shhnhc9f6sehqe3x");
        assertValidAddress("grin1cq636ment795xn68knzu0ewp73f3zdlgv6dsqv8x7vf2v0j4ek5sk6nmk3");
        assertValidAddress("grin1wm78wjsf2ws507hea4zqrcywxltjwhtgfrwzhdrr9l80l7tpz5fsj58lk0");
        assertValidAddress("grin1jezf3lkcexvj3ydjwanan6khs42fr4036guh0c4vkc04fyxarl6svjzuuh");
    }

    @Test
    public void testInvalidAddresses() {
        // invalid slatepack address (bech32 format invalid)
        assertInvalidAddress("grin1p4fuklglxqsgg602hu4c4jl4aunu5tynyf4lkg96ezh3jefzpy6swshp5x");  // from 0015-slatepack.md#slatepackaddress

        // grinbox
        assertInvalidAddress("gVvk7rLBg3r3qoWYL3VsREnBbooT7nynxx5HtDvUWCJUaNCnddvY");
        assertInvalidAddress("grinbox://gVtWzX5NTLCBkyNV19QVdnLXue13heAVRD36sfkGD6xpqy7k7e4a");
        assertInvalidAddress("gVw9TWimGFXRjoDXWhWxeNQbu84ZpLkvnenkKvA5aJeDo31eM5tC@somerelay.com");
        assertInvalidAddress("gVw9TWimGFXRjoDXWhWxeNQbu84ZpLkvnenkKvA5aJeDo31eM5tC@somerelay.com:1220");
        assertInvalidAddress("grinbox://gVwjSsYW5vvHpK4AunJ5piKhhQTV6V3Jb818Uqs6PdC3SsB36AsA@somerelay.com");
        assertInvalidAddress("grinbox://gVwjSsYW5vvHpK4AunJ5piKhhQTV6V3Jb818Uqs6PdC3SsB36AsA@somerelay.com:1220");

        // valid IP:port addresses but not supported in Bisq
        assertInvalidAddress("0.0.0.0:8080");
        assertInvalidAddress("173.194.34.134:8080");
        assertInvalidAddress("127.0.0.1:8080");
        assertInvalidAddress("192.168.0.1:8080");
        assertInvalidAddress("18.101.25.153:8080");
        assertInvalidAddress("173.194.34.134:1");
        assertInvalidAddress("173.194.34.134:11");
        assertInvalidAddress("173.194.34.134:1111");
        assertInvalidAddress("173.194.34.134:65535");

        // invalid IP:port addresses
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

        assertInvalidAddress("gVvk7rLBg3r3qoWYL3VsREnBbooT7nynxx5HtDvUWCJUaNCnddvY1111");
        assertInvalidAddress("grinbox:/gVtWzX5NTLCBkyNV19QVdnLXue13heAVRD36sfkGD6xpqy7k7e4a");
        assertInvalidAddress("gVw9TWimGFXRjoDXWhWxeNQbu84ZpLkvnenkKvA5aJeDo31eM5tC@somerelay.com.");
        assertInvalidAddress("gVw9TWimGFXRjoDXWhWxeNQbu84ZpLkvnenkKvA5aJeDo31eM5tC@somerelay.com:1220a");
        assertInvalidAddress("grinbox://gVwjSsYW5vvHpK4AunJ5piKhhQTV6V3Jb818Uqs6PdC3SsB36AsAsomerelay.com");
        assertInvalidAddress("grinbox://gVwjSsYW5vvHpK4AunJ5piKhhQTV6V3Jb818Uqs6PdC3SsB36AsA@somerelay.com1220");
    }
}
