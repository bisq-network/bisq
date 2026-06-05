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

package bisq.core.support.dispute.agent;

import bisq.network.p2p.P2PService;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

public class DisputeAgentServiceTest {
    @Test
    void getsBannedAgentsEvenWithoutNetworkFilter() {
        TestDisputeAgentService service = new TestDisputeAgentService();

        service.getDisputeAgents();

        assertEquals(List.of("denied.onion:9999"), service.bannedDisputeAgents);
    }

    private static class TestDisputeAgentService extends DisputeAgentService<DisputeAgent> {
        private List<String> bannedDisputeAgents;

        private TestDisputeAgentService() {
            super(mock(P2PService.class));
        }

        @Override
        protected Set<DisputeAgent> getDisputeAgentSet(List<String> bannedDisputeAgents) {
            this.bannedDisputeAgents = bannedDisputeAgents;
            return Set.of();
        }

        @Override
        protected List<String> getDisputeAgentsFromFilter() {
            return List.of("denied.onion:9999");
        }
    }
}
