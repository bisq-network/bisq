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

import bisq.network.p2p.NodeAddress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class DisputeAgentSelection {
    public static final int LOOK_BACK_RANGE = 100;

    public static <T extends DisputeAgent> T getRandomMediator(DisputeAgentManager<T> disputeAgentManager) {
        return getRandomDisputeAgent(disputeAgentManager);
    }

    public static <T extends DisputeAgent> T getRandomRefundAgent(DisputeAgentManager<T> disputeAgentManager) {
        return getRandomDisputeAgent(disputeAgentManager);
    }

    public static boolean hasAvailableDisputeAgent(DisputeAgentManager<? extends DisputeAgent> disputeAgentManager) {
        return !disputeAgentManager.getObservableMap().isEmpty();
    }

    public static <T extends DisputeAgent> boolean hasAvailableAcceptedDisputeAgent(
            List<NodeAddress> acceptedNodeAddresses,
            DisputeAgentManager<T> disputeAgentManager) {
        return acceptedNodeAddresses != null &&
                acceptedNodeAddresses.stream().anyMatch(nodeAddress -> disputeAgentManager.getObservableMap().containsKey(nodeAddress));
    }

    public static <T extends DisputeAgent> T getRandomAcceptedMediator(List<NodeAddress> acceptedNodeAddresses,
                                                                       DisputeAgentManager<T> disputeAgentManager) {
        List<T> acceptedDisputeAgents = acceptedNodeAddresses == null ? List.of() :
                acceptedNodeAddresses.stream()
                        .map(disputeAgentManager.getObservableMap()::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(acceptedDisputeAgents);
        Optional<T> optionalDisputeAgent = acceptedDisputeAgents.stream().findFirst();
        checkArgument(optionalDisputeAgent.isPresent(), "No accepted mediator available for offer");
        return optionalDisputeAgent.get();
    }

    private static <T extends DisputeAgent> T getRandomDisputeAgent(DisputeAgentManager<T> disputeAgentManager) {
        List<T> disputeAgents = new ArrayList<>(disputeAgentManager.getObservableMap().values());
        Collections.shuffle(disputeAgents);

        Optional<T> optionalDisputeAgent = disputeAgents.stream().findFirst();
        checkArgument(optionalDisputeAgent.isPresent(), "optionalDisputeAgent has to be present");
        return optionalDisputeAgent.get();
    }
}
