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

package bisq.core.encoding.canonical;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import javax.annotation.Nullable;

public final class TreeMapIterator<K, V> implements CanonicalMapEntryIterator<K, V> {
    @Nullable
    private final Comparator<? super K> comparator;

    public TreeMapIterator() {
        this(null);
    }

    public TreeMapIterator(@Nullable Comparator<? super K> comparator) {
        this.comparator = comparator;
    }

    public static <K extends Comparable<? super K>, V> TreeMapIterator<K, V> naturalOrder() {
        return new TreeMapIterator<>();
    }

    public static <K, V> TreeMapIterator<K, V> comparing(Comparator<? super K> comparator) {
        return new TreeMapIterator<>(Objects.requireNonNull(comparator));
    }

    @Override
    public Iterator<Map.Entry<K, V>> iterate(List<Map.Entry<K, V>> entries) {
        TreeMap<K, V> sortedEntries = comparator == null ? new TreeMap<>() : new TreeMap<>(comparator);
        entries.forEach(entry -> {
            K key = Objects.requireNonNull(entry.getKey(), "Canonical map keys must not be null");
            if (sortedEntries.containsKey(key)) {
                throw new IllegalArgumentException("Duplicate canonical map key " + key);
            }
            sortedEntries.put(key, entry.getValue());
        });
        return sortedEntries.entrySet().iterator();
    }
}
