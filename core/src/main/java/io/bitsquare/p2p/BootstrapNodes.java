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

import io.bitsquare.BitsquareException;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BootstrapNodes {
    private static final Logger log = LoggerFactory.getLogger(BootstrapNodes.class);

    private static final List<Node> bootstrapNodes = Arrays.asList(
            Node.at("digitalocean1.bitsquare.io", "188.226.179.109"),
            Node.at("aws1.bitsquare.io", "52.24.144.42"),
            Node.at("aws2.bitsquare.io", "52.11.125.194")
    );
    private static Node selectedNode;

    public static List<Node> getAllBootstrapNodes(int p2pId) {
        switch (p2pId) {
            case Node.MAIN_NET_P2P_ID:
                return bootstrapNodes.stream().map(e -> e.withP2pIdAndPort(Node.MAIN_NET_P2P_ID, Node.MAIN_NET_PORT)).collect(Collectors.toList());
            case Node.TEST_NET_P2P_ID:
                return bootstrapNodes.stream().map(e -> e.withP2pIdAndPort(Node.TEST_NET_P2P_ID, Node.TEST_NET_PORT)).collect(Collectors.toList());
            case Node.REG_TEST_P2P_ID:
                return bootstrapNodes.stream().map(e -> e.withP2pIdAndPort(Node.REG_TEST_P2P_ID, Node.REG_TEST_PORT)).collect(Collectors.toList());
            default:
                throw new BitsquareException("Unsupported P2pId. p2pId=" + p2pId);
        }
    }

    public static Node selectNode(int p2pId) {
        if (selectedNode == null)
            selectedNode = getAllBootstrapNodes(p2pId).get(new Random().nextInt(bootstrapNodes.size()));
        else
            throw new BitsquareException("selectNode must be called only once.");

        return selectedNode;
    }

    public static Node getSelectedNode() {
        if (selectedNode == null)
            throw new BitsquareException("selectNode must be called first.");

        return selectedNode;
    }

    public static Node getFallbackNode() {
        if (bootstrapNodes.size() > 1)
            return BootstrapNodes.getAllBootstrapNodes(selectedNode.getP2pId()).stream().filter(e -> !e.equals(selectedNode)).findAny().get();
        else
            return null;

    }

    // Localhost default use regtest
    private static Node localhostNode = selectLocalhostNode(Node.REG_TEST_P2P_ID);

    public static Node selectLocalhostNode(int p2pId) {
        final Node localhostNode = Node.at("localhost", "127.0.0.1");
        switch (p2pId) {
            case Node.MAIN_NET_P2P_ID:
                BootstrapNodes.localhostNode = localhostNode.withP2pIdAndPort(Node.MAIN_NET_P2P_ID, Node.MAIN_NET_PORT);
                break;
            case Node.TEST_NET_P2P_ID:
                BootstrapNodes.localhostNode = localhostNode.withP2pIdAndPort(Node.TEST_NET_P2P_ID, Node.TEST_NET_PORT);
                break;
            case Node.REG_TEST_P2P_ID:
                BootstrapNodes.localhostNode = localhostNode.withP2pIdAndPort(Node.REG_TEST_P2P_ID, Node.REG_TEST_PORT);
                break;
            default:
                throw new BitsquareException("Unsupported P2pId. p2pId=" + p2pId);
        }
        return BootstrapNodes.localhostNode;
    }

    public static Node getLocalhostNode() {
        return localhostNode;
    }
}
