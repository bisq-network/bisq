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

package bisq.desktop.main.dao.monitor.daostate;

import bisq.core.dao.monitoring.model.DaoStateBlock;
import bisq.core.dao.monitoring.model.DaoStateHash;
import bisq.core.locale.Res;

import java.util.Locale;
import java.util.function.IntSupplier;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class DaoStateBlockListItemTest {

    @Before
    public void setup() {
        Locale.setDefault(new Locale("en", "US"));
        Res.setBaseCurrencyCode("BTC");
        Res.setBaseCurrencyName("Bitcoin");
    }

    @Test
    public void testEqualsAndHashCode() {
        var block = new DaoStateBlock(new DaoStateHash(0, new byte[0], new byte[0]));
        var item1 = new DaoStateBlockListItem(block, newSupplier(1));
        var item2 = new DaoStateBlockListItem(block, newSupplier(2));
        var item3 = new DaoStateBlockListItem(block, newSupplier(1));
        assertNotEquals(item1, item2);
        assertNotEquals(item2, item3);
        assertEquals(item1, item3);
        assertEquals(item1.hashCode(), item3.hashCode());
    }

    private IntSupplier newSupplier(int i) {
        //noinspection Convert2Lambda
        return new IntSupplier() {
            @Override
            public int getAsInt() {
                return i;
            }
        };
    }
}
