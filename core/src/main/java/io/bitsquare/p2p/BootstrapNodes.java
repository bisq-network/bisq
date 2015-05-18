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

import com.google.inject.name.Named;

import java.net.InetAddress;
import java.net.UnknownHostException;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import javax.inject.Inject;

import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BootstrapNodes {
    private static final Logger log = LoggerFactory.getLogger(BootstrapNodes.class);
    public static final String BOOTSTRAP_NODE_KEY = "bootstrapNode";

    private final List<Node> rawBootstrapNodes = Arrays.asList(
            Node.rawNodeAt("digitalocean1.bitsquare.io", "188.226.179.109"),
            Node.rawNodeAt("aws1.bitsquare.io", "52.24.144.42"),
            Node.rawNodeAt("aws2.bitsquare.io", "52.11.125.194")
    );
    private Node rawLocalhostNode = Node.rawNodeAt("localhost", "127.0.0.1");


    private Node preferredBootstrapNode;
    private List<Node> bootstrapNodes;
    private Node localhostNode;
    private int p2pId;
    private boolean inited;

    @Inject
    public BootstrapNodes(@Named(BOOTSTRAP_NODE_KEY) Node preferredBootstrapNode) {
        // preferredBootstrapNode need to be fully defined to get accepted (name, IP, p2pId, port)
        if (preferredBootstrapNode.getName() != null
                && preferredBootstrapNode.getIp() != null
                && preferredBootstrapNode.getP2pId() != -1
                && preferredBootstrapNode.getPort() != -1) {
            this.preferredBootstrapNode = preferredBootstrapNode;
        }
        else if (preferredBootstrapNode.getName() != null
                || preferredBootstrapNode.getIp() != null
                || preferredBootstrapNode.getP2pId() != -1
                || preferredBootstrapNode.getPort() != -1) {
            log.debug("preferredBootstrapNode not fully defined (name, IP, p2pId, port). preferredBootstrapNode=" + preferredBootstrapNode);
        }
    }

    public BootstrapNodes() {
    }

    public void initWithNetworkId(int p2pId) {
        if (!inited) {
            inited = true;
            this.p2pId = p2pId;
            if (preferredBootstrapNode != null) {
                bootstrapNodes = Arrays.asList(preferredBootstrapNode);
            }
            else {
                switch (p2pId) {
                    case Node.MAIN_NET_P2P_ID:
                        bootstrapNodes = rawBootstrapNodes.stream()
                                .map(e -> e.withP2pIdAndPort(Node.MAIN_NET_P2P_ID, Node.MAIN_NET_PORT)).collect(Collectors.toList());
                        localhostNode = rawLocalhostNode.withP2pIdAndPort(Node.MAIN_NET_P2P_ID, Node.MAIN_NET_PORT);
                        break;
                    case Node.TEST_NET_P2P_ID:
                        bootstrapNodes = rawBootstrapNodes.stream()
                                .map(e -> e.withP2pIdAndPort(Node.TEST_NET_P2P_ID, Node.TEST_NET_PORT)).collect(Collectors.toList());
                        localhostNode = rawLocalhostNode.withP2pIdAndPort(Node.TEST_NET_P2P_ID, Node.TEST_NET_PORT);
                        break;
                    case Node.REG_TEST_P2P_ID:
                        bootstrapNodes = rawBootstrapNodes.stream()
                                .map(e -> e.withP2pIdAndPort(Node.REG_TEST_P2P_ID, Node.REG_TEST_PORT)).collect(Collectors.toList());
                        localhostNode = rawLocalhostNode.withP2pIdAndPort(Node.REG_TEST_P2P_ID, Node.REG_TEST_PORT);
                        break;
                    default:
                        throw new BitsquareException("Unsupported P2pId. p2pId=" + p2pId);
                }
            }
        }
        else {
            throw new BitsquareException("initWithNetworkId called twice");
        }
    }

    public Node getRandomDiscoverNode() {
        return bootstrapNodes.get(new Random().nextInt(rawBootstrapNodes.size()));
    }

    public List<Node> getBootstrapNodes() {
        return bootstrapNodes;
    }

    public List<PeerAddress> getBootstrapPeerAddresses() {
        return bootstrapNodes.stream().map(e -> {
            try {
                return new PeerAddress(Number160.createHash(e.getName()), InetAddress.getByName(e.getIp()), e.getPort(), e.getPort());
            } catch (UnknownHostException e1) {
                e1.printStackTrace();
                log.error(e1.getMessage());
                return null;
            }
        }).collect(Collectors.toList());
    }

    public Node getLocalhostNode() {
        return localhostNode;
    }

    public int getP2pId() {
        return p2pId;
    }
}
