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

import io.bitsquare.BitsquareModule;
import io.bitsquare.app.ArgumentParser;
import io.bitsquare.network.BootstrapNodes;
import io.bitsquare.network.Node;

import com.google.inject.Injector;
import com.google.inject.name.Names;

import java.util.Properties;

import net.sourceforge.argparse4j.inf.Namespace;

public abstract class MessageModule extends BitsquareModule {

    private final Namespace argumentsNamespace;

    protected MessageModule(Properties properties, Namespace argumentsNamespace) {
        super(properties);
        this.argumentsNamespace = argumentsNamespace;
    }

    @Override
    protected final void configure() {
        bind(MessageFacade.class).to(messageFacade()).asEagerSingleton();
        bind(DHTSeedService.class);

        // we will probably later use disk storage instead of memory storage for TomP2P
        bind(Boolean.class).annotatedWith(Names.named("useDiskStorage")).toInstance(false);

        Node bootstrapNode = BootstrapNodes.DIGITAL_OCEAN_1;
        // Passed program args will override the properties of the default bootstrapNode
        // So you can use the same id and ip but different ports (e.g. running several nodes on one server with
        // different ports)
        if (argumentsNamespace.getString(ArgumentParser.SEED_ID_FLAG) != null)
            bootstrapNode.setId(argumentsNamespace.getString(ArgumentParser.SEED_ID_FLAG));

        if (argumentsNamespace.getString(ArgumentParser.SEED_IP_FLAG) != null)
            bootstrapNode.setIp(argumentsNamespace.getString(ArgumentParser.SEED_IP_FLAG));

        if (argumentsNamespace.getString(ArgumentParser.SEED_PORT_FLAG) != null)
            bootstrapNode.setPort(Integer.valueOf(argumentsNamespace.getString(ArgumentParser.SEED_PORT_FLAG)));

        bind(Node.class)
                .annotatedWith(Names.named("bootstrapNode"))
                .toInstance(bootstrapNode);

        doConfigure();
    }

    protected void doConfigure() {
    }

    protected abstract Class<? extends MessageFacade> messageFacade();

    @Override
    protected void doClose(Injector injector) {
        injector.getInstance(MessageFacade.class).shutDown();
    }
}
