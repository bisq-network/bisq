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

package bisq.core.support.dispute;

import bisq.core.offer.bisq_v1.OfferPayloadExtraDataMap;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class DisputeExtraDataMap {
    // This replicates the order of the Java HashMap for the given keys, inserted by current offer producers,
    // to support backward compatibility.
    static final List<String> LEGACY_HASHMAP_ORDER = List.of(
            Keys.COUNTER_CURRENCY_TX_ID,
            Keys.COUNTER_CURRENCY_EXTRA_DATA,
            Keys.RESERVED_0,
            Keys.RESERVED_1,
            Keys.RESERVED_2,
            Keys.RESERVED_3
    );
    private static final Set<String> SUPPORTED_KEYS = Set.copyOf(LEGACY_HASHMAP_ORDER);

    private final LinkedHashMap<String, String> map;
    private final boolean preserveInsertionOrder;

    // Locally created maps are always serialized in the legacy Java HashMap order.
    public DisputeExtraDataMap() {
        preserveInsertionOrder = false;
        map = new LinkedHashMap<>();
    }

    // If we get constructed from protobuf data we keep the order of the protobuf
    // LinkedHashMap
    public DisputeExtraDataMap(Map<String, String> map) {
        preserveInsertionOrder = true;
        this.map = new LinkedHashMap<>();
        putAll(map);
    }

    public String put(String key, String value) {
        validateEntry(key, value);
        String previousValue = map.put(key, value);
        maybeApplyLegacyHashMapOrder();
        return previousValue;
    }

    public void putAll(Map<String, String> map) {
        Objects.requireNonNull(map, "map must not be null");
        map.forEach(DisputeExtraDataMap::validateEntry);
        this.map.putAll(map);
        maybeApplyLegacyHashMapOrder();
    }

    public boolean containsKey(String key) {
        return map.containsKey(key);
    }

    public String get(String key) {
        return map.get(key);
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public int size() {
        return map.size();
    }

    public ImmutableSet<Map.Entry<String, String>> entrySet() {
        return ImmutableSet.copyOf(map.entrySet());
    }

    public ImmutableMap<String, String> getMap() {
        // ImmutableMap.copyOf preserves the order.
        // From the ImmutableMap.copyOf Java doc: The returned map iterates over entries in the same order
        // as the entrySet of the original map.
        return ImmutableMap.copyOf(map);
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof DisputeExtraDataMap that)) return false;

        return map.equals(that.map);
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }

    @Override
    public String toString() {
        return "DisputeExtraDataMap{" +
                "map=" + map +
                '}';
    }

    private void maybeApplyLegacyHashMapOrder() {
        if (preserveInsertionOrder) {
            return;
        }

        LinkedHashMap<String, String> orderedMap = new LinkedHashMap<>();
        LEGACY_HASHMAP_ORDER.stream()
                .filter(map::containsKey)
                .forEach(key -> orderedMap.put(key, map.get(key)));
        map.clear();
        map.putAll(orderedMap);
    }

    private static void validateEntry(String key, String value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        if (!SUPPORTED_KEYS.contains(key)) {
            throw new IllegalArgumentException("Unsupported Dispute extraDataMap key: " + key);
        }
    }

    public static class Keys {
        public static final String COUNTER_CURRENCY_TX_ID = "counterCurrencyTxId";
        public static final String COUNTER_CURRENCY_EXTRA_DATA = "counterCurrencyExtraData";

        // Reserved for future use.
        public static final String RESERVED_0 = "reserved0";
        public static final String RESERVED_1 = "reserved1";
        public static final String RESERVED_2 = "reserved2";
        public static final String RESERVED_3 = "reserved3";
    }
}
