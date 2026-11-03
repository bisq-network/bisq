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

package bisq.network.p2p.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TorLogRedactionTest {
    private static final String BRIDGE_FINGERPRINT = "0123456789ABCDEF0123456789ABCDEF01234567";
    private static final String BRIDGE_IDENTITY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";

    // A log of Tor 0.4.9 with a made up bridge: the warning as Tor writes it when it cannot reach the bridge, and the
    // other forms in which Tor names a relay or bridge
    @Test
    void theLogOfTorLosesTheNamesOfRelaysAndBridges() {
        String log = String.join("\n",
                "Oct 31 09:05:56.342 [notice] Tor 0.4.9.15 (git-4d8efff53a86670b) running on Linux with Libevent " +
                        "2.1.13-stable, OpenSSL 3.5.9, Zlib 1.3.1, Liblzma N/A, Libzstd N/A and Glibc 2.43 as libc.",
                "Oct 31 09:07:36.000 [warn] Problem bootstrapping. Stuck at 5% (conn): Connecting to a relay. " +
                        "(Connection refused; CONNECTREFUSED; count 1; recommendation warn; host " +
                        BRIDGE_FINGERPRINT + " at 192.0.2.77:44321)",
                "Oct 31 09:08:01.000 [notice] new bridge descriptor 'bridgenick' (fresh): $" + BRIDGE_FINGERPRINT +
                        "~bridgenick [" + BRIDGE_IDENTITY + "] at 192.0.2.77 and [2001:db8::77]",
                "Oct 31 09:08:02.000 [notice] Bridge at '[2001:db8::77]:443' isn't reachable by our firewall policy.",
                "Oct 31 09:08:03.000 [notice] Selected new guard bridgenick ($" + BRIDGE_FINGERPRINT + ")",
                "Oct 31 09:08:05.000 [warn] Bridge line obfs4 192.0.2.78:443 " + BRIDGE_FINGERPRINT +
                        " cert=AbCdEf0123+/xyz iat-mode=0 and webtunnel [2001:db8::78]:443 " +
                        "url=\"https://example.org/path\"",
                "Oct 31 09:08:06.000 [notice] Bridge 'bridgenick' has both an IPv4 and an IPv6 address.",
                "Oct 31 09:08:08.000 [warn] Tried connecting to router at 192.0.2.79:443 ID=" + BRIDGE_IDENTITY +
                        " RSA_ID=" + BRIDGE_FINGERPRINT + ", but RSA + ed25519 identity keys were not as expected",
                "Oct 31 09:08:07.000 [warn] Bridge line snowflake fronts=front1.example,front2.example ice=stun",
                "Oct 31 09:08:04.000 [notice] This version of Tor (0.4.9.15) is recommended. Recommended versions " +
                        "are: 0.4.8.10,0.4.8.11,0.4.9.15");

        String redacted = TorLogRedaction.withoutRelayIdentities(log);

        assertFalse(redacted.contains(BRIDGE_FINGERPRINT), redacted);
        assertFalse(redacted.contains(BRIDGE_IDENTITY), redacted);
        assertFalse(redacted.contains("bridgenick"), redacted);
        assertFalse(redacted.contains("192.0.2.77"), redacted);
        assertFalse(redacted.contains("2001:db8"), redacted);
        assertFalse(redacted.contains("192.0.2.78"), redacted);
        assertFalse(redacted.contains("AbCdEf0123"), redacted);
        assertFalse(redacted.contains("example.org"), redacted);
        assertFalse(redacted.contains("front1.example"), redacted);
        assertFalse(redacted.contains("front2.example"), redacted);
        assertTrue(redacted.contains("cert=[redacted] iat-mode=0"), redacted);
        assertTrue(redacted.contains("Tor 0.4.9.15 (git-4d8efff53a86670b)"), redacted);
        assertTrue(redacted.contains("OpenSSL 3.5.9, Zlib 1.3.1"), redacted);
        assertTrue(redacted.contains("Connection refused; CONNECTREFUSED; count 1; recommendation warn; host [relay] " +
                "at [address])"), redacted);
        assertTrue(redacted.contains("new bridge descriptor '[relay]' (fresh): [relay] [identity] at [address] and " +
                "[address]"), redacted);
        assertTrue(redacted.contains("Bridge at '[address]' isn't reachable"), redacted);
        assertTrue(redacted.contains("Selected new guard [relay]"), redacted);
        assertTrue(redacted.contains("This version of Tor (0.4.9.15) is recommended. Recommended versions are: " +
                "0.4.8.10,0.4.8.11,0.4.9.15"), redacted);
    }

    // Tor writes the address of the bootstrap warning, and of a refusal of an https proxy, without brackets (fmt_addr),
    // so an IPv6 address there has no brackets
    @Test
    void theLogOfTorLosesAnIpv6AddressWithoutBrackets() {
        String log = String.join("\n",
                "Oct 31 09:07:36.000 [warn] Problem bootstrapping. Stuck at 5% (conn): Connecting to a relay. " +
                        "(Connection refused; CONNECTREFUSED; count 1; recommendation warn; host " +
                        BRIDGE_FINGERPRINT + " at 2001:db8::77:44321)",
                "Oct 31 09:07:37.000 [warn] The https proxy refused to allow connection to 2001:db8:0:0:0:0:0:78 " +
                        "(status code 403, \"Forbidden\"). Closing.",
                "Oct 31 09:07:38.000 [warn] Problem bootstrapping. Stuck at 5% (conn): Connecting to a relay. " +
                        "(No route to host; NOROUTE; count 2; recommendation warn; host " + BRIDGE_FINGERPRINT +
                        " at ::ffff:192.0.2.79:443)",
                "Oct 31 09:07:39.000 [warn] The https proxy refused to allow connection to 2600:: " +
                        "(status code 403, \"Forbidden\"). Closing.");

        String redacted = TorLogRedaction.withoutRelayIdentities(log);

        assertFalse(redacted.contains("2001:db8"), redacted);
        assertFalse(redacted.contains("192.0.2.79"), redacted);
        assertTrue(redacted.startsWith("Oct 31 09:07:36.000 [warn] Problem bootstrapping."), redacted);
        assertTrue(redacted.contains("recommendation warn; host [relay] at [address])"), redacted);
        assertTrue(redacted.contains("Oct 31 09:07:37.000 [warn] The https proxy refused to allow connection to " +
                "[address] (status code 403"), redacted);
        assertTrue(redacted.contains("host [relay] at [address])\n"), redacted);
        assertFalse(redacted.contains("2600"), redacted);
        assertTrue(redacted.endsWith("Oct 31 09:07:39.000 [warn] The https proxy refused to allow connection to " +
                "[address] (status code 403, \"Forbidden\"). Closing."), redacted);
    }

    // Tor logs a LOG line of a pluggable transport as 'Managed proxy "%s": %s'. The messages are those of lyrebird:
    // webtunnel names the host of the bridge, meek its URL and front, and Go errors name hosts.
    @Test
    void theLogOfTorLosesTheUrlsAndHostNamesOfAPluggableTransport() {
        String proxy = "Managed proxy \"/home/user/.local/share/Bisq/btc_mainnet/tor/pluggable_transports/lyrebird\": ";
        String log = String.join("\n",
                "Oct 31 09:08:10.000 [notice] " + proxy + "Using TLS SNI: bridge.example.org",
                "Oct 31 09:08:11.000 [warn] " + proxy + "Failed to connect to https://meek.example.net/path using " +
                        "front cdn.example.com: dial tcp: lookup cdn.example.com: no such host",
                "Oct 31 09:08:12.000 [err] Managed proxy \"C:\\Program Files\\Bisq\\tor\\lyrebird.exe\": " +
                        "Error dialing: dial tcp: lookup Bridge-1.Example.ORG: no such host",
                "Oct 31 09:08:12.500 [notice] " + proxy + "Using TLS SNI: bridge.xn--p1ai",
                "Oct 31 09:08:13.000 [notice] Tor can't help you if you use it wrong! Learn how to be safe at " +
                        "https://support.torproject.org/faq/staying-anonymous/");

        String redacted = TorLogRedaction.withoutRelayIdentities(log);

        assertFalse(redacted.toLowerCase().contains("example."), redacted);
        assertTrue(redacted.contains(proxy + "Using TLS SNI: [host]"), redacted);
        assertTrue(redacted.contains(proxy + "Failed to connect to [url] using front [host]: dial tcp: " +
                "lookup [host]: no such host"), redacted);
        assertTrue(redacted.contains("Managed proxy \"C:\\Program Files\\Bisq\\tor\\lyrebird.exe\": Error dialing: " +
                "dial tcp: lookup [host]: no such host"), redacted);
        assertTrue(redacted.contains("09:08:12.500 [notice] " + proxy + "Using TLS SNI: [host]"), redacted);
        assertFalse(redacted.contains("p1ai"), redacted);
        // Lines of Tor itself keep their host names
        assertTrue(redacted.endsWith("https://support.torproject.org/faq/staying-anonymous/"), redacted);
    }

    // Support looks for the ports of the listeners, and a loopback address names no relay or bridge
    @Test
    void theLogOfTorKeepsLoopbackAddresses() {
        String log = String.join("\n",
                "Oct 31 09:05:57.000 [notice] Opened Socks listener connection (ready) on 127.0.0.1:9050",
                "Oct 31 09:05:57.000 [notice] Opened Control listener connection (ready) on [::1]:9051",
                "Oct 31 09:05:58.000 [notice] Bound to ::1 and 192.0.2.80:443 and [2001:db8::80]:443");

        String redacted = TorLogRedaction.withoutRelayIdentities(log);

        assertTrue(redacted.contains("(ready) on 127.0.0.1:9050"), redacted);
        assertTrue(redacted.contains("(ready) on [::1]:9051"), redacted);
        assertTrue(redacted.endsWith("Bound to ::1 and [address] and [address]"), redacted);
    }

    // The bootstrap event of a warning as the Tor library logs it into the log of the node
    @Test
    void theLogOfTheNodeLosesTheBridgeOfABootstrapWarning() {
        String log = String.join("\n",
                "Oct 31 10:35:54.240 [TorControlReader] INFO  b.n.t.c.BootstrapService: Tor bootstrap event: " +
                        "TorBootstrapEvent(progress=5, tag=conn, summary=Connecting to a relay\" " +
                        "WARNING=\"Connection refused\" REASON=CONNECTREFUSED COUNT=3 RECOMMENDATION=warn HOSTID=\"" +
                        BRIDGE_FINGERPRINT + "\" HOSTADDR=\"192.0.2.77:44321, timestamp=2026-10-31T10:35:54.240Z)",
                "Oct 31 10:36:00.000 [JavaFX Application Thread] INFO  b.c.a.AccountAgeWitnessService: witness hash " +
                        BRIDGE_FINGERPRINT.toLowerCase() + ", Bitcoin node 192.0.2.8:8333");

        String redacted = TorLogRedaction.withoutBootstrapEventHosts(log);

        assertTrue(redacted.contains("HOSTID=\"[relay]\" HOSTADDR=\"[relay], timestamp="), redacted);
        assertTrue(redacted.contains("WARNING=\"Connection refused\" REASON=CONNECTREFUSED"), redacted);
        // The rest of the log of the node stays as it is
        assertTrue(redacted.endsWith("witness hash " + BRIDGE_FINGERPRINT.toLowerCase() +
                ", Bitcoin node 192.0.2.8:8333"), redacted);
    }

    // At debug level, the Tor library logs the raw event of the control port, here with an IPv6 address
    @Test
    void theLogOfTheNodeLosesTheBridgeOfARawBootstrapEvent() {
        String log = "Oct 31 10:35:54.240 [TorControlReader] DEBUG b.n.t.c.TorControlReader: Received reply from Tor " +
                "control server: 650 STATUS_CLIENT WARN BOOTSTRAP PROGRESS=5 TAG=conn " +
                "SUMMARY=\"Connecting to a relay\" WARNING=\"Connection refused\" REASON=CONNECTREFUSED COUNT=1 " +
                "RECOMMENDATION=warn HOSTID=\"" +
                BRIDGE_FINGERPRINT + "\" HOSTADDR=\"2001:db8::77:44321\"";

        String redacted = TorLogRedaction.withoutBootstrapEventHosts(log);

        assertTrue(redacted.endsWith("RECOMMENDATION=warn HOSTID=\"[relay]\" HOSTADDR=\"[relay]\""), redacted);
    }
}
