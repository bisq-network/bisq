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

package bisq.core.util;

import bisq.core.locale.GlobalSettings;
import bisq.core.locale.Res;
import bisq.core.util.validation.RegexValidator;
import bisq.core.util.validation.RegexValidatorFactory;

import java.util.Locale;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class RegexValidatorTest {

    @Before
    public void setup() {
        Locale.setDefault(new Locale("en", "US"));
        GlobalSettings.setLocale(new Locale("en", "US"));
        Res.setBaseCurrencyCode("BTC");
        Res.setBaseCurrencyName("Bitcoin");
    }

    @Test
    public void testAddressRegexValidator() {
        RegexValidator regexValidator = RegexValidatorFactory.addressRegexValidator();

        assertTrue(regexValidator.validate("").isValid);
        assertFalse(regexValidator.validate(" ").isValid);

        // onion V2 addresses
        assertTrue(regexValidator.validate("abcdefghij234567.onion").isValid);
        assertTrue(regexValidator.validate("abcdefghijklmnop.onion,abcdefghijklmnop.onion").isValid);
        assertTrue(regexValidator.validate("abcdefghijklmnop.onion, abcdefghijklmnop.onion").isValid);
        assertTrue(regexValidator.validate("qrstuvwxyzABCDEF.onion,qrstuvwxyzABCDEF.onion,aaaaaaaaaaaaaaaa.onion").isValid);
        assertTrue(regexValidator.validate("GHIJKLMNOPQRSTUV.onion:9999").isValid);
        assertTrue(regexValidator.validate("WXYZ234567abcdef.onion,GHIJKLMNOPQRSTUV.onion:9999").isValid);
        assertTrue(regexValidator.validate("aaaaaaaaaaaaaaaa.onion:9999,WXYZ234567abcdef.onion:9999,2222222222222222.onion:9999").isValid);
        assertFalse(regexValidator.validate("abcd.onion").isValid);
        assertFalse(regexValidator.validate("abcdefghijklmnop,abcdefghijklmnop.onion").isValid);
        assertFalse(regexValidator.validate("abcdefghi2345689.onion:9999").isValid);
        assertFalse(regexValidator.validate("onion:9999,abcdefghijklmnop.onion:9999").isValid);
        assertFalse(regexValidator.validate("abcdefghijklmnop.onion:").isValid);

        // onion v3 addresses
        assertFalse(regexValidator.validate("32zzibxmqi2ybxpqyggwwuwz7a3lbvtzoloti7cxoevyvijexvgsfei.onion:8333").isValid); // 1 missing char
        assertTrue(regexValidator.validate("wizseedscybbttk4bmb2lzvbuk2jtect37lcpva4l3twktmkzemwbead.onion:8000").isValid);

        // ipv4 addresses
        assertTrue(regexValidator.validate("12.34.56.78").isValid);
        assertTrue(regexValidator.validate("12.34.56.78,87.65.43.21").isValid);
        assertTrue(regexValidator.validate("12.34.56.78:8888").isValid);
        assertFalse(regexValidator.validate("12.34.56.788").isValid);
        assertFalse(regexValidator.validate("12.34.56.78:").isValid);

        // ipv6 addresses
        assertTrue(regexValidator.validate("FE80:0000:0000:0000:0202:B3FF:FE1E:8329").isValid);
        assertTrue(regexValidator.validate("FE80::0202:B3FF:FE1E:8329").isValid);
        assertTrue(regexValidator.validate("FE80::0202:B3FF:FE1E:8329,FE80:0000:0000:0000:0202:B3FF:FE1E:8329").isValid);
        assertTrue(regexValidator.validate("::1").isValid);
        assertTrue(regexValidator.validate("fe80::").isValid);
        assertTrue(regexValidator.validate("2001::").isValid);
        assertTrue(regexValidator.validate("[::1]:8333").isValid);
        assertTrue(regexValidator.validate("[FE80::0202:B3FF:FE1E:8329]:8333").isValid);
        assertTrue(regexValidator.validate("[2001:db8::1]:80").isValid);
        assertTrue(regexValidator.validate("[aaaa::bbbb]:8333").isValid);
        assertFalse(regexValidator.validate("1200:0000:AB00:1234:O000:2552:7777:1313").isValid);

        // fqdn addresses
        assertTrue(regexValidator.validate("example.com").isValid);
        assertTrue(regexValidator.validate("mynode.local:8333").isValid);
        assertTrue(regexValidator.validate("foo.example.com,bar.example.com").isValid);
        assertTrue(regexValidator.validate("foo.example.com:8333,bar.example.com:8333").isValid);

        assertFalse(regexValidator.validate("mynode.local:65536").isValid);
        assertFalse(regexValidator.validate("-example.com").isValid);
        assertFalse(regexValidator.validate("example-.com").isValid);
    }

    @Test
    public void testOnionAddressRegexValidator() {
        RegexValidator regexValidator = RegexValidatorFactory.onionAddressRegexValidator();

        assertTrue(regexValidator.validate("").isValid);
        assertFalse(regexValidator.validate(" ").isValid);

        // onion V2 addresses
        assertTrue(regexValidator.validate("abcdefghij234567.onion").isValid);
        assertTrue(regexValidator.validate("abcdefghijklmnop.onion,abcdefghijklmnop.onion").isValid);
        assertTrue(regexValidator.validate("abcdefghijklmnop.onion, abcdefghijklmnop.onion").isValid);
        assertTrue(regexValidator.validate("qrstuvwxyzABCDEF.onion,qrstuvwxyzABCDEF.onion,aaaaaaaaaaaaaaaa.onion").isValid);
        assertTrue(regexValidator.validate("GHIJKLMNOPQRSTUV.onion:9999").isValid);
        assertTrue(regexValidator.validate("WXYZ234567abcdef.onion,GHIJKLMNOPQRSTUV.onion:9999").isValid);
        assertTrue(regexValidator.validate("aaaaaaaaaaaaaaaa.onion:9999,WXYZ234567abcdef.onion:9999,2222222222222222.onion:9999").isValid);
        assertFalse(regexValidator.validate("abcd.onion").isValid);
        assertFalse(regexValidator.validate("abcdefghijklmnop,abcdefghijklmnop.onion").isValid);
        assertFalse(regexValidator.validate("abcdefghi2345689.onion:9999").isValid);
        assertFalse(regexValidator.validate("onion:9999,abcdefghijklmnop.onion:9999").isValid);
        assertFalse(regexValidator.validate("abcdefghijklmnop.onion:").isValid);

        // onion v3 addresses
        assertFalse(regexValidator.validate("32zzibxmqi2ybxpqyggwwuwz7a3lbvtzoloti7cxoevyvijexvgsfei.onion:8333").isValid); // 1 missing char
        assertTrue(regexValidator.validate("wizseedscybbttk4bmb2lzvbuk2jtect37lcpva4l3twktmkzemwbead.onion:8000").isValid);

    }

    @Test
    public void testLocalnetAddressRegexValidator() {
        RegexValidator regexValidator = RegexValidatorFactory.localnetAddressRegexValidator();

        assertTrue(regexValidator.validate("").isValid);
        assertFalse(regexValidator.validate(" ").isValid);

        // onion V2 addresses
        assertFalse(regexValidator.validate("abcdefghij234567.onion").isValid);
        assertFalse(regexValidator.validate("abcdefghijklmnop.onion,abcdefghijklmnop.onion").isValid);
        assertFalse(regexValidator.validate("abcdefghijklmnop.onion, abcdefghijklmnop.onion").isValid);
        assertFalse(regexValidator.validate("qrstuvwxyzABCDEF.onion,qrstuvwxyzABCDEF.onion,aaaaaaaaaaaaaaaa.onion").isValid);
        assertFalse(regexValidator.validate("GHIJKLMNOPQRSTUV.onion:9999").isValid);
        assertFalse(regexValidator.validate("WXYZ234567abcdef.onion,GHIJKLMNOPQRSTUV.onion:9999").isValid);
        assertFalse(regexValidator.validate("aaaaaaaaaaaaaaaa.onion:9999,WXYZ234567abcdef.onion:9999,2222222222222222.onion:9999").isValid);
        assertFalse(regexValidator.validate("abcd.onion").isValid);
        assertFalse(regexValidator.validate("abcdefghijklmnop,abcdefghijklmnop.onion").isValid);
        assertFalse(regexValidator.validate("abcdefghi2345689.onion:9999").isValid);
        assertFalse(regexValidator.validate("onion:9999,abcdefghijklmnop.onion:9999").isValid);
        assertFalse(regexValidator.validate("abcdefghijklmnop.onion:").isValid);

        // onion v3 addresses
        assertFalse(regexValidator.validate("32zzibxmqi2ybxpqyggwwuwz7a3lbvtzoloti7cxoevyvijexvgsfei.onion:8333").isValid); // 1 missing char
        assertFalse(regexValidator.validate("wizseedscybbttk4bmb2lzvbuk2jtect37lcpva4l3twktmkzemwbead.onion:8000").isValid);

        // ipv4 addresses
        assertFalse(regexValidator.validate("12.34.56.78").isValid);
        assertFalse(regexValidator.validate("12.34.56.78,87.65.43.21").isValid);
        assertFalse(regexValidator.validate("12.34.56.78:8888").isValid);
        assertFalse(regexValidator.validate("12.34.56.788").isValid);
        assertFalse(regexValidator.validate("12.34.56.78:").isValid);

        // ipv4 local addresses
        assertTrue(regexValidator.validate("10.10.10.10").isValid);
        assertTrue(regexValidator.validate("172.19.1.1").isValid);
        assertTrue(regexValidator.validate("172.19.1.1").isValid);
        assertTrue(regexValidator.validate("192.168.1.1").isValid);
        assertTrue(regexValidator.validate("192.168.1.1,172.16.1.1").isValid);
        assertTrue(regexValidator.validate("192.168.1.1:8888,192.168.1.2:8888").isValid);
        assertFalse(regexValidator.validate("192.168.1.888").isValid);
        assertFalse(regexValidator.validate("192.168.1.1:").isValid);

        // ipv4 autolocal addresses
        assertTrue(regexValidator.validate("169.254.123.232").isValid);

        // ipv6 local addresses
        assertTrue(regexValidator.validate("fe80:2:3:4:5:6:7:8").isValid);
        assertTrue(regexValidator.validate("fe80::").isValid);
        assertTrue(regexValidator.validate("fc00::").isValid);
        assertTrue(regexValidator.validate("fd00::,fe80::1").isValid);
        assertTrue(regexValidator.validate("fd00::8").isValid);
        assertTrue(regexValidator.validate("fd00::7:8").isValid);
        assertTrue(regexValidator.validate("fd00::6:7:8").isValid);
        assertTrue(regexValidator.validate("fd00::5:6:7:8").isValid);
        assertTrue(regexValidator.validate("fd00::4:5:6:7:8").isValid);
        assertTrue(regexValidator.validate("fd00::3:4:5:6:7:8").isValid);
        assertTrue(regexValidator.validate("fd00:2:3:4:5:6:7:8").isValid);
        assertTrue(regexValidator.validate("fd00::0202:B3FF:FE1E:8329").isValid);
        assertTrue(regexValidator.validate("fd00::0202:B3FF:FE1E:8329,FE80::0202:B3FF:FE1E:8329").isValid);
        // ipv6 local with optional port at the end
        assertTrue(regexValidator.validate("[fd00::1]:8081").isValid);
        assertTrue(regexValidator.validate("[fd00::1]:8081,[fc00::1]:8081").isValid);
        assertTrue(regexValidator.validate("[FE80::0202:B3FF:FE1E:8329]:8333").isValid);

        // ipv6 loopback
        assertFalse(regexValidator.validate("::1").isValid);

        // ipv6 unicast
        assertFalse(regexValidator.validate("2001::").isValid);
        assertFalse(regexValidator.validate("[::1]:8333").isValid);
        assertFalse(regexValidator.validate("[2001:db8::1]:80").isValid);
        assertFalse(regexValidator.validate("[aaaa::bbbb]:8333").isValid);
        assertFalse(regexValidator.validate("1200:0000:AB00:1234:O000:2552:7777:1313").isValid);

        // *.local fqdn hostnames
        assertTrue(regexValidator.validate("mynode.local").isValid);
        assertTrue(regexValidator.validate("mynode.local:8081").isValid);

        // non-local fqdn hostnames
        assertFalse(regexValidator.validate("example.com").isValid);
        assertFalse(regexValidator.validate("foo.example.com,bar.example.com").isValid);
        assertFalse(regexValidator.validate("foo.example.com:8333,bar.example.com:8333").isValid);

        // invalid fqdn hostnames
        assertFalse(regexValidator.validate("mynode.local:65536").isValid);
        assertFalse(regexValidator.validate("-example.com").isValid);
        assertFalse(regexValidator.validate("example-.com").isValid);
    }

    @Test
    public void testLocalhostAddressRegexValidator() {
        RegexValidator regexValidator = RegexValidatorFactory.localhostAddressRegexValidator();

        assertTrue(regexValidator.validate("").isValid);
        assertFalse(regexValidator.validate(" ").isValid);

        // onion V2 addresses
        assertFalse(regexValidator.validate("abcdefghij234567.onion").isValid);
        assertFalse(regexValidator.validate("abcdefghijklmnop.onion,abcdefghijklmnop.onion").isValid);
        assertFalse(regexValidator.validate("abcdefghijklmnop.onion, abcdefghijklmnop.onion").isValid);
        assertFalse(regexValidator.validate("qrstuvwxyzABCDEF.onion,qrstuvwxyzABCDEF.onion,aaaaaaaaaaaaaaaa.onion").isValid);
        assertFalse(regexValidator.validate("GHIJKLMNOPQRSTUV.onion:9999").isValid);
        assertFalse(regexValidator.validate("WXYZ234567abcdef.onion,GHIJKLMNOPQRSTUV.onion:9999").isValid);
        assertFalse(regexValidator.validate("aaaaaaaaaaaaaaaa.onion:9999,WXYZ234567abcdef.onion:9999,2222222222222222.onion:9999").isValid);
        assertFalse(regexValidator.validate("abcd.onion").isValid);
        assertFalse(regexValidator.validate("abcdefghijklmnop,abcdefghijklmnop.onion").isValid);
        assertFalse(regexValidator.validate("abcdefghi2345689.onion:9999").isValid);
        assertFalse(regexValidator.validate("onion:9999,abcdefghijklmnop.onion:9999").isValid);
        assertFalse(regexValidator.validate("abcdefghijklmnop.onion:").isValid);

        // onion v3 addresses
        assertFalse(regexValidator.validate("32zzibxmqi2ybxpqyggwwuwz7a3lbvtzoloti7cxoevyvijexvgsfei.onion:8333").isValid); // 1 missing char
        assertFalse(regexValidator.validate("wizseedscybbttk4bmb2lzvbuk2jtect37lcpva4l3twktmkzemwbead.onion:8000").isValid);

        // ipv4 addresses
        assertFalse(regexValidator.validate("12.34.56.78").isValid);
        assertFalse(regexValidator.validate("12.34.56.78,87.65.43.21").isValid);
        assertFalse(regexValidator.validate("12.34.56.78:8888").isValid);
        assertFalse(regexValidator.validate("12.34.56.788").isValid);
        assertFalse(regexValidator.validate("12.34.56.78:").isValid);

        // ipv4 loopback addresses
        assertTrue(regexValidator.validate("127.0.0.1").isValid);
        assertTrue(regexValidator.validate("127.0.1.1").isValid);

        // ipv4 local addresses
        assertFalse(regexValidator.validate("10.10.10.10").isValid);
        assertFalse(regexValidator.validate("172.19.1.1").isValid);
        assertFalse(regexValidator.validate("172.19.1.1").isValid);
        assertFalse(regexValidator.validate("192.168.1.1").isValid);
        assertFalse(regexValidator.validate("192.168.1.1,172.16.1.1").isValid);
        assertFalse(regexValidator.validate("192.168.1.1:8888,192.168.1.2:8888").isValid);
        assertFalse(regexValidator.validate("192.168.1.888").isValid);
        assertFalse(regexValidator.validate("192.168.1.1:").isValid);

        // ipv4 autolocal addresses
        assertFalse(regexValidator.validate("169.254.123.232").isValid);

        // ipv6 local addresses
        assertFalse(regexValidator.validate("fe80::").isValid);
        assertFalse(regexValidator.validate("fc00::").isValid);
        assertFalse(regexValidator.validate("fd00::8").isValid);
        assertFalse(regexValidator.validate("fd00::7:8").isValid);
        assertFalse(regexValidator.validate("fd00::6:7:8").isValid);
        assertFalse(regexValidator.validate("fd00::5:6:7:8").isValid);
        assertFalse(regexValidator.validate("fd00::3:4:5:6:7:8").isValid);
        assertFalse(regexValidator.validate("fd00::4:5:6:7:8").isValid);
        assertFalse(regexValidator.validate("fd00:2:3:4:5:6:7:8").isValid);
        assertFalse(regexValidator.validate("fd00::0202:B3FF:FE1E:8329").isValid);

        assertFalse(regexValidator.validate("FE80:0000:0000:0000:0202:B3FF:FE1E:8329").isValid);
        assertFalse(regexValidator.validate("FE80::0202:B3FF:FE1E:8329").isValid);
        assertFalse(regexValidator.validate("FE80::0202:B3FF:FE1E:8329,FE80:0000:0000:0000:0202:B3FF:FE1E:8329").isValid);
        // ipv6 local with optional port at the end
        assertFalse(regexValidator.validate("[fd00::1]:8081").isValid);
        assertFalse(regexValidator.validate("[fd00::1]:8081,[fc00::1]:8081").isValid);

        // ipv6 loopback
        assertTrue(regexValidator.validate("::1").isValid);
        assertTrue(regexValidator.validate("::2").isValid);
        assertTrue(regexValidator.validate("[::1]:8333").isValid);

        // ipv6 unicast
        assertFalse(regexValidator.validate("2001::").isValid);
        assertFalse(regexValidator.validate("[FE80::0202:B3FF:FE1E:8329]:8333").isValid);
        assertFalse(regexValidator.validate("[2001:db8::1]:80").isValid);
        assertFalse(regexValidator.validate("[aaaa::bbbb]:8333").isValid);
        assertFalse(regexValidator.validate("1200:0000:AB00:1234:O000:2552:7777:1313").isValid);

        // localhost fqdn hostnames
        assertTrue(regexValidator.validate("localhost").isValid);
        assertTrue(regexValidator.validate("localhost:8081").isValid);

        // local fqdn hostnames
        assertFalse(regexValidator.validate("mynode.local:8081").isValid);

        // non-local fqdn hostnames
        assertFalse(regexValidator.validate("example.com").isValid);
        assertFalse(regexValidator.validate("foo.example.com,bar.example.com").isValid);
        assertFalse(regexValidator.validate("foo.example.com:8333,bar.example.com:8333").isValid);

        // invalid fqdn hostnames
        assertFalse(regexValidator.validate("mynode.local:65536").isValid);
        assertFalse(regexValidator.validate("-example.com").isValid);
        assertFalse(regexValidator.validate("example-.com").isValid);
    }
}
