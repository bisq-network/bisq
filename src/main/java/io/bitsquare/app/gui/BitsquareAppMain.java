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

package io.bitsquare.app.gui;

import io.bitsquare.app.BitsquareEnvironment;
import io.bitsquare.app.BitsquareExecutable;
import io.bitsquare.btc.BitcoinNetwork;
import io.bitsquare.network.BootstrapNodes;
import io.bitsquare.network.Node;
import io.bitsquare.util.joptsimple.EnumValueConverter;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import static io.bitsquare.app.BitsquareEnvironment.*;
import static io.bitsquare.msg.tomp2p.TomP2PMessageModule.*;
import static io.bitsquare.network.Node.*;

public class BitsquareAppMain extends BitsquareExecutable {

    public static void main(String[] args) throws Exception {
        new BitsquareAppMain().execute(args);
    }

    @Override
    protected void customizeOptionParsing(OptionParser parser) {
        parser.accepts(USER_DATA_DIR_KEY, "User data directory").withRequiredArg().defaultsTo(DEFAULT_USER_DATA_DIR);
        parser.accepts(APP_NAME_KEY, "Application name").withRequiredArg().defaultsTo(DEFAULT_APP_NAME);
        parser.accepts(APP_DATA_DIR_KEY, "Application data directory").withRequiredArg()
                .defaultsTo(DEFAULT_APP_DATA_DIR);
        parser.accepts(NAME_KEY, "Name of this node").withRequiredArg();
        parser.accepts(PORT_KEY, "Port to listen on").withRequiredArg().ofType(int.class).defaultsTo(Node.DEFAULT_PORT);
        parser.accepts(BitcoinNetwork.KEY).withRequiredArg().ofType(BitcoinNetwork.class)
                .withValuesConvertedBy(new EnumValueConverter(BitcoinNetwork.class))
                .defaultsTo(BitcoinNetwork.DEFAULT);
        parser.accepts(BOOTSTRAP_NODE_NAME_KEY).withRequiredArg().defaultsTo(BootstrapNodes.DEFAULT.getName());
        parser.accepts(BOOTSTRAP_NODE_IP_KEY).withRequiredArg().defaultsTo(BootstrapNodes.DEFAULT.getIp());
        parser.accepts(BOOTSTRAP_NODE_PORT_KEY).withRequiredArg().ofType(int.class)
                .defaultsTo(BootstrapNodes.DEFAULT.getPort());
        parser.accepts(NETWORK_INTERFACE_KEY, "Network interface").withRequiredArg();
    }

    @Override
    protected void doExecute(OptionSet options) {
        BitsquareApp.setEnvironment(new BitsquareEnvironment(options));
        javafx.application.Application.launch(BitsquareApp.class);
    }
}
