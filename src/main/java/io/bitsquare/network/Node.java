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

package io.bitsquare.network;

import com.google.common.base.Objects;

public final class Node {
    public static final String ID_KEY = "id";
    public static final String PORT_KEY = "port";
    public static final int DEFAULT_PORT = 7366;

    private final String id;
    private final String ip;
    private final int port;

    private Node(String id, String ip, int port) {
        this.id = id;
        this.ip = ip;
        this.port = port;
    }

    public static Node at(String id, String ip) {
        return Node.at(id, ip, DEFAULT_PORT);
    }

    public static Node at(String id, String ip, int port) {
        return new Node(id, ip, port);
    }

    public static Node at(String id, String ip, String port) {
        return new Node(id, ip, Integer.valueOf(port));
    }

    public String getId() {
        return id;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public String getPortAsString() {
        return String.valueOf(port);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;

        if (object == null || getClass() != object.getClass())
            return false;

        Node that = (Node) object;
        return Objects.equal(this.id, that.id) &&
                Objects.equal(this.ip, that.ip) &&
                Objects.equal(this.port, that.port);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id, ip, port);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(Node.class.getSimpleName())
                .add("id", id)
                .add("ip", ip)
                .add("port", port)
                .toString();
    }
}
