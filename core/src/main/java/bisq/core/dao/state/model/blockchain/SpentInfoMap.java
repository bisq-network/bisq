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
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.Nullable;

public final class SpentInfoMap extends TreeMap<TxOutputKey, SpentInfo>
        implements CanonicalMapEntryByteCache<String, SpentInfo> {
    private static final long serialVersionUID = 1L;

    private final transient Map<String, CachedMapEntryBytes> encodedMapEntryBytesByKey = new HashMap<>();

    public SpentInfoMap() {
    }

    public SpentInfoMap(Map<TxOutputKey, SpentInfo> source) {
        putAll(source);
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
    public void clear() {
        encodedMapEntryBytesByKey.clear();
        super.clear();
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
