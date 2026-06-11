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

    private final transient Map<String, CachedMapEntryBytes> encodedMapEntryBytesByKey = new HashMap<>();

    // The encoded-map cache stores the bytes for one complete canonical map field, including that field's tags.
    // It must therefore be keyed by the exact CanonicalSchema.Field instance, not by structural equality: the same
    // source map can be encoded differently by another schema field number or map encoding.
    private final transient Map<Object, byte[]> encodedMapBytesByCacheKey = new IdentityHashMap<>();

    public SpentInfoMap() {
    }

    public SpentInfoMap(Map<TxOutputKey, SpentInfo> source) {
        putAll(source);
    }

    @Override
    @Nullable
    public byte[] getEncodedMap(Object cacheKey) {
        return encodedMapBytesByCacheKey.get(cacheKey);
    }

    @Override
    public void putEncodedMap(Object cacheKey, byte[] encodedMap) {
        encodedMapBytesByCacheKey.put(cacheKey, encodedMap);
    }

    @Override
    @Nullable
    public byte[] getEncodedMapEntry(String canonicalKey, SpentInfo canonicalValue) {
        CachedMapEntryBytes cached = encodedMapEntryBytesByKey.get(canonicalKey);
        return cached != null && cached.spentInfo.equals(canonicalValue) ? cached.encodedMapEntry : null;
    }

    @Override
    public void putEncodedMapEntry(String canonicalKey, SpentInfo canonicalValue, byte[] encodedMapEntry) {
        encodedMapEntryBytesByKey.put(canonicalKey, new CachedMapEntryBytes(canonicalValue, encodedMapEntry));
    }

    @Override
    public SpentInfo put(TxOutputKey key, SpentInfo value) {
        encodedMapBytesByCacheKey.clear();
        return super.put(key, value);
    }

    @Override
    public void putAll(Map<? extends TxOutputKey, ? extends SpentInfo> map) {
        if (!map.isEmpty()) {
            encodedMapBytesByCacheKey.clear();
        }
        super.putAll(map);
    }

    @Override
    public SpentInfo remove(Object key) {
        encodedMapBytesByCacheKey.clear();
        return super.remove(key);
    }

    @Override
    public void clear() {
        encodedMapBytesByCacheKey.clear();
        encodedMapEntryBytesByKey.clear();
        super.clear();
    }

    @Override
    public Map.Entry<TxOutputKey, SpentInfo> pollFirstEntry() {
        Map.Entry<TxOutputKey, SpentInfo> entry = super.pollFirstEntry();
        if (entry != null) {
            encodedMapBytesByCacheKey.clear();
        }
        return entry;
    }

    @Override
    public Map.Entry<TxOutputKey, SpentInfo> pollLastEntry() {
        Map.Entry<TxOutputKey, SpentInfo> entry = super.pollLastEntry();
        if (entry != null) {
            encodedMapBytesByCacheKey.clear();
        }
        return entry;
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
