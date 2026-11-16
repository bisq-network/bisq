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

import bisq.network.p2p.BundleOfEnvelopes;
import bisq.network.p2p.storage.messages.RefreshOfferMessage;

import bisq.common.UserThread;
import bisq.common.app.Version;
import bisq.common.proto.ProtobufferException;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.network.NetworkProtoResolver;

import com.google.protobuf.ByteString;
import com.google.protobuf.UnknownFieldSet;

import java.net.Socket;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectionBundleElementParseTest {
    // A message of a known type which the resolver cannot convert, as one of a type which was removed
    @SuppressWarnings("deprecation")
    private static final protobuf.NetworkEnvelope INVALID_ELEMENT = protobuf.NetworkEnvelope.newBuilder()
            .setMessageVersion(Version.getP2PMessageVersion())
            .setFileTransferPart(protobuf.FileTransferPart.newBuilder())
            .build();
    // A message of a type which the node does not know, as one of a newer version. Its field is unknown, so no
    // message type is set.
    private static final protobuf.NetworkEnvelope UNKNOWN_ELEMENT = protobuf.NetworkEnvelope.newBuilder()
            .setMessageVersion(Version.getP2PMessageVersion())
            .setUnknownFields(UnknownFieldSet.newBuilder()
                    .addField(1000, UnknownFieldSet.Field.newBuilder()
                            .addLengthDelimited(ByteString.copyFromUtf8("message of a newer version"))
                            .build())
                    .build())
            .build();

    private final MessageListener messageListener = mock(MessageListener.class);
    private final ConnectionListener connectionListener = mock(ConnectionListener.class);
    private final NetworkProtoResolver networkProtoResolver = mock(NetworkProtoResolver.class);
    // The element of each proto, as the resolver returns it
    private final Map<protobuf.NetworkEnvelope, NetworkEnvelope> elements = new HashMap<>();

    @BeforeEach
    void setUp() throws ProtobufferException {
        UserThread.resetForTests();
        // Envelopes compare their message version, which must be set
        Version.setBaseCryptoNetworkId(1);
        when(networkProtoResolver.fromProto(any(protobuf.NetworkEnvelope.class))).thenAnswer(invocation -> {
            protobuf.NetworkEnvelope proto = invocation.getArgument(0);
            if (proto.hasBundleOfEnvelopes()) {
                return BundleOfEnvelopes.fromProto(proto.getBundleOfEnvelopes(), networkProtoResolver,
                        proto.getMessageVersion());
            }
            NetworkEnvelope element = elements.get(proto);
            if (element == null) {
                throw new ProtobufferException("Unknown test proto");
            }
            return element;
        });
    }

    @AfterEach
    void tearDown() {
        UserThread.resetForTests();
    }

    @Test
    void deliversTheOtherElementsOfABundleWithAnElementWhichCannotBeParsed() throws IOException {
        RefreshOfferMessage valid = refreshOfferMessage(1);

        Connection connection = connection(delimited(bundle(INVALID_ELEMENT, proto(valid))));

        verify(connectionListener, timeout(5_000)).onDisconnect(eq(CloseConnectionReason.NO_PROTO_BUFFER_ENV),
                same(connection));
        verify(messageListener).onMessage(same(valid), same(connection));
        // The first violation of the rule is tolerated, as for a single message
        assertNull(connection.getRuleViolation());
    }

    @Test
    void closesAtTheSecondElementWhichCannotBeParsed() throws IOException {
        RefreshOfferMessage first = refreshOfferMessage(1);
        RefreshOfferMessage second = refreshOfferMessage(2);

        Connection connection = connection(concat(delimited(bundle(INVALID_ELEMENT, proto(first))),
                delimited(bundle(INVALID_ELEMENT, proto(second)))));

        verify(connectionListener, timeout(5_000)).onDisconnect(eq(CloseConnectionReason.RULE_VIOLATION),
                same(connection));
        assertEquals(RuleViolation.INVALID_DATA_TYPE, connection.getRuleViolation());
        verify(messageListener).onMessage(same(first), same(connection));
        verify(messageListener, never()).onMessage(same(second), any());
    }

    @Test
    void closesAtTwoElementsWhichCannotBeParsedAndDeliversNoElementOfTheBundle() throws IOException {
        RefreshOfferMessage valid = refreshOfferMessage(1);
        RefreshOfferMessage next = refreshOfferMessage(2);

        Connection connection = connection(concat(
                delimited(bundle(proto(valid), INVALID_ELEMENT, INVALID_ELEMENT)),
                delimited(bundle(proto(next)))));

        verify(connectionListener, timeout(5_000)).onDisconnect(eq(CloseConnectionReason.RULE_VIOLATION),
                same(connection));
        assertEquals(RuleViolation.INVALID_DATA_TYPE, connection.getRuleViolation());
        verify(messageListener, never()).onMessage(any(), any());
    }

    // A relay of a newer version passes such elements on, unless their type requires a capability
    @Test
    void dropsElementsOfAnUnknownTypeWithoutAViolation() throws IOException {
        RefreshOfferMessage first = refreshOfferMessage(1);
        RefreshOfferMessage second = refreshOfferMessage(2);

        Connection connection = connection(concat(delimited(bundle(UNKNOWN_ELEMENT, proto(first))),
                delimited(bundle(UNKNOWN_ELEMENT, UNKNOWN_ELEMENT, proto(second)))));

        verify(connectionListener, timeout(5_000)).onDisconnect(eq(CloseConnectionReason.NO_PROTO_BUFFER_ENV),
                same(connection));
        verify(messageListener).onMessage(same(first), same(connection));
        verify(messageListener).onMessage(same(second), same(connection));
        assertNull(connection.getRuleViolation());
    }

    private RefreshOfferMessage refreshOfferMessage(int sequenceNumber) {
        RefreshOfferMessage message = new RefreshOfferMessage(new byte[20], new byte[46], new byte[20],
                sequenceNumber);
        elements.put(message.toProtoNetworkEnvelope(), message);
        return message;
    }

    private static protobuf.NetworkEnvelope proto(NetworkEnvelope networkEnvelope) {
        return networkEnvelope.toProtoNetworkEnvelope();
    }

    private static protobuf.NetworkEnvelope bundle(protobuf.NetworkEnvelope... envelopes) {
        return protobuf.NetworkEnvelope.newBuilder()
                .setMessageVersion(Version.getP2PMessageVersion())
                .setBundleOfEnvelopes(protobuf.BundleOfEnvelopes.newBuilder().addAllEnvelopes(Arrays.asList(envelopes)))
                .build();
    }

    private Connection connection(byte[] bytes) throws IOException {
        Socket socket = mock(Socket.class);
        when(socket.getInputStream()).thenReturn(new ByteArrayInputStream(bytes));
        when(socket.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        return new Connection(socket, messageListener, connectionListener, null, networkProtoResolver, null);
    }

    private static byte[] delimited(protobuf.NetworkEnvelope envelope) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        envelope.writeDelimitedTo(outputStream);
        return outputStream.toByteArray();
    }

    private static byte[] concat(byte[]... arrays) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (byte[] array : arrays) {
            outputStream.writeBytes(array);
        }
        return outputStream.toByteArray();
    }
}
