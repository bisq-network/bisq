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

import org.apache.http.HttpHost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.protocol.HttpContext;

import javax.net.ssl.SSLContext;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;

import java.io.IOException;

// This class is adapted from
//   http://stackoverflow.com/a/25203021/5616248
//
// This class routes connections over Socks, and avoids resolving hostnames locally.
class SocksSSLConnectionSocketFactory extends SSLConnectionSocketFactory {

    public SocksSSLConnectionSocketFactory(final SSLContext sslContext) {

        // TODO check alternative to deprecated call
        // Only allow connection's to site's with valid certs.
        super(sslContext, STRICT_HOSTNAME_VERIFIER);

        // Or to allow "insecure" (eg self-signed certs)
        // super(sslContext, ALLOW_ALL_HOSTNAME_VERIFIER);
    }

    /**
     * creates an unconnected Socks Proxy socket
     */
    @Override
    public Socket createSocket(final HttpContext context) throws IOException {
        InetSocketAddress socksaddr = (InetSocketAddress) context.getAttribute("socks.address");
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, socksaddr);
        return new Socket(proxy);
    }

    /**
     * connects a Socks Proxy socket and passes hostname to proxy without resolving it locally.
     */
    @Override
    public Socket connectSocket(int connectTimeout, Socket socket, HttpHost host, InetSocketAddress remoteAddress,
                                InetSocketAddress localAddress, HttpContext context) throws IOException {
        // Convert address to unresolved
        InetSocketAddress unresolvedRemote = InetSocketAddress
                .createUnresolved(host.getHostName(), remoteAddress.getPort());
        return super.connectSocket(connectTimeout, socket, host, unresolvedRemote, localAddress, context);
    }
}
