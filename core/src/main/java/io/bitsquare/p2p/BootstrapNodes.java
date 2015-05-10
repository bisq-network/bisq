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

package io.bitsquare.p2p;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BootstrapNodes {
    private static final Logger log = LoggerFactory.getLogger(BootstrapNodes.class);

    public static final int PORT = 7366;
    public static final String DEFAULT_NODE_NAME = "default";

    private static List<Node> bootstrapNodes = Arrays.asList(
            Node.at(DEFAULT_NODE_NAME, "188.226.179.109", PORT),
            Node.at(DEFAULT_NODE_NAME, "52.24.144.42", PORT),
            Node.at(DEFAULT_NODE_NAME, "52.11.125.194", PORT)
    );

    /**
     * A locally-running BootstrapNode instance.
     * Typically used only for testing. Not included in results from {@link #getAllBootstrapNodes()}.
     */
    public static Node LOCALHOST = Node.at("localhost", "127.0.0.1", PORT);

    private static Node selectedNode = bootstrapNodes.get(new Random().nextInt(bootstrapNodes.size()));

    public static List<Node> getAllBootstrapNodes() {
        return bootstrapNodes;
    }

    public static Node getSelectedNode() {
        return selectedNode;
    }
}
