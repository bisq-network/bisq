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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LegacyCollectorsToMapIteratorTest {
    private static final List<String> RESIZE_KEYS = IntStream.range(0, 13)
            .mapToObj(i -> String.format("k%02d", i))
            .collect(Collectors.toList());
    private static final List<String> JAVA_11_RESIZED_HASH_MAP_ORDER = List.of(
            "k00", "k11", "k10", "k02", "k01", "k12", "k04",
            "k03", "k06", "k05", "k08", "k07", "k09");
    private static final List<String> JAVA_11_TREEIFIED_COLLISION_HASH_MAP_ORDER = List.of(
            "AaAaBBBB", "AaAaAaAa", "AaAaAaBB", "AaAaBBAa", "AaBBAaAa", "AaBBAaBB",
            "AaBBBBAa", "AaBBBBBB", "BBAaAaAa", "BBAaAaBB", "BBAaBBAa", "BBAaBBBB",
            "BBBBAaAa", "BBBBAaBB", "BBBBBBAa", "BBBBBBBB");

    @Test
    public void emptyInputYieldsNoEntries() {
        Iterator<Map.Entry<String, Integer>> iterator = new LegacyCollectorsToMapIterator<Integer>().iterate(List.of());

        assertFalse(iterator.hasNext());
    }

    @Test
    public void nullKeysAreRejected() {
        List<Map.Entry<String, Integer>> entries = List.of(entry(null, 1));

        assertThrows(NullPointerException.class,
                () -> new LegacyCollectorsToMapIterator<Integer>().iterate(entries));
    }

    @Test
    public void duplicateKeysAreRejected() {
        List<Map.Entry<String, Integer>> entries = List.of(
                entry("same", 1),
                entry("same", 2));

        assertThrows(IllegalArgumentException.class,
                () -> new LegacyCollectorsToMapIterator<Integer>().iterate(entries));
    }

    @Test
    public void preservesJava11OrderAcrossResizeThreshold() {
        List<Map.Entry<String, Integer>> entries = entries(RESIZE_KEYS);

        assertEquals(JAVA_11_RESIZED_HASH_MAP_ORDER, orderedKeys(entries));
        assertEquals(JAVA_11_RESIZED_HASH_MAP_ORDER, orderedKeys(entries));
    }

    @Test
    public void preservesJava11OrderForTreeifiedHashCollisions() {
        List<String> collidingKeys = getSameHashStrings(4);
        List<Map.Entry<String, Integer>> entries = entries(collidingKeys);

        assertEquals(1, collidingKeys.stream().mapToInt(String::hashCode).distinct().count());
        assertEquals(JAVA_11_TREEIFIED_COLLISION_HASH_MAP_ORDER, orderedKeys(entries));
        assertEquals(JAVA_11_TREEIFIED_COLLISION_HASH_MAP_ORDER, orderedKeys(entries));
    }

    private static List<Map.Entry<String, Integer>> entries(List<String> keys) {
        List<Map.Entry<String, Integer>> result = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            result.add(entry(keys.get(i), i));
        }
        return result;
    }

    private static Map.Entry<String, Integer> entry(String key, Integer value) {
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }

    private static List<String> orderedKeys(List<Map.Entry<String, Integer>> entries) {
        Iterator<Map.Entry<String, Integer>> iterator = new LegacyCollectorsToMapIterator<Integer>().iterate(entries);
        List<String> result = new ArrayList<>();
        while (iterator.hasNext()) {
            result.add(iterator.next().getKey());
        }
        return result;
    }

    private static List<String> getSameHashStrings(int parts) {
        List<String> result = new ArrayList<>();
        int count = 1 << parts;
        for (int i = 0; i < count; i++) {
            StringBuilder stringBuilder = new StringBuilder();
            for (int bit = parts - 1; bit >= 0; bit--) {
                stringBuilder.append(((i >>> bit) & 1) == 0 ? "Aa" : "BB");
            }
            result.add(stringBuilder.toString());
        }
        result.sort(String::compareTo);
        return result;
    }
}
