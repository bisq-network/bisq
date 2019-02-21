/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.network.p2p.seed;

import bisq.core.app.BisqEnvironment;

import bisq.network.p2p.NodeAddress;

import java.util.Collections;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;

public class SeedNodeAddressLookupTest {


    @Test
    public void testResolveNodeAddressesWhenLocalAddressSpecified() {
        SeedNodeAddressLookup lookup = new SeedNodeAddressLookup(
                mock(BisqEnvironment.class), false, 0, "192.168.0.1:1234",
                "192.168.0.1:1234, 192.168.0.2:9897");

        Set<NodeAddress> actual = lookup.resolveNodeAddresses();
        Set<NodeAddress> expected = Collections.singleton(new NodeAddress("192.168.0.2:9897"));
        assertEquals(expected, actual);
    }

    @Test
    public void testResolveNodeAddressesWhenSeedNodesAreNull() {
        SeedNodeAddressLookup lookup = new SeedNodeAddressLookup(
                mock(BisqEnvironment.class), false, 0, "192.168.0.1:1234", null);

        Set<NodeAddress> actual = lookup.resolveNodeAddresses();
        assertFalse(actual.isEmpty());
    }
}
