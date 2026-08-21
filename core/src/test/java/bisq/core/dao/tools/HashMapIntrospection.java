/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.tools;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class HashMapIntrospection {
    static final int DEFAULT_INITIAL_CAPACITY = 16;
    static final float DEFAULT_LOAD_FACTOR = 0.75f;
    static final int DEFAULT_RESIZE_THRESHOLD = 12;
    static final int FIRST_DEFAULT_RESIZE_INSERTION = 13;
    static final int TREEIFY_THRESHOLD = 8;
    static final int UNTREEIFY_THRESHOLD = 6;
    static final int MIN_TREEIFY_CAPACITY = 64;

    private HashMapIntrospection() {
    }

    static Stats inspect(HashMap<?, ?> map) {
        try {
            Object[] table = getTable(map);
            Stats stats = new Stats();
            stats.size = map.size();
            stats.tableLength = table == null ? 0 : table.length;
            stats.threshold = getIntField(HashMap.class, map, "threshold");
            stats.loadFactor = getFloatField(HashMap.class, map, "loadFactor");

            if (table != null) {
                for (Object bin : table) {
                    if (bin == null) {
                        continue;
                    }

                    stats.nonEmptyBins++;
                    if (isTreeNode(bin)) {
                        stats.treeBins++;
                    }

                    int binLength = 0;
                    Object node = bin;
                    while (node != null) {
                        binLength++;
                        Field nextField = findField(node.getClass(), "next");
                        node = nextField.get(node);
                    }
                    stats.maxBinLength = Math.max(stats.maxBinLength, binLength);
                }
            }
            return stats;
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Cannot inspect java.util.HashMap internals. Run the tool with " +
                    "'--add-opens java.base/java.util=ALL-UNNAMED'.", e);
        }
    }

    static String keyIterationFingerprint(Map<String, ?> map) {
        MessageDigest digest = sha256();
        for (String key : map.keySet()) {
            byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
            digest.update(bytes);
        }
        return toHex(digest.digest());
    }

    static String keySample(Map<String, ?> map, int sampleKeys) {
        List<String> keys = new ArrayList<>();
        int count = 0;
        for (String key : map.keySet()) {
            keys.add(key);
            count++;
            if (count >= sampleKeys) {
                break;
            }
        }
        return keys.toString();
    }

    static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >>> 4) & 0x0f, 16));
            sb.append(Character.forDigit(b & 0x0f, 16));
        }
        return sb.toString();
    }

    static byte[] fromHex(String hex) {
        if ((hex.length() & 1) != 0) {
            throw new IllegalArgumentException("Hex string must have an even number of characters");
        }

        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int high = Character.digit(hex.charAt(i * 2), 16);
            int low = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Invalid hex character in: " + hex);
            }
            bytes[i] = (byte) ((high << 4) + low);
        }
        return bytes;
    }

    static byte[] sha256(byte[] bytes) {
        MessageDigest digest = sha256();
        return digest.digest(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object[] getTable(HashMap<?, ?> map) throws ReflectiveOperationException {
        Field tableField = findField(HashMap.class, "table");
        return (Object[]) tableField.get(map);
    }

    private static int getIntField(Class<?> type, Object instance, String name) throws ReflectiveOperationException {
        Field field = findField(type, name);
        return field.getInt(instance);
    }

    private static float getFloatField(Class<?> type, Object instance, String name) throws ReflectiveOperationException {
        Field field = findField(type, name);
        return field.getFloat(instance);
    }

    private static Field findField(Class<?> type, String name) throws ReflectiveOperationException {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static boolean isTreeNode(Object node) {
        return node.getClass().getName().equals("java.util.HashMap$TreeNode");
    }

    static final class Stats {
        int size;
        int tableLength;
        int threshold;
        float loadFactor;
        int nonEmptyBins;
        int maxBinLength;
        int treeBins;
    }
}
