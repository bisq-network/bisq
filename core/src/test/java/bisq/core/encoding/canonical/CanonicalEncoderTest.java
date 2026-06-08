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

package bisq.core.encoding.canonical;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
            if (!(obj instanceof SourceKey)) {
                return false;
            }
            return sourceKey.equals(((SourceKey) obj).sourceKey);
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
