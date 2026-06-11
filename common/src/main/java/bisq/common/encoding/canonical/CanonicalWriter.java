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

package bisq.common.encoding.canonical;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

public final class CanonicalWriter {
    private static final int WIRE_TYPE_VARINT = 0;
    private static final int WIRE_TYPE_FIXED64 = 1;
    private static final int WIRE_TYPE_LENGTH_DELIMITED = 2;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    public byte[] toByteArray() {
        return out.toByteArray();
    }

    /**
     * Writes pre-encoded bytes directly to the output without adding field tags,
     * wire types, lengths, or any other canonical encoding.
     */
    public void writeRawBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "Canonical raw bytes must not be null");
        out.write(bytes, 0, bytes.length);
    }

    public void writeInt32(int fieldNumber, int value) {
        if (value != 0) {
            writeInt32Value(fieldNumber, value);
        }
    }

    public void writeUInt32(int fieldNumber, int value) {
        if (value != 0) {
            writeUInt32Value(fieldNumber, value);
        }
    }

    public void writeInt64(int fieldNumber, long value) {
        if (value != 0) {
            writeInt64Value(fieldNumber, value);
        }
    }

    public void writeDouble(int fieldNumber, double value) {
        if (Double.doubleToRawLongBits(value) != 0) {
            writeDoubleValue(fieldNumber, value);
        }
    }

    public void writeBool(int fieldNumber, boolean value) {
        if (value) {
            writeBoolValue(fieldNumber, value);
        }
    }

    public void writeEnum(int fieldNumber, @Nullable CanonicalEnum value) {
        if (value != null) {
            writeEnum(fieldNumber, value.getCode());
        }
    }

    public void writeEnum(int fieldNumber, int value) {
        if (value != 0) {
            writeEnumValue(fieldNumber, value);
        }
    }

    public void writeString(int fieldNumber, @Nullable String value) {
        if (value != null && !value.isEmpty()) {
            writeStringValue(fieldNumber, value);
        }
    }

    public void writeRepeatedString(int fieldNumber, List<String> values) {
        values.forEach(value -> writeStringValue(fieldNumber, value));
    }

    public void writePackedRepeatedInt32(int fieldNumber, List<Integer> values) {
        if (values.isEmpty()) {
            return;
        }

        CanonicalWriter packedWriter = new CanonicalWriter();
        values.forEach(packedWriter::writeInt32NoTag);
        writeLengthDelimitedValue(fieldNumber, packedWriter.toByteArray());
    }

    public void writeBytes(int fieldNumber, @Nullable byte[] value) {
        if (value != null && value.length > 0) {
            writeLengthDelimitedValue(fieldNumber, value);
        }
    }

    public void writeCompose(int fieldNumber, @Nullable byte[] value) {
        writeNestedMessage(fieldNumber, value);
    }

    public void writeExtend(int fieldNumber, @Nullable byte[] value) {
        writeNestedMessage(fieldNumber, value);
    }

    public void writeOneof(int fieldNumber, @Nullable byte[] value) {
        writeNestedMessage(fieldNumber, value);
    }

    private void writeNestedMessage(int fieldNumber, @Nullable byte[] value) {
        if (value != null) {
            writeLengthDelimitedValue(fieldNumber, value);
        }
    }

    public void writeMapEntry(int fieldNumber, byte[] value) {
        writeLengthDelimitedValue(fieldNumber, value);
    }

    void writeInt32Value(int fieldNumber, int value) {
        writeTag(fieldNumber, WIRE_TYPE_VARINT);
        writeInt32NoTag(value);
    }

    void writeUInt32Value(int fieldNumber, int value) {
        writeTag(fieldNumber, WIRE_TYPE_VARINT);
        writeVarint32(value);
    }

    void writeInt64Value(int fieldNumber, long value) {
        writeTag(fieldNumber, WIRE_TYPE_VARINT);
        writeVarint64(value);
    }

    void writeDoubleValue(int fieldNumber, double value) {
        writeTag(fieldNumber, WIRE_TYPE_FIXED64);
        writeLittleEndian64(Double.doubleToRawLongBits(value));
    }

    void writeBoolValue(int fieldNumber, boolean value) {
        writeInt32Value(fieldNumber, value ? 1 : 0);
    }

    void writeEnumValue(int fieldNumber, int value) {
        writeInt32Value(fieldNumber, value);
    }

    void writeStringValue(int fieldNumber, String value) {
        writeLengthDelimitedValue(fieldNumber, value.getBytes(StandardCharsets.UTF_8));
    }

    void writeLengthDelimitedValue(int fieldNumber, byte[] value) {
        writeTag(fieldNumber, WIRE_TYPE_LENGTH_DELIMITED);
        writeVarint32(value.length);
        out.write(value, 0, value.length);
    }

    private void writeTag(int fieldNumber, int wireType) {
        writeVarint32((fieldNumber << 3) | wireType);
    }

    private void writeInt32NoTag(int value) {
        if (value >= 0) {
            writeVarint32(value);
        } else {
            writeVarint64(value);
        }
    }

    private void writeLittleEndian64(long value) {
        for (int i = 0; i < Long.BYTES; i++) {
            out.write((int) value & 0xff);
            value >>>= Byte.SIZE;
        }
    }

    private void writeVarint32(int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private void writeVarint64(long value) {
        while ((value & ~0x7FL) != 0) {
            out.write(((int) value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write((int) value);
    }
}
