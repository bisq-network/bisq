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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import lombok.Getter;

import javax.annotation.Nullable;

public final class CanonicalSchema<T> {
    public static final int CURRENT_VERSION = 1;
    private static final int MIN_FIELD_NUMBER = 1;
    private static final int MAX_FIELD_NUMBER = (1 << 29) - 1;
    private static final int FIRST_RESERVED_FIELD_NUMBER = 19000;
    private static final int LAST_RESERVED_FIELD_NUMBER = 19999;

    @Getter
    private final String messageName;
    @Getter
    private final int version;
    @Getter
    private final List<Field<T>> fields;

    private CanonicalSchema(String messageName, int version, List<Field<T>> fields) {
        this.messageName = messageName;
        this.version = version;
        this.fields = Collections.unmodifiableList(new ArrayList<>(fields));
    }

    public static <T> Builder<T> newBuilder(String messageName) {
        return new Builder<>(messageName);
    }

    public static <T> Builder<T> newBuilder(String messageName, int version) {
        return new Builder<T>(messageName).version(version);
    }

    public enum FieldType {
        INT32,
        INT64,
        ENUM,
        STRING,
        BYTES,
        COMPOSE,
        EXTEND,
        REPEATED_STRING,
        MAP
    }

    public enum Rule {
        OMIT_DEFAULT,
        OMIT_EMPTY,
        OMIT_NULL,
        LIST_ORDER,
        MAP_ORDER
    }

    public static final class Field<T> {
        @Getter
        private final int number;
        @Getter
        private final String name;
        @Getter
        private final FieldType type;
        @Getter
        private final Rule rule;
        private final Function<T, ?> getter;
        @Getter
        @Nullable
        private final CanonicalSchema<?> schema;
        @Getter
        @Nullable
        private final MapEncoding<?, ?, ?, ?> mapEncoding;

        private Field(int number,
                      String name,
                      FieldType type,
                      Rule rule,
                      Function<T, ?> getter,
                      @Nullable CanonicalSchema<?> schema,
                      @Nullable MapEncoding<?, ?, ?, ?> mapEncoding) {
            this.number = number;
            this.name = name;
            this.type = type;
            this.rule = rule;
            this.getter = getter;
            this.schema = schema;
            this.mapEncoding = mapEncoding;
        }

        @Nullable
        Object getValue(T value) {
            return getter.apply(value);
        }
    }

    private static final class FieldDefinition<T> {
        private final int number;
        private final String name;
        private final FieldType type;
        private final Rule rule;
        private final Function<T, ?> getter;
        @Nullable
        private final CanonicalSchema<?> schema;
        @Nullable
        private final Builder<?> schemaBuilder;
        @Nullable
        private final MapEncoding<?, ?, ?, ?> mapEncoding;

        private FieldDefinition(int number,
                                String name,
                                FieldType type,
                                Rule rule,
                                Function<T, ?> getter,
                                @Nullable
                                CanonicalSchema<?> schema,
                                @Nullable Builder<?> schemaBuilder,
                                @Nullable MapEncoding<?, ?, ?, ?> mapEncoding) {
            this.number = number;
            this.name = name;
            this.type = type;
            this.rule = rule;
            this.getter = getter;
            this.schema = schema;
            this.schemaBuilder = schemaBuilder;
            this.mapEncoding = mapEncoding;
        }

        private Field<T> build() {
            CanonicalSchema<?> builtSchema = hasNestedSchema(type)
                    ? schema != null ? schema : schemaBuilder.build()
                    : null;
            return new Field<>(number,
                    name,
                    type,
                    rule,
                    getter,
                    builtSchema,
                    mapEncoding);
        }
    }

    public static final class MapEncoding<SK, SV, K, V> {
        @Getter
        private final FieldType keyType;
        @Getter
        private final FieldType valueType;
        private final Function<Map.Entry<SK, SV>, K> keyMapper;
        private final Function<Map.Entry<SK, SV>, V> valueMapper;
        private final CanonicalMapEntryIterator<K, V> entryIterator;
        @Getter
        @Nullable
        private final CanonicalSchema<?> valueSchema;

        private MapEncoding(FieldType keyType,
                            FieldType valueType,
                            Function<Map.Entry<SK, SV>, K> keyMapper,
                            Function<Map.Entry<SK, SV>, V> valueMapper,
                            @Nullable CanonicalSchema<?> valueSchema,
                            CanonicalMapEntryIterator<K, V> entryIterator) {
            this.keyType = Objects.requireNonNull(keyType);
            this.valueType = Objects.requireNonNull(valueType);
            this.keyMapper = Objects.requireNonNull(keyMapper);
            this.valueMapper = Objects.requireNonNull(valueMapper);
            this.valueSchema = valueSchema;
            this.entryIterator = Objects.requireNonNull(entryIterator);

            if (!isSupportedMapKeyType(keyType)) {
                throw new IllegalArgumentException("Unsupported canonical map key type " + keyType);
            }
            if (!isSupportedMapValueType(valueType)) {
                throw new IllegalArgumentException("Unsupported canonical map value type " + valueType);
            }
            if (hasNestedSchema(valueType) && valueSchema == null) {
                throw new IllegalArgumentException("Canonical map message values must declare a nested schema");
            }
            if (!hasNestedSchema(valueType) && valueSchema != null) {
                throw new IllegalArgumentException("Only canonical map message values can declare a nested schema");
            }
        }

