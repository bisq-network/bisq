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

import bisq.core.support.dispute.agent.DisputeAgent;
import bisq.core.support.dispute.agent.DisputeAgentManager;
import bisq.core.support.dispute.mediation.mediator.Mediator;
import bisq.core.support.dispute.mediation.mediator.MediatorManager;

import bisq.network.p2p.NodeAddress;

import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ArbitratorSelectionTest {
    @Test
    public void testGetRandomArbitratorFromOne() {
        MediatorManager mediatorManager = createFakeMediatorManagerWith(
                createFakeMediatorWithId(1)
        );
        testArbitratorSelection(10, mediatorManager);
    }

    @Test
    public void testGetRandomArbitratorFromTwo() {
        MediatorManager mediatorManager = createFakeMediatorManagerWith(
                createFakeMediatorWithId(1),
                createFakeMediatorWithId(2)
        );
        testArbitratorSelection(10000, mediatorManager);
    }

    @Test
    public void testGetRandomArbitratorFromThree() {
        MediatorManager mediatorManager = createFakeMediatorManagerWith(
                createFakeMediatorWithId(1),
                createFakeMediatorWithId(2),
                createFakeMediatorWithId(3)
        );
        testArbitratorSelection(10000, mediatorManager);
    }
    @Test
    public void testGetRandomArbitratorFromFour() {
        MediatorManager mediatorManager = createFakeMediatorManagerWith(
                createFakeMediatorWithId(1),
                createFakeMediatorWithId(2),
                createFakeMediatorWithId(3),
                createFakeMediatorWithId(4)
        );
        testArbitratorSelection(1000, mediatorManager);
    }

    private MediatorManager createFakeMediatorManagerWith(Mediator... mediators) {
        ObservableMap<NodeAddress, Mediator> observableMap = FXCollections.observableHashMap();
        Arrays.stream(mediators)
                .forEach(mediator -> observableMap.put(mediator.getNodeAddress(), mediator));

        MediatorManager mediatorManager = mock(MediatorManager.class);
        when(mediatorManager.getObservableMap()).thenReturn(observableMap);
        return mediatorManager;
    }

    private Mediator createFakeMediatorWithId(int id) {
        Mediator mediator = mock(Mediator.class);
        when(mediator.getNodeAddress()).thenReturn(new NodeAddress("127.0.0.1", id));
        return mediator;
    }

    private <T extends DisputeAgent> void testArbitratorSelection(int iterations,
                                                                  DisputeAgentManager<T> disputeAgentManager) {
        int numberOfDisputeAgents = disputeAgentManager.getObservableMap().size();
        double expectedPercentage = 1.00 / numberOfDisputeAgents;
        System.out.printf("%ntestArbitratorSelection with %d arbitrators %d iterations, expected percentage=%f%n",
                numberOfDisputeAgents, iterations, expectedPercentage);

        Map<NodeAddress, Integer> results = new HashMap<>();
        for (int i = 0; i < iterations; i++) {
            T selectedArb = DisputeAgentSelection.getRandomMediator(disputeAgentManager);
            NodeAddress selectedArbNodeAddress = selectedArb.getNodeAddress();
            results.put(selectedArbNodeAddress, 1 + results.getOrDefault(selectedArbNodeAddress, 0));
        }

        assertEquals(results.size(), numberOfDisputeAgents);
        results.forEach((k, v) -> System.out.printf("arb=%s result=%d percentage=%f%n", k, v, (double) v / iterations));
        results.forEach((k, v) -> assertEquals(expectedPercentage, (double) v / iterations, 0.1));
    }
}
