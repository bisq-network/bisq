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

import bisq.common.config.Config;

import org.bitcoinj.core.BitcoinSerializer;
import org.bitcoinj.core.MessageSerializer;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VersionMessage;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The local node is the only peer of the wallet, so it is used only if the wallet can sync from it.
public class LocalBitcoinNodeTest {
    private static final NetworkParameters PARAMS = MainNetParams.get();
    private static final MessageSerializer SERIALIZER = PARAMS.getDefaultSerializer();

    private final AtomicBoolean versionReceived = new AtomicBoolean();
    private ServerSocket localNode;

    @AfterEach
    public void tearDown() throws IOException {
        if (localNode != null) {
            localNode.close();
        }
        // Config keeps the network and the data directory in static fields, which the other tests expect at the
        // defaults
        new Config();
    }

    @Test
    public void fullNodeIsUsed() throws IOException {
        startLocalNode(socket -> answerWithServices(socket,
                VersionMessage.NODE_NETWORK | VersionMessage.NODE_WITNESS | VersionMessage.NODE_BLOOM));

        assertTrue(LocalBitcoinNode.detect(PARAMS, localNode.getLocalPort()).isUsable());
        assertTrue(versionReceived.get());
    }

    @Test
    public void prunedNodeIsNotUsed() throws IOException {
        startLocalNode(socket -> answerWithServices(socket,
                VersionMessage.NODE_NETWORK_LIMITED | VersionMessage.NODE_WITNESS | VersionMessage.NODE_BLOOM));

        assertUnusable("pruned", LocalBitcoinNode.detect(PARAMS, localNode.getLocalPort()));
    }

    @Test
    public void nodeWithoutBloomFiltersIsNotUsed() throws IOException {
        startLocalNode(socket -> answerWithServices(socket,
                VersionMessage.NODE_NETWORK | VersionMessage.NODE_WITNESS));

        assertUnusable("bloom filters", LocalBitcoinNode.detect(PARAMS, localNode.getLocalPort()));
    }

    @Test
    public void nodeWithoutWitnessDataIsNotUsed() throws IOException {
        startLocalNode(socket -> answerWithServices(socket,
                VersionMessage.NODE_NETWORK | VersionMessage.NODE_BLOOM));

        assertUnusable("witness", LocalBitcoinNode.detect(PARAMS, localNode.getLocalPort()));
    }

    // A busy node can close the connection, so it is used as before
    @Test
    public void nodeThatClosesWithoutAnswerIsUsedAsBefore() throws IOException {
        startLocalNode(Socket::close);

        assertTrue(LocalBitcoinNode.detect(PARAMS, localNode.getLocalPort()).isUsable());
    }

    @Test
    public void otherMessageThanVersionIsNotUsed() throws IOException {
        startLocalNode(socket -> {
            readVersion(socket);
            SERIALIZER.serialize("verack", new byte[0], socket.getOutputStream());
        });

        assertUnusable("does not come from a Bitcoin node", LocalBitcoinNode.detect(PARAMS, localNode.getLocalPort()));
    }

    @Test
    public void otherServiceOnThePortIsNotUsed() throws IOException {
        startLocalNode(socket -> socket.getOutputStream()
                .write("HTTP/1.1 400 Bad Request\r\n\r\n".getBytes(StandardCharsets.US_ASCII)));

        assertUnusable("does not come from a Bitcoin node", LocalBitcoinNode.detect(PARAMS, localNode.getLocalPort()));
    }