        List<Map.Entry<K, V>> getEntries(Map<SK, SV> source) {
            List<Map.Entry<K, V>> entries = new ArrayList<>(source.size());
            source.entrySet().forEach(entry -> entries.add(new AbstractMap.SimpleImmutableEntry<>(
                    keyMapper.apply(entry),
                    valueMapper.apply(entry))));
            return entries;
        }

        Iterator<Map.Entry<K, V>> iterate(List<Map.Entry<K, V>> entries) {
            return entryIterator.iterate(entries);
        }
    }

    public static final class Builder<T> {
        private final String messageName;
        private final List<FieldDefinition<T>> fields = new ArrayList<>();
        private int version = CURRENT_VERSION;
        private int previousFieldNumber;

        private Builder(String messageName) {
            this.messageName = Objects.requireNonNull(messageName);
        }

        public Builder<T> version(int version) {
            if (version <= 0) {
                throw new IllegalArgumentException("Canonical schema version must be positive");
            }
            this.version = version;
            return this;
        }

        public Builder<T> int32(int number, String name, ToIntFunction<T> getter) {
            return add(number, name, FieldType.INT32, Rule.OMIT_DEFAULT,
                    getter::applyAsInt);
        }

        public Builder<T> int64(int number, String name, ToLongFunction<T> getter) {
            return add(number, name, FieldType.INT64, Rule.OMIT_DEFAULT,
                    getter::applyAsLong);
        }

        public Builder<T> enumField(int number, String name, Function<T, ? extends CanonicalEnum> getter) {
            return add(number, name, FieldType.ENUM, Rule.OMIT_DEFAULT, getter);
        }

        public Builder<T> string(int number, String name, Function<T, String> getter) {
            return add(number, name, FieldType.STRING, Rule.OMIT_EMPTY, getter);
        }

        public Builder<T> repeatedString(int number, String name, Function<T, List<String>> getter) {
            return add(number, name, FieldType.REPEATED_STRING, Rule.LIST_ORDER, getter);
        }

        public Builder<T> bytes(int number, String name, Function<T, byte[]> getter) {
            return add(number, name, FieldType.BYTES, Rule.OMIT_EMPTY, getter);
        }

        public <N> Builder<T> compose(int number,
                                      String name,
                                      Function<T, N> getter,
                                      CanonicalSchema<N> schema) {
            return add(number, name, FieldType.COMPOSE, Rule.OMIT_NULL, getter, schema);
        }

        public <N> Builder<T> compose(int number,
                                      String name,
                                      Function<T, N> getter,
                                      Builder<N> schemaBuilder) {
            return add(number, name, FieldType.COMPOSE, Rule.OMIT_NULL, getter, schemaBuilder);
        }

        public <N> Builder<T> extend(int number,
                                     String name,
                                     Function<T, N> getter,
                                     CanonicalSchema<N> schema) {
            return add(number, name, FieldType.EXTEND, Rule.OMIT_NULL, getter, schema);
        }

        public <N> Builder<T> extend(int number,
                                     String name,
                                     Function<T, N> getter,
                                     Builder<N> schemaBuilder) {
            return add(number, name, FieldType.EXTEND, Rule.OMIT_NULL, getter, schemaBuilder);
        }

        public <V> Builder<T> mapStringToCompose(int number,
                                                 String name,
                                                 Function<T, ? extends Map<String, V>> getter,
                                                 CanonicalSchema<V> valueSchema,
                                                 CanonicalMapEntryIterator<String, V> entryIterator) {
            Function<Map.Entry<String, V>, String> keyMapper = Map.Entry::getKey;
            Function<Map.Entry<String, V>, V> valueMapper = Map.Entry::getValue;
            return mapStringToCompose(number,
                    name,
                    getter,
                    keyMapper,
                    valueMapper,
                    valueSchema,
                    entryIterator);
        }

        public <SK, SV, V> Builder<T> mapStringToCompose(int number,
                                                         String name,
                                                         Function<T, ? extends Map<SK, SV>> getter,
                                                         Function<Map.Entry<SK, SV>, String> keyMapper,
                                                         Function<Map.Entry<SK, SV>, V> valueMapper,
                                                         CanonicalSchema<V> valueSchema,
                                                         CanonicalMapEntryIterator<String, V> entryIterator) {
            return add(number,
                    name,
                    FieldType.MAP,
                    Rule.MAP_ORDER,
                    getter,
                    new MapEncoding<>(FieldType.STRING,
                            FieldType.COMPOSE,
                            keyMapper,
                            valueMapper,
                            valueSchema,
                            entryIterator));
        }

