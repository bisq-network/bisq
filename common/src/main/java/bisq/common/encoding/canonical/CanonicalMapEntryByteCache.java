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

package bisq.common.encoding.canonical;

import javax.annotation.Nullable;

/**
 * Optional two-tier cache for canonical map encoding.
 * <p>
 * {@link CanonicalEncoder} first asks a cache-enabled map for a complete encoded map field via
 * {@link #getEncodedMap(Object)}. The {@code cacheKey} is the schema field instance used for the current encoding,
 * so implementations that cache full-map bytes should key by field identity. A full-map hit must return bytes for
 * exactly that map field, including its field tags, or {@code null} when unavailable.
 * <p>
 * If the full-map cache misses, {@link CanonicalEncoder} orders the map entries and then uses the per-entry cache
 * methods. {@link #getEncodedMapEntry(Object, Object)} must return bytes only when {@code canonicalValue} is the
 * same value instance that produced the cached entry bytes; otherwise it must return {@code null}. On a miss,
 * {@link CanonicalEncoder} encodes the entry and calls {@link #putEncodedMapEntry(Object, Object, byte[])}. After
 * all entries are written, it calls {@link #putEncodedMap(Object, byte[])} so implementations can cache the complete
 * encoded map field for later unchanged encodes.
 * <p>
 * Encoding cache-enabled maps mutates the cache. Implementations should therefore be thread-confined with the source
 * map or protected by external synchronization.
 */
public interface CanonicalMapEntryByteCache<K, V> {
    /**
     * @return encoded bytes for the complete canonical map field, or {@code null} when absent
     */
    @Nullable
    default byte[] getEncodedMap(Object cacheKey) {
        return null;
    }

    /**
     * Stores encoded bytes for the complete canonical map field.
     */
    default void putEncodedMap(Object cacheKey, byte[] encodedMap) {
    }

    /**
     * @return encoded bytes for one canonical map entry, or {@code null} when absent or when {@code canonicalValue}
     *         is not the same value instance that produced the cached bytes
     */
    @Nullable
    byte[] getEncodedMapEntry(K canonicalKey, V canonicalValue);

    /**
     * Stores encoded bytes for one canonical map entry.
     */
    void putEncodedMapEntry(K canonicalKey, V canonicalValue, byte[] encodedMapEntry);
}
