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

public interface BootstrapNodes {

    Node DIGITAL_OCEAN_1 = Node.at("digitalocean1.bitsquare.io", "188.226.179.109");

    /**
     * Alias to the default bootstrap node.
     */
    Node DEFAULT = DIGITAL_OCEAN_1;

    /**
     * A locally-running {@link io.bitsquare.app.cli.BootstrapNode} instance.
     * Typically used only for testing. Not included in results from {@link #all()}.
     */
    Node LOCALHOST = Node.at("localhost", "127.0.0.1");

    /**
     * All known public bootstrap nodes.
     */
    static List<Node> all() {
        return Arrays.asList(
                DIGITAL_OCEAN_1
        );
    }
}
