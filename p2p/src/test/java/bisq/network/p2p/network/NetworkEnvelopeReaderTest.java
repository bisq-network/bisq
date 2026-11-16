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

import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.UnknownFieldSet;
import com.google.protobuf.WireFormat;

import java.net.SocketTimeoutException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The receive tests of NetworkEnvelopeSocketTest of Bisq 2, with JUnit assertions instead of AssertJ. Unlike Bisq 2, an end
// of the stream inside a length prefix of up to 5 bytes is an EOFException.
class NetworkEnvelopeReaderTest {
    // The size limit which Connection passes, Connection.MAX_PERMITTED_MESSAGE_SIZE
    private static final int MAX_SERIALIZED_SIZE = 10 * 1024 * 1024;
    private static final byte[] NEXT_BYTES = {1, 2, 3};

    @Test
    void receivesConsecutiveMessages() throws IOException {
        protobuf.NetworkEnvelope first = createProto(1);
        protobuf.NetworkEnvelope second = createProto(2);
        NetworkEnvelopeReader receiver = createReceiver(
                Utilities.concatenateByteArrays(writeDelimited(first), writeDelimited(second)));

        assertEquals(first, receiver.receiveNextEnvelope());
        assertEquals(second, receiver.receiveNextEnvelope());
        assertNull(receiver.receiveNextEnvelope());
    }

    @Test
    void receivesAMessageOfTheSizeLimit() throws IOException {
        protobuf.NetworkEnvelope proto = createProtoOfSize(MAX_SERIALIZED_SIZE);

        NetworkEnvelopeReader receiver = createReceiver(writeDelimited(proto));

        assertEquals(proto, receiver.receiveNextEnvelope());
    }

