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

package bisq.core.payment.payload;

import com.google.common.collect.ImmutableMap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

public class PaymentAccountPayloadExcludeFromJsonMap {
    // This replicates the order of the Java HashMap for the given keys, inserted by current offer producers,
    // to support backward compatibility.
    static final List<String> LEGACY_HASHMAP_ORDER = List.of(
            Keys.HOLDER_NAME,
            Keys.SALT,
            Keys.RESERVED_0,
            Keys.RESERVED_1,
            Keys.RESERVED_2,
            Keys.RESERVED_3
    );
    private static final Set<String> SUPPORTED_KEYS = Set.copyOf(LEGACY_HASHMAP_ORDER);

    private final LinkedHashMap<String, String> map;
    private final boolean preserveInsertionOrder;

    // Locally created maps are always serialized in the legacy Java HashMap order.
    public PaymentAccountPayloadExcludeFromJsonMap() {
        preserveInsertionOrder = false;
        map = new LinkedHashMap<>();
    }

    // If we get constructed from protobuf data we keep the order of the protobuf
    // LinkedHashMap
    public PaymentAccountPayloadExcludeFromJsonMap(Map<String, String> map) {
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
        map.forEach(PaymentAccountPayloadExcludeFromJsonMap::validateEntry);
        this.map.putAll(map);
        maybeApplyLegacyHashMapOrder();
    }

    public boolean containsKey(String key) {
        return map.containsKey(key);
    }

    public String get(String key) {
        return map.get(key);
    }

    public String compute(String key, BiFunction<? super String, ? super String, ? extends String> remappingFunction) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(remappingFunction, "remappingFunction must not be null");
        if (!SUPPORTED_KEYS.contains(key)) {
            throw new IllegalArgumentException("Unsupported key: " + key);
        }

        String newValue = remappingFunction.apply(key, map.get(key));
        if (newValue == null) {
            map.remove(key);
            maybeApplyLegacyHashMapOrder();
            return null;
        }

        put(key, newValue);
        return newValue;
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public ImmutableMap<String, String> getMap() {
        // ImmutableMap.copyOf preserves the order.
        // From the ImmutableMap.copyOf Java doc: The returned map iterates over entries in the same order
        // as the entrySet of the original map.
        return ImmutableMap.copyOf(map);
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof PaymentAccountPayloadExcludeFromJsonMap that)) return false;

        return map.equals(that.map);
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }

    @Override
    public String toString() {
        return "PaymentAccountPayloadExcludeFromJsonMap{" +
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
            throw new IllegalArgumentException("Unsupported key: " + key);
        }
    }

    public String getOrDefault(String key, String defaultValue) {
        return map.getOrDefault(key, defaultValue);
    }

    public static class Keys {
        public static final String SALT = "salt";
        public static final String HOLDER_NAME = "holderName";


        // Reserved for future use.
        public static final String RESERVED_0 = "reserved0";
        public static final String RESERVED_1 = "reserved1";
        public static final String RESERVED_2 = "reserved2";
        public static final String RESERVED_3 = "reserved3";
    }

    public static class Values {
        // If maker is seller and has xmrAutoConf enabled it is set to "1" otherwise it is not set
        public static final String XMR_AUTO_CONF_ENABLED_VALUE = "1";
    }
}
