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

import net.tomp2p.connection.Ports;

import static io.bitsquare.msg.tomp2p.BootstrappedPeerFactory.*;
import static io.bitsquare.network.BootstrapNodes.DEFAULT_BOOTSTRAP_NODE;

public class TomP2PMessageModule extends MessageModule {

    public static final String BOOTSTRAP_NODE_ID_KEY = "id";
    public static final String BOOTSTRAP_NODE_IP_KEY = "ip";
    public static final String BOOTSTRAP_NODE_PORT_KEY = "port";
    public static final String NETWORK_INTERFACE_KEY = BootstrappedPeerFactory.NETWORK_INTERFACE_KEY;

    public TomP2PMessageModule(Properties properties) {
        super(properties);
    }

    @Override
    protected void doConfigure() {
        Integer randomPort = new Ports().tcpPort();
        bindConstant().annotatedWith(Names.named(TomP2PNode.CLIENT_PORT_KEY)).to(randomPort);
        bind(boolean.class).annotatedWith(Names.named(TomP2PNode.USE_DISK_STORAGE_KEY)).toInstance(false);
        bind(TomP2PNode.class).asEagerSingleton();

        bind(Node.class).annotatedWith(Names.named(BOOTSTRAP_NODE_KEY)).toInstance(
                Node.at(
                        properties.getProperty(BOOTSTRAP_NODE_ID_KEY, DEFAULT_BOOTSTRAP_NODE.getId()),
                        properties.getProperty(BOOTSTRAP_NODE_IP_KEY, DEFAULT_BOOTSTRAP_NODE.getIp()),
                        properties.getProperty(BOOTSTRAP_NODE_PORT_KEY, DEFAULT_BOOTSTRAP_NODE.getPortAsString())
                )
        );
        bindConstant().annotatedWith(Names.named(NETWORK_INTERFACE_KEY)).to(
                properties.getProperty(NETWORK_INTERFACE_KEY, NETWORK_INTERFACE_UNSPECIFIED));
        bind(BootstrappedPeerFactory.class).asEagerSingleton();
    }

    @Override
    protected Class<? extends MessageFacade> messageFacade() {
        return TomP2PMessageFacade.class;
    }
}
