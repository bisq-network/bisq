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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class LegacyHashMapMassiveRandomizedEntryOrderSupport {
    static final int ENTRY_COUNT = 50_000;
    static final long RANDOM_SEED = 0x4d595df4d0f33173L;

    private static final long MULTIPLIER = 0x5851f42d4c957f2dL;
    private static final long INCREMENT = 0x14057b7ef767814fL;

    private LegacyHashMapMassiveRandomizedEntryOrderSupport() {
    }

    static void fillMassiveRandomizedMap(Map<String, Integer> map) {
        List<String> keys = massiveRandomizedKeys();
        for (int i = 0; i < keys.size(); i++) {
            map.put(keys.get(i), i);
        }
    }

    static int[] entryValueOrder(Map<String, Integer> map) {
        int[] order = new int[map.size()];
        int index = 0;
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            order[index++] = entry.getValue();
        }
        return order;
    }

    static String entryValueOrderOutput(int[] order) {
        StringBuilder builder = new StringBuilder(order.length * 6);
        for (int i = 0; i < order.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(order[i]);
        }
        return builder.toString();
    }

    static String entryOrderOutput(Map<String, Integer> map) {
        StringBuilder builder = new StringBuilder(map.size() * 86);
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            builder.append(entry.getKey())
                    .append('=')
                    .append(entry.getValue())
                    .append('\n');
        }
        return builder.toString();
    }

    private static List<String> massiveRandomizedKeys() {
        List<String> keys = new ArrayList<>(ENTRY_COUNT);
        long state = RANDOM_SEED;
        for (int i = 0; i < ENTRY_COUNT; i++) {
            StringBuilder builder = new StringBuilder(80);
            for (int part = 0; part < 4; part++) {
                state = nextState(state);
                builder.append(toPaddedHex(state));
            }
            state = nextState(state);
            builder.append(':')
                    .append(Integer.toString((int) (state & 0x7), 10))
                    .append(':')
                    .append(String.format(Locale.ROOT, "%05d", i));
            keys.add(builder.toString());
        }
        return keys;
    }

    private static long nextState(long state) {
        return state * MULTIPLIER + INCREMENT;
    }

    private static String toPaddedHex(long value) {
        String hex = Long.toHexString(value);
        return "0000000000000000".substring(hex.length()) + hex;
    }
}
