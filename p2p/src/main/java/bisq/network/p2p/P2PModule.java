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

package bisq.network.p2p;

import bisq.network.NetworkOptionKeys;
import bisq.network.Socks5ProxyProvider;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.ConnectionConfig;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.BanList;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.peers.getdata.RequestDataManager;
import bisq.network.p2p.peers.keepalive.KeepAliveManager;
import bisq.network.p2p.peers.peerexchange.PeerExchangeManager;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;
import bisq.network.p2p.storage.persistence.PersistableNetworkPayloadListService;
import bisq.network.p2p.storage.persistence.ProtectedDataStoreService;
import bisq.network.p2p.storage.persistence.ResourceDataStoreService;

import bisq.common.app.AppModule;
import bisq.common.config.Config;

import org.springframework.core.env.Environment;

import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

import java.time.Clock;

import java.io.File;

import java.util.List;

import static bisq.common.config.Config.BAN_LIST;
import static bisq.common.config.Config.NODE_PORT;
import static bisq.common.config.Config.TOR_DIR;
import static bisq.common.config.Config.USE_LOCALHOST_FOR_P2P;
import static com.google.inject.name.Names.named;

public class P2PModule extends AppModule {

    public P2PModule(Environment environment, Config config) {
        super(environment, config);
    }

    @Override
    protected void configure() {
        bind(Clock.class).toInstance(Clock.systemDefaultZone());
        bind(P2PService.class).in(Singleton.class);
        bind(PeerManager.class).in(Singleton.class);
        bind(P2PDataStorage.class).in(Singleton.class);
        bind(AppendOnlyDataStoreService.class).in(Singleton.class);
        bind(ProtectedDataStoreService.class).in(Singleton.class);
        bind(PersistableNetworkPayloadListService.class).in(Singleton.class);
        bind(ResourceDataStoreService.class).in(Singleton.class);
        bind(RequestDataManager.class).in(Singleton.class);
        bind(PeerExchangeManager.class).in(Singleton.class);
        bind(KeepAliveManager.class).in(Singleton.class);
        bind(Broadcaster.class).in(Singleton.class);
        bind(BanList.class).in(Singleton.class);
        bind(ConnectionConfig.class).in(Singleton.class);
        bind(NetworkNode.class).toProvider(NetworkNodeProvider.class).in(Singleton.class);
        bind(Socks5ProxyProvider.class).in(Singleton.class);

        requestStaticInjection(Connection.class);

        bindConstant().annotatedWith(Names.named(USE_LOCALHOST_FOR_P2P)).to(config.isUseLocalhostForP2P());

        bind(File.class).annotatedWith(named(TOR_DIR)).toInstance(config.getTorDir());

        bind(int.class).annotatedWith(Names.named(NODE_PORT)).toInstance(config.getNodePort());

        Integer maxConnections = environment.getProperty(NetworkOptionKeys.MAX_CONNECTIONS, int.class, P2PService.MAX_CONNECTIONS_DEFAULT);
        bind(int.class).annotatedWith(Names.named(NetworkOptionKeys.MAX_CONNECTIONS)).toInstance(maxConnections);

        Integer networkId = environment.getProperty(NetworkOptionKeys.NETWORK_ID, int.class, 1);
        bind(int.class).annotatedWith(Names.named(NetworkOptionKeys.NETWORK_ID)).toInstance(networkId);
        bind(new TypeLiteral<List<String>>(){}).annotatedWith(named(BAN_LIST)).toInstance(config.getBanList());
        bindConstant().annotatedWith(named(NetworkOptionKeys.SOCKS_5_PROXY_BTC_ADDRESS)).to(environment.getRequiredProperty(NetworkOptionKeys.SOCKS_5_PROXY_BTC_ADDRESS));
        bindConstant().annotatedWith(named(NetworkOptionKeys.SOCKS_5_PROXY_HTTP_ADDRESS)).to(environment.getRequiredProperty(NetworkOptionKeys.SOCKS_5_PROXY_HTTP_ADDRESS));
        bindConstant().annotatedWith(named(NetworkOptionKeys.TORRC_FILE)).to(environment.getRequiredProperty(NetworkOptionKeys.TORRC_FILE));
        bindConstant().annotatedWith(named(NetworkOptionKeys.TORRC_OPTIONS)).to(environment.getRequiredProperty(NetworkOptionKeys.TORRC_OPTIONS));
        bindConstant().annotatedWith(named(NetworkOptionKeys.EXTERNAL_TOR_CONTROL_PORT)).to(environment.getRequiredProperty(NetworkOptionKeys.EXTERNAL_TOR_CONTROL_PORT));
        bindConstant().annotatedWith(named(NetworkOptionKeys.EXTERNAL_TOR_PASSWORD)).to(environment.getRequiredProperty(NetworkOptionKeys.EXTERNAL_TOR_PASSWORD));
        bindConstant().annotatedWith(named(NetworkOptionKeys.EXTERNAL_TOR_COOKIE_FILE)).to(environment.getRequiredProperty(NetworkOptionKeys.EXTERNAL_TOR_COOKIE_FILE));
        bindConstant().annotatedWith(named(NetworkOptionKeys.EXTERNAL_TOR_USE_SAFECOOKIE)).to(environment.containsProperty(NetworkOptionKeys.EXTERNAL_TOR_USE_SAFECOOKIE));
        bindConstant().annotatedWith(named(NetworkOptionKeys.TOR_STREAM_ISOLATION)).to(environment.containsProperty(NetworkOptionKeys.TOR_STREAM_ISOLATION));
        bindConstant().annotatedWith(named(NetworkOptionKeys.MSG_THROTTLE_PER_SEC)).to(environment.getRequiredProperty(NetworkOptionKeys.MSG_THROTTLE_PER_SEC));
        bindConstant().annotatedWith(named(NetworkOptionKeys.MSG_THROTTLE_PER_10_SEC)).to(environment.getRequiredProperty(NetworkOptionKeys.MSG_THROTTLE_PER_10_SEC));
        bindConstant().annotatedWith(named(NetworkOptionKeys.SEND_MSG_THROTTLE_TRIGGER)).to(environment.getRequiredProperty(NetworkOptionKeys.SEND_MSG_THROTTLE_TRIGGER));
        bindConstant().annotatedWith(named(NetworkOptionKeys.SEND_MSG_THROTTLE_SLEEP)).to(environment.getRequiredProperty(NetworkOptionKeys.SEND_MSG_THROTTLE_SLEEP));
        bindConstant().annotatedWith(named("MAX_SEQUENCE_NUMBER_MAP_SIZE_BEFORE_PURGE")).to(1000);
    }
}
