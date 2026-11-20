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

package bisq.network.p2p.peers;

import bisq.network.p2p.MockNode;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.CloseConnectionReason;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.InboundConnection;
import bisq.network.p2p.network.OutboundConnection;
import bisq.network.p2p.network.PeerType;
import bisq.network.p2p.network.RuleViolation;
import bisq.network.p2p.peers.peerexchange.Peer;

import bisq.common.app.Capabilities;

import java.io.IOException;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PeerManagerTest {
    private MockNode node;
    private int maxConnectionsPeer;
    private int maxConnectionsNonDirect;

    @BeforeEach
    public void setUp() throws IOException {
        node = new MockNode(2);
        maxConnectionsPeer = Math.max(4, (int) Math.round(node.getMaxConnections() * 1.3));
        maxConnectionsNonDirect = Math.max(8, (int) Math.round(node.getMaxConnections() * 1.7));
    }

    @AfterEach
    public void tearDown() {
        node.getPersistenceManager().shutdown();
    }

    @Test
    public void testCheckMaxConnectionsNotExceeded() {
        for (int i = 0; i < 2; i++) {
            node.addInboundConnection(PeerType.PEER);
        }
        assertEquals(2, node.getNetworkNode().getAllConnections().size());

        assertFalse(node.getPeerManager().checkMaxConnections());

        node.getNetworkNode().getAllConnections().forEach(connection ->
                verify(connection, never()).shutDown(eq(CloseConnectionReason.TOO_MANY_CONNECTIONS_OPEN), isA(Runnable.class)));
    }

    @Test
    public void testCheckMaxConnectionsExceededWithInboundPeers() throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            node.addInboundConnection(PeerType.PEER);
        }
        assertEquals(3, node.getNetworkNode().getAllConnections().size());
        List<Connection> inboundSortedPeerConnections = node.getNetworkNode().getAllConnections().stream()
                .filter(e -> e instanceof InboundConnection)
                .filter(e -> e.getConnectionState().getPeerType() == PeerType.PEER)
                .sorted(Comparator.comparingLong(o -> o.getStatistic().getLastActivityTimestamp()))
                .collect(Collectors.toList());
        Connection oldestConnection = inboundSortedPeerConnections.remove(0);

        assertTrue(node.getPeerManager().checkMaxConnections());
        // Need to wait because the shutDownCompleteHandler calls
        // checkMaxConnections on the user thread after a delay
        Thread.sleep(500);

        verify(oldestConnection, times(1)).shutDown(
                eq(CloseConnectionReason.TOO_MANY_CONNECTIONS_OPEN),
                isA(Runnable.class));
        inboundSortedPeerConnections.forEach(connection ->
                verify(connection, never()).shutDown(
                        eq(CloseConnectionReason.TOO_MANY_CONNECTIONS_OPEN),
                        isA(Runnable.class)));
    }

    @Test
    public void testCheckMaxConnectionsPeerLimitNotExceeded() {
        for (int i = 0; i < maxConnectionsPeer; i++) {
            node.addOutboundConnection(PeerType.PEER);
        }
        assertEquals(maxConnectionsPeer, node.getNetworkNode().getAllConnections().size());

        assertFalse(node.getPeerManager().checkMaxConnections());

        node.getNetworkNode().getAllConnections().forEach(connection ->
                verify(connection, never()).shutDown(eq(CloseConnectionReason.TOO_MANY_CONNECTIONS_OPEN), isA(Runnable.class)));
    }

    @Test
    public void testCheckMaxConnectionsPeerLimitExceeded() throws InterruptedException {
        for (int i = 0; i < maxConnectionsPeer + 1; i++) {
            node.addOutboundConnection(PeerType.PEER);
        }
        assertEquals(maxConnectionsPeer + 1, node.getNetworkNode().getAllConnections().size());
        List<Connection> sortedPeerConnections = node.getNetworkNode().getAllConnections().stream()
                .filter(e -> e.getConnectionState().getPeerType() == PeerType.PEER)
                .sorted(Comparator.comparingLong(o -> o.getStatistic().getLastActivityTimestamp()))
                .collect(Collectors.toList());
        Connection oldestConnection = sortedPeerConnections.remove(0);

        assertTrue(node.getPeerManager().checkMaxConnections());
        // Need to wait because the shutDownCompleteHandler calls
        // checkMaxConnections on the user thread after a delay
        Thread.sleep(500);

        verify(oldestConnection, times(1)).shutDown(
                eq(CloseConnectionReason.TOO_MANY_CONNECTIONS_OPEN),
                isA(Runnable.class));
        sortedPeerConnections.forEach(connection ->
                verify(connection, never()).shutDown(
                        eq(CloseConnectionReason.TOO_MANY_CONNECTIONS_OPEN),
                        isA(Runnable.class)));
    }

    @Test
    public void testCheckMaxConnectionsNonDirectLimitNotExceeded() {
        for (int i = 0; i < maxConnectionsNonDirect; i++) {
            node.addOutboundConnection(PeerType.INITIAL_DATA_EXCHANGE);
        }
        assertEquals(maxConnectionsNonDirect, node.getNetworkNode().getAllConnections().size());

        assertFalse(node.getPeerManager().checkMaxConnections());

        node.getNetworkNode().getAllConnections().forEach(connection ->
                verify(connection, never()).shutDown(eq(CloseConnectionReason.TOO_MANY_CONNECTIONS_OPEN), isA(Runnable.class)));
    }

    @Test
    @Disabled
    public void testCheckMaxConnectionsNonDirectLimitExceeded() throws InterruptedException {
        for (int i = 0; i < maxConnectionsNonDirect + 1; i++) {
            node.addOutboundConnection(PeerType.INITIAL_DATA_EXCHANGE);
        }
        assertEquals(maxConnectionsNonDirect + 1, node.getNetworkNode().getAllConnections().size());
        List<Connection> sortedPeerConnections = node.getNetworkNode().getAllConnections().stream()
                .filter(e -> e.getConnectionState().getPeerType() != PeerType.PEER)
                .filter(e -> e.getConnectionState().getPeerType() == PeerType.INITIAL_DATA_EXCHANGE)
                .sorted(Comparator.comparingLong(o -> o.getStatistic().getLastActivityTimestamp()))
                .collect(Collectors.toList());
        Connection oldestConnection = sortedPeerConnections.remove(0);

        assertTrue(node.getPeerManager().checkMaxConnections());
        // Need to wait because the shutDownCompleteHandler calls
        // checkMaxConnections on the user thread after a delay
        Thread.sleep(500);

        //TODO it reports "Wanted but not invoked:" but when debugging into it it is called. So seems to be some
        // mock setup issue
        verify(oldestConnection, times(1)).shutDown(
                eq(CloseConnectionReason.TOO_MANY_CONNECTIONS_OPEN),
                isA(Runnable.class));
        sortedPeerConnections.forEach(connection ->
                verify(connection, never()).shutDown(
                        eq(CloseConnectionReason.TOO_MANY_CONNECTIONS_OPEN),
                        isA(Runnable.class)));
    }

    @Test
    public void testCheckMaxConnectionsExceededWithOutboundSeeds() {
        for (int i = 0; i < 3; i++) {
            node.addOutboundConnection(PeerType.INITIAL_DATA_EXCHANGE);
        }
        assertEquals(3, node.getNetworkNode().getAllConnections().size());

        assertFalse(node.getPeerManager().checkMaxConnections());

        node.getNetworkNode().getAllConnections().forEach(connection ->
                verify(connection, never()).shutDown(eq(CloseConnectionReason.TOO_MANY_CONNECTIONS_OPEN), isA(Runnable.class)));
    }

    // The first message above 10 MB closes the connection for the rule violation MAX_MSG_SIZE_EXCEEDED (see
    // ConnectionTest). The peer is then removed from the persisted peers, also an honest peer of an earlier version.
    @Test
    public void testRuleViolationRemovesPersistedPeer() {
        NodeAddress peersNodeAddress = new NodeAddress("peer.onion", 8000);
        PeerManager peerManager = node.getPeerManager();
        peerManager.getPersistedPeers().add(new Peer(peersNodeAddress, new Capabilities()));
        Connection connection = mock(OutboundConnection.class);
        when(connection.getPeersNodeAddressOptional()).thenReturn(Optional.of(peersNodeAddress));
        when(connection.getRuleViolation()).thenReturn(RuleViolation.MAX_MSG_SIZE_EXCEEDED);

        peerManager.onDisconnect(CloseConnectionReason.RULE_VIOLATION, connection);

        assertTrue(peerManager.getPersistedPeers().isEmpty());
    }

    // A connection which ends without a rule violation, for example at the end of the stream, keeps the persisted peer
    @Test
    public void testTerminatedConnectionKeepsPersistedPeer() {
        NodeAddress peersNodeAddress = new NodeAddress("peer.onion", 8000);
        PeerManager peerManager = node.getPeerManager();
        Peer persistedPeer = new Peer(peersNodeAddress, new Capabilities());
        peerManager.getPersistedPeers().add(persistedPeer);
        Connection connection = mock(OutboundConnection.class);
        when(connection.getPeersNodeAddressOptional()).thenReturn(Optional.of(peersNodeAddress));

        peerManager.onDisconnect(CloseConnectionReason.TERMINATED, connection);

        assertTrue(peerManager.getPersistedPeers().contains(persistedPeer));
    }
}
