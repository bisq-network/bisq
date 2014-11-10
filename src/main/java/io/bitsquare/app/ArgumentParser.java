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

package io.bitsquare.app;

import io.bitsquare.network.Node;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import static io.bitsquare.app.AppModule.APP_NAME_KEY;
import static io.bitsquare.msg.tomp2p.TomP2PMessageModule.*;

public class ArgumentParser {

    private final net.sourceforge.argparse4j.inf.ArgumentParser parser;

    public ArgumentParser() {
        parser = ArgumentParsers.newArgumentParser("Bitsquare")
                .defaultHelp(true)
                .description("Bitsquare - The decentralized bitcoin exchange");

        // Args for local node config
        parser.addArgument("--" + Node.NAME_KEY)
                .help("Local node ID");
        parser.addArgument("--" + Node.PORT_KEY)
                .help("Local port to listen on");

        // Args for seed node config
        parser.addArgument("--" + BOOTSTRAP_NODE_NAME_KEY)
                .help("Seed node ID");
        parser.addArgument("--" + BOOTSTRAP_NODE_IP_KEY)
                .help("Seed node IP");
        parser.addArgument("--" + BOOTSTRAP_NODE_PORT_KEY)
                .help("Seed node port");

        // A custom network interface (needed at the moment for windows, but might be useful also later)
        parser.addArgument("--" + NETWORK_INTERFACE_KEY)
                .help("Network interface");

        // Args for app config
        parser.addArgument("-n", "--" + APP_NAME_KEY)
                .help("Name to append to default application name");
    }

    public Namespace parseArgs(String... args) {
        try {
            return parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
            return null;
        }
    }
}
