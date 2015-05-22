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

import com.google.common.base.Objects;

import java.io.IOException;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;

import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Node {
    private static final Logger log = LoggerFactory.getLogger(Node.class);

    public static final String NAME_KEY = "node.name";
    public static final String PORT_KEY = "node.port";
    public static final String P2P_ID_KEY = "node.p2pId";

    //public static int DEFAULT_PORT = findFreeSystemPort();

    // P2P network ids
    public static final int MAIN_NET_P2P_ID = 10;
    public static final int TEST_NET_P2P_ID = 11;
    public static final int REG_TEST_P2P_ID = 12;

    // ports
    public static final int MAIN_NET_PORT = 7370;
    public static final int TEST_NET_PORT = 7371;
    public static final int REG_TEST_PORT = 7372;


    private final String name;
    private final String ip;
    private final int port;
    private final int p2pId;

    private Node(String name, String ip, int p2pId, int port) {
        this.name = name;
        this.ip = ip;
        this.p2pId = p2pId;
        this.port = port;
    }

    // Not fully defined node
    public static Node rawNodeAt(String name, String ip) {
        return Node.at(name, ip, -1, -1);
    }

    public static Node at(String name, String ip, int p2pId, int port) {
        return new Node(name, ip, p2pId, port);
    }

    public Node withP2pIdAndPort(int p2pId, int port) {
        return Node.at(this.name, this.ip, p2pId, port);
    }

    public static final int CLIENT_PORT = findFreeSystemPort();

    public static int findFreeSystemPort() {
        int port = 7369;
        try {
            ServerSocket server = new ServerSocket(0);
            port = server.getLocalPort();
            log.debug("Random system port used for client: {}", port);
            server.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return port;
    }

    public PeerAddress toPeerAddressWithPort(int port) {
        try {
            return new PeerAddress(Number160.createHash(name),
                    InetAddress.getByName(ip),
                    port,
                    port);
        } catch (UnknownHostException e) {
            log.error(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public PeerAddress toPeerAddress() {
        try {
            return new PeerAddress(Number160.createHash(name),
                    InetAddress.getByName(ip),
                    port,
                    port);
        } catch (UnknownHostException e) {
            log.error(e.getMessage());
            throw new RuntimeException(e);
        }
    }


    public String getName() {
        return name;
    }

    public String getIp() {
        return ip;
    }

    public int getP2pId() {
        return p2pId;
    }

    public int getPort() {
        return port;
    }


    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;

        if (object == null || getClass() != object.getClass())
            return false;

        Node that = (Node) object;
        return Objects.equal(this.name, that.name) &&
                Objects.equal(this.ip, that.ip) &&
                Objects.equal(this.port, that.port);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, ip, port);
    }

    @Override
    public String toString() {
        return "Name='" + name + '\'' +
                "; IP='" + ip + '\'' +
                "; port=" + port +
                "; P2P network ID=" + p2pId;
    }
}
