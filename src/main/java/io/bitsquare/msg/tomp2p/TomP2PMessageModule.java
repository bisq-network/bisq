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

package io.bitsquare.msg.tomp2p;

import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.MessageModule;
import io.bitsquare.network.Node;

import com.google.inject.name.Names;

import java.util.Properties;

import static io.bitsquare.network.BootstrapNodes.DEFAULT_BOOTSTRAP_NODE;

public class TomP2PMessageModule extends MessageModule {

    public TomP2PMessageModule(Properties properties) {
        super(properties);
    }

    @Override
    protected void doConfigure() {
        // we will probably later use disk storage instead of memory storage for TomP2P
        bind(Boolean.class).annotatedWith(Names.named(TomP2PNode.USE_DISK_STORAGE_KEY)).toInstance(false);

        bind(TomP2PNode.class).asEagerSingleton();

        Node bootstrapNode = Node.at(
                properties.getProperty(BOOTSTRAP_NODE_ID_KEY, DEFAULT_BOOTSTRAP_NODE.getId()),
                properties.getProperty(BOOTSTRAP_NODE_IP_KEY, DEFAULT_BOOTSTRAP_NODE.getIp()),
                properties.getProperty(BOOTSTRAP_NODE_PORT_KEY, DEFAULT_BOOTSTRAP_NODE.getPortAsString())
        );

        bind(Node.class)
                .annotatedWith(Names.named(BootstrappedPeerFactory.BOOTSTRAP_NODE_KEY))
                .toInstance(bootstrapNode);

        bind(BootstrappedPeerFactory.class).asEagerSingleton();
    }

    @Override
    protected Class<? extends MessageFacade> messageFacade() {
        return TomP2PMessageFacade.class;
    }
}
