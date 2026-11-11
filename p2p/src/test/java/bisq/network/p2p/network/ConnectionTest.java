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

import bisq.common.UserThread;
import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.network.NetworkProtoResolver;

import java.net.Socket;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
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
}
