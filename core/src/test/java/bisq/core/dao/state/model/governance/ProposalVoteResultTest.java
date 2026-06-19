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

package bisq.core.dao.state.model.governance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProposalVoteResultTest {
    private static final int PRE_ACTIVATION_HEIGHT = 954_199;
    private static final int ACTIVATION_HEIGHT = 954_200;

    @Test
    void getThresholdReturnsAcceptedStakeRatio() {
        ProposalVoteResult proposalVoteResult = new ProposalVoteResult(createProposal(), 70, 30, 1, 1, 0);

        assertEquals(7_000, proposalVoteResult.getThreshold(ACTIVATION_HEIGHT));
    }

    @Test
    void getQuorumUsesLegacyOverflowBeforeActivation() {
        ProposalVoteResult proposalVoteResult = new ProposalVoteResult(createProposal(), Long.MAX_VALUE, 1, 1, 1, 0);

        assertEquals(Long.MIN_VALUE, proposalVoteResult.getQuorum(PRE_ACTIVATION_HEIGHT));
    }

    @Test
    void getQuorumThrowsOnOverflowAtActivation() {
        ProposalVoteResult proposalVoteResult = new ProposalVoteResult(createProposal(), Long.MAX_VALUE, 1, 1, 1, 0);

        assertThrows(ArithmeticException.class, () -> proposalVoteResult.getQuorum(ACTIVATION_HEIGHT));
    }

    @Test
    void getThresholdUsesLegacyIntermediateOverflowBeforeActivation() {
        long acceptedStake = Long.MAX_VALUE / 10_000 + 1;
        ProposalVoteResult proposalVoteResult = new ProposalVoteResult(createProposal(), acceptedStake, 1, 1, 1, 0);

        assertEquals(-9_999, proposalVoteResult.getThreshold(PRE_ACTIVATION_HEIGHT));
    }

    @Test
    void getThresholdKeepsOverflowingIntermediateExactAtActivation() {
        long acceptedStake = Long.MAX_VALUE / 10_000 + 1;
        ProposalVoteResult proposalVoteResult = new ProposalVoteResult(createProposal(), acceptedStake, 1, 1, 1, 0);

        assertEquals(9_999, proposalVoteResult.getThreshold(ACTIVATION_HEIGHT));
    }

    @Test
    void getThresholdThrowsOnTotalStakeOverflowAtActivation() {
        ProposalVoteResult proposalVoteResult = new ProposalVoteResult(createProposal(), Long.MAX_VALUE, 1, 1, 1, 0);

        assertThrows(ArithmeticException.class, () -> proposalVoteResult.getThreshold(ACTIVATION_HEIGHT));
    }

    private static Proposal createProposal() {
        Proposal proposal = mock(Proposal.class);
        when(proposal.getTxId()).thenReturn("proposalTxId");
        return proposal;
    }
}
