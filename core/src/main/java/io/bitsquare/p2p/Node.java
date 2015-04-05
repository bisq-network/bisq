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

import java.net.ServerSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Node {
    private static final Logger log = LoggerFactory.getLogger(Node.class);

    public static final String NAME_KEY = "node.name";
    public static final String PORT_KEY = "node.port";

    /**
     * Default port is one <a
     * href="https://www.iana.org/assignments/service-names-port-numbers/service-names-port-numbers.xhtml?&page=103">
     * currently unassigned by IANA</a> (7366-7390).
     */
    public static int DEFAULT_PORT = findFreeSystemPort();

    private final String name;
    private final String ip;
    private final int port;

    private Node(String name, String ip, int port) {
        this.name = name;
        this.ip = ip;
        this.port = port;
    }

    public static Node at(String name, String ip) {
        return Node.at(name, ip, DEFAULT_PORT);
    }

    public static Node at(String name, String ip, int port) {
        return new Node(name, ip, port);
    }

    public static Node at(String name, String ip, String port) {
        return new Node(name, ip, Integer.valueOf(port));
    }

    public String getName() {
        return name;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    /**
     * Return a copy of this node with the port updated to the given value.
     */
    public Node withPort(int newPort) {
        return Node.at(this.name, this.ip, newPort);
    }

    private static int findFreeSystemPort() {
        int port = 7366;
        try {
            ServerSocket server = new ServerSocket(0);
            port = server.getLocalPort();
            server.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

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
        return Objects.toStringHelper(Node.class.getSimpleName())
                .add("name", name)
                .add("ip", ip)
                .add("port", port)
                .toString();
    }
}
