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

package bisq.network.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlSafetyCheckerTest {

    // -- parseAndValidate: rejects unsafe inputs ---------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "file:///etc/passwd",
            "ftp://example.com/",
            "gopher://example.com/",
            "javascript:alert(1)",
            "data:text/plain,hello",
            "jar:http://x/!/",
            "ssh://example.com",
            "ws://example.com/",
            "wss://example.com/",
    })
    void rejectsNonHttpSchemes(String url) {
        assertThrows(UrlSafetyChecker.InvalidUrlException.class,
                () -> UrlSafetyChecker.parseAndValidate(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/relative/path",
            "example.com",
            "http://",
            "https://",
            "http:///path",
            "://example.com",
    })
    void rejectsMalformedOrRelative(String url) {
        assertThrows(UrlSafetyChecker.InvalidUrlException.class,
                () -> UrlSafetyChecker.parseAndValidate(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://user:pass@evil.com/",
            "https://attacker@example.com/",
    })
    void rejectsUserinfo(String url) {
        // userinfo can be used to confuse naïve "contains" host checks
        // (e.g. http://localhost@evil.com/ would historically have been treated as local)
        assertThrows(UrlSafetyChecker.InvalidUrlException.class,
                () -> UrlSafetyChecker.parseAndValidate(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
    })
    void rejectsEmpty(String url) {
        assertThrows(UrlSafetyChecker.InvalidUrlException.class,
                () -> UrlSafetyChecker.parseAndValidate(url));
    }

    @Test
    void rejectsNull() {
        assertThrows(UrlSafetyChecker.InvalidUrlException.class,
                () -> UrlSafetyChecker.parseAndValidate(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://example.com/",
            "https://example.com/path?q=1",
            "http://127.0.0.1:8080/",
            "http://localhost:9999/api",
            "https://expyuzz4wqqyqhjn.onion/",
            "http://[::1]:1234/path",
            "HTTP://Example.COM/",
    })
    void acceptsValidHttpUrls(String url) {
        UrlSafetyChecker.parseAndValidate(url); // does not throw
    }

    // -- isLocal: only loopback / "localhost" pass -------------------------------

    // Default (strict): only loopback / "localhost" --------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost/",
            "http://LOCALHOST:8080/",
            "http://127.0.0.1/",
            "http://127.0.0.1:9050/",
            "http://127.1.2.3/",            // entire 127.0.0.0/8 is loopback
            "http://[::1]/",
            "http://[::1]:8080/api",
            "http://ip6-localhost/",
            "http://ip6-loopback/",
    })
    void defaultModeTreatsLoopbackAsLocal(String url) {
        assertTrue(UrlSafetyChecker.isLocal(url), url);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // private/LAN: NOT local in default mode — must traverse Tor
            "http://10.0.0.1/",
            "http://172.16.0.1/",
            "http://192.168.1.5:18081/",
            "http://169.254.169.254/",
            "http://[fe80::1]/",
            "http://[fc00::1]/",
            // bypass attempts and public addresses
            "http://localhost.attacker.com/",     // suffix attack
            "http://attacker.com/localhost",      // path contains "localhost"
            "http://attacker.com/?h=localhost",   // query contains "localhost"
            "http://example.com.localhost.io/",   // fake local TLD
            "http://8.8.8.8/",                    // public IP
            "https://api.example.com/",
            "http://[2001:db8::1]/",              // public IPv6 (documentation prefix)
            "http://localhosT.evil.com/",         // case + suffix
    })
    void defaultModeTreatsEverythingElseAsRemote(String url) {
        assertFalse(UrlSafetyChecker.isLocal(url), url);
    }

    // allowPrivateRanges=true: LAN / link-local / unique-local count as local -------

    @ParameterizedTest
    @ValueSource(strings = {
            // loopback (still local)
            "http://localhost/",
            "http://127.0.0.1/",
            "http://[::1]/",
            // RFC1918 private IPv4
            "http://10.0.0.1/",
            "http://10.255.255.255/",
            "http://172.16.0.1/",
            "http://172.31.255.254/",
            "http://192.168.0.1/",
            "http://192.168.1.5:18081/",
            // link-local IPv4
            "http://169.254.0.1/",
            "http://169.254.169.254/",
            // IPv6 link-local + unique-local
            "http://[fe80::1]/",
            "http://[fc00::1]/",
            "http://[fd00:abcd::1]/",
    })
    void privateModeTreatsLanAsLocal(String url) {
        assertTrue(UrlSafetyChecker.isLocal(java.net.URI.create(url), true), url);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // even with allowPrivateRanges, public addresses must still go via Tor
            "http://8.8.8.8/",
            "http://1.1.1.1/",
            "http://172.15.0.1/",                 // just outside 172.16/12
            "http://172.32.0.1/",
            "http://192.169.0.1/",
            "http://9.255.255.255/",
            "https://api.example.com/",
            "http://[2001:db8::1]/",
            "http://attacker.com/localhost",
    })
    void privateModeStillRejectsPublicAndSpoofs(String url) {
        assertFalse(UrlSafetyChecker.isLocal(java.net.URI.create(url), true), url);
    }

    @Test
    void onionAddressIsNotLocal() {
        // Onion addresses must always traverse Tor — never short-circuit them
        assertFalse(UrlSafetyChecker.isLocal("http://expyuzz4wqqyqhjn.onion/"));
    }

    // -- Edge cases & adversarial inputs (deny by default) -----------------------
    //
    // Threat model: an attacker controls (or partially controls) a configuration
    // string that ends up as baseUrl — e.g. an XMR proof service list seeded over
    // P2P, an explorer URL pulled from preferences, etc. Goals tested below:
    //   1. Trick the parser into treating a remote host as local (deanonymise).
    //   2. Use an alternate IP encoding that bypasses naive equality on
    //      "127.0.0.1" / "192.168.x" but still routes to a LAN/loopback target.
    //   3. Smuggle control characters to confuse logs / downstream consumers.
    //   4. Parse-mismatch attacks — Java URI vs. server's URL parser disagreeing
    //      about hostname.
    // Each case asserts the safe outcome.

    @ParameterizedTest
    @ValueSource(strings = {
            // CR/LF/NUL/tab smuggling: java.net.URI rejects bare control chars in
            // the host portion. Verify we do NOT silently accept them.
            "http://evil.com\rlocalhost/",
            "http://evil.com\nlocalhost/",
            "http://localhost .evil.com/",
            "http://local\thost/",
            "http://localhost /",                    // trailing space
            "http:// localhost/",                    // leading space
            // Backslash variants — RFC 3986 disallows but some clients normalise.
            // URI treats backslash as part of host → invalid.
            "http:\\\\evil.com\\",
    })
    void rejectsControlCharsAndBackslash(String url) {
        assertThrows(UrlSafetyChecker.InvalidUrlException.class,
                () -> UrlSafetyChecker.parseAndValidate(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://@evil.com/",          // empty userinfo
            "http://%00@evil.com/",       // percent-encoded NUL in userinfo
            "https://evil.com:80@trusted.com/",   // confusable: looks like host:port to some readers
    })
    void rejectsAnyUserinfoForm(String url) {
        // Even an empty userinfo is suspicious — most legitimate URLs don't have one
        // and accepting it makes it trivial to confuse a "contains(localhost)" reader.
        assertThrows(UrlSafetyChecker.InvalidUrlException.class,
                () -> UrlSafetyChecker.parseAndValidate(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Forms that java.net.URI rejects outright at parse time — the
            // hardest defence: these never even reach isLocal().
            "http://0x7f.0x0.0x0.0x1/",          // hex octets
            "http://127.1/",                      // BSD short-form (1-,2-,3-octet)
            "http://127.0.1/",
    })
    void uriParserRejectsNonCanonicalIpv4(String url) {
        // We rely on URI's strictness here. If a future Java release relaxes
        // this, the tests below (rejectsNonCanonicalLocalLookalikes) take over.
        assertThrows(UrlSafetyChecker.InvalidUrlException.class,
                () -> UrlSafetyChecker.parseAndValidate(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // IPv4 alternate encodings that DO parse as URIs but which our
            // isNumericIp() refuses to treat as a numeric IP. They fall through
            // to "not a numeric IP, not localhost" → REMOTE. That is the safe
            // outcome: a non-canonical loopback literal in user config is more
            // likely an attack than a legitimate setting.
            "http://2130706433/",                 // 127.0.0.1 in 32-bit integer form
            "http://0177.0.0.1/",                 // octal octet
            "http://0/",                          // shorthand for 0.0.0.0
            "http://0.0.0.0/",                    // unspecified addr — Linux maps to loopback
            "http://255.255.255.255/",            // limited broadcast
            "http://224.0.0.1/",                  // multicast
            "http://[ff02::1]/",                  // IPv6 link-local multicast
            "http://100.64.0.1/",                 // RFC6598 carrier-grade NAT
    })
    void rejectsNonCanonicalLocalLookalikes(String url) {
        // Default mode is strict: only canonical loopback / "localhost" is local.
        assertFalse(UrlSafetyChecker.isLocal(url), url);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://[::ffff:127.0.0.1]/",        // IPv4-mapped IPv6 (mixed notation)
            "http://[::ffff:7f00:1]/",           // IPv4-mapped IPv6 (hex form)
            "http://[::ffff:7f00:0001]/",        // IPv4-mapped IPv6 (hex zero-padded)
            "http://[0:0:0:0:0:0:0:1]/",         // expanded ::1
    })
    void treatsIpv4MappedAndExpandedLoopbackAsLocal(String url) {
        // These ARE recognised by isNumericIp (contain ':') and InetAddress
        // reports them as loopback. They round-trip cleanly to ::1 / 127.0.0.1.
        assertTrue(UrlSafetyChecker.isLocal(url), url);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost./",                 // trailing dot (DNS root anchor)
            "http://LocalHost/",
            "http://LOCALHOST/",
    })
    void localhostTrailingDot(String url) {
        // Trailing-dot is the DNS-root-anchored form; many resolvers treat it as
        // identical to "localhost". We are stricter: only the bare literal
        // "localhost" (case-insensitive) qualifies. A user wanting LAN bypass
        // should configure 127.0.0.1.
        if (url.contains(".")) {
            assertFalse(UrlSafetyChecker.isLocal(url), url);
        } else {
            assertTrue(UrlSafetyChecker.isLocal(url), url);
        }
    }

    @Test
    void rejectsPunycode() {
        // Punycode parses as a valid URI host but isn't "localhost", so it's remote.
        assertFalse(UrlSafetyChecker.isLocal("http://xn--80ak6aa92e.com/"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhostε.example/",                    // greek epsilon suffix
            "http://lоcalhost/",                             // cyrillic 'о' instead of 'o'
    })
    void uriRejectsRawUnicodeHosts(String url) {
        // Java URI rejects raw unicode in the host (must be punycoded) — extra
        // defence against homograph attacks at parse time.
        assertThrows(UrlSafetyChecker.InvalidUrlException.class,
                () -> UrlSafetyChecker.parseAndValidate(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:0/",                // port 0
            "http://localhost:65535/api",         // max port
            "http://localhost/#localhost",        // fragment
            "http://localhost/?x=1&y=2",          // query
            "http://localhost//../etc/passwd",    // path traversal noise — still local host
            "http://localhost/very/deep/path/",
    })
    void localhostStaysLocalAcrossUrlComponents(String url) {
        assertTrue(UrlSafetyChecker.isLocal(url), url);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://attacker.com/#localhost",     // fragment containing literal
            "http://attacker.com/?h=127.0.0.1",   // query containing IP literal
            "http://attacker.com/127.0.0.1",      // path containing IP literal
            "http://attacker.com:80/localhost",   // port + path
    })
    void componentsOutsideHostNeverChangeLocality(String url) {
        // The ONLY thing that determines locality is the host. Path/query/fragment
        // contents must not influence it.
        assertFalse(UrlSafetyChecker.isLocal(url), url);
    }

    @Test
    void parseAndValidatePreservesHost() {
        // Sanity: the parsed URI must expose the literal host so downstream code
        // (cache keys, logs, allowlists) sees the same value the network stack uses.
        java.net.URI uri = UrlSafetyChecker.parseAndValidate("http://Example.COM:8080/api");
        // URI does not lower-case the host; we only lower-case for our local-check.
        assertTrue(uri.getHost().equalsIgnoreCase("example.com"));
        assertTrue(uri.getPort() == 8080);
    }

    @Test
    void privateModeRejectsCgNatAndBroadcast() {
        // RFC6598 carrier-grade NAT is *not* "your" LAN — it's the ISP's. Same
        // for broadcast/multicast: connecting to them from a config string is
        // never the right thing.
        assertFalse(UrlSafetyChecker.isLocal(java.net.URI.create("http://100.64.0.1/"), true));
        assertFalse(UrlSafetyChecker.isLocal(java.net.URI.create("http://255.255.255.255/"), true));
        assertFalse(UrlSafetyChecker.isLocal(java.net.URI.create("http://224.0.0.1/"), true));
        assertFalse(UrlSafetyChecker.isLocal(java.net.URI.create("http://[ff02::1]/"), true));
        assertFalse(UrlSafetyChecker.isLocal(java.net.URI.create("http://0.0.0.0/"), true));
    }

    @Test
    void privateModeStillRejectsNonCanonicalLoopback() {
        // Even when LAN is allowed, alternate IPv4 encodings remain rejected
        // because they're a strong signal of an attacker hand-crafting a URL.
        assertFalse(UrlSafetyChecker.isLocal(java.net.URI.create("http://2130706433/"), true));
        assertFalse(UrlSafetyChecker.isLocal(java.net.URI.create("http://0177.0.0.1/"), true));
        assertFalse(UrlSafetyChecker.isLocal(java.net.URI.create("http://127.1/"), true));
    }

    // -- isOnion -----------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "http://expyuzz4wqqyqhjn.onion/",
            "https://duckduckgogg42xjoc72x3sjasowoarfbgcmvfimaftt6twagswzczad.onion/",
            "http://example.onion:8080/path?query=1",
            "https://EXPYUZZ4WQQYQHJN.ONION/",
            "http://Mixed.OnIoN/",
            "http://sub.domain.example.onion/",
    })
    void isOnionRecognisesOnionHosts(String url) {
        assertTrue(UrlSafetyChecker.isOnion(UrlSafetyChecker.parseAndValidate(url)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://example.com/",
            "https://example.org/onion",
            "http://onion.example.com/",
            "http://127.0.0.1/",
            "http://localhost/",
            "http://[::1]/",
            "http://192.168.1.1/",
            "http://example.onion.evil.com/",
            "http://onion/",
            "http://onionx/",
            "http://example.onionx/",
    })
    void isOnionRejectsNonOnionHosts(String url) {
        assertFalse(UrlSafetyChecker.isOnion(UrlSafetyChecker.parseAndValidate(url)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://expyuzz4wqqyqhjn.onion./",
            "https://example.onion./path",
            "http://EXAMPLE.ONION./",
    })
    void isOnionMatchesTrailingDotFqdn(String url) {
        // FQDN form (one or more trailing dots) resolves to the same destination.
        // Must match so a no-proxy clearnet attempt cannot leak the lookup.
        // Note: consecutive-dot labels (e.g. "host.onion..") are not tested here
        // because Java's URI parser returns host=null for them, and
        // parseAndValidate rejects null-host URIs upstream — they never reach
        // isOnion. The trailing-dot strip in isOnion is still a defence in depth
        // for the single-dot FQDN case the URI parser does accept.
        assertTrue(UrlSafetyChecker.isOnion(java.net.URI.create(url)));
    }

    @Test
    void isOnionRejectsHostThatEqualsOnion() {
        // ".onion" alone (no label) is not a valid v2/v3 onion address; URI
        // parsing rejects an empty label, but bare "onion" must not match.
        assertFalse(UrlSafetyChecker.isOnion(java.net.URI.create("http://onion/")));
    }

    @Test
    void isOnionFalseForUriWithoutHost() {
        // Defensive: URI with no host (e.g. "http:/foo") — host() returns null.
        assertFalse(UrlSafetyChecker.isOnion(java.net.URI.create("http:/foo")));
    }

    @Test
    void hostnameNeverTriggersDnsLookup() {
        // Implementation invariant: a non-numeric, non-"localhost" host MUST NOT
        // be resolved. The only way to test this without intercepting DNS is by
        // observing that names which would resolve to loopback in /etc/hosts are
        // still treated as remote. We pick a name that no resolver would return.
        // (This test will fail if anyone "improves" isLocal to do hostname
        // resolution — that change would be a regression.)
        assertFalse(UrlSafetyChecker.isLocal(
                "http://this-host-must-never-be-resolved.invalid/"));
    }
}
