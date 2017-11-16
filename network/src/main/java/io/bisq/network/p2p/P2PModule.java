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

package io.bisq.network.p2p;

import com.google.inject.Singleton;
import com.google.inject.name.Names;
import io.bisq.common.app.AppModule;
import io.bisq.network.NetworkOptionKeys;
import io.bisq.network.Socks5ProxyProvider;
import io.bisq.network.p2p.network.NetworkNode;
import io.bisq.network.p2p.peers.BanList;
import io.bisq.network.p2p.peers.Broadcaster;
import io.bisq.network.p2p.peers.PeerManager;
import io.bisq.network.p2p.peers.getdata.RequestDataManager;
import io.bisq.network.p2p.peers.keepalive.KeepAliveManager;
import io.bisq.network.p2p.peers.peerexchange.PeerExchangeManager;
import io.bisq.network.p2p.storage.P2PDataStorage;
import org.springframework.core.env.Environment;

import java.io.File;

import static com.google.inject.name.Names.named;


public class P2PModule extends AppModule {

    public P2PModule(Environment environment) {
        super(environment);
    }

    @Override
    protected void configure() {
        bind(P2PService.class).in(Singleton.class);
        bind(PeerManager.class).in(Singleton.class);
        bind(P2PDataStorage.class).in(Singleton.class);
        bind(RequestDataManager.class).in(Singleton.class);
        bind(PeerExchangeManager.class).in(Singleton.class);
        bind(KeepAliveManager.class).in(Singleton.class);
        bind(Broadcaster.class).in(Singleton.class);
        bind(BanList.class).in(Singleton.class);
        bind(NetworkNode.class).toProvider(NetworkNodeProvider.class).in(Singleton.class);

        bind(Socks5ProxyProvider.class).in(Singleton.class);

        Boolean useLocalhostForP2P = environment.getProperty(NetworkOptionKeys.USE_LOCALHOST_FOR_P2P, boolean.class, false);
        bind(boolean.class).annotatedWith(Names.named(NetworkOptionKeys.USE_LOCALHOST_FOR_P2P)).toInstance(useLocalhostForP2P);

        File torDir = new File(environment.getRequiredProperty(NetworkOptionKeys.TOR_DIR));
        bind(File.class).annotatedWith(named(NetworkOptionKeys.TOR_DIR)).toInstance(torDir);

        // use a fixed port as arbitrator use that for his ID
        Integer port = environment.getProperty(NetworkOptionKeys.PORT_KEY, int.class, 9999);
        bind(int.class).annotatedWith(Names.named(NetworkOptionKeys.PORT_KEY)).toInstance(port);

        Integer maxConnections = environment.getProperty(NetworkOptionKeys.MAX_CONNECTIONS, int.class, P2PService.MAX_CONNECTIONS_DEFAULT);
        bind(int.class).annotatedWith(Names.named(NetworkOptionKeys.MAX_CONNECTIONS)).toInstance(maxConnections);

        Integer networkId = environment.getProperty(NetworkOptionKeys.NETWORK_ID, int.class, 1);
        bind(int.class).annotatedWith(Names.named(NetworkOptionKeys.NETWORK_ID)).toInstance(networkId);
        bindConstant().annotatedWith(named(NetworkOptionKeys.SEED_NODES_KEY)).to(environment.getRequiredProperty(NetworkOptionKeys.SEED_NODES_KEY));
        bindConstant().annotatedWith(named(NetworkOptionKeys.MY_ADDRESS)).to(environment.getRequiredProperty(NetworkOptionKeys.MY_ADDRESS));
        bindConstant().annotatedWith(named(NetworkOptionKeys.BAN_LIST)).to(environment.getRequiredProperty(NetworkOptionKeys.BAN_LIST));
        bindConstant().annotatedWith(named(NetworkOptionKeys.SOCKS_5_PROXY_BTC_ADDRESS)).to(environment.getRequiredProperty(NetworkOptionKeys.SOCKS_5_PROXY_BTC_ADDRESS));
        bindConstant().annotatedWith(named(NetworkOptionKeys.SOCKS_5_PROXY_HTTP_ADDRESS)).to(environment.getRequiredProperty(NetworkOptionKeys.SOCKS_5_PROXY_HTTP_ADDRESS));
    }
}
