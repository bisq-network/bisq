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

package bisq.core.offer.bisq_v1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static bisq.core.offer.bisq_v1.OfferPayloadExtraDataMap.Keys.ACCOUNT_AGE_WITNESS_HASH;
import static bisq.core.offer.bisq_v1.OfferPayloadExtraDataMap.Keys.CAPABILITIES;
import static bisq.core.offer.bisq_v1.OfferPayloadExtraDataMap.Keys.CASH_BY_MAIL_EXTRA_INFO;
import static bisq.core.offer.bisq_v1.OfferPayloadExtraDataMap.Keys.F2F_CITY;
import static bisq.core.offer.bisq_v1.OfferPayloadExtraDataMap.Keys.F2F_EXTRA_INFO;
import static bisq.core.offer.bisq_v1.OfferPayloadExtraDataMap.Keys.REFERRAL_ID;
import static bisq.core.offer.bisq_v1.OfferPayloadExtraDataMap.Keys.RESERVED_0;
import static bisq.core.offer.bisq_v1.OfferPayloadExtraDataMap.Keys.RESERVED_1;
import static bisq.core.offer.bisq_v1.OfferPayloadExtraDataMap.Keys.RESERVED_2;
import static bisq.core.offer.bisq_v1.OfferPayloadExtraDataMap.Keys.RESERVED_3;
import static bisq.core.offer.bisq_v1.OfferPayloadExtraDataMap.Keys.XMR_AUTO_CONF;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OfferPayloadExtraDataMapTest {
    private static final List<String> OFFER_UTIL_INSERTION_ORDER = List.of(
            ACCOUNT_AGE_WITNESS_HASH,
            REFERRAL_ID,
            F2F_CITY,
            F2F_EXTRA_INFO,
            CASH_BY_MAIL_EXTRA_INFO,
            CAPABILITIES,
            XMR_AUTO_CONF
    );
    private static final List<String> CANONICAL_ORDER = List.of(
            CAPABILITIES,
            REFERRAL_ID,
            XMR_AUTO_CONF,
            ACCOUNT_AGE_WITNESS_HASH,
            CASH_BY_MAIL_EXTRA_INFO,
            F2F_EXTRA_INFO,
            F2F_CITY,
            RESERVED_0,
            RESERVED_1,
            RESERVED_2,
            RESERVED_3
    );
    private static final String UNKNOWN_KEY = "unknownOfferPayloadExtraDataKey";

    @Test
    public void allEntrySetsSerializeLikeLegacyHashMapForOfferUtilInsertionOrder() {
        for (int mask = 1; mask < (1 << OFFER_UTIL_INSERTION_ORDER.size()); mask++) {
            Map<String, String> legacyHashMap = new HashMap<>();
            OfferPayloadExtraDataMap extraDataMap = new OfferPayloadExtraDataMap();

            for (String key : OFFER_UTIL_INSERTION_ORDER) {
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
        List<String> reverseInsertionOrder = new ArrayList<>(OFFER_UTIL_INSERTION_ORDER);
        Collections.reverse(reverseInsertionOrder);

        for (int mask = 1; mask < (1 << OFFER_UTIL_INSERTION_ORDER.size()); mask++) {
            OfferPayloadExtraDataMap offerUtilOrderMap = createLocalMap(OFFER_UTIL_INSERTION_ORDER, mask);
            OfferPayloadExtraDataMap reverseOrderMap = createLocalMap(reverseInsertionOrder, mask);

            assertEquals(expectedLegacyOrder(mask),
                    keys(reverseOrderMap.getMap()),
                    "Unexpected legacy key order for mask " + mask);
            assertEquals(keys(offerUtilOrderMap.getMap()),
                    keys(reverseOrderMap.getMap()),
                    "Caller insertion order changed key order for mask " + mask);
            assertArrayEquals(serialize(offerUtilOrderMap.getMap()),
                    serialize(reverseOrderMap.getMap()),
                    "Caller insertion order changed protobuf bytes for mask " + mask);
        }
    }

    @Test
    public void locallyCreatedMapCanonicalizesPutAllInput() {
        Map<String, String> nonCanonicalInput = new LinkedHashMap<>();
        nonCanonicalInput.put(CASH_BY_MAIL_EXTRA_INFO, valueFor(CASH_BY_MAIL_EXTRA_INFO));
        nonCanonicalInput.put(ACCOUNT_AGE_WITNESS_HASH, valueFor(ACCOUNT_AGE_WITNESS_HASH));
        nonCanonicalInput.put(XMR_AUTO_CONF, valueFor(XMR_AUTO_CONF));
        nonCanonicalInput.put(REFERRAL_ID, valueFor(REFERRAL_ID));

        OfferPayloadExtraDataMap extraDataMap = new OfferPayloadExtraDataMap();
        extraDataMap.putAll(nonCanonicalInput);

        assertEquals(List.of(REFERRAL_ID, XMR_AUTO_CONF, ACCOUNT_AGE_WITNESS_HASH, CASH_BY_MAIL_EXTRA_INFO),
                keys(extraDataMap.getMap()));
    }

    @Test
    public void reservedKeysUseFutureCanonicalOrderAfterLegacyKeys() {
        assertEquals(CANONICAL_ORDER, OfferPayloadExtraDataMap.LEGACY_HASHMAP_ORDER);

        Map<String, String> reverseCanonicalInput = new LinkedHashMap<>();
        List<String> reverseCanonicalOrder = new ArrayList<>(CANONICAL_ORDER);
        Collections.reverse(reverseCanonicalOrder);
        reverseCanonicalOrder.forEach(key -> reverseCanonicalInput.put(key, valueFor(key)));

        OfferPayloadExtraDataMap extraDataMap = new OfferPayloadExtraDataMap();
        extraDataMap.putAll(reverseCanonicalInput);

        assertEquals(CANONICAL_ORDER, keys(extraDataMap.getMap()));
    }

    @Test
    public void protobufConstructedMapPreservesInsertionOrder() {
        Map<String, String> protobufOrder = new LinkedHashMap<>();
        protobufOrder.put(CASH_BY_MAIL_EXTRA_INFO, valueFor(CASH_BY_MAIL_EXTRA_INFO));
        protobufOrder.put(ACCOUNT_AGE_WITNESS_HASH, valueFor(ACCOUNT_AGE_WITNESS_HASH));
        protobufOrder.put(XMR_AUTO_CONF, valueFor(XMR_AUTO_CONF));
        protobufOrder.put(REFERRAL_ID, valueFor(REFERRAL_ID));

        OfferPayloadExtraDataMap extraDataMap = new OfferPayloadExtraDataMap(protobufOrder);

        assertEquals(keys(protobufOrder), keys(extraDataMap.getMap()));
        assertArrayEquals(serialize(protobufOrder), serialize(extraDataMap.getMap()));
    }

    @Test
    public void rejectsUnknownKeys() {
        OfferPayloadExtraDataMap extraDataMap = new OfferPayloadExtraDataMap();
        IllegalArgumentException putException = assertThrows(IllegalArgumentException.class,
                () -> extraDataMap.put(UNKNOWN_KEY, "value"));
        assertTrue(putException.getMessage().contains(UNKNOWN_KEY));

        Map<String, String> mapWithUnknownKey = new LinkedHashMap<>();
        mapWithUnknownKey.put(CAPABILITIES, valueFor(CAPABILITIES));
        mapWithUnknownKey.put(UNKNOWN_KEY, "value");

        OfferPayloadExtraDataMap putAllMap = new OfferPayloadExtraDataMap();
        IllegalArgumentException putAllException = assertThrows(IllegalArgumentException.class,
                () -> putAllMap.putAll(mapWithUnknownKey));
        assertTrue(putAllException.getMessage().contains(UNKNOWN_KEY));
        assertTrue(putAllMap.isEmpty());

        IllegalArgumentException constructorException = assertThrows(IllegalArgumentException.class,
                () -> new OfferPayloadExtraDataMap(mapWithUnknownKey));
        assertTrue(constructorException.getMessage().contains(UNKNOWN_KEY));
    }

    private static OfferPayloadExtraDataMap createLocalMap(List<String> insertionOrder, int mask) {
        OfferPayloadExtraDataMap extraDataMap = new OfferPayloadExtraDataMap();
        insertionOrder.stream()
                .filter(key -> isSelected(mask, key))
                .forEach(key -> extraDataMap.put(key, valueFor(key)));
        return extraDataMap;
    }

    private static List<String> expectedLegacyOrder(int mask) {
        return OfferPayloadExtraDataMap.LEGACY_HASHMAP_ORDER.stream()
                .filter(key -> isSelected(mask, key))
                .toList();
    }

    private static boolean isSelected(int mask, String key) {
        int index = OFFER_UTIL_INSERTION_ORDER.indexOf(key);
        return (mask & (1 << index)) != 0;
    }

    private static String valueFor(String key) {
        return "value-for-" + key;
    }

    private static List<String> keys(Map<String, String> map) {
        return new ArrayList<>(map.keySet());
    }

    private static byte[] serialize(Map<String, String> map) {
        return protobuf.OfferPayload.newBuilder()
                .putAllExtraData(map)
                .build()
                .toByteArray();
    }
}
