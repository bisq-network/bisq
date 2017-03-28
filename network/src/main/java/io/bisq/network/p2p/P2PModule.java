/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.network.p2p;

import com.google.inject.Singleton;
import com.google.inject.name.Names;
import io.bisq.common.app.AppModule;
import io.bisq.network.NetworkOptionKeys;
import io.bisq.network.Socks5ProxyProvider;
import io.bisq.network.p2p.seed.SeedNodesRepository;
import org.springframework.core.env.Environment;

import java.io.File;

import static com.google.inject.name.Names.named;


public class P2PModule extends AppModule {

    public P2PModule(Environment env) {
        super(env);
    }

    @Override
    protected void configure() {
        bind(SeedNodesRepository.class).in(Singleton.class);
        bind(P2PService.class).in(Singleton.class);
        bind(Socks5ProxyProvider.class).in(Singleton.class);

        Boolean useLocalhostForP2P = env.getProperty(NetworkOptionKeys.USE_LOCALHOST_FOR_P2P, boolean.class, false);
        bind(boolean.class).annotatedWith(Names.named(NetworkOptionKeys.USE_LOCALHOST_FOR_P2P)).toInstance(useLocalhostForP2P);

        File torDir = new File(env.getRequiredProperty(NetworkOptionKeys.TOR_DIR));
        bind(File.class).annotatedWith(named(NetworkOptionKeys.TOR_DIR)).toInstance(torDir);

        // use a fixed port as arbitrator use that for his ID
        Integer port = env.getProperty(NetworkOptionKeys.PORT_KEY, int.class, 9999);
        bind(int.class).annotatedWith(Names.named(NetworkOptionKeys.PORT_KEY)).toInstance(port);

        Integer maxConnections = env.getProperty(NetworkOptionKeys.MAX_CONNECTIONS, int.class, P2PService.MAX_CONNECTIONS_DEFAULT);
        bind(int.class).annotatedWith(Names.named(NetworkOptionKeys.MAX_CONNECTIONS)).toInstance(maxConnections);

        Integer networkId = env.getProperty(NetworkOptionKeys.NETWORK_ID, int.class, 1);
        bind(int.class).annotatedWith(Names.named(NetworkOptionKeys.NETWORK_ID)).toInstance(networkId);
        bindConstant().annotatedWith(named(NetworkOptionKeys.SEED_NODES_KEY)).to(env.getRequiredProperty(NetworkOptionKeys.SEED_NODES_KEY));
        bindConstant().annotatedWith(named(NetworkOptionKeys.MY_ADDRESS)).to(env.getRequiredProperty(NetworkOptionKeys.MY_ADDRESS));
        bindConstant().annotatedWith(named(NetworkOptionKeys.BAN_LIST)).to(env.getRequiredProperty(NetworkOptionKeys.BAN_LIST));
        bindConstant().annotatedWith(named(NetworkOptionKeys.SOCKS_5_PROXY_BTC_ADDRESS)).to(env.getRequiredProperty(NetworkOptionKeys.SOCKS_5_PROXY_BTC_ADDRESS));
        bindConstant().annotatedWith(named(NetworkOptionKeys.SOCKS_5_PROXY_HTTP_ADDRESS)).to(env.getRequiredProperty(NetworkOptionKeys.SOCKS_5_PROXY_HTTP_ADDRESS));
    }
}