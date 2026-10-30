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

import bisq.network.p2p.CloseConnectionMessage;
import bisq.network.p2p.peers.keepalive.messages.Ping;
import bisq.network.tor.local_network.TorNetwork;
import bisq.network.tor.process.EmbeddedTorProcess;

import bisq.common.FrameRateTimer;
import bisq.common.Payload;
import bisq.common.UserThread;
import bisq.common.proto.ProtobufferException;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.network.NetworkProtoResolver;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.SingleThreadExecutorUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Clock;
import java.time.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs two nodes, each with its own Tor and onion service, on a private Tor network of tor-local-network on this host,
 * so the test needs neither the internet nor the Tor network. The network needs tor and tor-gencert in PATH; on Debian
 * and Ubuntu the package tor has both.
 */
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class TorNetworkNodePrivateNetworkTest {
    private static final String PASSPHRASE = "passphrase";
    private static final int MAX_CONNECTIONS = 12;
    // On the network, whose consensus lists all nodes about a minute after its start, a Tor bootstraps in seconds
    private static final Duration NODE_START_TIMEOUT = Duration.ofMinutes(4);
    private static final Duration MESSAGE_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration SHUT_DOWN_TIMEOUT = Duration.ofSeconds(30);

    @TempDir
    Path tempDir;

    private ExecutorService userThreadExecutor;
    private TorNetwork torNetwork;
    private final List<TorNetworkNode> nodes = new ArrayList<>();

    @BeforeEach
    void startNetwork() throws Exception {
        String path = System.getenv("PATH");
        assumeTrue(EmbeddedTorProcess.findExecutableInPath(path, "tor").isPresent() &&
                        EmbeddedTorProcess.findExecutableInPath(path, "tor-gencert").isPresent(),
                "The private Tor network needs tor and tor-gencert in PATH");

        UserThread.resetForTests();
        userThreadExecutor = SingleThreadExecutorUtils.getSingleThreadExecutor(
                "TorNetworkNodePrivateNetworkTest-UserThread");
        UserThread.setExecutor(userThreadExecutor);
        UserThread.setTimerClass(FrameRateTimer.class);

        torNetwork = new TorNetwork(Files.createDirectories(tempDir.resolve("network")), true)
                .addDirAuth(PASSPHRASE)
                .addDirAuth(PASSPHRASE)
                .addDirAuth(PASSPHRASE)

                .addRelay()
                .addRelay()
                .addRelay()
                .addRelay()
                .addRelay();
        torNetwork.start();
        // A node which starts earlier can ask other relays for the descriptor of an onion service than those to which
        // the onion service uploaded it, so its first connections can time out.
        torNetwork.awaitConsensusWithAllNodes(Duration.ofMinutes(3));
    }

    @AfterEach
    void stopNetwork() throws Exception {
        try {
            CountDownLatch shutDownLatch = new CountDownLatch(nodes.size());
            nodes.forEach(node -> node.shutDown(shutDownLatch::countDown));
            assertTrue(shutDownLatch.await(SHUT_DOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
        } finally {
            if (torNetwork != null) {
                torNetwork.close();
            }
            UserThread.resetForTests();
            if (userThreadExecutor != null) {
                userThreadExecutor.shutdownNow();
            }
        }
    }

    @Test
    void aNodeSendsAMessageToTheOnionServiceOfAnotherNode() throws Exception {
        TorNetworkNode alice = startNode("alice", 9001);
        TorNetworkNode bob = startNode("bob", 9002);
        CompletableFuture<NetworkEnvelope> receivedByBob = new CompletableFuture<>();
        bob.addMessageListener((networkEnvelope, connection) -> receivedByBob.complete(networkEnvelope));

        Connection connection = alice.sendMessage(bob.getNodeAddress(), new Ping(42, 0))
                .get(MESSAGE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

        Ping received = assertInstanceOf(Ping.class,
                receivedByBob.get(MESSAGE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
        assertEquals(42, received.getNonce());
        assertEquals(bob.getNodeAddress(), connection.getPeersNodeAddressOptional().orElseThrow());
    }

    private TorNetworkNode startNode(String name, int servicePort) throws Exception {
        NewTor torMode = NewTor.forTestNetwork(Files.createDirectories(tempDir.resolve(name)).toFile(),
                torNetwork.getDirectoryAuthorities());
        TorNetworkNode node = new TorNetworkNode(servicePort, new PingProtoResolver(), false, torMode, null,
                MAX_CONNECTIONS);
        nodes.add(node);

        CompletableFuture<Void> published = new CompletableFuture<>();
        node.start(new SetupListener() {
            @Override
            public void onTorNodeReady() {
            }

            @Override
            public void onHiddenServicePublished() {
                published.complete(null);
            }

            @Override
            public void onSetupFailed(Throwable throwable) {
                published.completeExceptionally(throwable);
            }

            @Override
            public void onRequestCustomBridges() {
            }
        });
        published.get(NODE_START_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        return node;
    }

    // The nodes exchange a Ping, and at their shutdown the message which closes the connection
    private static class PingProtoResolver implements NetworkProtoResolver {
        @Override
        public NetworkEnvelope fromProto(protobuf.NetworkEnvelope proto) throws ProtobufferException {
            if (proto.hasPing()) {
                return Ping.fromProto(proto.getPing(), proto.getMessageVersion());
            }
            if (proto.hasCloseConnectionMessage()) {
                return CloseConnectionMessage.fromProto(proto.getCloseConnectionMessage(), proto.getMessageVersion());
            }
            throw new ProtobufferException("Unexpected message: " + proto.getMessageCase());
        }

        @Override
        public NetworkPayload fromProto(protobuf.StoragePayload proto) {
            throw new UnsupportedOperationException();
        }

        @Override
        public NetworkPayload fromProto(protobuf.StorageEntryWrapper proto) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PersistablePayload fromProto(protobuf.PersistableNetworkPayload proto) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Payload fromProto(protobuf.PaymentAccountPayload proto) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Clock getClock() {
            return Clock.systemDefaultZone();
        }
    }
}
