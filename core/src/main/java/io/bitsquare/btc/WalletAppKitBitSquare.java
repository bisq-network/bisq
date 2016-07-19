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

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.net.BlockingClientManager;
import org.bitcoinj.net.discovery.IrcDiscovery;
import org.bitcoinj.core.PeerGroup;

import java.io.File;
import java.net.Proxy;
import java.util.concurrent.TimeoutException;

public class WalletAppKitBitSquare extends WalletAppKit {
    private Proxy proxy;
    
    /**
     * Creates a new WalletAppKit, with a newly created {@link Context}. Files will be stored in the given directory.
     */
    public WalletAppKitBitSquare(NetworkParameters params, Proxy proxy, File directory, String filePrefix) {
        super(params, directory, filePrefix);
        this.proxy = proxy;
    }
    
    public Proxy getProxy() {
        return proxy;
    }
    
    protected PeerGroup createPeerGroup() throws TimeoutException {
        int CONNECT_TIMEOUT_MSEC = 60 * 1000;
        ProxySocketFactory proxySocketFactory = new ProxySocketFactory(proxy);
        BlockingClientManager mgr = new BlockingClientManager(proxySocketFactory);
        PeerGroup result = new PeerGroup(params, vChain, mgr);
        
        mgr.setConnectTimeoutMillis(CONNECT_TIMEOUT_MSEC);
        result.setConnectTimeoutMillis(CONNECT_TIMEOUT_MSEC);
        
        // result.addPeerDiscovery(new OnionSeedPeers(params));
        return result;
    }    
}
