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

package bisq.core.offer.availability;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ArbitratorSelectionTest {
    @Test
    public void testGetLeastUsedArbitrator() {
        // We get least used selected
        List<String> lastAddressesUsedInTrades;
        Set<String> arbitrators;
        String result;

        lastAddressesUsedInTrades = Arrays.asList("arb1", "arb2", "arb1");
        arbitrators = new HashSet<>(Arrays.asList("arb1", "arb2"));
        result = DisputeAgentSelection.getLeastUsedDisputeAgent(lastAddressesUsedInTrades, arbitrators);
        assertEquals("arb2", result);

        // if all are same we use first according to alphanumeric sorting
        lastAddressesUsedInTrades = Arrays.asList("arb1", "arb2", "arb3");
        arbitrators = new HashSet<>(Arrays.asList("arb1", "arb2", "arb3"));
        result = DisputeAgentSelection.getLeastUsedDisputeAgent(lastAddressesUsedInTrades, arbitrators);
        assertEquals("arb1", result);

        lastAddressesUsedInTrades = Arrays.asList("arb1", "arb2", "arb3", "arb1");
        arbitrators = new HashSet<>(Arrays.asList("arb1", "arb2", "arb3"));
        result = DisputeAgentSelection.getLeastUsedDisputeAgent(lastAddressesUsedInTrades, arbitrators);
        assertEquals("arb2", result);

        lastAddressesUsedInTrades = Arrays.asList("arb1", "arb2", "arb3", "arb1", "arb2");
        arbitrators = new HashSet<>(Arrays.asList("arb1", "arb2", "arb3"));
        result = DisputeAgentSelection.getLeastUsedDisputeAgent(lastAddressesUsedInTrades, arbitrators);
        assertEquals("arb3", result);

        lastAddressesUsedInTrades = Arrays.asList("xxx", "ccc", "aaa");
        arbitrators = new HashSet<>(Arrays.asList("aaa", "ccc", "xxx"));
        result = DisputeAgentSelection.getLeastUsedDisputeAgent(lastAddressesUsedInTrades, arbitrators);
        assertEquals("aaa", result);
        lastAddressesUsedInTrades = Arrays.asList("333", "000", "111");
        arbitrators = new HashSet<>(Arrays.asList("111", "333", "000"));
        result = DisputeAgentSelection.getLeastUsedDisputeAgent(lastAddressesUsedInTrades, arbitrators);
        assertEquals("000", result);

        // if winner is not in our arb list we use our arb from arbitrators even if never used in trades
        lastAddressesUsedInTrades = Arrays.asList("arb1", "arb2", "arb3");
        arbitrators = new HashSet<>(Arrays.asList("arb4"));
        result = DisputeAgentSelection.getLeastUsedDisputeAgent(lastAddressesUsedInTrades, arbitrators);
        assertEquals("arb4", result);

        // if winner (arb2) is not in our arb list we use our arb from arbitrators
        lastAddressesUsedInTrades = Arrays.asList("arb1", "arb1", "arb1", "arb2");
        arbitrators = new HashSet<>(Arrays.asList("arb1"));
        result = DisputeAgentSelection.getLeastUsedDisputeAgent(lastAddressesUsedInTrades, arbitrators);
        assertEquals("arb1", result);

        // arb1 is used least
        lastAddressesUsedInTrades = Arrays.asList("arb1", "arb2", "arb2", "arb2", "arb1", "arb1", "arb2");
        arbitrators = new HashSet<>(Arrays.asList("arb1", "arb2"));
        result = DisputeAgentSelection.getLeastUsedDisputeAgent(lastAddressesUsedInTrades, arbitrators);
        assertEquals("arb1", result);
    }
}
