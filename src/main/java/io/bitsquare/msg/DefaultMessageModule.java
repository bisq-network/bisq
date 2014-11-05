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

package io.bitsquare.msg;

import io.bitsquare.AbstractBitsquareModule;
import io.bitsquare.network.BootstrapNode;
import io.bitsquare.network.Node;

import com.google.inject.Injector;
import com.google.inject.name.Names;

import java.util.Properties;

public class DefaultMessageModule extends AbstractBitsquareModule implements MessageModule {

    public DefaultMessageModule(Properties properties) {
        super(properties);
    }

    @Override
    protected void configure() {
        bind(MessageFacade.class).to(TomP2PMessageFacade.class).asEagerSingleton();
        bind(P2PNode.class).asEagerSingleton();
        bind(BootstrappedPeerFactory.class).asEagerSingleton();
        bind(DHTSeedService.class);

        // we will probably later use disk storage instead of memory storage for TomP2P
        bind(Boolean.class).annotatedWith(Names.named("useDiskStorage")).toInstance(false);

        bind(Node.class)
                .annotatedWith(Names.named("bootstrapNode"))
                .toInstance(BootstrapNode.DIGITAL_OCEAN1);
    }

    @Override
    public void doClose(Injector injector) {
        injector.getInstance(MessageFacade.class).shutDown();
    }
}