        public CanonicalSchema<T> build() {
            List<Field<T>> builtFields = new ArrayList<>(fields.size());
            fields.forEach(field -> builtFields.add(field.build()));
            return new CanonicalSchema<>(messageName, version, builtFields);
        }

        private Builder<T> add(int number,
                               String name,
                               FieldType type,
                               Rule rule,
                               Function<T, ?> getter) {
            return add(number, name, type, rule, getter, null, null);
        }

        private Builder<T> add(int number,
                               String name,
                               FieldType type,
                               Rule rule,
                               Function<T, ?> getter,
                               @Nullable
                               CanonicalSchema<?> schema) {
            return add(number, name, type, rule, getter, schema, null);
        }

        private Builder<T> add(int number,
                               String name,
                               FieldType type,
                               Rule rule,
                               Function<T, ?> getter,
                               @Nullable
                               Builder<?> schemaBuilder) {
            return add(number, name, type, rule, getter, null, schemaBuilder);
        }

        private Builder<T> add(int number,
                               String name,
                               FieldType type,
                               Rule rule,
                               Function<T, ?> getter,
                               @Nullable
                               CanonicalSchema<?> schema,
                               @Nullable
                               Builder<?> schemaBuilder) {
            return add(number, name, type, rule, getter, schema, schemaBuilder, null);
        }

        private Builder<T> add(int number,
                               String name,
                               FieldType type,
                               Rule rule,
                               Function<T, ?> getter,
                               MapEncoding<?, ?, ?, ?> mapEncoding) {
            return add(number, name, type, rule, getter, null, null, mapEncoding);
        }

        private Builder<T> add(int number,
                               String name,
                               FieldType type,
                               Rule rule,
                               Function<T, ?> getter,
                               @Nullable
                               CanonicalSchema<?> schema,
                               @Nullable
                               Builder<?> schemaBuilder,
                               @Nullable
                               MapEncoding<?, ?, ?, ?> mapEncoding) {
            validateFieldNumber(number);
            if (number <= previousFieldNumber) {
                throw new IllegalArgumentException("Canonical fields must be declared in ascending field-number order");
            }
            if (type == FieldType.MAP) {
                if (mapEncoding == null) {
                    throw new IllegalArgumentException("Map fields must declare a map encoding");
                }
                if (schema != null || schemaBuilder != null) {
                    throw new IllegalArgumentException("Map fields cannot declare a compose or extend nested schema");
                }
            } else if (mapEncoding != null) {
                throw new IllegalArgumentException("Only map fields can declare a map encoding");
            } else if (hasNestedSchema(type)) {
                if ((schema == null && schemaBuilder == null) || (schema != null && schemaBuilder != null)) {
                    throw new IllegalArgumentException("Compose and extend fields must declare exactly one nested schema or schema builder");
                }
            } else if (schema != null || schemaBuilder != null) {
                throw new IllegalArgumentException("Only compose and extend fields can declare a nested schema or schema builder");
            }

            fields.add(new FieldDefinition<>(number,
                    Objects.requireNonNull(name),
                    Objects.requireNonNull(type),
                    Objects.requireNonNull(rule),
                    Objects.requireNonNull(getter),
                    schema,
                    schemaBuilder,
                    mapEncoding));
            previousFieldNumber = number;
            return this;
        }

        private static void validateFieldNumber(int number) {
            if (number < MIN_FIELD_NUMBER || number > MAX_FIELD_NUMBER) {
                throw new IllegalArgumentException("Canonical field number must be in protobuf range 1..536870911");
            }
            if (number >= FIRST_RESERVED_FIELD_NUMBER && number <= LAST_RESERVED_FIELD_NUMBER) {
                throw new IllegalArgumentException("Canonical field number must not use protobuf reserved range 19000..19999");
            }
        }
    }

    private static boolean hasNestedSchema(FieldType type) {
        return type == FieldType.COMPOSE || type == FieldType.EXTEND;
    }

    private static boolean isSupportedMapKeyType(FieldType type) {
        return type == FieldType.INT32 ||
                type == FieldType.INT64 ||
                type == FieldType.ENUM ||
                type == FieldType.STRING;
    }

    private static boolean isSupportedMapValueType(FieldType type) {
        return type == FieldType.INT32 ||
                type == FieldType.INT64 ||
                type == FieldType.ENUM ||
                type == FieldType.STRING ||
                type == FieldType.BYTES ||
                type == FieldType.COMPOSE ||
                type == FieldType.EXTEND;
    }
}
