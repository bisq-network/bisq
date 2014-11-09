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

import java.util.Arrays;
import java.util.List;

// Ports 7366-7390 are not registered @see
// <a href="https://www.iana.org/assignments/service-names-port-numbers/service-names-port-numbers.xhtml?&page=103</a>
// Lets use ports in that range 7366-7390
// 7366 will be used as default port
public interface BootstrapNodes {
    Node LOCALHOST = Node.at("localhost", "127.0.0.1");
    Node DIGITAL_OCEAN_1 = Node.at("digitalocean1.bitsquare.io", "188.226.179.109");

    Node DEFAULT_BOOTSTRAP_NODE = DIGITAL_OCEAN_1;

    static List<Node> all() {
        return Arrays.asList(
                LOCALHOST, DIGITAL_OCEAN_1
        );
    }
}
