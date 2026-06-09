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

import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

public final class CanonicalEncoder {
    public static final CanonicalEncoder DEFAULT = new CanonicalEncoder();

    public <T> byte[] encode(T value, CanonicalSchema<T> schema) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(schema);

        CanonicalWriter writer = new CanonicalWriter();
        schema.getFields().forEach(field -> writeField(writer, field, value));
        return writer.toByteArray();
    }

    private <T> void writeField(CanonicalWriter writer, CanonicalSchema.Field<T> field, T value) {
        @Nullable
        Object fieldValue = field.getValue(value);

        switch (field.getType()) {
            case INT32:
                writer.writeInt32(field.getNumber(), (int) fieldValue);
                break;
            case UINT32:
                writer.writeUInt32(field.getNumber(), (int) fieldValue);
                break;
            case INT64:
                writer.writeInt64(field.getNumber(), (long) fieldValue);
                break;
            case DOUBLE:
                writer.writeDouble(field.getNumber(), (double) fieldValue);
                break;
            case BOOL:
                writer.writeBool(field.getNumber(), (boolean) fieldValue);
                break;
            case ENUM:
                writer.writeEnum(field.getNumber(), (CanonicalEnum) fieldValue);
                break;
            case STRING:
                writer.writeString(field.getNumber(), (String) fieldValue);
                break;
            case BYTES:
                writer.writeBytes(field.getNumber(), (byte[]) fieldValue);
                break;
            case COMPOSE:
                writeCompose(writer, field, fieldValue);
                break;
            case EXTEND:
                writeExtend(writer, field, fieldValue);
                break;
            case ONEOF:
                writeOneof(writer, field, fieldValue);
                break;
            case REPEATED_COMPOSE:
                writeRepeatedCompose(writer, field, fieldValue);
                break;
            case PACKED_REPEATED_INT32:
                writePackedRepeatedInt32(writer, field, fieldValue);
                break;
            case REPEATED_STRING:
                writeRepeatedString(writer, field, fieldValue);
                break;
            case MAP:
                writeMap(writer, field, fieldValue);
                break;
        }
    }

    private <T> void writeCompose(CanonicalWriter writer, CanonicalSchema.Field<T> field, @Nullable Object fieldValue) {
        if (fieldValue != null) {
            writer.writeCompose(field.getNumber(), encodeNested(fieldValue, Objects.requireNonNull(field.getSchema())));
        }
    }

    private <T> void writeExtend(CanonicalWriter writer, CanonicalSchema.Field<T> field, @Nullable Object fieldValue) {
        if (fieldValue != null) {
            writer.writeExtend(field.getNumber(), encodeNested(fieldValue, Objects.requireNonNull(field.getSchema())));
        }
    }

    private <T> void writeOneof(CanonicalWriter writer, CanonicalSchema.Field<T> field, @Nullable Object fieldValue) {
        if (fieldValue != null) {
            writer.writeOneof(field.getNumber(), encodeNested(fieldValue, Objects.requireNonNull(field.getSchema())));
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void writeRepeatedString(CanonicalWriter writer, CanonicalSchema.Field<T> field, Object fieldValue) {
        writer.writeRepeatedString(field.getNumber(), (List<String>) fieldValue);
    }

    private <T> void writeRepeatedCompose(CanonicalWriter writer, CanonicalSchema.Field<T> field, Object fieldValue) {
        List<?> values = (List<?>) Objects.requireNonNull(fieldValue);
        CanonicalSchema<?> schema = Objects.requireNonNull(field.getSchema());
        values.forEach(value -> writer.writeCompose(field.getNumber(), encodeNested(value, schema)));
    }

    @SuppressWarnings("unchecked")
    private <T> void writePackedRepeatedInt32(CanonicalWriter writer, CanonicalSchema.Field<T> field, Object fieldValue) {
        writer.writePackedRepeatedInt32(field.getNumber(), (List<Integer>) fieldValue);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> void writeMap(CanonicalWriter writer, CanonicalSchema.Field<T> field, @Nullable Object fieldValue) {
        Map<?, ?> source = (Map<?, ?>) Objects.requireNonNull(fieldValue);
        if (source.isEmpty()) {
            return;
        }

        CanonicalSchema.MapEncoding mapEncoding = Objects.requireNonNull(field.getMapEncoding());
        List<Map.Entry<?, ?>> entries = mapEncoding.getEntries((Map) source);
        mapEncoding.iterate(entries).forEachRemaining(entry ->
                writer.writeMapEntry(field.getNumber(), encodeMapEntry((Map.Entry<?, ?>) entry, mapEncoding)));
    }

    private byte[] encodeMapEntry(Map.Entry<?, ?> entry, CanonicalSchema.MapEncoding<?, ?, ?, ?> mapEncoding) {
        CanonicalWriter writer = new CanonicalWriter();
        writeMapEntryValue(writer, 1, mapEncoding.getKeyType(), entry.getKey(), null);
        writeMapEntryValue(writer, 2, mapEncoding.getValueType(), entry.getValue(), mapEncoding.getValueSchema());
        return writer.toByteArray();
    }

    private void writeMapEntryValue(CanonicalWriter writer,
                                    int fieldNumber,
                                    CanonicalSchema.FieldType type,
                                    @Nullable Object fieldValue,
                                    @Nullable CanonicalSchema<?> schema) {
        Objects.requireNonNull(fieldValue, "Canonical map entry values must not be null");

        switch (type) {
            case INT32:
                writer.writeInt32Value(fieldNumber, (int) fieldValue);
                break;
            case UINT32:
                writer.writeUInt32Value(fieldNumber, (int) fieldValue);
                break;
            case INT64:
                writer.writeInt64Value(fieldNumber, (long) fieldValue);
                break;
            case DOUBLE:
                writer.writeDoubleValue(fieldNumber, (double) fieldValue);
                break;
            case BOOL:
                writer.writeBoolValue(fieldNumber, (boolean) fieldValue);
                break;
            case ENUM:
                if (fieldValue instanceof CanonicalEnum canonicalEnum) {
                    writer.writeEnumValue(fieldNumber, canonicalEnum.getCode());
                } else {
                    writer.writeEnumValue(fieldNumber, (int) fieldValue);
                }
                break;
            case STRING:
                writer.writeStringValue(fieldNumber, (String) fieldValue);
                break;
            case BYTES:
                writer.writeLengthDelimitedValue(fieldNumber, (byte[]) fieldValue);
                break;
            case COMPOSE:
                writer.writeLengthDelimitedValue(fieldNumber, encodeNested(fieldValue, Objects.requireNonNull(schema)));
                break;
            case EXTEND:
                writer.writeLengthDelimitedValue(fieldNumber, encodeNested(fieldValue, Objects.requireNonNull(schema)));
                break;
            case ONEOF:
            case PACKED_REPEATED_INT32:
            case REPEATED_COMPOSE:
            case REPEATED_STRING:
            case MAP:
                throw new IllegalArgumentException("Unsupported canonical map entry type " + type);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private byte[] encodeNested(Object value, CanonicalSchema<?> schema) {
        return encode(value, (CanonicalSchema) schema);
    }
}
