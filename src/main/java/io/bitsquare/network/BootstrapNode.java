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

public enum BootstrapNode implements Node {
    LOCALHOST(Node.at("localhost", "127.0.0.1")),
    DIGITAL_OCEAN1(Node.at("digitalocean1.bitsquare.io", "188.226.179.109"));

    private final Node self;

    BootstrapNode(Node self) {
        this.self = self;
    }

    @Override
    public String getId() {
        return self.getId();
    }

    @Override
    public String getIp() {
        return self.getIp();
    }

    @Override
    public int getPort() {
        return self.getPort();
    }
}
