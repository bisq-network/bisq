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

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validates URLs before they are dispatched and decides whether a request can be
 * sent in the clear (loopback/link-local only) or must be routed via Tor.
 *
 * The matcher purposefully does NOT perform DNS lookups for non-numeric hosts;
 * doing so would leak the destination to the system resolver before Tor sees it.
 */
public final class UrlSafetyChecker {

    public static final class InvalidUrlException extends RuntimeException {
        public InvalidUrlException(String msg) {
            super(msg);
        }
    }

    private static final Pattern TRAILING_DOTS = Pattern.compile("\\.+$");

    private UrlSafetyChecker() {
    }

    /**
     * Parses {@code baseUrl} and returns the resulting URI. Only http and https are
     * accepted; the URI must carry an absolute, non-empty host. All other inputs
     * (file://, javascript:, ftp://, gopher://, missing host, userinfo, …) are
     * rejected so they cannot reach the network stack.
     */
    public static URI parseAndValidate(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new InvalidUrlException("baseUrl must not be null or empty");
        }
        URI uri;
        try {
            uri = new URI(baseUrl);
        } catch (URISyntaxException e) {
            // Do not echo the raw input — it may be a malformed credential string
            // and exception messages routinely end up in logs and crash reports.
            throw new InvalidUrlException("Malformed URL");
        }
        if (!uri.isAbsolute()) {
            throw new InvalidUrlException("URL must be absolute");
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new InvalidUrlException("URL is missing a scheme");
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            // scheme itself is safe to echo — credentials live in userinfo, not the scheme.
            throw new InvalidUrlException("Unsupported URL scheme: " + scheme);
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new InvalidUrlException("URL is missing a host");
        }
        if (uri.getUserInfo() != null) {
            // userinfo can be used to smuggle a different effective host past naïve
            // checks. Reject the URL but never echo it: the userinfo portion may
            // be a real password.
            throw new InvalidUrlException("Userinfo is not permitted in baseUrl");
        }
        return uri;
    }

    /**
     * Default: only loopback (127.0.0.0/8, ::1, "localhost") is treated as local.
     * Private LAN ranges still go through Tor. See
     * {@link #isLocal(URI, boolean)} to opt in to private ranges.
     */
    public static boolean isLocal(URI uri) {
        return isLocal(uri, false);
    }

    /**
     * Returns true iff {@code uri}'s host is on this machine or, when
     * {@code allowPrivateRanges} is true, on a private/link-local network
     * reachable directly without traversing the public Internet:
     *   - always: the literal "localhost" (and IPv6 equivalents) plus any
     *     loopback IP literal (127.0.0.0/8, ::1);
     *   - only if {@code allowPrivateRanges}:
     *       RFC1918 private IPv4 (10/8, 172.16/12, 192.168/16),
     *       IPv4 link-local (169.254/16),
     *       IPv6 link-local (fe80::/10),
     *       IPv6 unique-local (fc00::/7).
     *
     * Hostnames that would require a DNS lookup are NEVER considered local —
     * they could resolve to anything and answering would leak the lookup
     * outside Tor.
     */
    public static boolean isLocal(URI uri, boolean allowPrivateRanges) {
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        // strip the brackets URI keeps around IPv6 literals
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        String lower = host.toLowerCase(Locale.ROOT);
        if (lower.equals("localhost") || lower.equals("ip6-localhost") || lower.equals("ip6-loopback")) {
            return true;
        }
        if (!isNumericIp(host)) {
            return false;
        }
        try {
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isLoopbackAddress()) {
                return true;
            }
            if (!allowPrivateRanges) {
                return false;
            }
            if (addr.isLinkLocalAddress() || addr.isSiteLocalAddress()) {
                // isSiteLocalAddress covers RFC1918 IPv4 (10/8, 172.16/12, 192.168/16)
                // and the deprecated IPv6 fec0::/10. isLinkLocalAddress covers
                // 169.254/16 and fe80::/10.
                return true;
            }
            // IPv6 unique-local fc00::/7 (RFC4193) — not flagged by InetAddress.
            byte[] bytes = addr.getAddress();
            return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * Convenience: parses {@code baseUrl} then returns {@link #isLocal(URI)}.
     */
    public static boolean isLocal(String baseUrl) {
        return isLocal(parseAndValidate(baseUrl));
    }

    /**
     * True iff {@code uri}'s host is a Tor onion address. Onion hosts are only
     * resolvable through a Tor circuit, so callers must never attempt direct
     * connection nor system DNS resolution for them.
     */
    public static boolean isOnion(URI uri) {
        String host = uri.getHost();
        if (host == null) return false;
        // Strip trailing dots (FQDN form). "host.onion." resolves to the same
        // destination as "host.onion"; treating it as non-onion would let a
        // clearnet attempt leak the lookup to the system DNS resolver.
        String lower = TRAILING_DOTS.matcher(host.toLowerCase(Locale.ROOT)).replaceFirst("");
        return lower.endsWith(".onion");
    }

    private static boolean isNumericIp(String host) {
        // IPv6 literal contains a colon and only hex/digits/colons/dots
        if (host.indexOf(':') >= 0) {
            for (int i = 0; i < host.length(); i++) {
                char c = host.charAt(i);
                boolean ok = (c >= '0' && c <= '9')
                        || (c >= 'a' && c <= 'f')
                        || (c >= 'A' && c <= 'F')
                        || c == ':' || c == '.';
                if (!ok) return false;
            }
            return true;
        }
        // IPv4 literal: four dot-separated decimal octets in [0,255]
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String p : parts) {
            if (p.isEmpty() || p.length() > 3) return false;
            for (int i = 0; i < p.length(); i++) {
                char c = p.charAt(i);
                if (c < '0' || c > '9') return false;
            }
            int v = Integer.parseInt(p);
            if (v < 0 || v > 255) return false;
        }
        return true;
    }
}
