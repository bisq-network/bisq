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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static bisq.core.support.dispute.DisputeExtraDataMap.Keys.COUNTER_CURRENCY_EXTRA_DATA;
import static bisq.core.support.dispute.DisputeExtraDataMap.Keys.COUNTER_CURRENCY_TX_ID;
import static bisq.core.support.dispute.DisputeExtraDataMap.Keys.RESERVED_0;
import static bisq.core.support.dispute.DisputeExtraDataMap.Keys.RESERVED_1;
import static bisq.core.support.dispute.DisputeExtraDataMap.Keys.RESERVED_2;
import static bisq.core.support.dispute.DisputeExtraDataMap.Keys.RESERVED_3;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DisputeExtraDataMapTest {
    private static final List<String> PENDING_TRADES_INSERTION_ORDER = List.of(
            COUNTER_CURRENCY_TX_ID,
            COUNTER_CURRENCY_EXTRA_DATA
    );
    private static final List<String> CANONICAL_ORDER = List.of(
            COUNTER_CURRENCY_TX_ID,
            COUNTER_CURRENCY_EXTRA_DATA,
            RESERVED_0,
            RESERVED_1,
            RESERVED_2,
            RESERVED_3
    );
    private static final String UNKNOWN_KEY = "unknownDisputeExtraDataKey";

    @Test
    public void allEntrySetsSerializeLikeLegacyHashMapForPendingTradesInsertionOrder() {
        for (int mask = 1; mask < (1 << PENDING_TRADES_INSERTION_ORDER.size()); mask++) {
            Map<String, String> legacyHashMap = new HashMap<>();
            DisputeExtraDataMap extraDataMap = new DisputeExtraDataMap();

            for (String key : PENDING_TRADES_INSERTION_ORDER) {
                if (isSelected(mask, key)) {
                    legacyHashMap.put(key, valueFor(key));
                    extraDataMap.put(key, valueFor(key));
                }
            }

            assertEquals(keys(legacyHashMap),
                    keys(extraDataMap.getMap()),
                    "Unexpected key order for mask " + mask);
            assertArrayEquals(serialize(legacyHashMap),
                    serialize(extraDataMap.getMap()),
                    "Unexpected protobuf bytes for mask " + mask);
        }
    }

    @Test
    public void locallyCreatedMapUsesLegacyOrderRegardlessOfCallerInsertionOrder() {
        List<String> reverseInsertionOrder = new ArrayList<>(PENDING_TRADES_INSERTION_ORDER);
        Collections.reverse(reverseInsertionOrder);

        for (int mask = 1; mask < (1 << PENDING_TRADES_INSERTION_ORDER.size()); mask++) {
            DisputeExtraDataMap pendingTradesOrderMap = createLocalMap(PENDING_TRADES_INSERTION_ORDER, mask);
            DisputeExtraDataMap reverseOrderMap = createLocalMap(reverseInsertionOrder, mask);

            assertEquals(expectedLegacyOrder(mask),
                    keys(reverseOrderMap.getMap()),
                    "Unexpected legacy key order for mask " + mask);
            assertEquals(keys(pendingTradesOrderMap.getMap()),
                    keys(reverseOrderMap.getMap()),
                    "Caller insertion order changed key order for mask " + mask);
            assertArrayEquals(serialize(pendingTradesOrderMap.getMap()),
                    serialize(reverseOrderMap.getMap()),
                    "Caller insertion order changed protobuf bytes for mask " + mask);
        }
    }

    @Test
    public void locallyCreatedMapCanonicalizesPutAllInput() {
        Map<String, String> nonCanonicalInput = new LinkedHashMap<>();
        nonCanonicalInput.put(COUNTER_CURRENCY_EXTRA_DATA, valueFor(COUNTER_CURRENCY_EXTRA_DATA));
        nonCanonicalInput.put(COUNTER_CURRENCY_TX_ID, valueFor(COUNTER_CURRENCY_TX_ID));

        DisputeExtraDataMap extraDataMap = new DisputeExtraDataMap();
        extraDataMap.putAll(nonCanonicalInput);

        assertEquals(List.of(COUNTER_CURRENCY_TX_ID, COUNTER_CURRENCY_EXTRA_DATA),
                keys(extraDataMap.getMap()));
    }

    @Test
    public void reservedKeysUseFutureCanonicalOrderAfterLegacyKeys() {
        assertEquals(CANONICAL_ORDER, DisputeExtraDataMap.LEGACY_HASHMAP_ORDER);

        Map<String, String> reverseCanonicalInput = new LinkedHashMap<>();
        List<String> reverseCanonicalOrder = new ArrayList<>(CANONICAL_ORDER);
        Collections.reverse(reverseCanonicalOrder);
        reverseCanonicalOrder.forEach(key -> reverseCanonicalInput.put(key, valueFor(key)));

        DisputeExtraDataMap extraDataMap = new DisputeExtraDataMap();
        extraDataMap.putAll(reverseCanonicalInput);

        assertEquals(CANONICAL_ORDER, keys(extraDataMap.getMap()));
    }

    @Test
    public void protobufConstructedMapPreservesInsertionOrder() {
        Map<String, String> protobufOrder = new LinkedHashMap<>();
        protobufOrder.put(COUNTER_CURRENCY_EXTRA_DATA, valueFor(COUNTER_CURRENCY_EXTRA_DATA));
        protobufOrder.put(COUNTER_CURRENCY_TX_ID, valueFor(COUNTER_CURRENCY_TX_ID));

        DisputeExtraDataMap extraDataMap = new DisputeExtraDataMap(protobufOrder);

        assertEquals(keys(protobufOrder), keys(extraDataMap.getMap()));
        assertArrayEquals(serialize(protobufOrder), serialize(extraDataMap.getMap()));
    }

    @Test
    public void getMapUsesCanonicalOrder() {
        DisputeExtraDataMap extraDataMap = new DisputeExtraDataMap();
        extraDataMap.put(COUNTER_CURRENCY_EXTRA_DATA, valueFor(COUNTER_CURRENCY_EXTRA_DATA));
        extraDataMap.put(COUNTER_CURRENCY_TX_ID, valueFor(COUNTER_CURRENCY_TX_ID));

        assertEquals(List.of(COUNTER_CURRENCY_TX_ID, COUNTER_CURRENCY_EXTRA_DATA),
                keys(extraDataMap.getMap()));
    }

    @Test
    public void rejectsUnknownKeys() {
        DisputeExtraDataMap extraDataMap = new DisputeExtraDataMap();
        IllegalArgumentException putException = assertThrows(IllegalArgumentException.class,
                () -> extraDataMap.put(UNKNOWN_KEY, "value"));
        assertTrue(putException.getMessage().contains("Dispute"));
        assertTrue(putException.getMessage().contains(UNKNOWN_KEY));

        Map<String, String> mapWithUnknownKey = new LinkedHashMap<>();
        mapWithUnknownKey.put(COUNTER_CURRENCY_TX_ID, valueFor(COUNTER_CURRENCY_TX_ID));
        mapWithUnknownKey.put(UNKNOWN_KEY, "value");

        DisputeExtraDataMap putAllMap = new DisputeExtraDataMap();
        IllegalArgumentException putAllException = assertThrows(IllegalArgumentException.class,
                () -> putAllMap.putAll(mapWithUnknownKey));
        assertTrue(putAllException.getMessage().contains("Dispute"));
        assertTrue(putAllException.getMessage().contains(UNKNOWN_KEY));
        assertTrue(putAllMap.isEmpty());

        IllegalArgumentException constructorException = assertThrows(IllegalArgumentException.class,
                () -> new DisputeExtraDataMap(mapWithUnknownKey));
        assertTrue(constructorException.getMessage().contains("Dispute"));
        assertTrue(constructorException.getMessage().contains(UNKNOWN_KEY));
    }

    private static DisputeExtraDataMap createLocalMap(List<String> insertionOrder, int mask) {
        DisputeExtraDataMap extraDataMap = new DisputeExtraDataMap();
        insertionOrder.stream()
                .filter(key -> isSelected(mask, key))
                .forEach(key -> extraDataMap.put(key, valueFor(key)));
        return extraDataMap;
    }

    private static List<String> expectedLegacyOrder(int mask) {
        return DisputeExtraDataMap.LEGACY_HASHMAP_ORDER.stream()
                .filter(key -> isSelected(mask, key))
                .toList();
    }

    private static boolean isSelected(int mask, String key) {
        int index = PENDING_TRADES_INSERTION_ORDER.indexOf(key);
        return (mask & (1 << index)) != 0;
    }

    private static String valueFor(String key) {
        return "value-for-" + key;
    }

    private static List<String> keys(Map<String, String> map) {
        return new ArrayList<>(map.keySet());
    }

    private static byte[] serialize(Map<String, String> map) {
        return protobuf.Dispute.newBuilder()
                .putAllExtraData(map)
                .build()
                .toByteArray();
    }
}
