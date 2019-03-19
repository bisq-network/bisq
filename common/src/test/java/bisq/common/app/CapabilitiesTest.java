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

package bisq.common.app;

import java.util.HashSet;

import org.junit.Test;

import static bisq.common.app.Capability.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CapabilitiesTest {

    @Test
    public void testNoCapabilitiesAvailable() {
        Capabilities DUT = new Capabilities();

        assertTrue(DUT.containsAll(new HashSet<>()));
        assertFalse(DUT.containsAll(new Capabilities(SEED_NODE)));
    }

    @Test
    public void testO() {
        Capabilities DUT = new Capabilities(TRADE_STATISTICS);

        assertTrue(DUT.containsAll(new HashSet<>()));
    }

    @Test
    public void testSingleMatch() {
        Capabilities DUT = new Capabilities(TRADE_STATISTICS);

        // single match
        assertTrue(DUT.containsAll(new Capabilities(TRADE_STATISTICS)));
        assertFalse(DUT.containsAll(new Capabilities(SEED_NODE)));
    }

    @Test
    public void testMultiMatch() {
        Capabilities DUT = new Capabilities(TRADE_STATISTICS, TRADE_STATISTICS_2);

        assertTrue(DUT.containsAll(new Capabilities(TRADE_STATISTICS)));
        assertFalse(DUT.containsAll(new Capabilities(SEED_NODE)));
        assertTrue(DUT.containsAll(new Capabilities(TRADE_STATISTICS, TRADE_STATISTICS_2)));
        assertFalse(DUT.containsAll(new Capabilities(SEED_NODE, TRADE_STATISTICS_2)));
    }
}
