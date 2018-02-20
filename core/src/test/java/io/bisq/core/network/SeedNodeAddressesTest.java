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

public class SeedNodeAddressesTest {
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
