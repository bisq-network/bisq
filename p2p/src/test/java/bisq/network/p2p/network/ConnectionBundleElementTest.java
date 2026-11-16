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
import bisq.network.p2p.CloseConnectionMessage;
import bisq.network.p2p.peers.keepalive.messages.Ping;
import bisq.network.p2p.storage.messages.AddPersistableNetworkPayloadMessage;
import bisq.network.p2p.storage.messages.RefreshOfferMessage;
import bisq.network.p2p.storage.mocks.PersistableNetworkPayloadStub;

import bisq.common.UserThread;
import bisq.common.app.Version;
import bisq.common.proto.ProtobufferException;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.network.NetworkProtoResolver;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectionBundleElementTest {
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
    void deliversTheElementsOfABundle() throws IOException {
        RefreshOfferMessage first = refreshOfferMessage(1, 100);
        RefreshOfferMessage second = refreshOfferMessage(2, 100);

        Connection connection = connection(delimited(bundle(proto(first), proto(second))));

        verifyClosedAtTheEndOfTheStream(connection);
        verify(messageListener).onMessage(same(first), same(connection));
        verify(messageListener).onMessage(same(second), same(connection));
        assertNull(connection.getRuleViolation());
    }

    @Test
    void dropsAnElementAboveTheSizeOfItsTypeAndDeliversTheOtherElements() throws IOException {
        RefreshOfferMessage small = refreshOfferMessage(1, 100);
        RefreshOfferMessage oversized = refreshOfferMessage(2, Connection.getPermittedMessageSize());
        assertTrue(proto(oversized).getSerializedSize() > Connection.getPermittedMessageSize());

        Connection connection = connection(delimited(bundle(proto(small), proto(oversized))));

        verifyClosedAtTheEndOfTheStream(connection);
        verify(messageListener).onMessage(same(small), same(connection));
        verify(messageListener, never()).onMessage(same(oversized), any());
        assertNull(connection.getRuleViolation());
    }

    // A relay of an earlier version stores and relays payloads without a size limit, so such elements are dropped
    // without a rule violation
    @Test
    void dropsElementsAboveTheSizeOfTheirTypeWithoutAViolation() throws IOException {
        RefreshOfferMessage small = refreshOfferMessage(1, 100);
        RefreshOfferMessage oversized = refreshOfferMessage(2, Connection.getPermittedMessageSize());
        RefreshOfferMessage oversizedToo = refreshOfferMessage(3, Connection.getPermittedMessageSize());
        RefreshOfferMessage next = refreshOfferMessage(4, 100);

        Connection connection = connection(concat(
                delimited(bundle(proto(small), proto(oversized), proto(oversizedToo))),
                delimited(bundle(proto(next)))));

        verifyClosedAtTheEndOfTheStream(connection);
        verify(messageListener).onMessage(same(small), same(connection));
        verify(messageListener).onMessage(same(next), same(connection));
        verify(messageListener, never()).onMessage(same(oversized), any());
        verify(messageListener, never()).onMessage(same(oversizedToo), any());
        assertNull(connection.getRuleViolation());
    }

    // A relay of an earlier version passes some messages on with the message version which it received, for example a
    // RefreshOfferMessage, so such elements are dropped without a rule violation
    @Test
    void dropsElementsOfAnotherNetworkWithoutAViolation() throws IOException {
        RefreshOfferMessage small = refreshOfferMessage(1, 100);
        RefreshOfferMessage otherNetwork = ofAnotherNetwork(refreshOfferMessage(2, 100));
        RefreshOfferMessage otherNetworkToo = ofAnotherNetwork(refreshOfferMessage(3, 100));

        Connection connection = connection(delimited(bundle(proto(small), proto(otherNetwork),
                proto(otherNetworkToo))));

        verifyClosedAtTheEndOfTheStream(connection);
        verify(messageListener).onMessage(same(small), same(connection));
        verify(messageListener, never()).onMessage(same(otherNetwork), any());
        verify(messageListener, never()).onMessage(same(otherNetworkToo), any());
        assertNull(connection.getRuleViolation());
    }

    // A relay of an earlier version stores and relays a payload which this version rejects as malformed, for example
    // a SignedWitness without keys, so such elements are dropped without a rule violation
    @Test
    void dropsElementsWhosePayloadHashHasTheWrongSizeWithoutAViolation() throws IOException {
        RefreshOfferMessage small = refreshOfferMessage(1, 100);
        // The stub cannot be serialized, so it stands for the element which the resolver returns for any proto
        protobuf.NetworkEnvelope wrongHashSizeProto = protobuf.NetworkEnvelope.newBuilder()
                .setMessageVersion(Version.getP2PMessageVersion())
                .setAddPersistableNetworkPayloadMessage(protobuf.AddPersistableNetworkPayloadMessage.newBuilder())
                .build();
        AddPersistableNetworkPayloadMessage wrongHashSize =
                new AddPersistableNetworkPayloadMessage(new PersistableNetworkPayloadStub(false));
        elements.put(wrongHashSizeProto, wrongHashSize);

        Connection connection = connection(delimited(bundle(wrongHashSizeProto, proto(small), wrongHashSizeProto)));

        verifyClosedAtTheEndOfTheStream(connection);
        verify(messageListener, never()).onMessage(same(wrongHashSize), any());
        verify(messageListener).onMessage(same(small), same(connection));
        assertNull(connection.getRuleViolation());
    }

    @Test
    void dropsAnElementWhichIsNoBroadcastMessage() throws IOException {
        Ping ping = new Ping(1, 0);
        RefreshOfferMessage small = refreshOfferMessage(1, 100);
        elements.put(ping.toProtoNetworkEnvelope(), ping);

        Connection connection = connection(delimited(bundle(ping.toProtoNetworkEnvelope(), proto(small))));

        verifyClosedAtTheEndOfTheStream(connection);
        verify(messageListener, never()).onMessage(same(ping), any());
        verify(messageListener).onMessage(same(small), same(connection));
    }

    @Test
    void closesAtTheSecondElementWhichIsNoBroadcastMessage() throws IOException {
        // A CloseConnectionMessage in a bundle must not reach the handlers as a message; only the connection handles it
        CloseConnectionMessage closeConnectionMessage = new CloseConnectionMessage("reason");
        RefreshOfferMessage nested = refreshOfferMessage(1, 100);
        protobuf.NetworkEnvelope nestedBundle = bundle(proto(nested));
        elements.put(closeConnectionMessage.toProtoNetworkEnvelope(), closeConnectionMessage);

        Connection connection = connection(delimited(bundle(closeConnectionMessage.toProtoNetworkEnvelope(),
                nestedBundle)));

        verifyClosedForRuleViolation(connection, RuleViolation.INVALID_DATA_TYPE);
        verify(messageListener, never()).onMessage(any(), any());
    }

    private void verifyClosedAtTheEndOfTheStream(Connection connection) {
        // The end of the stream closes the connection after the messages before it are handled
        verify(connectionListener, timeout(5_000)).onDisconnect(eq(CloseConnectionReason.NO_PROTO_BUFFER_ENV),
                same(connection));
    }

    private void verifyClosedForRuleViolation(Connection connection, RuleViolation ruleViolation) {
        verify(connectionListener, timeout(5_000)).onDisconnect(eq(CloseConnectionReason.RULE_VIOLATION),
                same(connection));
        assertEquals(ruleViolation, connection.getRuleViolation());
    }

    private RefreshOfferMessage refreshOfferMessage(int sequenceNumber, int signatureSize) {
        byte[] signature = new byte[signatureSize];
        Arrays.fill(signature, (byte) 1);
        RefreshOfferMessage message = new RefreshOfferMessage(new byte[20], signature, new byte[20], sequenceNumber);
        elements.put(message.toProtoNetworkEnvelope(), message);
        return message;
    }

    // The message with the message version of another network
    private RefreshOfferMessage ofAnotherNetwork(RefreshOfferMessage message) {
        protobuf.NetworkEnvelope otherNetworkProto = proto(message).toBuilder()
                .setMessageVersion(Version.getP2PMessageVersion() + 1)
                .build();
        RefreshOfferMessage otherNetwork = RefreshOfferMessage.fromProto(otherNetworkProto.getRefreshOfferMessage(),
                otherNetworkProto.getMessageVersion());
        elements.put(otherNetworkProto, otherNetwork);
        return otherNetwork;
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
