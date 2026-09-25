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

package bisq.network.p2p.storage;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.TestUtils;
import bisq.network.p2p.network.CloseConnectionReason;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.ConnectionListener;
import bisq.network.p2p.network.LocalhostNetworkNode;
import bisq.network.p2p.network.OutboundConnection;
import bisq.network.p2p.network.SetupListener;
import bisq.network.p2p.peers.getdata.messages.GetUpdatedDataRequest;
import bisq.network.p2p.peers.keepalive.messages.Ping;
import bisq.network.p2p.storage.mocks.ExpirableProtectedStoragePayloadStub;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.proto.network.NetworkProtoResolver;

import java.net.ServerSocket;
import java.net.Socket;

import java.security.KeyPair;

import java.io.IOException;

import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static bisq.network.p2p.storage.TestState.getTestNodeAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests of the P2PDataStore ConnectionListener interface with the connections of a real network node.
 */
public class P2PDataStoreDisconnectNetworkTest {
    private final AtomicReference<CloseConnectionReason> closeConnectionReason = new AtomicReference<>();
    private final AtomicReference<Connection> closedConnection = new AtomicReference<>();
    private final CountDownLatch disconnectLatch = new CountDownLatch(1);
    private LocalhostNetworkNode networkNode;

    @AfterEach
    public void tearDown() {
        if (networkNode != null)
            networkNode.shutDown(null);
    }

    // TESTCASE: A dialed peer that claims the owner's address keeps the dialed address, so only its own entries are backdated
    @Test
    public void outboundPeerClaimsOwnerAddressAndDisconnects() throws Exception {
        TestState testState = new TestState();
        try (ServerSocket peersServerSocket = new ServerSocket(0)) {
            peersServerSocket.setSoTimeout(30_000);
            NodeAddress peersNodeAddress = new NodeAddress("localhost", peersServerSocket.getLocalPort());
            ProtectedStorageEntry ownersEntry = addEntry(testState, getTestNodeAddress());
            ProtectedStorageEntry peersEntry = addEntry(testState, peersNodeAddress);
            long ownersCreationTimeStamp = ownersEntry.getCreationTimeStamp();
            long peersCreationTimeStamp = peersEntry.getCreationTimeStamp();
            startNetworkNode(testState, getFreePort());

            networkNode.sendMessage(peersNodeAddress, new Ping(1, 0));

            // The dialed peer claims the owner's address as sender and keeps the connection until the node drops it
            try (Socket socket = peersServerSocket.accept()) {
                sendGetUpdatedDataRequest(socket, getTestNodeAddress());
                assertTrue(disconnectLatch.await(30, TimeUnit.SECONDS));
            }

            assertInstanceOf(OutboundConnection.class, closedConnection.get());
            assertEquals(Optional.of(peersNodeAddress), closedConnection.get().getPeersNodeAddressOptional());
            assertFalse(closeConnectionReason.get().isIntended);
            assertEquals(ownersCreationTimeStamp, ownersEntry.getCreationTimeStamp());
            assertTrue(peersEntry.getCreationTimeStamp() < peersCreationTimeStamp);
        }
    }

    private void startNetworkNode(TestState testState, int port) throws InterruptedException, IOException {
        networkNode = new LocalhostNetworkNode(port, getUpdatedDataRequestResolver(), null, 12);
        // Added before the listener below, so the storage has handled a disconnect when the latch opens
        networkNode.addConnectionListener(testState.mockedStorage);
        networkNode.addConnectionListener(new ConnectionListener() {
            @Override
            public void onConnection(Connection connection) {
            }

            @Override
            public void onDisconnect(CloseConnectionReason reason, Connection connection) {
                closeConnectionReason.set(reason);
                closedConnection.set(connection);
                disconnectLatch.countDown();
            }
        });

        CountDownLatch startupLatch = new CountDownLatch(1);
        networkNode.start(new SetupListener() {
            @Override
            public void onTorNodeReady() {
            }

            @Override
            public void onHiddenServicePublished() {
                startupLatch.countDown();
            }
        });
        assertTrue(startupLatch.await(30, TimeUnit.SECONDS));
    }

    private static ProtectedStorageEntry addEntry(TestState testState, NodeAddress ownerNodeAddress) throws Exception {
        KeyPair ownerKeys = TestUtils.generateKeyPair();
        ProtectedStoragePayload protectedStoragePayload = new ExpirableProtectedStoragePayloadStub(ownerKeys.getPublic(),
                TimeUnit.DAYS.toMillis(90), ownerNodeAddress);
        ProtectedStorageEntry protectedStorageEntry = testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);
        assertTrue(testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, ownerNodeAddress, null));
        return protectedStorageEntry;
    }

    private static void sendGetUpdatedDataRequest(Socket socket, NodeAddress senderNodeAddress) throws IOException {
        new GetUpdatedDataRequest(senderNodeAddress, 1, new HashSet<>())
                .toProtoNetworkEnvelope()
                .writeDelimitedTo(socket.getOutputStream());
        socket.getOutputStream().flush();
    }

    private static int getFreePort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        }
    }

    private static NetworkProtoResolver getUpdatedDataRequestResolver() throws IOException {
        NetworkProtoResolver networkProtoResolver = mock(NetworkProtoResolver.class);
        when(networkProtoResolver.fromProto(any(protobuf.NetworkEnvelope.class))).thenAnswer(invocation -> {
            protobuf.NetworkEnvelope proto = invocation.getArgument(0);
            return GetUpdatedDataRequest.fromProto(proto.getGetUpdatedDataRequest(), proto.getMessageVersion());
        });
        return networkProtoResolver;
    }
}
