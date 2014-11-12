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

import io.bitsquare.msg.MessageModule;
import io.bitsquare.msg.MessageService;
import io.bitsquare.network.BootstrapNodes;
import io.bitsquare.network.Node;

import com.google.inject.name.Names;

import org.springframework.core.env.Environment;

import static io.bitsquare.msg.tomp2p.BootstrappedPeerFactory.*;

public class TomP2PMessageModule extends MessageModule {

    public static final String BOOTSTRAP_NODE_NAME_KEY = "bootstrap.node.name";
    public static final String BOOTSTRAP_NODE_IP_KEY = "bootstrap.node.ip";
    public static final String BOOTSTRAP_NODE_PORT_KEY = "bootstrap.node.port";
    public static final String NETWORK_INTERFACE_KEY = BootstrappedPeerFactory.NETWORK_INTERFACE_KEY;

    public TomP2PMessageModule(Environment env) {
        super(env);
    }

    @Override
    protected void doConfigure() {
        bind(int.class).annotatedWith(Names.named(Node.PORT_KEY)).toInstance(
                env.getProperty(Node.PORT_KEY, Integer.class, Node.DEFAULT_PORT));
        bind(TomP2PNode.class).asEagerSingleton();

        bind(Node.class).annotatedWith(Names.named(BOOTSTRAP_NODE_KEY)).toInstance(
                Node.at(
                        env.getProperty(BOOTSTRAP_NODE_NAME_KEY, BootstrapNodes.DEFAULT.getName()),
                        env.getProperty(BOOTSTRAP_NODE_IP_KEY, BootstrapNodes.DEFAULT.getIp()),
                        env.getProperty(BOOTSTRAP_NODE_PORT_KEY, BootstrapNodes.DEFAULT.getPortAsString())
                )
        );
        bindConstant().annotatedWith(Names.named(NETWORK_INTERFACE_KEY)).to(
                env.getProperty(NETWORK_INTERFACE_KEY, NETWORK_INTERFACE_UNSPECIFIED));
        bind(BootstrappedPeerFactory.class).asEagerSingleton();
    }

    @Override
    protected Class<? extends MessageService> messageService() {
        return TomP2PMessageService.class;
    }
}
