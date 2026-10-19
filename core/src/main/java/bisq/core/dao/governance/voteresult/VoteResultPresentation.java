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
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.governance.Cycle;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.dao.state.model.governance.DecryptedBallotsWithMerits;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.ProposalVoteResult;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The vote results of a cycle for the vote result view and its JSON export. Never part of the DAO state, and not
 * used by the API, which returns the evaluated proposals only.
 * <p>
 * A validated proposal of the cycle that got no accept or reject vote has no EvaluatedProposal (VoteResultService).
 * Its requester still needs to see that it was not accepted, so once the node has the vote result of the cycle it is
 * shown as not accepted with no counted votes.
 */
public class VoteResultPresentation {
    private final DaoStateService daoStateService;
    private final CycleService cycleService;

    @Inject
    public VoteResultPresentation(DaoStateService daoStateService, CycleService cycleService) {
        this.daoStateService = daoStateService;
        this.cycleService = cycleService;
    }

    /**
     * @param validatedProposalsOfCycle the validated proposals of the cycle, which the view has already, so that they
     *                                  are not validated again
     */
    public List<EvaluatedProposal> getVoteResultsForDisplay(Cycle cycle, List<Proposal> validatedProposalsOfCycle) {
        List<EvaluatedProposal> voteResults = daoStateService.getEvaluatedProposalList().stream()
                .filter(evaluatedProposal -> cycleService.isTxInCycle(cycle, evaluatedProposal.getProposalTxId()))
                .collect(Collectors.toCollection(ArrayList::new));
        if (daoStateService.getChainHeight() < cycle.getFirstBlockOfPhase(DaoPhase.Phase.RESULT)) {
            return voteResults;
        }

        // VoteResultService applies the vote result at the first block of the RESULT phase. It stores the decrypted
        // ballots of the cycle if it computed the result, and there is nothing to compute if the cycle had no vote
        // reveal. Without the result, for example if the node did not have the blind votes of the majority, there is
        // no outcome to show.
        List<DecryptedBallotsWithMerits> decryptedVotesForCycle = daoStateService.getDecryptedBallotsWithMeritsList()
                .stream()
                .filter(decryptedBallots -> cycleService.isTxInCycle(cycle, decryptedBallots.getVoteRevealTxId()))
                .collect(Collectors.toList());
        if (decryptedVotesForCycle.isEmpty() && hasVoteReveal(cycle)) {
            return voteResults;
        }

        // Each decrypted ballot list of the cycle ignored the proposal. A proposal with votes has no EvaluatedProposal
        // if the issuance limit was exceeded, as the result then contains only the proposals that would have been
        // accepted. An earlier version may have recorded a proposal without votes.
        Set<String> evaluatedTxIds = voteResults.stream()
                .map(EvaluatedProposal::getProposalTxId)
                .collect(Collectors.toSet());
        validatedProposalsOfCycle.stream()
                .filter(proposal -> !evaluatedTxIds.contains(proposal.getTxId()))
                .filter(proposal -> decryptedVotesForCycle.stream()
                        .noneMatch(decryptedBallots -> decryptedBallots.getVote(proposal.getTxId()).isPresent()))
                .map(proposal -> new EvaluatedProposal(false,
                        new ProposalVoteResult(proposal, 0, 0, 0, 0, decryptedVotesForCycle.size())))
                .forEach(voteResults::add);
        return voteResults;
    }

    private boolean hasVoteReveal(Cycle cycle) {
        return daoStateService.getVoteRevealOpReturnTxOutputs().stream()
                .anyMatch(txOutput -> cycle.isInPhase(txOutput.getBlockHeight(), DaoPhase.Phase.VOTE_REVEAL));
    }
}
