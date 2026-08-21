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

import org.apache.http.conn.DnsResolver;

import java.net.InetAddress;
import java.net.UnknownHostException;

// This class is adapted from
//   http://stackoverflow.com/a/25203021/5616248
class FakeDnsResolver implements DnsResolver {
    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        // The custom socket factories never use this resolver — DNS is handled by
        // the SOCKS proxy. We still must return a non-null value because Apache's
        // connection manager requires it. Return the unspecified address (0.0.0.0)
        // so that any accidental connect attempt fails locally rather than reaching
        // a real host (the previous 1.1.1.1 placeholder was a public Cloudflare IP
        // and could mask leaks during testing).
        return new InetAddress[]{InetAddress.getByAddress(new byte[]{0, 0, 0, 0})};
    }
}
