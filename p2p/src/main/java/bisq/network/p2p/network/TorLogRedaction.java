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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes the relays and bridges from the logs which "Zip log files" gives to the user to share. A bridge which a user
 * got privately is blocked once it is known.
 */
public final class TorLogRedaction {
    // The names of relays and bridges in a log of Tor, see withoutRelayIdentities
    private static final Pattern BRIDGE_NICKNAME = Pattern.compile("((?:new bridge descriptor|Bridge) ')[^']*(')");
    private static final Pattern NICKNAME_WITH_FINGERPRINT =
            Pattern.compile("\\b[A-Za-z0-9]{1,19} \\(\\$[0-9A-Fa-f]{40}\\)");
    private static final Pattern RELAY_FINGERPRINT =
            Pattern.compile("\\$?\\b[0-9A-Fa-f]{40}\\b(?:[~=][A-Za-z0-9]{1,19})?");
    private static final Pattern RELAY_IDENTITY = Pattern.compile("\\[[A-Za-z0-9+/]{43}=?]");
    // An ed25519 identity without brackets, as in "ID=... RSA_ID=..." of a warning about a connection to a relay
    private static final Pattern BARE_RELAY_IDENTITY =
            Pattern.compile("(?<![A-Za-z0-9+/])[A-Za-z0-9+/]{43}(?![A-Za-z0-9+/=])");
    // The arguments of a bridge line which name the bridge or the way to it
    private static final Pattern BRIDGE_ARGUMENT =
            Pattern.compile("\\b(cert|url|front|fronts)=(?:\"[^\"]*\"|[^\\s;)'\"]+)");
    // A loopback address stays, so that support sees the ports of the listeners. It names no relay or bridge.
    private static final Pattern IPV6_ADDRESS =
            Pattern.compile("\\[(?!::1])[0-9A-Fa-f.]*:[0-9A-Fa-f:.]*](?::\\d{1,5})?");
    // An IPv6 address without brackets, as in "host <id> at 2001:db8::77:443" of a bootstrap warning, also one which
    // ends with "::". It has "::" or at least three colons, unlike a time such as 09:07:36.000.
    private static final Pattern UNDECORATED_IPV6_ADDRESS = Pattern.compile("(?<![0-9A-Za-z:.])" +
            "(?!::1(?![0-9A-Za-z:]))" +
            "(?=[0-9A-Fa-f.]*:[0-9A-Fa-f.]*:[0-9A-Fa-f.]*:|[0-9A-Fa-f:.]*::)" +
            "[0-9A-Fa-f:.]*[0-9A-Fa-f](?:::)?(?![0-9A-Za-z:])");
    // Tor logs the messages of a pluggable transport in lines which name the managed proxy. lyrebird writes the host
    // of a webtunnel bridge there ("Using TLS SNI: <host>"), the URL and front of a meek bridge, and errors with host
    // names.
    private static final Pattern MANAGED_PROXY_LINE = Pattern.compile("(?m)^.*\\b[Mm]anaged proxy\\b.*$");
    private static final Pattern URL = Pattern.compile("\\b[A-Za-z][A-Za-z0-9+.-]*://[^\\s\"'<>]+");
    // A host name ends with a top level domain of letters, or of Punycode such as xn--p1ai. A file name such as
    // lyrebird.exe in the path of the proxy follows a separator, and stays.
    private static final Pattern HOST_NAME = Pattern.compile("(?<![A-Za-z0-9_./\\\\-])" +
            "(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+" +
            "(?:[A-Za-z]{2,63}|[Xx][Nn]--[A-Za-z0-9-]{0,58}[A-Za-z0-9])(?![A-Za-z0-9_-])");
    // A version of Tor, such as 0.4.9.15, also has four numbers with dots, and starts with 0. Loopback addresses stay.
    private static final Pattern IPV4_ADDRESS =
            Pattern.compile("\\b(?!0\\.|127\\.)(?:\\d{1,3}\\.){3}\\d{1,3}\\b(?::\\d{1,5})?");
    // The relay or bridge of a bootstrap warning in the log of the node, see withoutBootstrapEventHosts
    private static final Pattern BOOTSTRAP_EVENT_HOST = Pattern.compile("\\b(HOSTID|HOSTADDR)=\"[^\"\\s,)]*");

    private TorLogRedaction() {
    }

    /**
     * The text of a log of Tor without the fingerprints, nicknames, identities and addresses of relays and bridges.
     * Tor names a relay or bridge by its fingerprint, as $fingerprint~nickname or as nickname ($fingerprint), by its
     * ed25519 identity, in brackets or not, and by its address, an IPv6 address in brackets or not, also at its notice
     * level, for example in the warning about a bridge which it cannot reach. Tor 0.4.9 logs no bridge line, but the
     * arguments of one which name the bridge, such as cert and url, are removed too, and so are URLs and host names
     * in the messages of a pluggable transport. A bridge which a user got privately is blocked once it is known, and
     * the user shares the logs. The versions of Tor, which start with 0, and loopback addresses stay.
     */
    public static String withoutRelayIdentities(String torLogText) {
        String text = MANAGED_PROXY_LINE.matcher(torLogText)
                .replaceAll(line -> Matcher.quoteReplacement(withoutUrlsAndHostNames(line.group())));
        text = BRIDGE_NICKNAME.matcher(text).replaceAll("$1[relay]$2");
        text = NICKNAME_WITH_FINGERPRINT.matcher(text).replaceAll("[relay]");
        text = RELAY_FINGERPRINT.matcher(text).replaceAll("[relay]");
        text = RELAY_IDENTITY.matcher(text).replaceAll("[identity]");
        text = BARE_RELAY_IDENTITY.matcher(text).replaceAll("[identity]");
        text = BRIDGE_ARGUMENT.matcher(text).replaceAll("$1=[redacted]");
        text = IPV6_ADDRESS.matcher(text).replaceAll("[address]");
        text = UNDECORATED_IPV6_ADDRESS.matcher(text).replaceAll("[address]");
        return IPV4_ADDRESS.matcher(text).replaceAll("[address]");
    }

    private static String withoutUrlsAndHostNames(String line) {
        String text = URL.matcher(line).replaceAll("[url]");
        return HOST_NAME.matcher(text).replaceAll("[host]");
    }

    /**
     * The text of a log of the node without the relay or bridge of a bootstrap warning: the Tor library logs the
     * bootstrap events of Tor, and the event of a warning names in HOSTID and HOSTADDR the relay or bridge which Tor
     * could not reach.
     */
    public static String withoutBootstrapEventHosts(String nodeLogText) {
        return BOOTSTRAP_EVENT_HOST.matcher(nodeLogText).replaceAll("$1=\"[relay]");
    }
}
