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
import java.nio.charset.StandardCharsets;
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
 * <p><strong>Primitive encodings.</strong>
 * <ul>
 *   <li>{@code i32} — 4 bytes, big-endian, two's-complement signed</li>
 *   <li>{@code i64} — 8 bytes, big-endian, two's-complement signed</li>
 *   <li>{@code bool} — 1 byte, {@code 0x00} or {@code 0x01}</li>
 *   <li>{@code varint} — unsigned LEB128 (1..5 bytes for {@code int},
 *       1..10 bytes for {@code long}). Always minimum-width.</li>
 *   <li>{@code bytes} — {@code varint(length)} then {@code length} raw bytes</li>
 *   <li>{@code string} — {@code bytes} of the UTF-8 encoding of the string</li>
 *   <li>{@code enum} — {@code string} of {@link Enum#name()}; insensitive to
 *       ordinal reordering across versions</li>
 *   <li>{@code optional(T)} — 1-byte presence tag ({@code 0x00} absent,
 *       {@code 0x01} present) then, if present, the value encoded as {@code T}</li>
 *   <li>{@code list(T)} — {@code varint(count)} then each item encoded as {@code T}</li>
 *   <li>{@code sortedMap(K,V)} — {@code varint(size)} then each entry's key and
 *       value in {@code TreeMap.entrySet()} order (natural sort)</li>
 * </ul>
 *
 * <p>Field order within a struct is fixed by the writer method; there are no
 * field tags. The byte stream is positional, not self-describing at the field
 * level — that keeps the encoded size small. Format version is signaled by a
 * single leading tag byte chosen by the caller.
 */
public final class CanonicalWriter {

    private final ByteArrayOutputStream out;

    public CanonicalWriter() {
        this(1024);
    }

    public CanonicalWriter(int initialCapacity) {
        this.out = new ByteArrayOutputStream(initialCapacity);
    }

    public byte[] toByteArray() {
        return out.toByteArray();
    }

    public CanonicalWriter writeRawByte(int b) {
        out.write(b & 0xff);
        return this;
    }

    public CanonicalWriter writeI32(int value) {
        out.write((value >>> 24) & 0xff);
        out.write((value >>> 16) & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write(value & 0xff);
        return this;
    }

    public CanonicalWriter writeI64(long value) {
        out.write((int) ((value >>> 56) & 0xff));
        out.write((int) ((value >>> 48) & 0xff));
        out.write((int) ((value >>> 40) & 0xff));
        out.write((int) ((value >>> 32) & 0xff));
        out.write((int) ((value >>> 24) & 0xff));
        out.write((int) ((value >>> 16) & 0xff));
        out.write((int) ((value >>> 8) & 0xff));
        out.write((int) (value & 0xff));
        return this;
    }

    public CanonicalWriter writeBool(boolean value) {
        out.write(value ? 1 : 0);
        return this;
    }

    public CanonicalWriter writeVarint(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Negative varint not supported: " + value);
        }
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value & 0x7F);
        return this;
    }

    public CanonicalWriter writeVarintLong(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Negative varint not supported: " + value);
        }
        while ((value & ~0x7FL) != 0L) {
            out.write((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.write((int) (value & 0x7F));
        return this;
    }

    public CanonicalWriter writeBytes(byte[] b) {
        writeVarint(b.length);
        out.write(b, 0, b.length);
        return this;
    }

    public CanonicalWriter writeString(String s) {
        return writeBytes(s.getBytes(StandardCharsets.UTF_8));
    }

    public CanonicalWriter writeEnum(Enum<?> e) {
        return writeString(e.name());
    }

    public CanonicalWriter writeOptionalString(String s) {
        if (s == null) {
            out.write(0);
        } else {
            out.write(1);
            writeString(s);
        }
        return this;
    }

    public CanonicalWriter writeOptionalBytes(byte[] b) {
        if (b == null) {
            out.write(0);
        } else {
            out.write(1);
            writeBytes(b);
        }
        return this;
    }

    public CanonicalWriter writeOptionalEnum(Enum<?> e) {
        if (e == null) {
            out.write(0);
        } else {
            out.write(1);
            writeEnum(e);
        }
        return this;
    }

    public <T> CanonicalWriter writeOptional(T value, BiConsumer<CanonicalWriter, T> writer) {
        if (value == null) {
            out.write(0);
        } else {
            out.write(1);
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
}
