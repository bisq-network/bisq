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

import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class NodeTests {

    @Test
    public void testEqualsAndHashCode() {
        Node node1a = Node.at("bitsquare1.example.com", "203.0.113.1");
        Node node1b = Node.at("bitsquare1.example.com", "203.0.113.1");

        assertThat(node1a, equalTo(node1a));

        assertThat(node1a, equalTo(node1b));
        assertThat(node1b, equalTo(node1a));

        assertThat(node1a, not((Object) equalTo(null)));
        assertThat(node1a, not((Object) equalTo("not a node")));

        assertThat(node1a, not(equalTo(Node.at("bitsquare2.example.com", node1a.getIp()))));
        assertThat(node1a, not(equalTo(Node.at(node1a.getName(), "203.0.113.2"))));

        Node node2 = Node.at("bitsquare2.example.com", "203.0.113.2");
        assertThat(node1a.hashCode(), equalTo(node1b.hashCode()));
        assertThat(node1a.hashCode(), not(equalTo(node2.hashCode())));
    }
}