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

import com.google.common.collect.ImmutableSortedSet;

import java.util.Comparator;
import java.util.NavigableSet;
import java.util.stream.IntStream;

import org.jetbrains.annotations.NotNull;

import org.junit.jupiter.api.Test;

import static bisq.common.util.RangeUtils.subSet;
import static com.google.common.collect.Range.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RangeUtilsTest {
    @Test
    public void subSetWithStrictlyIncreasingKey() {
        var subSetWithValue = subSet(range(0, 10)).withKey(n -> n.value);

        assertEquals(range(0, 10), subSetWithValue.overRange(all()));
        assertEquals(range(0, 6), subSetWithValue.overRange(atMost(5)));
        assertEquals(range(5, 10), subSetWithValue.overRange(atLeast(5)));
        assertEquals(range(0, 5), subSetWithValue.overRange(lessThan(5)));
        assertEquals(range(6, 10), subSetWithValue.overRange(greaterThan(5)));
        assertEquals(range(3, 8), subSetWithValue.overRange(closed(3, 7)));
        assertEquals(range(3, 7), subSetWithValue.overRange(closedOpen(3, 7)));
        assertEquals(range(4, 8), subSetWithValue.overRange(openClosed(3, 7)));
        assertEquals(range(4, 7), subSetWithValue.overRange(open(3, 7)));
        assertEquals(range(5, 6), subSetWithValue.overRange(singleton(5)));
        assertEquals(range(0, 1), subSetWithValue.overRange(singleton(0)));
        assertEquals(range(0, 0), subSetWithValue.overRange(singleton(-1)));
        assertEquals(range(9, 10), subSetWithValue.overRange(singleton(9)));
        assertEquals(range(0, 0), subSetWithValue.overRange(singleton(10)));
        assertEquals(range(0, 0), subSetWithValue.overRange(closedOpen(5, 5)));
        assertEquals(range(0, 10), subSetWithValue.overRange(closed(-1, 10)));
    }

    @Test
    public void subSetWithNonStrictlyIncreasingKey() {
        var subSetWithValueDiv3 = subSet(range(0, 10)).withKey(n -> n.value / 3);

        assertEquals(range(0, 10), subSetWithValueDiv3.overRange(closed(0, 3)));
        assertEquals(range(0, 9), subSetWithValueDiv3.overRange(closedOpen(0, 3)));
        assertEquals(range(3, 10), subSetWithValueDiv3.overRange(openClosed(0, 3)));
        assertEquals(range(3, 9), subSetWithValueDiv3.overRange(open(0, 3)));
        assertEquals(range(0, 3), subSetWithValueDiv3.overRange(singleton(0)));
        assertEquals(range(3, 6), subSetWithValueDiv3.overRange(singleton(1)));
        assertEquals(range(9, 10), subSetWithValueDiv3.overRange(singleton(3)));
    }

    private static NavigableSet<TestInteger> range(int startInclusive, int endExclusive) {
        return IntStream.range(startInclusive, endExclusive)
                .mapToObj(TestInteger::new)
                .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));
    }

    private static final class TestInteger implements ComparableExt<TestInteger> {
        int value;

        TestInteger(int value) {
            this.value = value;
        }

        @Override
        public int compareTo(@NotNull ComparableExt<TestInteger> o) {
            return o instanceof TestInteger ? Integer.compare(value, ((TestInteger) o).value) : -o.compareTo(this);
        }

        @Override
        public boolean equals(Object o) {
            return this == o || o instanceof TestInteger && value == ((TestInteger) o).value;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(value);
        }

        @Override
        public String toString() {
            return Integer.toString(value);
        }
    }
}
