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

import bisq.network.tor.TorSocksProxyFactory;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import java.io.IOException;

/**
 * A socket which connects to its target through a SOCKS5 proxy with SOCKS credentials derived from a stream ID. Tor
 * puts streams with different credentials on different circuits. The SOCKS client of the Java runtime cannot send
 * credentials for each socket: it asks the global {@link java.net.Authenticator}, or sends the name of the account
 * of the operating system, so all its streams share their circuits.
 * <p>
 * The handshake offers only username and password authentication and fails if the proxy does not accept it, so that
 * a stream never continues without its credentials. The target is sent as a domain name, so that the proxy resolves
 * it.
 */
class IsolatingSocksSocket extends Socket {
    // The attribute of the HTTP context which holds the stream ID. Without it, the HTTP client uses the SOCKS client
    // of the Java runtime.
    static final String STREAM_ID_ATTRIBUTE = "socks.streamId";

    private final InetSocketAddress socksAddress;
    private final String streamId;

    IsolatingSocksSocket(InetSocketAddress socksAddress, String streamId) {
        this.socksAddress = socksAddress;
        this.streamId = streamId;
    }

    // The timeout limits the connect to the proxy and each read of the handshake, which waits until the proxy has
    // connected to the target.
    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
        if (!(endpoint instanceof InetSocketAddress target)) {
            throw new IllegalArgumentException("Unsupported address type: " + endpoint);
        }
        super.connect(socksAddress, timeout);
        try {
            int soTimeout = getSoTimeout();
            setSoTimeout(timeout);
            TorSocksProxyFactory.performHandshake(this, target.getHostString(), target.getPort(), streamId);
            setSoTimeout(soTimeout);
        } catch (IOException | RuntimeException e) {
            try {
                close();
            } catch (IOException closeException) {
                e.addSuppressed(closeException);
            }
            throw e;
        }
    }
}
