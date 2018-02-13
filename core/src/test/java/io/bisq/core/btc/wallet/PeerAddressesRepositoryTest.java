package io.bisq.core.btc.wallet;

import com.google.common.collect.Lists;
import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import io.bisq.core.btc.BitcoinNodes.BtcNode;
import org.bitcoinj.core.PeerAddress;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class PeerAddressesRepositoryTest {
    @Test
    public void testGetPeerAddressesWhenClearNodes() {
        BtcNode node = mock(BtcNode.class);
        when(node.hasClearNetAddress()).thenReturn(true);

        BtcNodeConverter converter = mock(BtcNodeConverter.class, RETURNS_DEEP_STUBS);
        PeerAddressesRepository repository = new PeerAddressesRepository(converter,
                Collections.singletonList(node));

        List<PeerAddress> peers = repository.getPeerAddresses(null, false);

        assertFalse(peers.isEmpty());
    }

    @Test
    public void testGetPeerAddressesWhenConverterReturnsNull() {
        BtcNodeConverter converter = mock(BtcNodeConverter.class);
        when(converter.convertClearNode(any())).thenReturn(null);

        BtcNode node = mock(BtcNode.class);
        when(node.hasClearNetAddress()).thenReturn(true);

        PeerAddressesRepository repository = new PeerAddressesRepository(converter,
                Collections.singletonList(node));

        List<PeerAddress> peers = repository.getPeerAddresses(null, false);

        verify(converter).convertClearNode(any());
        assertTrue(peers.isEmpty());
    }

    @Test
    public void testGetPeerAddressesWhenProxyAndClearNodes() {
        BtcNode node = mock(BtcNode.class);
        when(node.hasClearNetAddress()).thenReturn(true);

        BtcNode onionNode = mock(BtcNode.class);
        when(node.hasOnionAddress()).thenReturn(true);

        BtcNodeConverter converter = mock(BtcNodeConverter.class, RETURNS_DEEP_STUBS);
        PeerAddressesRepository repository = new PeerAddressesRepository(converter,
                Lists.newArrayList(node, onionNode));

        List<PeerAddress> peers = repository.getPeerAddresses(mock(Socks5Proxy.class), true);

        assertEquals(2, peers.size());
    }

    @Test
    public void testGetPeerAddressesWhenOnionNodesOnly() {
        BtcNode node = mock(BtcNode.class);
        when(node.hasClearNetAddress()).thenReturn(true);

        BtcNode onionNode = mock(BtcNode.class);
        when(node.hasOnionAddress()).thenReturn(true);

        BtcNodeConverter converter = mock(BtcNodeConverter.class, RETURNS_DEEP_STUBS);
        PeerAddressesRepository repository = new PeerAddressesRepository(converter,
                Lists.newArrayList(node, onionNode));

        List<PeerAddress> peers = repository.getPeerAddresses(mock(Socks5Proxy.class), false);

        assertEquals(1, peers.size());
    }
}
