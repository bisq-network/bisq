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

package bisq.core.dao.monitoring.serialization;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

/**
 * Self-describing byte writer for the canonical DAO state hash format.
 * No protobuf, no Java HashMap. Every primitive has a fixed-width or
 * length-prefixed binary encoding so the produced bytes depend only on
 * the logical values written, not on any JVM/JDK or library version.
 *
 * <p><strong>Streaming.</strong> The writer wraps an arbitrary
 * {@link OutputStream}. For large states use {@link #intoDigest(MessageDigest)}
 * so bytes flow directly into the hash without materializing an intermediate
 * {@code byte[]}. Use {@link #intoMemory()} when a byte array is genuinely
 * needed (e.g. for tests or for the {@code stateAsBytes} value that gets
 * folded into the prev-hash chain).
 *
 * <p><strong>Primitive encodings.</strong>
 * <ul>
 *   <li>{@code raw} — one byte written verbatim, used only for the leading
 *       version/domain separator bytes</li>
 *   <li>{@code i32}, {@code i64} — fixed-width big-endian two's-complement</li>
 *   <li>{@code bool} — 1 byte: {@code 0x00} or {@code 0x01}</li>
 *   <li>{@code varint} — unsigned LEB128 (1..5 bytes for {@code int},
 *       1..10 bytes for {@code long}). Always minimum-width.</li>
 *   <li>{@code bytes} — {@code varint(length)} then {@code length} raw bytes</li>
 *   <li>{@code string} — {@code bytes} of the UTF-8 encoding</li>
 *   <li>{@code enumCode} — {@code varint} of a caller-supplied stable code.
 *       Codes live next to the call site (see {@link CanonicalLeafWriter}) so
 *       enum-constant renames in the model classes cannot silently fork the
 *       hash. Reordering an enum is also caught by golden-vector tests.</li>
 *   <li>{@code optional(T)} — 1-byte presence ({@code 0x00} absent /
 *       {@code 0x01} present), then if present the value as {@code T}</li>
 *   <li>{@code list(T)} — {@code varint(count)} then items in order</li>
 *   <li>{@code sortedMap(K,V)} — {@code varint(size)} then entries in
 *       {@code TreeMap.entrySet()} order (natural sort)</li>
 * </ul>
 *
 * <p>Field order within a struct is fixed by the writer method; there are no
 * field tags. The byte stream is positional. Format version + domain are
 * signaled by a domain-separator string at the very start of the preimage.
 */
public final class CanonicalWriter {

    private final OutputStream out;

    private CanonicalWriter(OutputStream out) {
        this.out = out;
    }

    /**
     * Writer backed by a fresh in-memory buffer. Use {@link #toByteArray()}
     * to retrieve the bytes when done.
     */
    public static CanonicalWriter intoMemory() {
        return new CanonicalWriter(new ByteArrayOutputStream(1024));
    }

    /**
     * Writer that streams every written byte directly into the given
     * {@link MessageDigest}. No intermediate {@code byte[]} is built; suitable
     * for large states. {@link #toByteArray()} is not available on this
     * writer (call {@code digest.digest()} on the supplied digest instead).
     */
    public static CanonicalWriter intoDigest(MessageDigest digest) {
        return new CanonicalWriter(new MessageDigestOutputStream(digest));
    }

    /**
     * Snapshot of the buffered bytes. Only valid for writers created via
     * {@link #intoMemory()}; throws otherwise.
     */
    public byte[] toByteArray() {
        if (!(out instanceof ByteArrayOutputStream)) {
            throw new IllegalStateException(
                    "toByteArray() only valid for writers created via intoMemory()");
        }
        return ((ByteArrayOutputStream) out).toByteArray();
    }

    public CanonicalWriter writeRawByte(int b) {
        write(b & 0xff);
        return this;
    }

    public CanonicalWriter writeRawBytes(byte[] b) {
        write(b, 0, b.length);
        return this;
    }

    public CanonicalWriter writeI32(int value) {
        write((value >>> 24) & 0xff);
        write((value >>> 16) & 0xff);
        write((value >>> 8) & 0xff);
        write(value & 0xff);
        return this;
    }

    public CanonicalWriter writeI64(long value) {
        write((int) ((value >>> 56) & 0xff));
        write((int) ((value >>> 48) & 0xff));
        write((int) ((value >>> 40) & 0xff));
        write((int) ((value >>> 32) & 0xff));
        write((int) ((value >>> 24) & 0xff));
        write((int) ((value >>> 16) & 0xff));
        write((int) ((value >>> 8) & 0xff));
        write((int) (value & 0xff));
        return this;
    }

    public CanonicalWriter writeBool(boolean value) {
        write(value ? 1 : 0);
        return this;
    }

    public CanonicalWriter writeVarint(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Negative varint not supported: " + value);
        }
        while ((value & ~0x7F) != 0) {
            write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        write(value & 0x7F);
        return this;
    }

    public CanonicalWriter writeVarintLong(long value) {
        if (value < 0L) {
            throw new IllegalArgumentException("Negative varint not supported: " + value);
        }
        while ((value & ~0x7FL) != 0L) {
            write((int) ((value & 0x7FL) | 0x80L));
            value >>>= 7;
        }
        write((int) (value & 0x7FL));
        return this;
    }

    public CanonicalWriter writeBytes(byte[] b) {
        writeVarint(b.length);
        write(b, 0, b.length);
        return this;
    }

    public CanonicalWriter writeString(String s) {
        return writeBytes(s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Write an enum as a stable numeric code. The mapping table lives at the
     * call site (see {@link CanonicalLeafWriter}), not on the enum class, so
     * a rename or reorder of the model enum cannot silently fork bytes.
     */
    public CanonicalWriter writeEnumCode(int code) {
        return writeVarint(code);
    }

    public CanonicalWriter writeOptionalString(String s) {
        if (s == null) {
            write(0);
        } else {
            write(1);
            writeString(s);
        }
        return this;
    }

    public CanonicalWriter writeOptionalBytes(byte[] b) {
        if (b == null) {
            write(0);
        } else {
            write(1);
            writeBytes(b);
        }
        return this;
    }

    public <T> CanonicalWriter writeOptional(T value, BiConsumer<CanonicalWriter, T> writer) {
        if (value == null) {
            write(0);
        } else {
            write(1);
            writer.accept(this, value);
        }
        return this;
    }

    public <T> CanonicalWriter writeList(List<T> items, BiConsumer<CanonicalWriter, T> writer) {
        writeVarint(items.size());
        for (T item : items) {
            writer.accept(this, item);
        }
        return this;
    }

    public CanonicalWriter writeStringList(List<String> items) {
        return writeList(items, CanonicalWriter::writeString);
    }

    public <K, V> CanonicalWriter writeSortedMap(TreeMap<K, V> map,
                                                 BiConsumer<CanonicalWriter, K> keyWriter,
                                                 BiConsumer<CanonicalWriter, V> valueWriter) {
        writeVarint(map.size());
        for (Map.Entry<K, V> entry : map.entrySet()) {
            keyWriter.accept(this, entry.getKey());
            valueWriter.accept(this, entry.getValue());
        }
        return this;
    }

    // ---------- internal IO plumbing ----------

    private void write(int b) {
        try {
            out.write(b);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void write(byte[] buf, int off, int len) {
        try {
            out.write(buf, off, len);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final class MessageDigestOutputStream extends OutputStream {
        private final MessageDigest digest;

        MessageDigestOutputStream(MessageDigest digest) {
            this.digest = digest;
        }

        @Override
        public void write(int b) {
            digest.update((byte) b);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            digest.update(b, off, len);
        }
    }
}
