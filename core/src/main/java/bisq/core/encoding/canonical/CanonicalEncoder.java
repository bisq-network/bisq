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

package bisq.core.encoding.canonical;

import java.util.List;
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
            case INT64:
                writer.writeInt64(field.getNumber(), (long) fieldValue);
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
            case REPEATED_STRING:
                writeRepeatedString(writer, field, fieldValue);
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

    @SuppressWarnings("unchecked")
    private <T> void writeRepeatedString(CanonicalWriter writer, CanonicalSchema.Field<T> field, Object fieldValue) {
        writer.writeRepeatedString(field.getNumber(), (List<String>) fieldValue);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private byte[] encodeNested(Object value, CanonicalSchema<?> schema) {
        return encode(value, (CanonicalSchema) schema);
    }
}
