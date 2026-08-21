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

package bisq.core.support.dispute.agent;

import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;

import bisq.common.crypto.PubKeyRing;

import java.security.PublicKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DisputeAgentManagerTest {
    private static final byte[] REGISTRATION_PUB_KEY = new byte[]{1};
    private static final String REGISTRATION_PUB_KEY_HEX = "01";

    @Test
    void updateMapClearsAcceptedAgentsBeforeAddingFilteredAgents() {
        DisputeAgent firstAgent = mockDisputeAgent("agent-one:1");
        DisputeAgent secondAgent = mockDisputeAgent("agent-two:1");
        NodeAddress firstAgentAddress = firstAgent.getNodeAddress();
        NodeAddress secondAgentAddress = secondAgent.getNodeAddress();
        DisputeAgentService<DisputeAgent> disputeAgentService = mockDisputeAgentService();
        TestDisputeAgentManager manager = new TestDisputeAgentManager(disputeAgentService);

        when(disputeAgentService.getDisputeAgents()).thenReturn(Map.of(firstAgentAddress, firstAgent));
        manager.updateMap();

        assertThat(manager.getAcceptedDisputeAgents(), contains(firstAgent));

        when(disputeAgentService.getDisputeAgents()).thenReturn(Map.of(secondAgentAddress, secondAgent));
        manager.updateMap();

        assertThat(manager.getAcceptedDisputeAgents(), contains(secondAgent));
    }

    private DisputeAgent mockDisputeAgent(String fullAddress) {
        PubKeyRing pubKeyRing = mock(PubKeyRing.class);
        PublicKey publicKey = mock(PublicKey.class);
        when(pubKeyRing.getSignaturePubKey()).thenReturn(publicKey);

        DisputeAgent disputeAgent = mock(DisputeAgent.class);
        when(disputeAgent.getNodeAddress()).thenReturn(new NodeAddress(fullAddress));
        when(disputeAgent.getRegistrationPubKey()).thenReturn(REGISTRATION_PUB_KEY);
        when(disputeAgent.getPubKeyRing()).thenReturn(pubKeyRing);
        return disputeAgent;
    }

    @SuppressWarnings("unchecked")
    private DisputeAgentService<DisputeAgent> mockDisputeAgentService() {
        return mock(DisputeAgentService.class);
    }

    private static final class TestDisputeAgentManager extends DisputeAgentManager<DisputeAgent> {
        private final List<DisputeAgent> acceptedDisputeAgents = new ArrayList<>();

        private TestDisputeAgentManager(DisputeAgentService<DisputeAgent> disputeAgentService) {
            super(null, disputeAgentService, mock(User.class), null, false);
        }

        private List<DisputeAgent> getAcceptedDisputeAgents() {
            return acceptedDisputeAgents;
        }

        @Override
        protected List<String> getPubKeyList() {
            return List.of(REGISTRATION_PUB_KEY_HEX);
        }

        @Override
        protected boolean isExpectedInstance(ProtectedStorageEntry data) {
            return true;
        }

        @Override
        protected void addAcceptedDisputeAgentToUser(DisputeAgent disputeAgent) {
            acceptedDisputeAgents.add(disputeAgent);
        }

        @Override
        protected DisputeAgent getRegisteredDisputeAgentFromUser() {
            return null;
        }

        @Override
        protected void clearAcceptedDisputeAgentsAtUser() {
            acceptedDisputeAgents.clear();
        }

        @Override
        protected List<DisputeAgent> getAcceptedDisputeAgentsFromUser() {
            return acceptedDisputeAgents;
        }

        @Override
        protected void removeAcceptedDisputeAgentFromUser(ProtectedStorageEntry data) {
        }

        @Override
        protected void setRegisteredDisputeAgentAtUser(DisputeAgent disputeAgent) {
        }

        @Override
        protected boolean verifySignature(PublicKey storageSignaturePubKey, byte[] registrationPubKey, String signature) {
            return true;
        }
    }
}
