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

package bisq.core.btc.nodes;

import bisq.core.btc.nodes.BtcNodes.BtcNode;

import org.bitcoinj.core.PeerAddress;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;

import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BtcNodesRepositoryTest {
    @Test
    public void testGetPeerAddressesWhenClearNodes() {
        BtcNode node = mock(BtcNode.class);
        when(node.hasClearNetAddress()).thenReturn(true);

        BtcNodeConverter converter = mock(BtcNodeConverter.class, RETURNS_DEEP_STUBS);
        BtcNodesRepository repository = new BtcNodesRepository(converter,
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

        BtcNodesRepository repository = new BtcNodesRepository(converter,
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
        BtcNodesRepository repository = new BtcNodesRepository(converter,
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
        BtcNodesRepository repository = new BtcNodesRepository(converter,
                Lists.newArrayList(node, onionNode));

        List<PeerAddress> peers = repository.getPeerAddresses(mock(Socks5Proxy.class), false);

        assertEquals(1, peers.size());
    }
}
