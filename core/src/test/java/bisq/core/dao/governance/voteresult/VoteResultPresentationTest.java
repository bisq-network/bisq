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

import bisq.core.dao.governance.period.CycleService;
import bisq.core.dao.governance.proposal.ProposalService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.governance.Ballot;
import bisq.core.dao.state.model.governance.BallotList;
import bisq.core.dao.state.model.governance.Cycle;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.dao.state.model.governance.DecryptedBallotsWithMerits;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.dao.state.model.governance.GenericProposal;
import bisq.core.dao.state.model.governance.MeritList;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.ProposalVoteResult;
import bisq.core.dao.state.model.governance.Vote;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VoteResultPresentationTest {
    // VOTE_REVEAL from 106 to 107, RESULT from 109
    private static final Cycle CYCLE = cycle(100);
    private static final Cycle OTHER_CYCLE = cycle(200);
    private static final int FIRST_BLOCK_OF_RESULT_PHASE = 109;

    private final DaoStateService daoStateService = mock(DaoStateService.class);
    private final ProposalService proposalService = mock(ProposalService.class);
    private final CycleService cycleService = mock(CycleService.class);
    private final Proposal votedProposal = proposal("voted");
    private final Proposal proposalWithoutVotes = proposal("withoutVotes");
    private final Proposal proposalOfOtherCycle = proposal("otherCycle");
    private final EvaluatedProposal votedResult = new EvaluatedProposal(true,
            new ProposalVoteResult(votedProposal, 1000, 0, 1, 0, 0));
    private final EvaluatedProposal resultOfOtherCycle = new EvaluatedProposal(true,
            new ProposalVoteResult(proposalOfOtherCycle, 1000, 0, 1, 0, 0));
    private VoteResultPresentation presentation;

    @BeforeEach
    void setUp() {
        Map<String, Cycle> cycleOfTx = Map.of("voted", CYCLE, "withoutVotes", CYCLE, "otherCycle", OTHER_CYCLE,
                "voteReveal", CYCLE, "otherVoteReveal", OTHER_CYCLE);
        when(cycleService.isTxInCycle(any(Cycle.class), anyString()))
                .thenAnswer(invocation -> cycleOfTx.get(invocation.<String>getArgument(1)) == invocation.getArgument(0));
        when(daoStateService.getEvaluatedProposalList()).thenReturn(List.of(votedResult, resultOfOtherCycle));
        when(proposalService.getValidatedProposals())
                .thenReturn(List.of(votedProposal, proposalWithoutVotes, proposalOfOtherCycle));
        presentation = new VoteResultPresentation(daoStateService, proposalService, cycleService);
    }

    // No revealed ballot contained the proposal, so it has no EvaluatedProposal in the DAO state
    @Test
    void withTheVoteResultAProposalWithoutVotesIsShownAsNotAccepted() {
        withComputedVoteResult();
        when(daoStateService.getChainHeight()).thenReturn(FIRST_BLOCK_OF_RESULT_PHASE);

        List<EvaluatedProposal> voteResults = presentation.getVoteResultsForDisplay(CYCLE);

        assertEquals(2, voteResults.size());
        assertSame(votedResult, voteResults.get(0));
        EvaluatedProposal withoutVotes = voteResults.get(1);
        assertSame(proposalWithoutVotes, withoutVotes.getProposal());
        assertFalse(withoutVotes.isAccepted());
        assertEquals(0, withoutVotes.getProposalVoteResult().getNumActiveVotes());
        assertEquals(0, withoutVotes.getProposalVoteResult().getStakeOfAcceptedVotes());
        assertEquals(0, withoutVotes.getProposalVoteResult().getStakeOfRejectedVotes());
        assertEquals(1, withoutVotes.getProposalVoteResult().getNumIgnoredVotes());
    }

    @Test
    void beforeTheResultPhaseNoProposalIsShownWithoutVotes() {
        withComputedVoteResult();
        when(daoStateService.getChainHeight()).thenReturn(FIRST_BLOCK_OF_RESULT_PHASE - 1);

        assertEquals(List.of(votedResult), presentation.getVoteResultsForDisplay(CYCLE));
    }

    // Without a vote reveal there is no vote, so every proposal of the cycle is not accepted
    @Test
    void aCycleWithoutVoteRevealShowsItsProposalsAsNotAccepted() {
        when(daoStateService.getEvaluatedProposalList()).thenReturn(List.of());
        when(daoStateService.getDecryptedBallotsWithMeritsList()).thenReturn(List.of());
        when(daoStateService.getVoteRevealOpReturnTxOutputs()).thenReturn(Set.of());
        when(daoStateService.getChainHeight()).thenReturn(FIRST_BLOCK_OF_RESULT_PHASE);

        List<EvaluatedProposal> voteResults = presentation.getVoteResultsForDisplay(CYCLE);

        assertEquals(2, voteResults.size());
        assertFalse(voteResults.get(0).isAccepted());
        assertFalse(voteResults.get(1).isAccepted());
        assertEquals(0, voteResults.get(0).getProposalVoteResult().getNumIgnoredVotes());
    }

    // The node revealed votes but did not compute the result, for example without the majority's blind votes
    @Test
    void withoutTheVoteResultNoProposalIsShownWithoutVotes() {
        when(daoStateService.getEvaluatedProposalList()).thenReturn(List.of());
        when(daoStateService.getDecryptedBallotsWithMeritsList()).thenReturn(List.of());
        TxOutput voteRevealOutput = voteRevealOutput(106);
        when(daoStateService.getVoteRevealOpReturnTxOutputs()).thenReturn(Set.of(voteRevealOutput));
        when(daoStateService.getChainHeight()).thenReturn(FIRST_BLOCK_OF_RESULT_PHASE);

        assertEquals(List.of(), presentation.getVoteResultsForDisplay(CYCLE));
    }

    // VoteResultService ignores a vote reveal outside the VOTE_REVEAL phase, so the cycle had no vote
    @Test
    void aVoteRevealAfterTheVoteRevealPhaseDoesNotCount() {
        when(daoStateService.getEvaluatedProposalList()).thenReturn(List.of());
        when(daoStateService.getDecryptedBallotsWithMeritsList()).thenReturn(List.of());
        TxOutput voteRevealOutput = voteRevealOutput(108);
        when(daoStateService.getVoteRevealOpReturnTxOutputs()).thenReturn(Set.of(voteRevealOutput));
        when(daoStateService.getChainHeight()).thenReturn(FIRST_BLOCK_OF_RESULT_PHASE);

        assertEquals(2, presentation.getVoteResultsForDisplay(CYCLE).size());
    }

    // If the issuance limit was exceeded, the result contains only the proposals that would have been accepted
    @Test
    void aProposalWithVotesIsNotShownWithoutVotes() {
        when(daoStateService.getEvaluatedProposalList()).thenReturn(List.of());
        DecryptedBallotsWithMerits decryptedBallots = decryptedBallots("voteReveal",
                new Ballot(votedProposal, new Vote(false)));
        when(daoStateService.getDecryptedBallotsWithMeritsList()).thenReturn(List.of(decryptedBallots));
        TxOutput voteRevealOutput = voteRevealOutput(106);
        when(daoStateService.getVoteRevealOpReturnTxOutputs()).thenReturn(Set.of(voteRevealOutput));
        when(daoStateService.getChainHeight()).thenReturn(FIRST_BLOCK_OF_RESULT_PHASE);

        List<EvaluatedProposal> voteResults = presentation.getVoteResultsForDisplay(CYCLE);

        assertEquals(1, voteResults.size());
        assertSame(proposalWithoutVotes, voteResults.get(0).getProposal());
    }

    @Test
    void aProposalWithoutVotesRecordedByAnEarlierVersionIsShownOnce() {
        EvaluatedProposal recordedWithoutVotes = new EvaluatedProposal(false,
                new ProposalVoteResult(proposalWithoutVotes, 0, 0, 0, 0, 1));
        when(daoStateService.getEvaluatedProposalList()).thenReturn(List.of(votedResult, recordedWithoutVotes));
        withComputedVoteResult();
        when(daoStateService.getChainHeight()).thenReturn(FIRST_BLOCK_OF_RESULT_PHASE);

        assertEquals(List.of(votedResult, recordedWithoutVotes), presentation.getVoteResultsForDisplay(CYCLE));
    }

    @Test
    void theVoteResultOfAnotherCycleDoesNotCount() {
        when(daoStateService.getEvaluatedProposalList()).thenReturn(List.of());
        DecryptedBallotsWithMerits decryptedBallots = decryptedBallots("otherVoteReveal");
        when(daoStateService.getDecryptedBallotsWithMeritsList()).thenReturn(List.of(decryptedBallots));
        TxOutput voteRevealOutput = voteRevealOutput(106);
        when(daoStateService.getVoteRevealOpReturnTxOutputs()).thenReturn(Set.of(voteRevealOutput));
        when(daoStateService.getChainHeight()).thenReturn(FIRST_BLOCK_OF_RESULT_PHASE);

        assertEquals(List.of(), presentation.getVoteResultsForDisplay(CYCLE));
    }

    @Test
    void aVoteRevealOfAnotherCycleDoesNotCount() {
        when(daoStateService.getEvaluatedProposalList()).thenReturn(List.of());
        DecryptedBallotsWithMerits decryptedBallots = decryptedBallots("otherVoteReveal");
        when(daoStateService.getDecryptedBallotsWithMeritsList()).thenReturn(List.of(decryptedBallots));
        TxOutput voteRevealOutput = voteRevealOutput(206);
        when(daoStateService.getVoteRevealOpReturnTxOutputs()).thenReturn(Set.of(voteRevealOutput));
        when(daoStateService.getChainHeight()).thenReturn(FIRST_BLOCK_OF_RESULT_PHASE);

        assertEquals(2, presentation.getVoteResultsForDisplay(CYCLE).size());
    }

    private void withComputedVoteResult() {
        DecryptedBallotsWithMerits decryptedBallots = decryptedBallots("voteReveal",
                new Ballot(votedProposal, new Vote(true)));
        when(daoStateService.getDecryptedBallotsWithMeritsList()).thenReturn(List.of(decryptedBallots));
        TxOutput voteRevealOutput = voteRevealOutput(106);
        when(daoStateService.getVoteRevealOpReturnTxOutputs()).thenReturn(Set.of(voteRevealOutput));
    }

    private static DecryptedBallotsWithMerits decryptedBallots(String voteRevealTxId, Ballot... ballots) {
        return new DecryptedBallotsWithMerits(new byte[20], "blindVote", voteRevealTxId, 1000,
                new BallotList(List.of(ballots)), new MeritList(List.of()));
    }

    private static TxOutput voteRevealOutput(int blockHeight) {
        TxOutput txOutput = mock(TxOutput.class);
        when(txOutput.getBlockHeight()).thenReturn(blockHeight);
        return txOutput;
    }

    private static Cycle cycle(int heightOfFirstBlock) {
        return new Cycle(heightOfFirstBlock, ImmutableList.of(new DaoPhase(DaoPhase.Phase.PROPOSAL, 2),
                new DaoPhase(DaoPhase.Phase.BREAK1, 1),
                new DaoPhase(DaoPhase.Phase.BLIND_VOTE, 2),
                new DaoPhase(DaoPhase.Phase.BREAK2, 1),
                new DaoPhase(DaoPhase.Phase.VOTE_REVEAL, 2),
                new DaoPhase(DaoPhase.Phase.BREAK3, 1),
                new DaoPhase(DaoPhase.Phase.RESULT, 2)));
    }

    private static Proposal proposal(String txId) {
        return new GenericProposal("name", "https://bisq.network", null).cloneProposal(txId);
    }
}
