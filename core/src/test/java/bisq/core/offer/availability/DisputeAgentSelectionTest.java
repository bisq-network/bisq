/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
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

import bisq.network.p2p.NodeAddress;

import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DisputeAgentSelectionTest {
    @Test
    void hasAvailableAcceptedDisputeAgentRequiresAddressInFilteredManagerMap() {
        NodeAddress bannedAddress = new NodeAddress("banned-agent:1");
        NodeAddress allowedAddress = new NodeAddress("allowed-agent:1");
        DisputeAgentManager<DisputeAgent> disputeAgentManager = mockDisputeAgentManagerWith(Map.of(allowedAddress, mock(DisputeAgent.class)));

        assertFalse(DisputeAgentSelection.hasAvailableAcceptedDisputeAgent(List.of(bannedAddress), disputeAgentManager));
        assertTrue(DisputeAgentSelection.hasAvailableAcceptedDisputeAgent(List.of(bannedAddress, allowedAddress), disputeAgentManager));
    }

    @Test
    void hasAvailableAcceptedDisputeAgentRejectsMissingAcceptedAddresses() {
        DisputeAgentManager<DisputeAgent> disputeAgentManager = mockDisputeAgentManagerWith(Map.of(new NodeAddress("allowed-agent:1"), mock(DisputeAgent.class)));

        assertFalse(DisputeAgentSelection.hasAvailableAcceptedDisputeAgent(null, disputeAgentManager));
        assertFalse(DisputeAgentSelection.hasAvailableAcceptedDisputeAgent(List.of(), disputeAgentManager));
    }

    @Test
    void getRandomAcceptedMediatorReturnsAgentFromIntersectionOnly() {
        NodeAddress bannedAddress = new NodeAddress("banned-agent:1");
        NodeAddress allowedAddress = new NodeAddress("allowed-agent:1");
        DisputeAgent allowedAgent = mock(DisputeAgent.class);
        DisputeAgentManager<DisputeAgent> disputeAgentManager = mockDisputeAgentManagerWith(Map.of(allowedAddress, allowedAgent));

        DisputeAgent selected = DisputeAgentSelection.getRandomAcceptedMediator(
                List.of(bannedAddress, allowedAddress), disputeAgentManager);

        assertSame(allowedAgent, selected);
    }

    @Test
    void getRandomAcceptedMediatorThrowsWhenNoAcceptedAddressAvailable() {
        NodeAddress bannedAddress = new NodeAddress("banned-agent:1");
        DisputeAgentManager<DisputeAgent> disputeAgentManager = mockDisputeAgentManagerWith(Map.of(new NodeAddress("allowed-agent:1"), mock(DisputeAgent.class)));

        assertThrows(IllegalArgumentException.class,
                () -> DisputeAgentSelection.getRandomAcceptedMediator(List.of(bannedAddress), disputeAgentManager));
        assertThrows(IllegalArgumentException.class,
                () -> DisputeAgentSelection.getRandomAcceptedMediator(null, disputeAgentManager));
        assertThrows(IllegalArgumentException.class,
                () -> DisputeAgentSelection.getRandomAcceptedMediator(List.of(), disputeAgentManager));
    }

    @SuppressWarnings("unchecked")
    private DisputeAgentManager<DisputeAgent> mockDisputeAgentManagerWith(Map<NodeAddress, DisputeAgent> entries) {
        ObservableMap<NodeAddress, DisputeAgent> disputeAgents = FXCollections.observableHashMap();
        disputeAgents.putAll(entries);

        DisputeAgentManager<DisputeAgent> disputeAgentManager = mock(DisputeAgentManager.class);
        when(disputeAgentManager.getObservableMap()).thenReturn(disputeAgents);
        return disputeAgentManager;
    }
}
