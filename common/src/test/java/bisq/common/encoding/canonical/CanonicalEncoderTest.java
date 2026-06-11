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
 * You should have received a copy of the GNU Affero General Public
 * License along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.common.encoding.canonical;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CanonicalEncoderTest {
    private static final List<String> KEYS = List.of(
            "k00", "k01", "k02", "k03", "k04", "k05", "k06", "k07", "k08", "k09",
            "k10", "k11", "k12", "k13", "k14", "k15", "k16", "k17", "k18", "k19",
            "k20", "k21", "k22", "k23", "k24", "k25", "k26", "k27", "k28", "k29",
            "k30", "k31", "k32", "k33", "k34", "k35", "k36", "k37", "k38", "k39");
    private static final List<String> JAVA_11_HASH_MAP_ORDER = List.of(
            "k31", "k30", "k11", "k33", "k10", "k32", "k13", "k35", "k12", "k34",
            "k15", "k37", "k14", "k36", "k17", "k39", "k16", "k38", "k19", "k18",
            "k20", "k00", "k22", "k21", "k02", "k24", "k01", "k23", "k04", "k26",
            "k03", "k25", "k06", "k28", "k05", "k27", "k08", "k07", "k29", "k09");

    private static final CanonicalSchema<Value> VALUE_SCHEMA = CanonicalSchema.<Value>newBuilder()
            .int32(1, Value::getValue)
            .build();

    @Test
    public void supportsDoubleAndPackedRepeatedInt32() {
        NumberContainer container = new NumberContainer(1.5, List.of(0, 1, 128));

        CanonicalSchema<NumberContainer> schema = CanonicalSchema.<NumberContainer>newBuilder()
                .doubleField(1, NumberContainer::getDifficulty)
                .packedRepeatedInt32(2, NumberContainer::getVersions)
                .build();

        assertArrayEquals(bytes(
                        0x09, 0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0xf8, 0x3f,
                        0x12, 0x04,
                        0x00,
                        0x01,
                        0x80, 0x01),
                CanonicalEncoder.DEFAULT.encode(container, schema));
    }

    @Test
    public void writeRawBytesRejectsNull() {
        CanonicalWriter writer = new CanonicalWriter();

        assertThrows(NullPointerException.class, () -> writer.writeRawBytes(null));
    }

    @Test
    public void rejectsNullTreeMapComparator() {
        assertThrows(IllegalArgumentException.class, () -> TreeMapIterator.comparing(null));
    }

    @Test
    public void supportsUInt32BoolRepeatedComposeAndMapStringToString() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("b", "second");
        tags.put("a", "first");
        ComplexContainer container = new ComplexContainer(300, true, List.of(new Value(1), new Value(2)), tags);

        CanonicalSchema<ComplexContainer> schema = CanonicalSchema.<ComplexContainer>newBuilder()
                .uint32(1, ComplexContainer::getVersion)
                .bool(2, ComplexContainer::isAccepted)
                .repeatedCompose(3, ComplexContainer::getValues, VALUE_SCHEMA)
                .mapStringToString(4,
                        ComplexContainer::getTags,
                        TreeMapIterator.naturalOrder())
                .build();

        assertArrayEquals(bytes(
                        0x08, 0xac, 0x02,
                        0x10, 0x01,
                        0x1a, 0x02, 0x08, 0x01,
                        0x1a, 0x02, 0x08, 0x02,
                        0x22, 0x0a, 0x0a, 0x01, 0x61, 0x12, 0x05, 0x66, 0x69, 0x72, 0x73, 0x74,
                        0x22, 0x0b, 0x0a, 0x01, 0x62, 0x12, 0x06, 0x73, 0x65, 0x63, 0x6f, 0x6e, 0x64),
                CanonicalEncoder.DEFAULT.encode(container, schema));
    }

    @Test
    public void supportsOneofNestedMessage() {
        CanonicalSchema<OneofContainer> schema = CanonicalSchema.<OneofContainer>newBuilder()
                .oneof(4, OneofContainer::getValue, VALUE_SCHEMA)
                .build();

        assertArrayEquals(bytes(
                        0x22, 0x02,
                        0x08, 0x01),
                CanonicalEncoder.DEFAULT.encode(new OneofContainer(new Value(1)), schema));
        assertArrayEquals(bytes(),
                CanonicalEncoder.DEFAULT.encode(new OneofContainer(null), schema));
    }

    @Test
    public void oneofWrapperMessageNameDoesNotChangeEncodedBytes() {
        CanonicalSchema<Value> legacyShape = CanonicalSchema.<Value>newBuilder()
                .oneof(22, value -> value, VALUE_SCHEMA)
                .build();
        CanonicalSchema<Value> wrapperShape = CanonicalSchema.oneof("PersistableEnvelope",
                22,
                CanonicalSchema.<Value>newBuilder()
                        .int32(1, Value::getValue));
        Value value = new Value(1);

        assertArrayEquals(CanonicalEncoder.DEFAULT.encode(value, legacyShape),
                CanonicalEncoder.DEFAULT.encode(value, wrapperShape));
    }

    @Test
    public void mapStringToComposeUsesTreeMapOrder() {
        Map<String, Value> values = new LinkedHashMap<>();
        values.put("b", new Value(2));
        values.put("a", new Value(1));

        CanonicalSchema<Container> schema = CanonicalSchema.<Container>newBuilder()
                .mapStringToCompose(1,
                        Container::getValues,
                        VALUE_SCHEMA,
                        TreeMapIterator.naturalOrder())
                .build();

        assertArrayEquals(bytes(
                        0x0a, 0x07, 0x0a, 0x01, 0x61, 0x12, 0x02, 0x08, 0x01,
                        0x0a, 0x07, 0x0a, 0x01, 0x62, 0x12, 0x02, 0x08, 0x02),
                CanonicalEncoder.DEFAULT.encode(new Container(values), schema));
    }

    @Test
    public void mapStringToComposeMapsSourceEntriesBeforeOrdering() {
        Map<SourceKey, Value> values = new LinkedHashMap<>();
        values.put(new SourceKey("source-b", "b"), new Value(2));
        values.put(new SourceKey("source-a", "a"), new Value(1));

        CanonicalSchema<MappedContainer> schema = CanonicalSchema.<MappedContainer>newBuilder()
                .mapStringToCompose(1,
                        MappedContainer::getValues,
                        entry -> entry.getKey().getMapKey(),
                        Map.Entry::getValue,
                        VALUE_SCHEMA,
                        TreeMapIterator.naturalOrder())
                .build();

        assertArrayEquals(bytes(
                        0x0a, 0x07, 0x0a, 0x01, 0x61, 0x12, 0x02, 0x08, 0x01,
                        0x0a, 0x07, 0x0a, 0x01, 0x62, 0x12, 0x02, 0x08, 0x02),
                CanonicalEncoder.DEFAULT.encode(new MappedContainer(values), schema));
    }

    @Test
    public void mapStringToComposeUsesLegacyHashMapOrder() {
        Map<String, Value> values = new LinkedHashMap<>();
        KEYS.forEach(key -> values.put(key, new Value(1)));

        CanonicalSchema<Container> schema = CanonicalSchema.<Container>newBuilder()
                .mapStringToCompose(1,
                        Container::getValues,
                        VALUE_SCHEMA,
                        new LegacyCollectorsToMapIterator<>())
                .build();

        assertEquals(JAVA_11_HASH_MAP_ORDER,
                readMapKeys(CanonicalEncoder.DEFAULT.encode(new Container(values), schema)));
    }

    @Test
    public void mapStringToComposeUsesMapEntryCacheWhenSourceOptsIn() {
        CountingCacheMap values = new CountingCacheMap();
        values.put("b", new Value(2));
        values.put("a", new Value(1));

        CanonicalSchema<Container> schema = CanonicalSchema.<Container>newBuilder()
                .mapStringToCompose(1,
                        Container::getValues,
                        VALUE_SCHEMA,
                        TreeMapIterator.naturalOrder())
                .build();

        byte[] first = CanonicalEncoder.DEFAULT.encode(new Container(values), schema);
        assertEquals(2, values.entryCacheReads);
        assertEquals(2, values.entryCacheWrites);
        assertEquals(1, values.mapCacheWrites);

        assertArrayEquals(first, CanonicalEncoder.DEFAULT.encode(new Container(values), schema));
        assertEquals(1, values.mapCacheHits);
        assertEquals(2, values.entryCacheReads);
        assertEquals(2, values.entryCacheWrites);
        assertEquals(1, values.mapCacheWrites);

        values.put("b", new Value(3));

        CanonicalEncoder.DEFAULT.encode(new Container(values), schema);
        assertEquals(4, values.entryCacheReads);
        assertEquals(3, values.entryCacheWrites);
        assertEquals(2, values.mapCacheWrites);
    }

    @Test
    public void mapStringToComposeRejectsDuplicateMappedKeys() {
        Map<SourceKey, Value> values = new LinkedHashMap<>();
        values.put(new SourceKey("source-a", "same"), new Value(1));
        values.put(new SourceKey("source-b", "same"), new Value(2));

        CanonicalSchema<MappedContainer> schema = CanonicalSchema.<MappedContainer>newBuilder()
                .mapStringToCompose(1,
                        MappedContainer::getValues,
                        entry -> entry.getKey().getMapKey(),
                        Map.Entry::getValue,
                        VALUE_SCHEMA,
                        TreeMapIterator.naturalOrder())
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> CanonicalEncoder.DEFAULT.encode(new MappedContainer(values), schema));
    }

    private static List<String> readMapKeys(byte[] bytes) {
        KeyReader reader = new KeyReader(bytes);
        return reader.readKeys();
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }

    private static final class KeyReader {
        private final byte[] bytes;
        private int position;
        private final ArrayList<String> keys = new ArrayList<>();

        private KeyReader(byte[] bytes) {
            this.bytes = bytes;
        }

        private List<String> readKeys() {
            while (position < bytes.length) {
                int tagPosition = position;
                assertEquals(0x0a, readVarint(), "map field tag at position " + tagPosition + " in " + hexAround(tagPosition));
                int entryLength = readVarint();
                int entryEnd = position + entryLength;
                assertEquals(0x0a, readVarint());
                int keyLength = readVarint();
                keys.add(new String(bytes, position, keyLength, StandardCharsets.UTF_8));
                position = entryEnd;
            }
            return keys;
        }

        private int readVarint() {
            int value = 0;
            int shift = 0;
            while (true) {
                int current = bytes[position++] & 0xff;
                value |= (current & 0x7f) << shift;
                if ((current & 0x80) == 0) {
                    return value;
                }
                shift += 7;
            }
        }

        private String hexAround(int start) {
            StringBuilder stringBuilder = new StringBuilder();
            int begin = Math.max(0, start - 12);
            int end = Math.min(bytes.length, start + 16);
            for (int i = begin; i < end; i++) {
                if (i > begin) {
                    stringBuilder.append(' ');
                }
                stringBuilder.append(String.format("%02x", bytes[i] & 0xff));
            }
            return stringBuilder.toString();
        }
    }

    private static final class Container {
        private final Map<String, Value> values;

        private Container(Map<String, Value> values) {
            this.values = values;
        }

        private Map<String, Value> getValues() {
            return values;
        }
    }

    private static final class MappedContainer {
        private final Map<SourceKey, Value> values;

        private MappedContainer(Map<SourceKey, Value> values) {
            this.values = values;
        }

        private Map<SourceKey, Value> getValues() {
            return values;
        }
    }

    private static final class ComplexContainer {
        private final int version;
        private final boolean accepted;
        private final List<Value> values;
        private final Map<String, String> tags;

        private ComplexContainer(int version, boolean accepted, List<Value> values, Map<String, String> tags) {
            this.version = version;
            this.accepted = accepted;
            this.values = values;
            this.tags = tags;
        }

        private int getVersion() {
            return version;
        }

        private boolean isAccepted() {
            return accepted;
        }

        private List<Value> getValues() {
            return values;
        }

        private Map<String, String> getTags() {
            return tags;
        }
    }

    private static final class OneofContainer {
        private final Value value;

        private OneofContainer(Value value) {
            this.value = value;
        }

        private Value getValue() {
            return value;
        }
    }

    private static final class SourceKey {
        private final String sourceKey;
        private final String mapKey;

        private SourceKey(String sourceKey, String mapKey) {
            this.sourceKey = sourceKey;
            this.mapKey = mapKey;
        }

        private String getMapKey() {
            return mapKey;
        }

        @Override
        public int hashCode() {
            return sourceKey.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SourceKey that)) {
                return false;
            }
            return sourceKey.equals(that.sourceKey);
        }
    }

    private static final class NumberContainer {
        private final double difficulty;
        private final List<Integer> versions;

        private NumberContainer(double difficulty, List<Integer> versions) {
            this.difficulty = difficulty;
            this.versions = versions;
        }

        private double getDifficulty() {
            return difficulty;
        }

        private List<Integer> getVersions() {
            return versions;
        }
    }

    private static final class CountingCacheMap extends LinkedHashMap<String, Value>
            implements CanonicalMapEntryByteCache<String, Value> {
        private final Map<String, CachedMapEntryBytes> encodedMapEntryBytesByKey = new HashMap<>();
        private final Map<Object, byte[]> encodedMapBytesByCacheKey = new HashMap<>();
        private int mapCacheHits;
        private int mapCacheWrites;
        private int entryCacheReads;
        private int entryCacheWrites;

        @Override
        public Value put(String key, Value value) {
            encodedMapBytesByCacheKey.clear();
            return super.put(key, value);
        }

        @Override
        public byte[] getEncodedMap(Object cacheKey) {
            byte[] encodedMap = encodedMapBytesByCacheKey.get(cacheKey);
            if (encodedMap != null) {
                mapCacheHits++;
            }
            return encodedMap;
        }

        @Override
        public void putEncodedMap(Object cacheKey, byte[] encodedMap) {
            encodedMapBytesByCacheKey.put(cacheKey, encodedMap);
            mapCacheWrites++;
        }

        @Override
        public byte[] getEncodedMapEntry(String canonicalKey, Value canonicalValue) {
            entryCacheReads++;
            CachedMapEntryBytes cached = encodedMapEntryBytesByKey.get(canonicalKey);
            return cached != null && cached.value == canonicalValue ? cached.encodedMapEntry : null;
        }

        @Override
        public void putEncodedMapEntry(String canonicalKey, Value canonicalValue, byte[] encodedMapEntry) {
            encodedMapEntryBytesByKey.put(canonicalKey, new CachedMapEntryBytes(canonicalValue, encodedMapEntry));
            entryCacheWrites++;
        }
    }

    private static final class CachedMapEntryBytes {
        private final Value value;
        private final byte[] encodedMapEntry;

        private CachedMapEntryBytes(Value value, byte[] encodedMapEntry) {
            this.value = value;
            this.encodedMapEntry = encodedMapEntry;
        }
    }

    private static final class Value {
        private final int value;

        private Value(int value) {
            this.value = value;
        }

        private int getValue() {
            return value;
        }
    }
}
