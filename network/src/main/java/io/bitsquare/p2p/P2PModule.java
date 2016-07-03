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

package io.bitsquare.p2p;

import com.google.inject.Singleton;
import com.google.inject.name.Names;
import io.bitsquare.app.AppModule;
import io.bitsquare.network.OptionKeys;
import io.bitsquare.p2p.seed.SeedNodesRepository;
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

        Boolean useLocalhost = env.getProperty(OptionKeys.USE_LOCALHOST, boolean.class, false);
        bind(boolean.class).annotatedWith(Names.named(OptionKeys.USE_LOCALHOST)).toInstance(useLocalhost);

        File torDir = new File(env.getRequiredProperty(OptionKeys.TOR_DIR));
        bind(File.class).annotatedWith(named(OptionKeys.TOR_DIR)).toInstance(torDir);

        // use a fixed port as arbitrator use that for his ID
        Integer port = env.getProperty(OptionKeys.PORT_KEY, int.class, 9999);
        bind(int.class).annotatedWith(Names.named(OptionKeys.PORT_KEY)).toInstance(port);

        Integer maxConnections = env.getProperty(OptionKeys.MAX_CONNECTIONS, int.class, P2PService.MAX_CONNECTIONS_DEFAULT);
        bind(int.class).annotatedWith(Names.named(OptionKeys.MAX_CONNECTIONS)).toInstance(maxConnections);

        Integer networkId = env.getProperty(OptionKeys.NETWORK_ID, int.class, 1);
        bind(int.class).annotatedWith(Names.named(OptionKeys.NETWORK_ID)).toInstance(networkId);
        bindConstant().annotatedWith(named(OptionKeys.SEED_NODES_KEY)).to(env.getRequiredProperty(OptionKeys.SEED_NODES_KEY));
    }
}