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

package bisq.common.util;

import com.google.common.collect.BoundType;
import com.google.common.collect.Range;

import java.util.Collections;
import java.util.NavigableSet;
import java.util.function.Function;
import java.util.function.Predicate;

public final class RangeUtils {
    private RangeUtils() {
    }

    public static <E extends ComparableExt<E>> NavigableSet<E> subSet(NavigableSet<E> set,
                                                                      Predicate<? super E> fromFilter,
                                                                      Predicate<? super E> toFilter) {
        E fromElement = ComparableExt.higher(set, fromFilter);
        E toElement = ComparableExt.lower(set, toFilter);
        return fromElement != null && toElement != null && fromElement.compareTo(toElement) <= 0 ?
                set.subSet(fromElement, true, toElement, true) : Collections.emptyNavigableSet();
    }

    public static <E extends ComparableExt<E>> SubCollection<NavigableSet<E>, E> subSet(NavigableSet<E> set) {
        return new SubCollection<>() {
            @Override
            public <K extends Comparable<? super K>> WithKeyFunction<NavigableSet<E>, K> withKey(Function<E, K> increasingKeyFn) {
                return (Range<K> range) -> {
                    var fromToFilter = boundFilters(increasingKeyFn, range);
                    return subSet(set, fromToFilter.first, fromToFilter.second);
                };
            }
        };
    }

    private static <E, K extends Comparable<? super K>> Tuple2<Predicate<E>, Predicate<E>> boundFilters(Function<E, K> keyFn,
                                                                                                        Range<K> keyRange) {
        Predicate<E> fromFilter, toFilter;
        if (keyRange.hasLowerBound()) {
            K fromKey = keyRange.lowerEndpoint();
            fromFilter = keyRange.lowerBoundType() == BoundType.CLOSED
                    ? (E e) -> fromKey.compareTo(keyFn.apply(e)) <= 0
                    : (E e) -> fromKey.compareTo(keyFn.apply(e)) < 0;
        } else {
            fromFilter = e -> true;
        }
        if (keyRange.hasUpperBound()) {
            K toKey = keyRange.upperEndpoint();
            toFilter = keyRange.upperBoundType() == BoundType.CLOSED
                    ? (E e) -> toKey.compareTo(keyFn.apply(e)) < 0
                    : (E e) -> toKey.compareTo(keyFn.apply(e)) <= 0;
        } else {
            toFilter = e -> false;
        }
        return new Tuple2<>(fromFilter, toFilter);
    }

    public interface SubCollection<C, E> {
        <K extends Comparable<? super K>> WithKeyFunction<C, K> withKey(Function<E, K> increasingKeyFn);
    }

    public interface WithKeyFunction<C, K extends Comparable<? super K>> {
        C overRange(Range<K> range);
    }
}
