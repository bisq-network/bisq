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

package bisq.core.network.p2p.seed;

import com.google.common.collect.Sets;

import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ImmutableSetDecoratorTest {
    @Test(expected = UnsupportedOperationException.class)
    public void testAdd() {
        Set<Integer> original = Sets.newHashSet(1, 2, 3);
        Set<Integer> decorator = new ImmutableSetDecorator<>(original);
        decorator.add(4);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testRemove() {
        Set<Integer> original = Sets.newHashSet(1, 2, 3);
        Set<Integer> decorator = new ImmutableSetDecorator<>(original);
        decorator.remove(3);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testClear() {
        Set<Integer> original = Sets.newHashSet(1, 2, 3);
        Set<Integer> decorator = new ImmutableSetDecorator<>(original);
        decorator.clear();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testRemoveWithIterator() {
        Set<Integer> original = Sets.newHashSet(1, 2, 3);
        Set<Integer> decorator = new ImmutableSetDecorator<>(original);
        decorator.iterator().remove();
    }

    @Test
    public void testBackingCollection() {
        Set<Integer> original = Sets.newHashSet(1, 2, 3);
        Set<Integer> decorator = new ImmutableSetDecorator<>(original);

        original.remove(2);
        assertTrue(decorator.contains(2));
    }
}
