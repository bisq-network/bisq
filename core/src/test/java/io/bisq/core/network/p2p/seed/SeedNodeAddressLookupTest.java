package io.bisq.core.network.p2p.seed;

import io.bisq.core.app.BisqEnvironment;
import io.bisq.network.p2p.NodeAddress;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Before;
import org.junit.Test;

import java.security.Security;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;

public class SeedNodeAddressLookupTest {
    @Before
    public void setUp() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    public void testResolveNodeAddressesWhenLocalAddressSpecified() {
        SeedNodeAddressLookup lookup = new SeedNodeAddressLookup(
                mock(BisqEnvironment.class), false, 1, "192.168.0.1:1234",
                "192.168.0.1:1234, 192.168.0.2:9897");

        Set<NodeAddress> actual = lookup.resolveNodeAddresses();
        Set<NodeAddress> expected = Collections.singleton(new NodeAddress("192.168.0.2:9897"));
        assertEquals(expected, actual);
    }

    @Test
    public void testResolveNodeAddressesWhenSeedNodesAreNull() {
        SeedNodeAddressLookup lookup = new SeedNodeAddressLookup(
                mock(BisqEnvironment.class), false, 1, "192.168.0.1:1234", null);

        Set<NodeAddress> actual = lookup.resolveNodeAddresses();
        assertFalse(actual.isEmpty());
    }
}
