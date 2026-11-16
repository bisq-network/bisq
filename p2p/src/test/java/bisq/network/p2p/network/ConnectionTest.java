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

import bisq.network.p2p.peers.keepalive.messages.Ping;

import bisq.common.UserThread;
import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.network.NetworkProtoResolver;

import com.google.protobuf.CodedOutputStream;

import java.net.Socket;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectionTest {
    private final MessageListener messageListener = mock(MessageListener.class);
    private final ConnectionListener connectionListener = mock(ConnectionListener.class);
    private final NetworkProtoResolver networkProtoResolver = mock(NetworkProtoResolver.class);

    @BeforeEach
    void setUp() {
        UserThread.resetForTests();
    }

    @AfterEach
    void tearDown() {
        UserThread.resetForTests();
    }

    @Test
    void closesConnectionAtASizeAboveTheMaximumWithoutReadingTheMessage() throws IOException {
        InputStream inputStream = new SequenceInputStream(
                new ByteArrayInputStream(varint(Connection.getMaxPermittedMessageSize() + 1)),
                new FailingInputStream());

        Connection connection = connection(inputStream);

        verify(connectionListener, timeout(5_000)).onDisconnect(eq(CloseConnectionReason.RULE_VIOLATION),
                same(connection));
        assertEquals(RuleViolation.MAX_MSG_SIZE_EXCEEDED, connection.getRuleViolation());
        verify(messageListener, never()).onMessage(any(), any());
    }

    // A peer can close the connection at any time. A rule violation would remove it from the persisted peers.
    @Test
    void closesWithoutARuleViolationIfTheStreamEndsInsideTheLengthPrefix() throws IOException {
        Connection connection = connection(new ByteArrayInputStream(new byte[]{(byte) 0x80}));

        verify(connectionListener, timeout(5_000)).onDisconnect(eq(CloseConnectionReason.TERMINATED),
                same(connection));
        assertNull(connection.getRuleViolation());
        verify(messageListener, never()).onMessage(any(), any());
    }

    @Test
    void deliversTheNextMessageAfterBytesWhichAreNotAValidEnvelope() throws IOException {
        Ping ping = new Ping(1, 0);
        when(networkProtoResolver.fromProto(any(protobuf.NetworkEnvelope.class))).thenReturn(ping);
        // A field tag of 0 is not valid. Protobuf parses from a stream with a buffer of 4096 bytes, so a parser which
        // reads from the stream would stop inside this envelope.
        byte[] invalidEnvelope = new byte[10_000];
        InputStream inputStream = new ByteArrayInputStream(
                concat(varint(invalidEnvelope.length), invalidEnvelope, delimited(ping.toProtoNetworkEnvelope())));

        Connection connection = connection(inputStream);

        verify(messageListener, timeout(5_000)).onMessage(same(ping), same(connection));
        // Closed at the end of the stream, not for the invalid envelope
        verify(connectionListener, timeout(5_000)).onDisconnect(eq(CloseConnectionReason.NO_PROTO_BUFFER_ENV),
                same(connection));
    }

    @Test
    void dropsAMessageAboveTheSizeOfItsTypeAndDeliversTheNextOne() throws Exception {
        protobuf.NetworkEnvelope oversized = closeConnectionMessage("x".repeat(Connection.getPermittedMessageSize()));
        protobuf.NetworkEnvelope small = ping(1);
        NetworkEnvelope oversizedEnvelope = mock(NetworkEnvelope.class);
        NetworkEnvelope smallEnvelope = mock(NetworkEnvelope.class);
        when(networkProtoResolver.fromProto(oversized)).thenReturn(oversizedEnvelope);
        when(networkProtoResolver.fromProto(small)).thenReturn(smallEnvelope);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        oversized.writeDelimitedTo(outputStream);
        small.writeDelimitedTo(outputStream);

        Connection connection = connection(new ByteArrayInputStream(outputStream.toByteArray()));

        // The end of the stream closes the connection after both messages
        verify(connectionListener, timeout(5_000)).onDisconnect(eq(CloseConnectionReason.NO_PROTO_BUFFER_ENV),
                same(connection));
        verify(messageListener, never()).onMessage(same(oversizedEnvelope), any());
        verify(messageListener).onMessage(same(smallEnvelope), same(connection));
        // The first oversized message is tolerated, so the connection stayed open for the next one
        assertNull(connection.getRuleViolation());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void checksReceivedSizeDespiteProtobufNormalization(int bytesAboveLimit) throws IOException {
        protobuf.NetworkEnvelope first = ping(1);
        protobuf.NetworkEnvelope next = ping(2);
        NetworkEnvelope firstEnvelope = mock(NetworkEnvelope.class);
        NetworkEnvelope nextEnvelope = mock(NetworkEnvelope.class);
        when(networkProtoResolver.fromProto(first)).thenReturn(firstEnvelope);
        when(networkProtoResolver.fromProto(next)).thenReturn(nextEnvelope);

        // Repeated singular fields count towards the received size, but protobuf keeps only the last value.
        int receivedSize = Connection.getPermittedMessageSize() + bytesAboveLimit;
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(message);
        for (int size = first.getSerializedSize(); size < receivedSize; size += 2) {
            codedOutputStream.writeUInt32(1, 0);
        }
        codedOutputStream.flush();
        message.writeBytes(first.toByteArray());
        assertEquals(receivedSize, message.size());
        assertEquals(first, protobuf.NetworkEnvelope.parseFrom(message.toByteArray()));
        assertTrue(first.getSerializedSize() < Connection.getPermittedMessageSize());

        Connection connection = connection(new ByteArrayInputStream(
                concat(varint(message.size()), message.toByteArray(), delimited(next))));

        verify(connectionListener, timeout(5_000)).onDisconnect(eq(CloseConnectionReason.NO_PROTO_BUFFER_ENV),
                same(connection));
        if (bytesAboveLimit == 0) {
            verify(messageListener).onMessage(same(firstEnvelope), same(connection));
        } else {
            verify(messageListener, never()).onMessage(same(firstEnvelope), any());
        }
        verify(messageListener).onMessage(same(nextEnvelope), same(connection));
        assertNull(connection.getRuleViolation());
        assertEquals(receivedSize + next.getSerializedSize(), connection.getStatistic().getReceivedBytes());
    }

    private Connection connection(InputStream inputStream) throws IOException {
        Socket socket = mock(Socket.class);
        when(socket.getInputStream()).thenReturn(inputStream);
        when(socket.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        return new Connection(socket, messageListener, connectionListener, null, networkProtoResolver, null);
    }

    private static protobuf.NetworkEnvelope ping(int nonce) {
        return protobuf.NetworkEnvelope.newBuilder()
                .setMessageVersion(Version.getP2PMessageVersion())
                .setPing(protobuf.Ping.newBuilder().setNonce(nonce))
                .build();
    }

    private static protobuf.NetworkEnvelope closeConnectionMessage(String reason) {
        return protobuf.NetworkEnvelope.newBuilder()
                .setMessageVersion(Version.getP2PMessageVersion())
                .setCloseConnectionMessage(protobuf.CloseConnectionMessage.newBuilder().setReason(reason))
                .build();
    }

    private static byte[] delimited(protobuf.NetworkEnvelope envelope) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        envelope.writeDelimitedTo(outputStream);
        return outputStream.toByteArray();
    }

    private static byte[] varint(long value) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(outputStream);
        codedOutputStream.writeUInt64NoTag(value);
        codedOutputStream.flush();
        return outputStream.toByteArray();
    }

    private static byte[] concat(byte[]... arrays) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (byte[] array : arrays) {
            outputStream.writeBytes(array);
        }
        return outputStream.toByteArray();
    }

    // Stands for the bytes of an envelope which must not be read
    private static final class FailingInputStream extends InputStream {
        @Override
        public int read() throws IOException {
            throw new IOException("The envelope must not be read");
        }
    }
}
