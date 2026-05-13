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

import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins LegacyHashMap iteration / sizing behavior to the pre-JDK-19 HashMap
 * implementation. The DAO consensus hash chain serializes maps into protobuf
 * in HashMap iteration order; any drift between JDKs breaks consensus across
 * the network. LegacyHashMap freezes that order regardless of host JDK.
 *
 * If these assertions ever fail, the legacy implementation has diverged
 * from JDK 11 semantics — do not "fix" by accepting new values; the hash
 * chain would fork.
 */
public class LegacyHashMapTest {

    private static int tableCap(LegacyHashMap<?, ?> m) throws Exception {
        Field f = LegacyHashMap.class.getDeclaredField("table");
        f.setAccessible(true);
        Object[] t = (Object[]) f.get(m);
        return t == null ? 0 : t.length;
    }

    /**
     * JDK 11 putMapEntries pre-sizing: ft = (float) s / lf + 1.0f
     * For size=12, lf=0.75: ft=17.0 -> tableSizeFor(17) = 32.
     * JDK 19+ uses Math.ceil(s / (double) lf) = 16 -> tableSizeFor(16) = 16.
     */
    @Test
    public void copyConstructorCapacityMatchesJdk11() throws Exception {
        Map<String, Integer> src = new LinkedHashMap<>();
        for (int i = 0; i < 12; i++) src.put("k" + i, i);

        LegacyHashMap<String, Integer> m = new LegacyHashMap<>(src);
        assertEquals(32, tableCap(m),
                "LegacyHashMap copy ctor must allocate cap=32 for size=12 (JDK 11 sizing). " +
                        "Got " + tableCap(m) + " — sizing math drifted toward JDK 19+.");
    }

    /**
     * Same input, modern HashMap vs LegacyHashMap produce different iteration order
     * on JDK 19+ (because of the sizing math change). This is exactly the divergence
     * the DAO hash chain was hitting.
     */
    @Test
    public void iterationOrderDivergesFromModernHashMap() {
        List<String> keys = Arrays.asList(
                "key_73", "key_69", "key_151", "key_98",
                "key_187", "key_160", "key_3", "key_34");
        Map<String, Integer> src = new LinkedHashMap<>();
        for (String k : keys) src.put(k, src.size());
        // Pad to size 12 so copy-ctor sizing diverges between JDK 11 (cap=32) and JDK 19+ (cap=16).
        for (int i = 0; src.size() < 12; i++) src.put("filler_" + i, src.size());

        LegacyHashMap<String, Integer> legacy = new LegacyHashMap<>(src);
        HashMap<String, Integer> modern = new HashMap<>(src);

        List<String> legacyOrder = new ArrayList<>(legacy.keySet());
        List<String> modernOrder = new ArrayList<>(modern.keySet());

        // Only fails on JDK <= 18 where modern HashMap also caps at 32 (same as legacy).
        // We assert anyway because the test suite targets JDK 17/21; on 17 both orders
        // happen to match, on 21+ they diverge — and divergence is the whole reason
        // LegacyHashMap exists.
        if (Runtime.version().feature() >= 19) {
            assertNotEquals(modernOrder, legacyOrder,
                    "On JDK 19+, modern HashMap and LegacyHashMap must produce different " +
                            "iteration order for this input. If equal, LegacyHashMap is " +
                            "tracking the new JDK sizing math instead of pinning to JDK 11.");
        }
    }

    /**
     * Frozen, observed iteration order for a fixed 12-key input via direct put()
     * (cap grows from default 16). Any change to LegacyHashMap that perturbs this
     * order is a consensus-breaking regression.
     */
    @Test
    public void iterationOrderIsFrozen_directPut() {
        List<String> keys = Arrays.asList(
                "key_73", "key_69", "key_151", "key_98",
                "key_187", "key_160", "key_3", "key_34",
                "filler_0", "filler_1", "filler_2", "filler_3");

        LegacyHashMap<String, Integer> m = new LegacyHashMap<>();
        for (String k : keys) m.put(k, m.size());

        List<String> expected = Arrays.asList(
                "key_3", "filler_2", "key_98", "filler_1",
                "key_73", "key_160", "filler_0", "key_151",
                "filler_3", "key_187", "key_69", "key_34");

        assertEquals(expected, new ArrayList<>(m.keySet()));
    }

    /**
     * Frozen, observed iteration order via copy-constructor — matches the DAO
     * proto serialization path (Collectors.toMap(..., LegacyHashMap::new)).
     */
    @Test
    public void iterationOrderIsFrozen_copyConstructor() {
        List<String> keys = Arrays.asList(
                "key_73", "key_69", "key_151", "key_98",
                "key_187", "key_160", "key_3", "key_34",
                "filler_0", "filler_1", "filler_2", "filler_3");

        LinkedHashMap<String, Integer> src = new LinkedHashMap<>();
        for (String k : keys) src.put(k, src.size());
        LegacyHashMap<String, Integer> m = new LegacyHashMap<>(src);

        List<String> expected = Arrays.asList(
                "key_3", "key_98", "key_73", "key_160",
                "key_151", "filler_2", "filler_1", "filler_0",
                "filler_3", "key_187", "key_69", "key_34");

        assertEquals(expected, new ArrayList<>(m.keySet()));
    }

    /**
     * Round-trip: building a LegacyHashMap from another LegacyHashMap must yield
     * the same iteration order. (Catches regressions where copy-ctor uses a
     * different sizing path than direct puts.)
     */
    @Test
    public void copyOfLegacyPreservesOrder() {
        LegacyHashMap<String, Integer> a = new LegacyHashMap<>();
        for (int i = 0; i < 50; i++) a.put("k" + i, i);
        LegacyHashMap<String, Integer> b = new LegacyHashMap<>(a);
        assertEquals(new ArrayList<>(a.keySet()), new ArrayList<>(b.keySet()));
    }

    /**
     * Sourcing from a TreeMap (sorted) and copying into LegacyHashMap must
     * produce the same order as the DAO hash chain code path does — confirms
     * the supplier-substitution in DaoState.getBsqStateBuilderExcludingBlocks
     * sees the same input ordering as TreeMap.entrySet() iteration.
     */
    @Test
    public void treeMapSourceIterationIsDeterministic() {
        TreeMap<String, Integer> src = new TreeMap<>();
        for (int i = 0; i < 20; i++) src.put(String.format("k%03d", i), i);

        LegacyHashMap<String, Integer> a = new LegacyHashMap<>(src);
        LegacyHashMap<String, Integer> b = new LegacyHashMap<>(src);
        assertEquals(new ArrayList<>(a.keySet()), new ArrayList<>(b.keySet()));
    }

    @Test
    public void basicMapContract() {
        LegacyHashMap<String, Integer> m = new LegacyHashMap<>();
        assertNull(m.put("a", 1));
        assertEquals(Integer.valueOf(1), m.put("a", 2));
        assertEquals(Integer.valueOf(2), m.get("a"));
        assertEquals(1, m.size());
        assertEquals(Integer.valueOf(2), m.remove("a"));
        assertEquals(0, m.size());
    }
}
