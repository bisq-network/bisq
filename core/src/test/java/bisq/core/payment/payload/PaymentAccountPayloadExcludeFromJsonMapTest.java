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

package bisq.core.payment.payload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static bisq.core.payment.payload.PaymentAccountPayloadExcludeFromJsonMap.Keys.HOLDER_NAME;
import static bisq.core.payment.payload.PaymentAccountPayloadExcludeFromJsonMap.Keys.RESERVED_0;
import static bisq.core.payment.payload.PaymentAccountPayloadExcludeFromJsonMap.Keys.RESERVED_1;
import static bisq.core.payment.payload.PaymentAccountPayloadExcludeFromJsonMap.Keys.RESERVED_2;
import static bisq.core.payment.payload.PaymentAccountPayloadExcludeFromJsonMap.Keys.RESERVED_3;
import static bisq.core.payment.payload.PaymentAccountPayloadExcludeFromJsonMap.Keys.SALT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PaymentAccountPayloadExcludeFromJsonMapTest {
    private static final List<String> PAYMENT_ACCOUNT_INSERTION_ORDER = List.of(
            SALT,
            HOLDER_NAME
    );
    private static final List<String> CANONICAL_ORDER = List.of(
            HOLDER_NAME,
            SALT,
            RESERVED_0,
            RESERVED_1,
            RESERVED_2,
            RESERVED_3
    );
    private static final String UNKNOWN_KEY = "unknownPaymentAccountPayloadExcludeFromJsonKey";

    @Test
    public void allEntrySetsSerializeLikeLegacyHashMapForPaymentAccountInsertionOrder() {
        for (int mask = 1; mask < (1 << PAYMENT_ACCOUNT_INSERTION_ORDER.size()); mask++) {
            Map<String, String> legacyHashMap = new HashMap<>();
            PaymentAccountPayloadExcludeFromJsonMap excludeFromJsonMap = new PaymentAccountPayloadExcludeFromJsonMap();

            for (String key : PAYMENT_ACCOUNT_INSERTION_ORDER) {
                if (isSelected(mask, key)) {
                    legacyHashMap.put(key, valueFor(key));
                    excludeFromJsonMap.put(key, valueFor(key));
                }
            }

            assertEquals(keys(legacyHashMap),
                    keys(excludeFromJsonMap.getMap()),
                    "Unexpected key order for mask " + mask);
            assertArrayEquals(serialize(legacyHashMap),
                    serialize(excludeFromJsonMap.getMap()),
                    "Unexpected protobuf bytes for mask " + mask);
        }
    }

    @Test
    public void locallyCreatedMapUsesLegacyOrderRegardlessOfCallerInsertionOrder() {
        List<String> reverseInsertionOrder = new ArrayList<>(PAYMENT_ACCOUNT_INSERTION_ORDER);
        Collections.reverse(reverseInsertionOrder);

        for (int mask = 1; mask < (1 << PAYMENT_ACCOUNT_INSERTION_ORDER.size()); mask++) {
            PaymentAccountPayloadExcludeFromJsonMap paymentAccountOrderMap =
                    createLocalMap(PAYMENT_ACCOUNT_INSERTION_ORDER, mask);
            PaymentAccountPayloadExcludeFromJsonMap reverseOrderMap =
                    createLocalMap(reverseInsertionOrder, mask);

            assertEquals(expectedLegacyOrder(mask),
                    keys(reverseOrderMap.getMap()),
                    "Unexpected legacy key order for mask " + mask);
            assertEquals(keys(paymentAccountOrderMap.getMap()),
                    keys(reverseOrderMap.getMap()),
                    "Caller insertion order changed key order for mask " + mask);
            assertArrayEquals(serialize(paymentAccountOrderMap.getMap()),
                    serialize(reverseOrderMap.getMap()),
                    "Caller insertion order changed protobuf bytes for mask " + mask);
        }
    }

    @Test
    public void locallyCreatedMapCanonicalizesPutAllInput() {
        Map<String, String> nonCanonicalInput = new LinkedHashMap<>();
        nonCanonicalInput.put(SALT, valueFor(SALT));
        nonCanonicalInput.put(HOLDER_NAME, valueFor(HOLDER_NAME));

        PaymentAccountPayloadExcludeFromJsonMap excludeFromJsonMap = new PaymentAccountPayloadExcludeFromJsonMap();
        excludeFromJsonMap.putAll(nonCanonicalInput);

        assertEquals(List.of(HOLDER_NAME, SALT), keys(excludeFromJsonMap.getMap()));
    }

    @Test
    public void reservedKeysUseFutureCanonicalOrderAfterLegacyKeys() {
        assertEquals(CANONICAL_ORDER, PaymentAccountPayloadExcludeFromJsonMap.LEGACY_HASHMAP_ORDER);

        Map<String, String> reverseCanonicalInput = new LinkedHashMap<>();
        List<String> reverseCanonicalOrder = new ArrayList<>(CANONICAL_ORDER);
        Collections.reverse(reverseCanonicalOrder);
        reverseCanonicalOrder.forEach(key -> reverseCanonicalInput.put(key, valueFor(key)));

        PaymentAccountPayloadExcludeFromJsonMap excludeFromJsonMap = new PaymentAccountPayloadExcludeFromJsonMap();
        excludeFromJsonMap.putAll(reverseCanonicalInput);

        assertEquals(CANONICAL_ORDER, keys(excludeFromJsonMap.getMap()));
    }

    @Test
    public void protobufConstructedMapPreservesInsertionOrder() {
        Map<String, String> protobufOrder = new LinkedHashMap<>();
        protobufOrder.put(SALT, valueFor(SALT));
        protobufOrder.put(HOLDER_NAME, valueFor(HOLDER_NAME));

        PaymentAccountPayloadExcludeFromJsonMap excludeFromJsonMap =
                new PaymentAccountPayloadExcludeFromJsonMap(protobufOrder);

        assertEquals(keys(protobufOrder), keys(excludeFromJsonMap.getMap()));
        assertArrayEquals(serialize(protobufOrder), serialize(excludeFromJsonMap.getMap()));
    }

    @Test
    public void computeUsesCanonicalOrderAndRemovesNullValues() {
        PaymentAccountPayloadExcludeFromJsonMap excludeFromJsonMap = new PaymentAccountPayloadExcludeFromJsonMap();
        excludeFromJsonMap.put(SALT, valueFor(SALT));
        excludeFromJsonMap.compute(HOLDER_NAME, (key, value) -> valueFor(key));

        assertEquals(List.of(HOLDER_NAME, SALT), keys(excludeFromJsonMap.getMap()));

        excludeFromJsonMap.compute(HOLDER_NAME, (key, value) -> null);

        assertEquals(List.of(SALT), keys(excludeFromJsonMap.getMap()));
    }

    @Test
    public void rejectsUnknownKeys() {
        PaymentAccountPayloadExcludeFromJsonMap excludeFromJsonMap = new PaymentAccountPayloadExcludeFromJsonMap();
        IllegalArgumentException putException = assertThrows(IllegalArgumentException.class,
                () -> excludeFromJsonMap.put(UNKNOWN_KEY, "value"));
        assertTrue(putException.getMessage().contains(UNKNOWN_KEY));

        Map<String, String> mapWithUnknownKey = new LinkedHashMap<>();
        mapWithUnknownKey.put(SALT, valueFor(SALT));
        mapWithUnknownKey.put(UNKNOWN_KEY, "value");

        PaymentAccountPayloadExcludeFromJsonMap putAllMap = new PaymentAccountPayloadExcludeFromJsonMap();
        IllegalArgumentException putAllException = assertThrows(IllegalArgumentException.class,
                () -> putAllMap.putAll(mapWithUnknownKey));
        assertTrue(putAllException.getMessage().contains(UNKNOWN_KEY));
        assertTrue(putAllMap.isEmpty());

        IllegalArgumentException constructorException = assertThrows(IllegalArgumentException.class,
                () -> new PaymentAccountPayloadExcludeFromJsonMap(mapWithUnknownKey));
        assertTrue(constructorException.getMessage().contains(UNKNOWN_KEY));
    }

    private static PaymentAccountPayloadExcludeFromJsonMap createLocalMap(List<String> insertionOrder, int mask) {
        PaymentAccountPayloadExcludeFromJsonMap excludeFromJsonMap = new PaymentAccountPayloadExcludeFromJsonMap();
        insertionOrder.stream()
                .filter(key -> isSelected(mask, key))
                .forEach(key -> excludeFromJsonMap.put(key, valueFor(key)));
        return excludeFromJsonMap;
    }

    private static List<String> expectedLegacyOrder(int mask) {
        return PaymentAccountPayloadExcludeFromJsonMap.LEGACY_HASHMAP_ORDER.stream()
                .filter(key -> isSelected(mask, key))
                .toList();
    }

    private static boolean isSelected(int mask, String key) {
        int index = PAYMENT_ACCOUNT_INSERTION_ORDER.indexOf(key);
        return (mask & (1 << index)) != 0;
    }

    private static String valueFor(String key) {
        return "value-for-" + key;
    }

    private static List<String> keys(Map<String, String> map) {
        return new ArrayList<>(map.keySet());
    }

    private static byte[] serialize(Map<String, String> map) {
        return protobuf.PaymentAccountPayload.newBuilder()
                .putAllExcludeFromJsonData(map)
                .build()
                .toByteArray();
    }
}
