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

package bisq.core.dao.state.model.blockchain;

import bisq.common.encoding.canonical.CanonicalMapEntryByteCache;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.Nullable;

public final class SpentInfoMap extends TreeMap<TxOutputKey, SpentInfo>
        implements CanonicalMapEntryByteCache<String, SpentInfo> {
    private static final long serialVersionUID = 1L;

    // These caches intentionally trade steady-state heap for avoiding repeated canonical encoding of the large
    // spentInfoMap. The full-map snapshot is shallow: it duplicates TreeMap structure, but not TxOutputKey or
    // SpentInfo instances.
    private final transient Map<String, CachedMapEntryBytes> encodedMapEntryBytesByKey = new HashMap<>();

    // The encoded-map cache stores the bytes for one complete canonical map field, including that field's tags.
    // It must therefore be keyed by the exact CanonicalSchema.Field instance, not by structural equality: the same
    // source map can be encoded differently by another schema field number or map encoding.
    private final transient Map<Object, byte[]> encodedMapBytesByCacheKey = new IdentityHashMap<>();

    // TreeMap view removals do not call our overridden mutators, and TreeMap.modCount is not accessible here.
    // Keep a shallow snapshot of the map content that produced the full-map cache and drop both cache tiers if
    // current content no longer matches it.
    @Nullable
    private transient Map<TxOutputKey, SpentInfo> encodedMapSnapshot;

    public SpentInfoMap() {
    }

    public SpentInfoMap(Map<TxOutputKey, SpentInfo> source) {
        putAll(source);
    }

    @Override
    @Nullable
    public byte[] getEncodedMap(Object cacheKey) {
        byte[] encodedMap = encodedMapBytesByCacheKey.get(cacheKey);
        if (encodedMap == null) {
            return null;
        }

        if (!matchesEncodedMapSnapshot()) {
            invalidateAllEncodedCaches();
            return null;
        }

        return encodedMap;
    }

    @Override
    public void putEncodedMap(Object cacheKey, byte[] encodedMap) {
        encodedMapBytesByCacheKey.put(cacheKey, encodedMap);
        encodedMapSnapshot = new TreeMap<>(this);
    }

    @Override
    @Nullable
    public byte[] getEncodedMapEntry(String canonicalKey, SpentInfo canonicalValue) {
        CachedMapEntryBytes cached = encodedMapEntryBytesByKey.get(canonicalKey);
        return cached != null && cached.spentInfo == canonicalValue ? cached.encodedMapEntry : null;
    }

    @Override
    public void putEncodedMapEntry(String canonicalKey, SpentInfo canonicalValue, byte[] encodedMapEntry) {
        encodedMapEntryBytesByKey.put(canonicalKey, new CachedMapEntryBytes(canonicalValue, encodedMapEntry));
    }

    @Override
    public SpentInfo put(TxOutputKey key, SpentInfo value) {
        invalidateEncodedMapCache();
        return super.put(key, value);
    }

    @Override
    public void putAll(Map<? extends TxOutputKey, ? extends SpentInfo> map) {
        if (!map.isEmpty()) {
            invalidateEncodedMapCache();
        }
        super.putAll(map);
    }

    @Override
    public SpentInfo remove(Object key) {
        SpentInfo removed = super.remove(key);
        if (removed != null) {
            invalidateEncodedMapCache();
            encodedMapEntryBytesByKey.remove(key.toString());
        }
        return removed;
    }

    @Override
    public void clear() {
        invalidateAllEncodedCaches();
        super.clear();
    }

    @Override
    public Map.Entry<TxOutputKey, SpentInfo> pollFirstEntry() {
        Map.Entry<TxOutputKey, SpentInfo> entry = super.pollFirstEntry();
        if (entry != null) {
            invalidateEncodedMapCache();
            encodedMapEntryBytesByKey.remove(entry.getKey().toString());
        }
        return entry;
    }

    @Override
    public Map.Entry<TxOutputKey, SpentInfo> pollLastEntry() {
        Map.Entry<TxOutputKey, SpentInfo> entry = super.pollLastEntry();
        if (entry != null) {
            invalidateEncodedMapCache();
            encodedMapEntryBytesByKey.remove(entry.getKey().toString());
        }
        return entry;
    }

    private boolean matchesEncodedMapSnapshot() {
        return encodedMapSnapshot != null &&
                size() == encodedMapSnapshot.size() &&
                entrySet().equals(encodedMapSnapshot.entrySet());
    }

    private void invalidateEncodedMapCache() {
        encodedMapBytesByCacheKey.clear();
        encodedMapSnapshot = null;
    }

    private void invalidateAllEncodedCaches() {
        encodedMapBytesByCacheKey.clear();
        encodedMapEntryBytesByKey.clear();
        encodedMapSnapshot = null;
    }

    private static final class CachedMapEntryBytes {
        private final SpentInfo spentInfo;
        private final byte[] encodedMapEntry;

        private CachedMapEntryBytes(SpentInfo spentInfo, byte[] encodedMapEntry) {
            this.spentInfo = spentInfo;
            this.encodedMapEntry = encodedMapEntry;
        }
    }
}
