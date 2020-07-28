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

package bisq.core.util;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.param.Param;
import bisq.core.filter.Filter;
import bisq.core.filter.FilterManager;

import com.google.common.primitives.Longs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class FeeReceiverSelectorTest {
    @Mock
    private DaoFacade daoFacade;
    @Mock
    private FilterManager filterManager;

    @Test
    public void testGetAddress() {
        Random rnd = new Random(123);
        when(filterManager.getFilter()).thenReturn(filterWithReceivers(
                List.of("", "foo#0.001", "ill-formed", "bar#0.002", "baz#0.001", "partial#bad")));

        Map<String, Integer> selectionCounts = new HashMap<>();
        for (int i = 0; i < 400; i++) {
            String address = FeeReceiverSelector.getAddress(daoFacade, filterManager, rnd);
            selectionCounts.compute(address, (k, n) -> n != null ? n + 1 : 1);
        }

        assertEquals(3, selectionCounts.size());

        // Check within 2 std. of the expected values (95% confidence each):
        assertEquals(100.0, selectionCounts.get("foo"), 18);
        assertEquals(200.0, selectionCounts.get("bar"), 20);
        assertEquals(100.0, selectionCounts.get("baz"), 18);
    }

    @Test
    public void testGetAddress_noValidReceivers() {
        when(daoFacade.getParamValue(Param.RECIPIENT_BTC_ADDRESS)).thenReturn("default");

        when(filterManager.getFilter()).thenReturn(null);
        assertEquals("default", FeeReceiverSelector.getAddress(daoFacade, filterManager));

        when(filterManager.getFilter()).thenReturn(filterWithReceivers(null));
        assertEquals("default", FeeReceiverSelector.getAddress(daoFacade, filterManager));

        when(filterManager.getFilter()).thenReturn(filterWithReceivers(List.of()));
        assertEquals("default", FeeReceiverSelector.getAddress(daoFacade, filterManager));

        when(filterManager.getFilter()).thenReturn(filterWithReceivers(List.of("ill-formed")));
        assertEquals("default", FeeReceiverSelector.getAddress(daoFacade, filterManager));
    }

    @Test
    public void testWeightedSelection() {
        Random rnd = new Random(456);

        int[] selections = new int[3];
        for (int i = 0; i < 6000; i++) {
            selections[FeeReceiverSelector.weightedSelection(Longs.asList(1, 2, 3), rnd)]++;
        }

        // Check within 2 std. of the expected values (95% confidence each):
        assertEquals(1000.0, selections[0], 58);
        assertEquals(2000.0, selections[1], 74);
        assertEquals(3000.0, selections[2], 78);
    }

    private static Filter filterWithReceivers(List<String> btcFeeReceiverAddresses) {
        return new Filter(null, null, null, null,
                null, null, null, null,
                false, null, false, null,
                null, null, null, null,
                btcFeeReceiverAddresses);
    }
}
