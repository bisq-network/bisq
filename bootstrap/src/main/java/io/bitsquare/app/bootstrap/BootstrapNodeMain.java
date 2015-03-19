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

package io.bitsquare.app.bootstrap;

import io.bitsquare.app.BitsquareEnvironment;
import io.bitsquare.app.BitsquareExecutable;
import io.bitsquare.p2p.Node;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

public class BootstrapNodeMain extends BitsquareExecutable {

    public static void main(String[] args) throws Exception {
        new BootstrapNodeMain().execute(args);
    }

    protected void customizeOptionParsing(OptionParser parser) {
        parser.accepts(Node.NAME_KEY, description("Name of this node", null))
                .withRequiredArg()
                .isRequired();
        parser.accepts(Node.PORT_KEY, description("Port to listen on", Node.DEFAULT_PORT))
                .withRequiredArg()
                .ofType(int.class);
    }

    protected void doExecute(OptionSet options) {
        new BootstrapNode(new BitsquareEnvironment(options)).start();
    }
}
