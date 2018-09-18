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

import bisq.network.p2p.NodeAddress;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SeedNodeAddressesTest {


    @Test
    public void testCollector() {
        List<NodeAddress> addresses = Lists.newArrayList(
                new NodeAddress("192.168.0.1:1111"),
                new NodeAddress("192.168.0.1:1111"),
                new NodeAddress("192.168.0.2:2222"));
        Set<NodeAddress> expected = new HashSet<>(addresses);

        SeedNodeAddresses actual = addresses.stream()
                .collect(SeedNodeAddresses.collector());

        assertEquals(expected, actual);
    }

    @Test
    public void testExcludeByFullAddress() {
        Set<NodeAddress> delegate = Sets.newHashSet(
                new NodeAddress("192.168.0.1:1111"),
                new NodeAddress("192.168.0.2:2222"));
        SeedNodeAddresses addresses = new SeedNodeAddresses(delegate);
        SeedNodeAddresses actual = addresses.excludeByFullAddress("192.168.0.1:1111");

        assertEquals(1, actual.size());
    }

    @Test
    public void testExcludeByHost() {
        Set<NodeAddress> delegate = Sets.newHashSet(
                new NodeAddress("aaa:1111"),
                new NodeAddress("aaa:2222"),
                new NodeAddress("bbb:1111"),
                new NodeAddress("bbb:2222"),
                new NodeAddress("ccc:1111"),
                new NodeAddress("ccc:2222"));
        SeedNodeAddresses addresses = new SeedNodeAddresses(delegate);

        Set<String> hosts = Sets.newHashSet("aaa", "bbb");
        SeedNodeAddresses actual = addresses.excludeByHost(hosts);

        Set<NodeAddress> expected = Sets.newHashSet(
                new NodeAddress("ccc:1111"),
                new NodeAddress("ccc:2222"));

        assertEquals(expected, actual);
    }

    @Test
    public void testFromString() {
        Set<NodeAddress> expected = Sets.newHashSet(
                new NodeAddress("192.168.0.1:1111"),
                new NodeAddress("192.168.0.2:2222"));
        SeedNodeAddresses actual = SeedNodeAddresses.fromString("192.168.0.1:1111, 192.168.0.2:2222");
        assertEquals(expected, actual);
    }

    @Test
    public void testFromEmptyString() {
        SeedNodeAddresses nodeAddresses = SeedNodeAddresses.fromString("");
        assertTrue(nodeAddresses.isEmpty());
    }
}