    @Test
    void appliesTheSizeLimitToEachMessage() throws IOException {
        protobuf.NetworkEnvelope proto = createProtoOfSize(MAX_SERIALIZED_SIZE / 2 + 1);
        byte[] message = writeDelimited(proto);
        NetworkEnvelopeReader receiver = createReceiver(Utilities.concatenateByteArrays(message, message));

        assertEquals(proto, receiver.receiveNextEnvelope());
        assertEquals(proto, receiver.receiveNextEnvelope());
        assertNull(receiver.receiveNextEnvelope());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4096})
    void doesNotReadBytesOfTheNextMessage(int maxBytesPerRead) throws IOException {
        // A reader must not take bytes of the next message in advance: a reader which continues on the same input
        // stream would miss them.
        protobuf.NetworkEnvelope first = createProto(1);
        protobuf.NetworkEnvelope second = createProto(2);
        byte[] secondMessage = writeDelimited(second);
        byte[] receivedBytes = Utilities.concatenateByteArrays(writeDelimited(first), secondMessage);
        ByteArrayInputStream input = new ByteArrayInputStream(receivedBytes) {
            @Override
            public synchronized int read(byte[] bytes, int offset, int length) {
                return super.read(bytes, offset, Math.min(length, maxBytesPerRead));
            }
        };

        assertEquals(first, createReceiver(input).receiveNextEnvelope());
        assertEquals(secondMessage.length, input.available());
        NetworkEnvelopeReader nextReceiver = createReceiver(input);
        assertEquals(second, nextReceiver.receiveNextEnvelope());
        assertNull(nextReceiver.receiveNextEnvelope());
    }

    @Test
    void readsAMessageOfLengthZero() throws IOException {
        // A length prefix of 0 announces a message without fields. It is not the end of the stream.
        NetworkEnvelopeReader receiver = createReceiver(new byte[]{0});

        assertEquals(protobuf.NetworkEnvelope.getDefaultInstance(), receiver.receiveNextEnvelope());
        assertNull(receiver.receiveNextEnvelope());
    }

    @Test
    void rejectsALengthPrefixAboveTheSizeLimitBeforeReadingTheMessage() throws IOException {
        byte[] receivedBytes = Utilities.concatenateByteArrays(lengthPrefix(MAX_SERIALIZED_SIZE + 1), NEXT_BYTES);
        ByteArrayInputStream input = new ByteArrayInputStream(receivedBytes);
        NetworkEnvelopeReader receiver = createReceiver(input);

        assertThrows(InvalidProtocolBufferException.class, receiver::receiveNextEnvelope);
        // Only the length prefix was read.
        assertArrayEquals(NEXT_BYTES, input.readAllBytes());
    }

    @ParameterizedTest
    @ValueSource(longs = {MAX_SERIALIZED_SIZE + 1, Integer.MAX_VALUE, 1L << 31, (1L << 32) - 1, 1L << 32,
            (1L << 32) + 5, (1L << 35) - 1})
    void rejectsALengthAboveTheSizeLimitWithoutTruncatingIt(long length) {
        // A length of 2^32 + 5 has 5 bytes. Truncated to 32 bits, it would be 5.
        NetworkEnvelopeReader receiver = createReceiver(inputWhichFailsAfter(lengthPrefix(length)));

        assertThrows(InvalidProtocolBufferException.class, receiver::receiveNextEnvelope);
    }

    @Test
    void rejectsALengthPrefixOfMoreThanFiveBytes() throws IOException {
        byte[][] lengthPrefixes = {
                // Value 0 in 6 bytes. Protobuf writes a length with at most 5 bytes.
                {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x00},
                // Value 2^64 + 5 in 10 bytes. readRawVarint64 decodes it as 5.
                {(byte) 0x85, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
                        (byte) 0x80, (byte) 0x80, 0x02},
                // 11 bytes: not a varint.
                {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
                        (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x00}
        };
        for (byte[] lengthPrefix : lengthPrefixes) {
            ByteArrayInputStream input = new ByteArrayInputStream(
                    Utilities.concatenateByteArrays(lengthPrefix, NEXT_BYTES));
            NetworkEnvelopeReader receiver = createReceiver(input);

            assertThrows(InvalidProtocolBufferException.class, receiver::receiveNextEnvelope);
            // The reader did not read the message.
            assertTrue(input.available() >= NEXT_BYTES.length);
        }
    }

    // Connection closes for a rule violation at an InvalidProtocolBufferException, so the reader ends as at the end of a
    // message.
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void failsIfTheStreamEndsInsideTheLengthPrefix(int numReceivedBytes) {
        // Each byte has the continuation bit, so the length prefix is not complete
        byte[] partialLengthPrefix = new byte[numReceivedBytes];
        Arrays.fill(partialLengthPrefix, (byte) 0x80);
        NetworkEnvelopeReader receiver = createReceiver(partialLengthPrefix);

        assertThrows(EOFException.class, receiver::receiveNextEnvelope);
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 9})
    void rejectsALengthPrefixOfMoreThanFiveBytesAlsoIfTheStreamEndsInsideIt(int numReceivedBytes) {
        // The fifth byte has the continuation bit, so the length prefix has more than 5 bytes
        byte[] partialLengthPrefix = new byte[numReceivedBytes];
        Arrays.fill(partialLengthPrefix, (byte) 0x80);
        NetworkEnvelopeReader receiver = createReceiver(partialLengthPrefix);

        assertThrows(InvalidProtocolBufferException.class, receiver::receiveNextEnvelope);
    }

    @Test
    void failsIfTheStreamEndsInsideAMessage() {
        // The received bytes are a valid envelope, but the length prefix announces one byte more.
        byte[] message = createProto(1).toByteArray();
        NetworkEnvelopeReader receiver = createReceiver(
                Utilities.concatenateByteArrays(lengthPrefix(message.length + 1), message));

        assertThrows(EOFException.class, receiver::receiveNextEnvelope);
    }

    @Test
    void rejectsATruncatedFieldWithoutReadingTheNextMessage() throws IOException {
        // A message of one byte: the tag of the field message_version, without its value.
        byte[] nextMessage = writeDelimited(createProto(1));
        ByteArrayInputStream input = new ByteArrayInputStream(
                Utilities.concatenateByteArrays(new byte[]{1, 0x08}, nextMessage));
        NetworkEnvelopeReader receiver = createReceiver(input);

        assertThrows(InvalidProtocolBufferException.class, receiver::receiveNextEnvelope);
        assertArrayEquals(nextMessage, input.readAllBytes());
    }

    @Test
    void rejectsAFieldWhichIsLongerThanItsMessage() throws IOException {
        // A small message with a field which announces 2^31 - 1 bytes. The parser must not read or allocate them.
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(body);
        codedOutputStream.writeTag(1000, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        codedOutputStream.writeUInt32NoTag(Integer.MAX_VALUE);
        codedOutputStream.flush();
        NetworkEnvelopeReader receiver = createReceiver(
                Utilities.concatenateByteArrays(lengthPrefix(body.size()), body.toByteArray()));

        assertThrows(InvalidProtocolBufferException.class, receiver::receiveNextEnvelope);
    }

    @Test
    void rejectsAnEndGroupTagWithoutAStartGroupTag() {
        // 0x08 0x02 is the field message_version with value 2. 0x0C is the end-group tag of field 1, once at the end
        // and once at the start of the message.
        byte[][] messages = {{0x08, 0x02, 0x0C}, {0x0C, 0x08, 0x02}};
        for (byte[] message : messages) {
            NetworkEnvelopeReader receiver = createReceiver(
                    Utilities.concatenateByteArrays(lengthPrefix(message.length), message));

            assertThrows(InvalidProtocolBufferException.class, receiver::receiveNextEnvelope);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0x00, 0x0C})
    void parseFailureDoesNotRetainThePartialMessage(int invalidTag) {
        // Protobuf attaches the parsed fields to failures, including failures after the complete envelope.
        byte[] message = Utilities.concatenateByteArrays(createProtoOfSize(64 * 1024).toByteArray(),
                new byte[]{(byte) invalidTag});

        InvalidProtocolBufferException exception = assertThrows(InvalidProtocolBufferException.class,
                () -> NetworkEnvelopeReader.parse(message));

        assertNull(exception.getUnfinishedMessage());
        assertFalse(exception.getMessage().isBlank());
    }

    // A peer which sends a byte shortly before each socket timeout must not keep the connection without limit (see
    // docs/specifications/network/received-messages.md). Each read here returns 1 byte and takes 61 seconds.
    @Test
    void failsIfTheBytesOfAMessageDoNotArriveWithinTheDeadline() throws IOException {
        protobuf.NetworkEnvelope proto = createProtoOfSize(10);
        AtomicLong nanoTime = new AtomicLong();
        NetworkEnvelopeReader receiver = createReceiver(slowInput(writeDelimited(proto), nanoTime, 61), nanoTime);

        assertThrows(SocketTimeoutException.class, receiver::receiveNextMessage);
    }

    @Test
    void failsIfTheFinalBytesArriveAfterTheDeadline() throws IOException {
        protobuf.NetworkEnvelope proto = createProtoOfSize(10);
        AtomicLong nanoTime = new AtomicLong();
        // The first nine body bytes arrive in 225 seconds; only the final read crosses the deadline.
        NetworkEnvelopeReader receiver = createReceiver(slowInput(writeDelimited(proto), nanoTime, 25), nanoTime);

        assertThrows(SocketTimeoutException.class, receiver::receiveNextMessage);
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 24})
    void readsAMessageWhoseBytesArriveWithinTheDeadline(int secondsPerRead) throws IOException {
        protobuf.NetworkEnvelope proto = createProtoOfSize(10);
        AtomicLong nanoTime = new AtomicLong();
        // Ten body reads, completing before or exactly at the deadline.
        NetworkEnvelopeReader receiver = createReceiver(slowInput(writeDelimited(proto), nanoTime, secondsPerRead),
                nanoTime);

        assertEquals(proto, receiver.receiveNextEnvelope());
    }

    @Test
    void returnsNullIfTheStreamEndsBeforeTheNextMessage() throws IOException {
        assertNull(createReceiver(new byte[0]).receiveNextEnvelope());
    }

    // An envelope with an unknown field which pads it to the given serialized size.
    private static protobuf.NetworkEnvelope createProtoOfSize(int serializedSize) {
        int paddingFieldNumber = 1000;
        int versionFieldSize = 2;
        int fieldSize = serializedSize - versionFieldSize - CodedOutputStream.computeTagSize(paddingFieldNumber);
        // The length of the padding precedes the padding. It can need one byte fewer than the length of the field.
        int paddingSize = fieldSize - CodedOutputStream.computeUInt32SizeNoTag(fieldSize);
        paddingSize = fieldSize - CodedOutputStream.computeUInt32SizeNoTag(paddingSize);
        UnknownFieldSet padding = UnknownFieldSet.newBuilder()
                .addField(paddingFieldNumber, UnknownFieldSet.Field.newBuilder()
                        .addLengthDelimited(ByteString.copyFrom(new byte[paddingSize]))
                        .build())
                .build();
        protobuf.NetworkEnvelope proto = protobuf.NetworkEnvelope.newBuilder()
                .setMessageVersion(1)
                .setUnknownFields(padding)
                .build();
        assertEquals(serializedSize, proto.getSerializedSize());
        return proto;
    }

    private static byte[] lengthPrefix(long length) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(outputStream);
            codedOutputStream.writeUInt64NoTag(length);
            codedOutputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return outputStream.toByteArray();
    }

    // Returns the given bytes. A read of a further byte fails the test.
    private static InputStream inputWhichFailsAfter(byte[] bytes) {
        return new InputStream() {
            private int position;

            @Override
            public int read() {
                if (position == bytes.length) {
                    throw new AssertionError("The reader read more than " + bytes.length + " bytes");
                }
                return Byte.toUnsignedInt(bytes[position++]);
            }
        };
    }

    private static protobuf.NetworkEnvelope createProto(int version) {
        return protobuf.NetworkEnvelope.newBuilder().setMessageVersion(version).build();
    }

    private static byte[] writeDelimited(protobuf.NetworkEnvelope proto) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        proto.writeDelimitedTo(outputStream);
        return outputStream.toByteArray();
    }

    private static NetworkEnvelopeReader createReceiver(byte[] receivedBytes) {
        return createReceiver(new ByteArrayInputStream(receivedBytes));
    }

    private static NetworkEnvelopeReader createReceiver(InputStream inputStream) {
        return new NetworkEnvelopeReader(inputStream, MAX_SERIALIZED_SIZE);
    }

    // Returns at most 1 byte per read and advances the time by the given seconds with each read
    private static InputStream slowInput(byte[] bytes, AtomicLong nanoTime, long secondsPerRead) {
        return new ByteArrayInputStream(bytes) {
            @Override
            public synchronized int read(byte[] buffer, int offset, int length) {
                nanoTime.addAndGet(TimeUnit.SECONDS.toNanos(secondsPerRead));
                return super.read(buffer, offset, Math.min(length, 1));
            }
        };
    }

    private static NetworkEnvelopeReader createReceiver(InputStream inputStream, AtomicLong nanoTime) {
        return new NetworkEnvelopeReader(inputStream, MAX_SERIALIZED_SIZE, nanoTime::get);
    }
}
