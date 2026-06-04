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

import com.google.common.collect.ImmutableMap;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import lombok.Getter;

public class OfferPayloadExtraDataMap {
    // This replicates the order of the Java HashMap for the given keys to support backward compatibility.
    private static final List<String> LEGACY_HASHMAP_ORDER = List.of(
            Keys.CAPABILITIES,
            Keys.REFERRAL_ID,
            Keys.XMR_AUTO_CONF,
            Keys.ACCOUNT_AGE_WITNESS_HASH,
            Keys.CASH_BY_MAIL_EXTRA_INFO,
            Keys.F2F_EXTRA_INFO,
            Keys.F2F_CITY
    );

    public ImmutableMap<String, String> getMap() {
        // ImmutableMap.copyOf preserves the order.
        // From the ImmutableMap.copyOf Java doc: The returned map iterates over entries in the same order
        // as the entrySet of the original map.
        return ImmutableMap.copyOf(map);
    }

    private final Map<String, String> map;

    // We use the LEGACY_HASHMAP_ORDER as comparator for the TreeMap
    public OfferPayloadExtraDataMap() {
        map = new TreeMap<>(Comparator.comparingInt(LEGACY_HASHMAP_ORDER::indexOf));
    }

    // If we get constructed from protobuf data we keep the order of the protobuf
    // LinkedHashMap
    public OfferPayloadExtraDataMap(Map<String, String> map) {
        this.map = new LinkedHashMap<>(map);
    }

    public String put(String key, String value) {
        return map.put(key, value);
    }

    public void putAll(Map<String, String> map) {
        this.map.putAll(map);
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

    public static class Keys {
        // Keys for extra map
        // Only set for fiat offers
        public static final String ACCOUNT_AGE_WITNESS_HASH = "accountAgeWitnessHash";
        public static final String REFERRAL_ID = "referralId";
        // Only used in payment method F2F
        public static final String F2F_CITY = "f2fCity";
        public static final String F2F_EXTRA_INFO = "f2fExtraInfo";
        public static final String CASH_BY_MAIL_EXTRA_INFO = "cashByMailExtraInfo";
        // Comma separated list of ordinal of a bisq.common.app.Capability. E.g. ordinal of
        // Capability.SIGNED_ACCOUNT_AGE_WITNESS is 11 and Capability.MEDIATION is 12 so if we want to signal that maker
        // of the offer supports both capabilities we add "11, 12" to capabilities.
        public static final String CAPABILITIES = "capabilities";
        // If maker is seller and has xmrAutoConf enabled it is set to "1" otherwise it is not set
        public static final String XMR_AUTO_CONF = "xmrAutoConf";
    }

    public static class Values {
        // If maker is seller and has xmrAutoConf enabled it is set to "1" otherwise it is not set
        public static final String XMR_AUTO_CONF_ENABLED_VALUE = "1";
    }
}
