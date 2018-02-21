package io.bisq.core.network;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.bisq.network.p2p.NodeAddress;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Before;
import org.junit.Test;

import java.security.Security;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NodeAddressesTest {
    @Before
    public void setUp() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    public void testCollector() {
        List<NodeAddress> addresses = Lists.newArrayList(
                new NodeAddress("192.168.0.1:1111"),
                new NodeAddress("192.168.0.1:1111"),
                new NodeAddress("192.168.0.2:2222"));
        Set<NodeAddress> expected = new HashSet<>(addresses);

        NodeAddresses actual = addresses.stream()
                .collect(NodeAddresses.collector());

        assertEquals(expected, actual);
    }

    @Test
    public void testExcludeByFullAddress() {
        Set<NodeAddress> delegate = Sets.newHashSet(
                new NodeAddress("192.168.0.1:1111"),
                new NodeAddress("192.168.0.2:2222"));
        NodeAddresses addresses = new NodeAddresses(delegate);
        NodeAddresses actual = addresses.excludeByFullAddress("192.168.0.1:1111");

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
        NodeAddresses addresses = new NodeAddresses(delegate);

        Set<String> hosts = Sets.newHashSet("aaa", "bbb");
        NodeAddresses actual = addresses.excludeByHost(hosts);

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
        NodeAddresses actual = NodeAddresses.fromString("192.168.0.1:1111, 192.168.0.2:2222");
        assertEquals(expected, actual);
    }

    @Test
    public void testFromEmptyString() {
        NodeAddresses nodeAddresses = NodeAddresses.fromString("");
        assertTrue(nodeAddresses.isEmpty());
    }
}
