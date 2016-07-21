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

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.net.BlockingClientManager;
import org.bitcoinj.core.PeerGroup;

import java.io.File;
import java.net.Proxy;
import java.net.InetSocketAddress;
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
        if(socks5Proxy == null) {
            return super.createPeerGroup();
        }
        
        // proxy case.
        Proxy proxy = new Proxy ( Proxy.Type.SOCKS,
                                  new InetSocketAddress(socks5Proxy.getInetAddress().getHostName(),
                                                        socks5Proxy.getPort() ) );
        
        int CONNECT_TIMEOUT_MSEC = 60 * 1000;  // same value used in bitcoinj.
        ProxySocketFactory proxySocketFactory = new ProxySocketFactory(proxy);
        BlockingClientManager mgr = new BlockingClientManager(proxySocketFactory);
        PeerGroup peerGroup = new PeerGroup(params, vChain, mgr);
        
        mgr.setConnectTimeoutMillis(CONNECT_TIMEOUT_MSEC);
        peerGroup.setConnectTimeoutMillis(CONNECT_TIMEOUT_MSEC);

        // This enables remote DNS lookup of peers over socks5 proxy.
        // It is slower, but more private.
        // This could be turned into a user option.
        this.setDiscovery( new SeedPeersSocks5Dns(socks5Proxy, params) );
        
        return peerGroup;
    }    
}
