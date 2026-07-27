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

package bisq.network.p2p.network;

import bisq.network.p2p.NodeAddress;

import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.network.NetworkProtoResolver;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;

import java.net.Socket;

import java.io.IOException;

import java.util.Optional;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import java.lang.reflect.Field;

import org.jetbrains.annotations.Nullable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NetworkNodeTest {
    private static final NodeAddress PEER_NODE_ADDRESS = new NodeAddress("peer", 8080);

    @Test
    public void sendMessageUsesInboundConnectionByDefault() throws Exception {
        TestNetworkNode networkNode = new TestNetworkNode();

        try {
            InboundConnection inboundConnection = confirmedInboundConnection();
            addConnection(networkNode, "inBoundConnections", inboundConnection);
            NetworkEnvelope networkEnvelope = mock(NetworkEnvelope.class);

            SettableFuture<Connection> future = networkNode.sendMessage(PEER_NODE_ADDRESS, networkEnvelope);

            assertSame(inboundConnection, future.get(1, TimeUnit.SECONDS));
            verify(inboundConnection).sendMessage(networkEnvelope);
            assertEquals(0, networkNode.createSocketCalls);
        } finally {
            shutDownExecutors(networkNode);
        }
    }

    @Test
    public void sendMessageWithInboundDisabledDoesNotUseInboundConnection() throws Exception {
        TestNetworkNode networkNode = new TestNetworkNode();

        try {
            InboundConnection inboundConnection = confirmedInboundConnection();
            addConnection(networkNode, "inBoundConnections", inboundConnection);
            NetworkEnvelope networkEnvelope = mock(NetworkEnvelope.class);

            SettableFuture<Connection> future = networkNode.sendMessage(PEER_NODE_ADDRESS, networkEnvelope, false);

            ExecutionException exception = assertThrows(ExecutionException.class,
                    () -> future.get(1, TimeUnit.SECONDS));
            assertTrue(exception.getCause() instanceof IOException);
            verify(inboundConnection, never()).sendMessage(any(NetworkEnvelope.class));
            assertEquals(1, networkNode.createSocketCalls);
        } finally {
            shutDownExecutors(networkNode);
        }
    }

    @Test
    public void sendMessageWithInboundDisabledStillUsesOutboundConnection() throws Exception {
        TestNetworkNode networkNode = new TestNetworkNode();

        try {
            OutboundConnection outboundConnection = confirmedOutboundConnection();
            addConnection(networkNode, "outBoundConnections", outboundConnection);
            NetworkEnvelope networkEnvelope = mock(NetworkEnvelope.class);

            SettableFuture<Connection> future = networkNode.sendMessage(PEER_NODE_ADDRESS, networkEnvelope, false);

            assertSame(outboundConnection, future.get(1, TimeUnit.SECONDS));
            verify(outboundConnection).sendMessage(networkEnvelope);
            assertEquals(0, networkNode.createSocketCalls);
        } finally {
            shutDownExecutors(networkNode);
        }
    }

    private static InboundConnection confirmedInboundConnection() {
        InboundConnection connection = mock(InboundConnection.class);
        stubConfirmedConnection(connection);
        return connection;
    }

    private static OutboundConnection confirmedOutboundConnection() {
        OutboundConnection connection = mock(OutboundConnection.class);
        stubConfirmedConnection(connection);
        return connection;
    }

    private static void stubConfirmedConnection(Connection connection) {
        when(connection.hasPeersNodeAddress()).thenReturn(true);
        when(connection.getPeersNodeAddressOptional()).thenReturn(Optional.of(PEER_NODE_ADDRESS));
        when(connection.isStopped()).thenReturn(false);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Connection> void addConnection(NetworkNode networkNode,
                                                            String fieldName,
                                                            T connection) throws Exception {
        Field field = NetworkNode.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        ((CopyOnWriteArraySet<T>) field.get(networkNode)).add(connection);
    }

    private static void shutDownExecutors(NetworkNode networkNode) throws Exception {
        shutDownExecutor(networkNode, "connectionExecutor");
        shutDownExecutor(networkNode, "sendMessageExecutor");
    }

    private static void shutDownExecutor(NetworkNode networkNode, String fieldName) throws Exception {
        Field field = NetworkNode.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        ((ListeningExecutorService) field.get(networkNode)).shutdownNow();
    }

    private static class TestNetworkNode extends NetworkNode {
        private int createSocketCalls;

        private TestNetworkNode() {
            super(8080, mock(NetworkProtoResolver.class), null, 2);
        }

        @Override
        public void start(@Nullable SetupListener setupListener) {
        }

        @Override
        protected Socket createSocket(NodeAddress peersNodeAddress) throws IOException {
            createSocketCalls++;
            throw new IOException("Outbound socket creation disabled in test");
        }
    }
}
