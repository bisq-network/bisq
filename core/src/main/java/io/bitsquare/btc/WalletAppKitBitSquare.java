/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.btc;

import com.google.common.net.InetAddresses;
import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.net.BlockingClientManager;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.util.concurrent.TimeoutException;

public class WalletAppKitBitSquare extends WalletAppKit {
    private Socks5Proxy socks5Proxy;

    /**
     * Creates a new WalletAppKit, with a newly created {@link Context}. Files will be stored in the given directory.
     */
    public WalletAppKitBitSquare(NetworkParameters params, Socks5Proxy socks5Proxy, File directory, String filePrefix) {
        super(params, directory, filePrefix);
        this.socks5Proxy = socks5Proxy;
    }

    public Socks5Proxy getProxy() {
        return socks5Proxy;
    }

    protected PeerGroup createPeerGroup() throws TimeoutException {
        // no proxy case.
        if (socks5Proxy == null || isLocalHostFullNodeRunning()) {
            return super.createPeerGroup();
        } else {
            // proxy case.
            Proxy proxy = new Proxy(Proxy.Type.SOCKS,
                    new InetSocketAddress(socks5Proxy.getInetAddress().getHostName(),
                            socks5Proxy.getPort()));

            int CONNECT_TIMEOUT_MSEC = 60 * 1000;  // same value used in bitcoinj.
            ProxySocketFactory proxySocketFactory = new ProxySocketFactory(proxy);
            BlockingClientManager mgr = new BlockingClientManager(proxySocketFactory);
            PeerGroup peerGroup = new PeerGroup(params, vChain, mgr);

            mgr.setConnectTimeoutMillis(CONNECT_TIMEOUT_MSEC);
            peerGroup.setConnectTimeoutMillis(CONNECT_TIMEOUT_MSEC);

            return peerGroup;
        }
    }

    private boolean isLocalHostFullNodeRunning() {
        // We check first if a local node is running, if so we connect direct without proxy.
        // Borrowed form PeerGroup.maybeCheckForLocalhostPeer()
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(InetAddresses.forString("127.0.0.1"), params.getPort()), PeerGroup.DEFAULT_CONNECT_TIMEOUT_MILLIS);
            try {
                socket.close();
            } catch (IOException ignore) {
            }
            return true;
        } catch (IOException e) {
            log.debug("Localhost peer not detected.");
            return false;
        }
    }
}