    // The pruned node sends its answer a byte every 500 ms, so no single read waits long. The deadline
    // still ends the wait, and the node is used as one that does not answer.
    @Test
    public void slowAnswerEndsAtTheDeadline() throws IOException {
        startLocalNode(socket -> {
            readVersion(socket);
            VersionMessage versionMessage = new VersionMessage(PARAMS, 900_000);
            versionMessage.localServices =
                    VersionMessage.NODE_NETWORK_LIMITED | VersionMessage.NODE_WITNESS | VersionMessage.NODE_BLOOM;
            ByteArrayOutputStream answer = new ByteArrayOutputStream();
            SERIALIZER.serialize(versionMessage, answer);
            for (byte b : answer.toByteArray()) {
                socket.getOutputStream().write(b);
                socket.getOutputStream().flush();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });

        long start = System.currentTimeMillis();
        assertTrue(LocalBitcoinNode.detect(PARAMS, localNode.getLocalPort()).isUsable());
        assertTrue(System.currentTimeMillis() - start < 7_000);
    }

    // A version message has about 130 bytes. An announced size of 30 MB is refused at once, without
    // allocating it and waiting for the payload until the deadline, and no Bitcoin node sends it.
    @Test
    public void oversizedVersionMessageIsRefusedAtOnce() throws IOException {
        startLocalNode(socket -> {
            readVersion(socket);
            byte[] header = new byte[4 + BitcoinSerializer.BitcoinPacketHeader.HEADER_LENGTH];
            Utils.uint32ToByteArrayBE(PARAMS.getPacketMagic(), header, 0);
            byte[] command = "version".getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(command, 0, header, 4, command.length);
            Utils.uint32ToByteArrayLE(30_000_000, header, 16);
            socket.getOutputStream().write(header);
        });

        long start = System.currentTimeMillis();
        assertUnusable("does not come from a Bitcoin node", LocalBitcoinNode.detect(PARAMS, localNode.getLocalPort()));
        assertTrue(System.currentTimeMillis() - start < 2_000);
    }

    @Test
    public void closedPortIsNotDetected() throws IOException {
        int port;
        try (ServerSocket closed = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = closed.getLocalPort();
        }

        assertFalse(LocalBitcoinNode.detect(PARAMS, port).found());
    }

    // The user decides whether the wallet continues without a node that was found but cannot serve it
    @Test
    public void unusableNodeGivesTheReasonForTheUser() throws IOException {
        startLocalNode(socket -> answerWithServices(socket,
                VersionMessage.NODE_NETWORK_LIMITED | VersionMessage.NODE_WITNESS | VersionMessage.NODE_BLOOM));

        LocalBitcoinNode localBitcoinNode = new LocalBitcoinNode(new Config(), localNode.getLocalPort());

        assertFalse(localBitcoinNode.shouldBeUsed());
        assertTrue(localBitcoinNode.getUnusableReason().orElseThrow().contains("pruned"));
    }

    @Test
    public void usableNodeGivesNoReason() throws IOException {
        startLocalNode(socket -> answerWithServices(socket,
                VersionMessage.NODE_NETWORK | VersionMessage.NODE_WITNESS | VersionMessage.NODE_BLOOM));

        LocalBitcoinNode localBitcoinNode = new LocalBitcoinNode(new Config(), localNode.getLocalPort());

        assertTrue(localBitcoinNode.shouldBeUsed());
        assertTrue(localBitcoinNode.getUnusableReason().isEmpty());
    }

    // An ignored node is not contacted, so there is nothing to ask the user
    @Test
    public void ignoredNodeIsNotChecked() throws IOException {
        startLocalNode(socket -> answerWithServices(socket,
                VersionMessage.NODE_NETWORK_LIMITED | VersionMessage.NODE_WITNESS | VersionMessage.NODE_BLOOM));

        LocalBitcoinNode localBitcoinNode = new LocalBitcoinNode(new Config("--ignoreLocalBtcNode=true"),
                localNode.getLocalPort());

        assertFalse(localBitcoinNode.shouldBeUsed());
        assertTrue(localBitcoinNode.getUnusableReason().isEmpty());
        assertFalse(versionReceived.get());
    }

    // On BTC_REGTEST the peer of the wallet is set with --bitcoinRegtestHost, so there is nothing to ask the user
    @Test
    public void unusableNodeGivesNoReasonOnRegtest() throws IOException {
        NetworkParameters regtest = RegTestParams.get();
        long pruned = VersionMessage.NODE_NETWORK_LIMITED | VersionMessage.NODE_WITNESS | VersionMessage.NODE_BLOOM;
        startLocalNode(socket -> answerWithServices(socket, pruned, regtest));

        LocalBitcoinNode localBitcoinNode = new LocalBitcoinNode(new Config("--baseCurrencyNetwork=BTC_REGTEST"),
                localNode.getLocalPort());

        assertFalse(localBitcoinNode.shouldBeUsed());
        assertTrue(localBitcoinNode.getUnusableReason().isEmpty());
    }

    private static void assertUnusable(String reasonPart, LocalBitcoinNode.Detection detection) {
        assertTrue(detection.found());
        assertFalse(detection.isUsable());
        assertTrue(detection.unusableReason().contains(reasonPart), detection.unusableReason());
    }

    private interface Answer {
        void answer(Socket socket) throws IOException;
    }

    private void startLocalNode(Answer answer) throws IOException {
        localNode = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        Thread thread = new Thread(() -> {
            try (Socket socket = localNode.accept()) {
                answer.answer(socket);
                // Keep the connection open until the client closes it.
                while (!socket.isClosed() && socket.getInputStream().read() != -1) {
                }
            } catch (IOException ignore) {
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void answerWithServices(Socket socket, long services) throws IOException {
        answerWithServices(socket, services, PARAMS);
    }

    // Like Bitcoin Core, the fake node answers only after it got a valid version message.
    private void answerWithServices(Socket socket, long services, NetworkParameters params) throws IOException {
        readVersion(socket, params);
        VersionMessage versionMessage = new VersionMessage(params, 900_000);
        versionMessage.localServices = services;
        params.getDefaultSerializer().serialize(versionMessage, socket.getOutputStream());
    }

    private void readVersion(Socket socket) throws IOException {
        readVersion(socket, PARAMS);
    }

    private void readVersion(Socket socket, NetworkParameters params) throws IOException {
        MessageSerializer serializer = params.getDefaultSerializer();
        DataInputStream in = new DataInputStream(socket.getInputStream());
        byte[] magic = new byte[4];
        in.readFully(magic);
        if (Utils.readUint32BE(magic, 0) != params.getPacketMagic()) {
            throw new IOException("Unexpected magic");
        }
        byte[] headerBytes = new byte[BitcoinSerializer.BitcoinPacketHeader.HEADER_LENGTH];
        in.readFully(headerBytes);
        BitcoinSerializer.BitcoinPacketHeader header = serializer.deserializeHeader(ByteBuffer.wrap(headerBytes));
        byte[] payload = new byte[header.size];
        in.readFully(payload);
        if (header.command.equals("version") &&
                serializer.deserializePayload(header, ByteBuffer.wrap(payload)) instanceof VersionMessage) {
            versionReceived.set(true);
        } else {
            throw new IOException("Expected a version message, got " + header.command);
        }
    }
}
