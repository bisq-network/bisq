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

package io.bitsquare.p2p.tomp2p;

import io.bitsquare.p2p.AddressService;
import io.bitsquare.p2p.BootstrapNodes;
import io.bitsquare.p2p.ClientNode;
import io.bitsquare.p2p.MailboxService;
import io.bitsquare.p2p.MessageService;
import io.bitsquare.p2p.Node;
import io.bitsquare.p2p.P2PModule;

import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.name.Names;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.env.Environment;

import static io.bitsquare.p2p.tomp2p.BootstrappedPeerBuilder.NETWORK_INTERFACE_UNSPECIFIED;

public class TomP2PModule extends P2PModule {
    private static final Logger log = LoggerFactory.getLogger(TomP2PModule.class);
    public static final String BOOTSTRAP_NODE_NAME_KEY = "bootstrap.node.name"; 
    public static final String BOOTSTRAP_NODE_IP_KEY = "bootstrap.node.ip";
    public static final String BOOTSTRAP_NODE_P2P_ID_KEY = "bootstrap.node.p2pId";
    public static final String BOOTSTRAP_NODE_PORT_KEY = "bootstrap.node.port";
    public static final String NETWORK_INTERFACE_KEY = BootstrappedPeerBuilder.NETWORK_INTERFACE_KEY;
    public static final String USE_MANUAL_PORT_FORWARDING_KEY = BootstrappedPeerBuilder.USE_MANUAL_PORT_FORWARDING_KEY;

    public TomP2PModule(Environment env) {
        super(env);
    }

    @Override
    protected void doConfigure() {
        // Used both ClientNode and TomP2PNode for injection
        bind(BootstrapNodes.class).in(Singleton.class);
        bind(ClientNode.class).to(TomP2PNode.class).in(Singleton.class);
        bind(TomP2PNode.class).in(Singleton.class);

        bind(BootstrappedPeerBuilder.class).in(Singleton.class);

        bind(AddressService.class).to(TomP2PAddressService.class).in(Singleton.class);
        bind(MessageService.class).to(TomP2PMessageService.class).in(Singleton.class);
        bind(MailboxService.class).to(TomP2PMailboxService.class).in(Singleton.class);

        bind(int.class).annotatedWith(Names.named(Node.PORT_KEY)).toInstance(env.getProperty(Node.PORT_KEY, int.class, Node.CLIENT_PORT));
        bind(boolean.class).annotatedWith(Names.named(USE_MANUAL_PORT_FORWARDING_KEY)).toInstance(
                env.getProperty(USE_MANUAL_PORT_FORWARDING_KEY, boolean.class, false));

        bind(Node.class).annotatedWith(Names.named(BootstrapNodes.BOOTSTRAP_NODE_KEY)).toInstance(
                Node.at(env.getProperty(BOOTSTRAP_NODE_NAME_KEY, ""),
                        env.getProperty(BOOTSTRAP_NODE_IP_KEY, ""),
                        Integer.valueOf(env.getProperty(BOOTSTRAP_NODE_P2P_ID_KEY, "-1")),
                        Integer.valueOf(env.getProperty(BOOTSTRAP_NODE_PORT_KEY, "-1"))
                ));
        bindConstant().annotatedWith(Names.named(NETWORK_INTERFACE_KEY)).to(env.getProperty(NETWORK_INTERFACE_KEY, NETWORK_INTERFACE_UNSPECIFIED));
    }

    @Override
    protected void doClose(Injector injector) {
        log.trace("doClose " + getClass().getSimpleName());
        // First shut down AddressService to remove address from DHT
        injector.getInstance(AddressService.class).shutDown();
        injector.getInstance(BootstrappedPeerBuilder.class).shutDown();
        injector.getInstance(MailboxService.class).shutDown();
        injector.getInstance(MessageService.class).shutDown();

    }
}