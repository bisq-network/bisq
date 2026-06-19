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

package bisq.core.dao.governance.voteresult;

import bisq.core.dao.governance.proposal.IssuanceProposal;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.ProposalVoteResult;

import org.bitcoinj.core.Coin;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class VoteResultServiceTest {
    private static final int PRE_ACTIVATION_HEIGHT = 954_199;
    private static final int ACTIVATION_HEIGHT = 954_200;

    @Test
    void getCombinedStakeAddsStakeAndMerit() {
        assertEquals(15, VoteResultService.getCombinedStake(10, 5, ACTIVATION_HEIGHT));
    }

    @Test
    void getCombinedStakeUsesLegacyOverflowBeforeActivation() {
        assertEquals(Long.MIN_VALUE, VoteResultService.getCombinedStake(Long.MAX_VALUE, 1, PRE_ACTIVATION_HEIGHT));
    }

    @Test
    void getCombinedStakeThrowsOnOverflowAtActivation() {
        assertThrows(ArithmeticException.class,
                () -> VoteResultService.getCombinedStake(Long.MAX_VALUE, 1, ACTIVATION_HEIGHT));
    }

    @Test
    void addVoteStakeUsesLegacyOverflowBeforeActivation() {
        assertEquals(Long.MIN_VALUE, VoteResultService.addVoteStake(Long.MAX_VALUE, 1, PRE_ACTIVATION_HEIGHT));
    }

    @Test
    void addVoteStakeThrowsOnOverflowAtActivation() {
        assertThrows(ArithmeticException.class,
                () -> VoteResultService.addVoteStake(Long.MAX_VALUE, 1, ACTIVATION_HEIGHT));
    }

    @Test
    void addBlindVoteListStakeUsesLegacyOverflowBeforeActivation() {
        assertEquals(Long.MIN_VALUE, VoteResultService.addBlindVoteListStake(Long.MAX_VALUE,
                1,
                PRE_ACTIVATION_HEIGHT));
    }

    @Test
    void addBlindVoteListStakeThrowsOnOverflowAtActivation() {
        assertThrows(ArithmeticException.class,
                () -> VoteResultService.addBlindVoteListStake(Long.MAX_VALUE, 1, ACTIVATION_HEIGHT));
    }

    @Test
    void getSumIssuanceUsesLegacyOverflowBeforeActivation() {
        Set<EvaluatedProposal> evaluatedProposals = Set.of(
                acceptedIssuanceProposal(Long.MAX_VALUE),
                acceptedIssuanceProposal(1));

        assertEquals(Long.MIN_VALUE, VoteResultService.getSumIssuance(evaluatedProposals, PRE_ACTIVATION_HEIGHT));
    }

    @Test
    void getSumIssuanceThrowsOnOverflowAtActivation() {
        Set<EvaluatedProposal> evaluatedProposals = Set.of(
                acceptedIssuanceProposal(Long.MAX_VALUE),
                acceptedIssuanceProposal(1));

        assertThrows(ArithmeticException.class,
                () -> VoteResultService.getSumIssuance(evaluatedProposals, ACTIVATION_HEIGHT));
    }

    private static EvaluatedProposal acceptedIssuanceProposal(long requestedBsq) {
        Proposal proposal = mock(Proposal.class, withSettings().extraInterfaces(IssuanceProposal.class));
        when(((IssuanceProposal) proposal).getRequestedBsq()).thenReturn(Coin.valueOf(requestedBsq));
        return new EvaluatedProposal(true, new ProposalVoteResult(proposal, 0, 0, 0, 0, 0));
    }
}
