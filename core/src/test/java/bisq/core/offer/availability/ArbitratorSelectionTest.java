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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ArbitratorSelectionTest {
    @Test
    public void testGetRandomArbitratorFromZero() {
        testArbitratorSelection(10, new HashSet<>());
    }
    @Test
    public void testGetRandomArbitratorFromOne() {
        testArbitratorSelection(10, new HashSet<>(Arrays.asList("arb1")));
    }
    @Test
    public void testGetRandomArbitratorFromTwo() {
        testArbitratorSelection(10000, new HashSet<>(Arrays.asList("arb1", "arb2")));
    }
    @Test
    public void testGetRandomArbitratorFromThree() {
        testArbitratorSelection(10000, new HashSet<>(Arrays.asList("arb1", "arb2", "arb3")));
    }
    @Test
    public void testGetRandomArbitratorFromFour() {
        testArbitratorSelection(1000, new HashSet<>(Arrays.asList("arb1", "arb2", "arb3", "arb4")));
    }

    private void testArbitratorSelection(int iterations, Set<String> arbitrators) {
        double expectedPercentage = 1.00 / arbitrators.size();
        System.out.printf("%ntestArbitratorSelection with %d arbitrators %d iterations, expected percentage=%f%n",
                arbitrators.size(), iterations, expectedPercentage);
        Map<String, Integer> results = new HashMap<>();
        for (int i=0; i < iterations; i++) {
            String selectedArb = DisputeAgentSelection.getRandomDisputeAgent(arbitrators);
            if (selectedArb != null) {
                results.put(selectedArb, 1 + results.getOrDefault(selectedArb, 0));
            }
        }
        assertEquals(results.size(), arbitrators.size());
        results.forEach((k, v) -> System.out.printf("arb=%s result=%d percentage=%f%n", k, v, (double)v / iterations));
        results.forEach((k, v) -> assertEquals(expectedPercentage, (double)v / iterations, 0.1));
    }
}
