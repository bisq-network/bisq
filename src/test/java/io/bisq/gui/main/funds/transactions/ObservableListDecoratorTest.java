package io.bisq.gui.main.funds.transactions;

import com.google.common.collect.Lists;
import org.junit.Test;

import java.util.Collection;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ObservableListDecoratorTest {
    @Test
    public void testSetAll() {
        ObservableListDecorator<Integer> list = new ObservableListDecorator<>();
        Collection<Integer> state = Lists.newArrayList(3, 2, 1);
        list.setAll(state);
        assertEquals(state, list);

        state = Lists.newArrayList(0, 0, 0, 0);
        list.setAll(state);
        assertEquals(state, list);
    }

    @Test
    public void testForEach() {
        ObservableListDecorator<Supplier> list = new ObservableListDecorator<>();
        Collection<Supplier> state = Lists.newArrayList(mock(Supplier.class), mock(Supplier.class));
        list.setAll(state);
        assertEquals(state, list);

        list.forEach(Supplier::get);

        state.forEach(supplier -> verify(supplier).get());
    }
}
