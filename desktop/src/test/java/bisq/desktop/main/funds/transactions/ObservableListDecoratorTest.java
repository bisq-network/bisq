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

package bisq.desktop.main.funds.transactions;

import com.google.common.collect.Lists;

import java.util.Collection;
import java.util.function.Supplier;

import org.junit.Test;

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
