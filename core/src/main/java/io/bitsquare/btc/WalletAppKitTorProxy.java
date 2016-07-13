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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.Service;
import io.bitsquare.btc.listeners.AddressConfidenceListener;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.btc.listeners.TxConfidenceListener;
import io.bitsquare.common.Timer;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.common.handlers.ExceptionHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.storage.FileUtil;
import io.bitsquare.storage.Storage;
import io.bitsquare.user.Preferences;
import javafx.beans.property.*;
import org.bitcoinj.core.*;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.net.BlockingClientManager;
import org.bitcoinj.net.discovery.IrcDiscovery;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.net.Proxy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public class WalletAppKitTorProxy extends WalletAppKit {
    Proxy proxy;
    
    /**
     * Creates a new WalletAppKit, with a newly created {@link Context}. Files will be stored in the given directory.
     */
    public WalletAppKitTorProxy(NetworkParameters params, Proxy proxy, File directory, String filePrefix) {
        super(params, directory, filePrefix);
        this.proxy = proxy;
    }    
    
    protected PeerGroup createPeerGroup() throws TimeoutException {
//        System.setProperty("socksProxyHost", "127.0.0.1");
//        System.setProperty("socksProxyPort", "9050");
//        System.setProperty("socksProxyHost", proxy.address().getHostString());
//        System.setProperty("socksProxyPort", Integer.toString(proxy.address().getPort()));
        // TESTING: always use tor.
        if (true || useTor) {
            // discovery = new IrcDiscovery("#bitcoin");
            int CONNECT_TIMEOUT_MSEC = 60 * 1000;
            ProxySocketFactory proxySocketFactory = new ProxySocketFactory(proxy);
            BlockingClientManager mgr = new BlockingClientManager(proxySocketFactory);
            PeerGroup result = new PeerGroup(params, vChain, mgr);
            
            mgr.setConnectTimeoutMillis(CONNECT_TIMEOUT_MSEC);
            result.setConnectTimeoutMillis(CONNECT_TIMEOUT_MSEC);
            
            // We can't use TorDiscovery cuz we don't have a torClient object.
            // result.addPeerDiscovery(new TorDiscovery(params, torClient));
            return result;
        }
        else {
            return new PeerGroup(params, vChain);
        }
    }    
}
