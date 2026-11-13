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

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.InvalidProtocolBufferException;

import com.google.common.annotations.VisibleForTesting;

import java.net.SocketTimeoutException;

import java.time.Duration;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.LongSupplier;

import org.jetbrains.annotations.Nullable;

/**
 * Reads messages with the framing of docs/specifications/network/received-messages.md: a varint length prefix,
 * followed by the serialized envelope. It is the receive part of NetworkEnvelopeSocket of Bisq 2, with the same code.
 * The owner of the stream passes the size limit of the envelope.
 */
class NetworkEnvelopeReader {
    // Protobuf writes the length as a varint of 32 bits, which has at most 5 bytes.
    private static final int MAX_LENGTH_PREFIX_SIZE = 5;
    // The bytes of a message must arrive within this time after its length prefix, as in Bisq 2. The requesters of the
    // large responses wait at most 4 minutes for them (see docs/specifications/network/received-messages.md).
    static final Duration MAX_MESSAGE_READ_DURATION = Duration.ofMinutes(4);
    // The size of a single read of the bytes of a message. The deadline is checked after each read.
    private static final int READ_CHUNK_SIZE = 64 * 1024;

    private final InputStream inputStream;
    private final int maxSerializedSize;
    private final LongSupplier nanoTime;

    NetworkEnvelopeReader(InputStream inputStream, int maxSerializedSize) {
        this(inputStream, maxSerializedSize, System::nanoTime);
    }

    @VisibleForTesting
    NetworkEnvelopeReader(InputStream inputStream, int maxSerializedSize, LongSupplier nanoTime) {
        this.inputStream = inputStream;
        this.maxSerializedSize = maxSerializedSize;
        this.nanoTime = nanoTime;
    }

    /**
     * Reads and parses the next message. Connection uses {@link #receiveNextMessage()} and {@link #parse(byte[])}
     * instead, so that it closes the connection after a failure of the framing, but reads on after bytes which are not
     * a valid message.
     *
     * @return The next message, or null if the input stream ends before the first byte of a length prefix.
     */
    @Nullable
    protobuf.NetworkEnvelope receiveNextEnvelope() throws IOException {
        byte[] message = receiveNextMessage();
        return message == null ? null : parse(message);
    }

    /**
     * Reads the bytes of the next message (see docs/specifications/network/received-messages.md). The method runs before
     * any other check of the message, also on an inbound connection whose peer is unknown.
     * <p>
     * Connection closes the connection after any exception of this method: the start of the next message is then
     * unknown.
     *
     * @return The bytes of the next message, or null if the input stream ends before the first byte of a length prefix.
     * @throws InvalidProtocolBufferException if the length prefix has more than 5 bytes, if the length exceeds the size
     *                                        limit (before any byte of the message is read), or if the stream ends
     *                                        inside the length prefix.
     * @throws EOFException                   if the stream ends inside the message.
     * @throws SocketTimeoutException         if the bytes of the message do not arrive within
     *                                        {@link #MAX_MESSAGE_READ_DURATION}.
     */
    @Nullable
    byte[] receiveNextMessage() throws IOException {
        // The decoder must not take bytes after the length prefix: they would be missing for the next message. With a
        // buffer of 1 byte it reads the prefix one byte at a time.
        CodedInputStream lengthPrefixInput = CodedInputStream.newInstance(inputStream, 1);
        // Reads the first byte of the prefix into the buffer of the decoder, where readRawVarint64 uses it
        if (lengthPrefixInput.isAtEnd()) {
            return null;
        }
        // readRawVarint32, which parseDelimitedFrom uses, keeps only the lower 32 bits, so a length of 2^32 would
        // become 0. readRawVarint64 keeps 64 bits, but it reads up to 10 bytes and drops higher bits of a prefix of 10
        // bytes. A prefix of at most 5 bytes has at most 35 bits, so the check of its size after the call makes the
        // decoded length exact. An invalid prefix of 6 to 10 bytes has been read then, but no byte of the message.
        long size = lengthPrefixInput.readRawVarint64();
        if (lengthPrefixInput.getTotalBytesRead() > MAX_LENGTH_PREFIX_SIZE) {
            throw new InvalidProtocolBufferException("The length prefix of the received message has more than " +
                    MAX_LENGTH_PREFIX_SIZE + " bytes.");
        }
        // Before we read or allocate anything for the message. We do not read past a larger message to reach the next
        // one: a node which follows the specification never sends it.
        if (size > maxSerializedSize) {
            throw new InvalidProtocolBufferException("The length prefix of the received message is " + size +
                    " bytes. The size limit is " + maxSerializedSize + " bytes.");
        }
        return readMessage((int) size);
    }

    /**
     * Parses the bytes of a message.
     * <p>
     * parseFrom(byte[]) reads only the bytes of the array: a field which is longer than the rest of the message fails.
     * It also rejects an end-group tag without a start-group tag. The parsed objects do not reference the array.
     * <p>
     * The nesting limit stays at the protobuf default of 100 levels. The memory of a parse grows with the number of
     * sub-messages, not with their depth, so a lower limit would not reduce it. A lower limit would only be a new
     * protocol rule which a later message type could break.
     */
    static protobuf.NetworkEnvelope parse(byte[] message) throws InvalidProtocolBufferException {
        try {
            return protobuf.NetworkEnvelope.parseFrom(message);
        } catch (InvalidProtocolBufferException exception) {
            clearUnfinishedMessages(exception);
            throw exception;
        }
    }

    // We read all bytes of the message before we parse it. The parsed objects of a message can need many times more
    // memory than its bytes, up to about 22 times for many empty sub-messages. While a peer sends a message slowly, we
    // hold only its bytes. Only the length marks the end of a message, protobuf has no end marker.
    // The buffer grows while the bytes arrive, so a length prefix alone does not allocate the announced size. We read
    // exactly the bytes of the message, never a byte of the next one.
    // The socket timeout applies to each single read only. A peer which sends a byte shortly before each timeout would
    // keep the read thread and the connection without limit, so a deadline applies to the whole message. A read which
    // blocks cannot be interrupted, so the deadline is checked after each read, and the connection closes at the latest
    // one socket timeout after the deadline.
    private byte[] readMessage(int size) throws IOException {
        long deadline = nanoTime.getAsLong() + MAX_MESSAGE_READ_DURATION.toNanos();
        ByteArrayOutputStream message = new ByteArrayOutputStream(Math.min(size, READ_CHUNK_SIZE));
        byte[] chunk = new byte[Math.min(size, READ_CHUNK_SIZE)];
        int remaining = size;
        while (remaining > 0) {
            int numRead = inputStream.read(chunk, 0, Math.min(chunk.length, remaining));
            // The received part can be a valid message, for example if the stream ended at a field boundary
            if (numRead == -1) {
                throw new EOFException("The input stream ended after " + (size - remaining) + " of " + size +
                        " bytes of a message.");
            }
            message.write(chunk, 0, numRead);
            remaining -= numRead;
            if (nanoTime.getAsLong() - deadline > 0) {
                throw new SocketTimeoutException("Received " + (size - remaining) + " of " + size +
                        " bytes of a message after the deadline of " + MAX_MESSAGE_READ_DURATION.toSeconds() +
                        " seconds.");
            }
        }
        return message.toByteArray();
    }

    // Protobuf attaches the parsed part of a message to a parse failure. Bisq 2 has this in ProtobufUtils.
    private static void clearUnfinishedMessages(Throwable exception) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = exception; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof InvalidProtocolBufferException parseException) {
                parseException.setUnfinishedMessage(null);
            }
        }
    }
}
