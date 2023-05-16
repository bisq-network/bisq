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

import java.util.NavigableSet;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import static bisq.common.util.Preconditions.checkComparatorNullOrNatural;

/**
 * A {@link Comparable} which may be compared with adhoc mark/delimiter objects, in
 * addition to objects of the same type, to support searches for adjacent elements in
 * a sorted collection without having to use dummy objects for this purpose. For example,
 * one may wish to find the smallest object after a given date, in a collection sorted by
 * date. This is to work round the limitation that {@link java.util.SortedSet} and
 * {@link java.util.SortedMap} only support comparison with other keys when searching for
 * elements rather than allowing general binary searches with a predicate.
 *
 * <p>Implementations should define {@link Comparable#compareTo(Object)} like follows:
 * <pre>{@code
 * public int compareTo(@NotNull ComparableExt<Foo> o) {
 *     return o instanceof Foo ? this.normalCompareTo((Foo) o) : -o.compareTo(this);
 * }
 * }</pre>
 * @param <T>
 */
public interface ComparableExt<T> extends Comparable<ComparableExt<T>> {
    @SuppressWarnings("unchecked")
    @Nullable
    static <E extends ComparableExt<E>> E lower(NavigableSet<E> set, Predicate<? super E> filter) {
        checkComparatorNullOrNatural(set.comparator(), "Set must be naturally ordered");
        return (E) ((NavigableSet<ComparableExt<E>>) set).lower(Mark.of(filter));
    }

    @SuppressWarnings("unchecked")
    @Nullable
    static <E extends ComparableExt<E>> E higher(NavigableSet<E> set, Predicate<? super E> filter) {
        checkComparatorNullOrNatural(set.comparator(), "Set must be naturally ordered");
        return (E) ((NavigableSet<ComparableExt<E>>) set).higher(Mark.of(filter));
    }

    interface Mark<T> extends ComparableExt<T> {
        @SuppressWarnings("unchecked")
        static <T> Mark<T> of(Predicate<? super T> filter) {
            return x -> x instanceof Mark ? 0 : filter.test((T) x) ? -1 : 1;
        }
    }
}
