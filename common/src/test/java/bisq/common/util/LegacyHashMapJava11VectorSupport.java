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

package bisq.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class LegacyHashMapJava11VectorSupport {
    enum KeySetId {
        EMPTY,
        NULL_AND_EDGE_STRINGS,
        JDK19_COPY_DIVERGENCE_12,
        K40,
        TX_OUTPUT_K40,
        COLLIDING_STRINGS_8,
        COLLIDING_STRINGS_16,
        COLLIDING_STRINGS_64,
        COLLIDING_STRINGS_WITH_SPLITS_128,
        RANDOM_TX_OUTPUT_2K,
        RANDOM_TX_OUTPUT_50K,
        DUPLICATE_K40
    }

    enum BuildMode {
        DIRECT_PUT,
        COPY_FROM_LINKED_HASH_MAP,
        COPY_FROM_TREE_MAP,
        COLLECTORS_TO_MAP_MERGE,
        PUT_UPDATE_REMOVE_REINSERT,
        TREE_BIN_REMOVE_REINSERT
    }

    static final class Vector {
        final String name;
        final KeySetId keySetId;
        final BuildMode buildMode;
        final int expectedSize;
        final String expectedKeyOrderSha256;

        Vector(String name,
               KeySetId keySetId,
               BuildMode buildMode,
               int expectedSize,
               String expectedKeyOrderSha256) {
            this.name = name;
            this.keySetId = keySetId;
            this.buildMode = buildMode;
            this.expectedSize = expectedSize;
            this.expectedKeyOrderSha256 = expectedKeyOrderSha256;
        }
    }

    private LegacyHashMapJava11VectorSupport() {
    }

    static List<Vector> vectorDefinitionsWithoutExpectedHashes() {
        return Arrays.asList(
                vector("empty_direct_put", KeySetId.EMPTY, BuildMode.DIRECT_PUT),
                vector("null_and_edge_strings_direct_put", KeySetId.NULL_AND_EDGE_STRINGS, BuildMode.DIRECT_PUT),
                vector("small_copy_constructor_jdk19_divergence", KeySetId.JDK19_COPY_DIVERGENCE_12,
                        BuildMode.COPY_FROM_LINKED_HASH_MAP),
                vector("sorted_k40_direct_put", KeySetId.K40, BuildMode.DIRECT_PUT),
                vector("sorted_k40_copy_from_tree_map", KeySetId.K40, BuildMode.COPY_FROM_TREE_MAP),
                vector("tx_output_k40_collectors_merge", KeySetId.TX_OUTPUT_K40, BuildMode.COLLECTORS_TO_MAP_MERGE),
                vector("colliding_strings_8_list_bin", KeySetId.COLLIDING_STRINGS_8, BuildMode.DIRECT_PUT),
                vector("colliding_strings_16_tree_bin", KeySetId.COLLIDING_STRINGS_16, BuildMode.DIRECT_PUT),
                vector("colliding_strings_64_tree_bin", KeySetId.COLLIDING_STRINGS_64, BuildMode.DIRECT_PUT),
                vector("colliding_strings_128_tree_bin_splits", KeySetId.COLLIDING_STRINGS_WITH_SPLITS_128,
                        BuildMode.DIRECT_PUT),
                vector("random_tx_output_2k_direct_put", KeySetId.RANDOM_TX_OUTPUT_2K, BuildMode.DIRECT_PUT),
                vector("random_tx_output_50k_direct_put", KeySetId.RANDOM_TX_OUTPUT_50K, BuildMode.DIRECT_PUT),
                vector("duplicate_k40_collectors_merge_keep_first", KeySetId.DUPLICATE_K40,
                        BuildMode.COLLECTORS_TO_MAP_MERGE),
                vector("random_tx_output_2k_mutate", KeySetId.RANDOM_TX_OUTPUT_2K,
                        BuildMode.PUT_UPDATE_REMOVE_REINSERT),
                vector("colliding_strings_64_tree_bin_mutate", KeySetId.COLLIDING_STRINGS_64,
                        BuildMode.TREE_BIN_REMOVE_REINSERT)
        );
    }

    static Vector vector(String name, KeySetId keySetId, BuildMode buildMode) {
        return new Vector(name, keySetId, buildMode, -1, "");
    }

    static Map<String, Integer> buildMap(Vector vector,
                                         Supplier<Map<String, Integer>> emptyMapFactory,
                                         Function<Map<String, Integer>, Map<String, Integer>> copyFactory) {
        List<String> keys = keys(vector.keySetId);
        switch (vector.buildMode) {
            case DIRECT_PUT:
                return directPut(keys, emptyMapFactory);
            case COPY_FROM_LINKED_HASH_MAP:
                return copyFromLinkedHashMap(keys, copyFactory);
            case COPY_FROM_TREE_MAP:
                return copyFromTreeMap(keys, copyFactory);
            case COLLECTORS_TO_MAP_MERGE:
                return collectorsToMapMerge(keys, emptyMapFactory);
            case PUT_UPDATE_REMOVE_REINSERT:
                return putUpdateRemoveReinsert(keys, emptyMapFactory);
            case TREE_BIN_REMOVE_REINSERT:
                return treeBinRemoveReinsert(keys, emptyMapFactory);
            default:
                throw new IllegalArgumentException("Unknown build mode: " + vector.buildMode);
        }
    }

    static String keyOrderDigest(Map<String, Integer> map) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String key : map.keySet()) {
                if (key == null) {
                    digest.update((byte) 0);
                } else {
                    digest.update((byte) 1);
                    digest.update(key.getBytes(StandardCharsets.UTF_8));
                }
                digest.update((byte) 0xff);
            }

            StringBuilder builder = new StringBuilder();
            for (byte b : digest.digest()) {
                builder.append(String.format(Locale.ROOT, "%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static List<String> keys(KeySetId keySetId) {
        switch (keySetId) {
            case EMPTY:
                return Collections.emptyList();
            case NULL_AND_EDGE_STRINGS:
                return nullAndEdgeStrings();
            case JDK19_COPY_DIVERGENCE_12:
                return jdk19CopyDivergenceKeys();
            case K40:
                return numberedKeys("k", 40);
            case TX_OUTPUT_K40:
                return numberedKeys("k", 40).stream()
                        .map(key -> key + ":0")
                        .collect(Collectors.toList());
            case COLLIDING_STRINGS_8:
                return sameHashStrings(3);
            case COLLIDING_STRINGS_16:
                return sameHashStrings(4);
            case COLLIDING_STRINGS_64:
                return sameHashStrings(6);
            case COLLIDING_STRINGS_WITH_SPLITS_128:
                return sameHashStrings(7);
            case RANDOM_TX_OUTPUT_2K:
                return randomTxOutputKeys(2_000);
            case RANDOM_TX_OUTPUT_50K:
                return randomTxOutputKeys(50_000);
            case DUPLICATE_K40:
                return duplicateK40Keys();
            default:
                throw new IllegalArgumentException("Unknown key set: " + keySetId);
        }
    }

    private static Map<String, Integer> directPut(List<String> keys,
                                                  Supplier<Map<String, Integer>> emptyMapFactory) {
        Map<String, Integer> map = emptyMapFactory.get();
        for (String key : keys) {
            map.put(key, map.size());
        }
        return map;
    }

    private static Map<String, Integer> copyFromLinkedHashMap(
            List<String> keys,
            Function<Map<String, Integer>, Map<String, Integer>> copyFactory) {
        LinkedHashMap<String, Integer> source = new LinkedHashMap<>();
        for (String key : keys) {
            source.put(key, source.size());
        }
        return copyFactory.apply(source);
    }

    private static Map<String, Integer> copyFromTreeMap(
            List<String> keys,
            Function<Map<String, Integer>, Map<String, Integer>> copyFactory) {
        TreeMap<String, Integer> source = new TreeMap<>();
        for (String key : keys) {
            source.put(key, source.size());
        }
        return copyFactory.apply(source);
    }

    private static Map<String, Integer> collectorsToMapMerge(
            List<String> keys,
            Supplier<Map<String, Integer>> emptyMapFactory) {
        return keys.stream().collect(Collectors.toMap(
                key -> key,
                key -> key == null ? -1 : key.length(),
                (first, second) -> first,
                emptyMapFactory));
    }

    private static Map<String, Integer> putUpdateRemoveReinsert(
            List<String> keys,
            Supplier<Map<String, Integer>> emptyMapFactory) {
        Map<String, Integer> map = directPut(keys, emptyMapFactory);
        List<String> removed = new ArrayList<>();
        for (int i = 0; i < keys.size(); i += 7) {
            String key = keys.get(i);
            removed.add(key);
            map.remove(key);
        }
        for (int i = 3; i < keys.size(); i += 11) {
            map.put(keys.get(i), 100_000 + i);
        }
        for (int i = removed.size() - 1; i >= 0; i--) {
            map.put(removed.get(i), 200_000 + i);
        }
        return map;
    }

    private static Map<String, Integer> treeBinRemoveReinsert(
            List<String> keys,
            Supplier<Map<String, Integer>> emptyMapFactory) {
        Map<String, Integer> map = directPut(keys, emptyMapFactory);
        int[] removals = {3, 5, 8, 13, 21, 34, 55};
        for (int index : removals) {
            map.remove(keys.get(index));
        }
        for (int index : new int[]{21, 8, 55}) {
            map.put(keys.get(index), 300_000 + index);
        }
        return map;
    }

    private static List<String> numberedKeys(String prefix, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> String.format(Locale.ROOT, "%s%02d", prefix, i))
                .collect(Collectors.toList());
    }

    private static List<String> nullAndEdgeStrings() {
        List<String> keys = new ArrayList<>();
        keys.add(null);
        keys.add("");
        keys.add(" ");
        keys.add("0");
        keys.add("00");
        keys.add("Aa");
        keys.add("BB");
        keys.add("key-with-dash");
        keys.add("key_with_underscore");
        keys.add("UPPER");
        keys.add("lower");
        keys.add("1234567890");
        keys.add("ffff");
        keys.add("0000");
        return keys;
    }

    private static List<String> jdk19CopyDivergenceKeys() {
        List<String> keys = new ArrayList<>(Arrays.asList(
                "key_73", "key_69", "key_151", "key_98",
                "key_187", "key_160", "key_3", "key_34"));
        for (int i = 0; keys.size() < 12; i++) {
            keys.add("filler_" + i);
        }
        return keys;
    }

    private static List<String> sameHashStrings(int parts) {
        List<String> result = new ArrayList<>();
        int count = 1 << parts;
        for (int i = 0; i < count; i++) {
            StringBuilder builder = new StringBuilder();
            for (int bit = parts - 1; bit >= 0; bit--) {
                builder.append(((i >>> bit) & 1) == 0 ? "Aa" : "BB");
            }
            result.add(builder.toString());
        }
        Collections.sort(result);
        return result;
    }

    private static List<String> randomTxOutputKeys(int count) {
        List<String> keys = new ArrayList<>(count);
        long state = 0x6a09e667f3bcc909L;
        for (int i = 0; i < count; i++) {
            StringBuilder txId = new StringBuilder(64);
            for (int part = 0; part < 4; part++) {
                state = state * 0x5851f42d4c957f2dL + 0x14057b7ef767814fL;
                txId.append(toPaddedHex(state));
            }
            keys.add(txId + ":" + (i & 3));
        }
        Collections.sort(keys);
        return keys;
    }

    private static String toPaddedHex(long value) {
        String hex = Long.toHexString(value);
        return "0000000000000000".substring(hex.length()) + hex;
    }

    private static List<String> duplicateK40Keys() {
        List<String> keys = new ArrayList<>(numberedKeys("k", 40));
        keys.addAll(numberedKeys("k", 10));
        keys.add("k03");
        keys.add("k17");
        keys.add("k39");
        return keys;
    }
}
