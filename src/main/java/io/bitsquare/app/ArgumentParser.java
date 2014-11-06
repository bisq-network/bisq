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

import io.bitsquare.network.BootstrapNode;
import io.bitsquare.network.Node;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

/*
optional arguments:
  -h, --help                                show this help message and exit
  -d PEER_ID, --peerid PEER                 Seed peer ID (default: "digitalocean1.bitsquare.io")
  -p PORT, --port PORT                      IP port to listen on. (default: 5000)
  -i INTERFACE, --interface INTERFACE       Network interface to listen on.
  -n NAME, --name NAME                      Append name to application name.
 */
public class ArgumentParser {

    public static final String PEER_ID_FLAG = "peerid";
    public static final String PORT_FLAG = "port";
    public static final String INTERFACE_HINT_FLAG = "interface";
    public static final String NAME_FLAG = "name";
    public static final String PEER_ID_DEFAULT = BootstrapNode.DIGITAL_OCEAN1.getId();

    private final net.sourceforge.argparse4j.inf.ArgumentParser parser;

    public ArgumentParser() {
        parser = ArgumentParsers.newArgumentParser("Bitsquare")
                .defaultHelp(true)
                .description("Bitsquare - The decentralized bitcoin exchange.");
        parser.addArgument("-d", "--" + PEER_ID_FLAG)
                .setDefault(PEER_ID_DEFAULT)
                .help("Seed peer ID.");
        parser.addArgument("-p", "--" + PORT_FLAG)
                .setDefault(Node.DEFAULT_PORT)
                .help("IP port to listen on.");
        parser.addArgument("-i", "--" + INTERFACE_HINT_FLAG)
                .help("Network interface to listen on.");
        parser.addArgument("-n", "--" + NAME_FLAG)
                .help("Append name to application name.");
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
