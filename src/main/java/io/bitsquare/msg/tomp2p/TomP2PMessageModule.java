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

import java.util.Properties;

import net.sourceforge.argparse4j.inf.Namespace;

public class TomP2PMessageModule extends MessageModule {

    public TomP2PMessageModule(Properties properties, Namespace argumentsNamespace) {
        super(properties, argumentsNamespace);
    }

    @Override
    protected void doConfigure() {
        bind(TomP2PNode.class).asEagerSingleton();
        bind(BootstrappedPeerFactory.class).asEagerSingleton();
    }

    @Override
    protected Class<? extends MessageFacade> messageFacade() {
        return TomP2PMessageFacade.class;
    }
}
