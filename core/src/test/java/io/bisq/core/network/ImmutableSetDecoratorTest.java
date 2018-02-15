package io.bisq.core.network;

import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.Set;

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
