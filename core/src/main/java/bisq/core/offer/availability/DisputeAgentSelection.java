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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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

    private static <T extends DisputeAgent> T getRandomDisputeAgent(DisputeAgentManager<T> disputeAgentManager) {
        List<T> disputeAgents = new ArrayList<>(disputeAgentManager.getObservableMap().values());
        Collections.shuffle(disputeAgents);

        Optional<T> optionalDisputeAgent = disputeAgents.stream().findFirst();
        checkArgument(optionalDisputeAgent.isPresent(), "optionalDisputeAgent has to be present");
        return optionalDisputeAgent.get();
    }
}
