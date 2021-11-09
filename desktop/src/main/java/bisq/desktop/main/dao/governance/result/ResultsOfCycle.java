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

package bisq.desktop.main.dao.governance.result;

import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.governance.Cycle;
import bisq.core.dao.state.model.governance.DecryptedBallotsWithMerits;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.dao.state.model.governance.Proposal;

import java.util.List;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
class ResultsOfCycle {
    private final Cycle cycle;
    private final int cycleIndex;
    private final int numVotes;
    private final int numAcceptedVotes;
    private final int numRejectedVotes;
    private final long meritAndStake;
    private final DaoStateService daoStateService;
    private long cycleStartTime;

    // All available proposals of cycle
    private final List<Proposal> proposals;

    // Proposals which ended up in voting
    private final List<EvaluatedProposal> evaluatedProposals;

    private final List<DecryptedBallotsWithMerits> decryptedVotesForCycle;

    ResultsOfCycle(Cycle cycle,
                   int cycleIndex,
                   long cycleStartTime,
                   List<Proposal> proposals,
                   List<EvaluatedProposal> evaluatedProposals,
                   List<DecryptedBallotsWithMerits> decryptedVotesForCycle,
                   long meritAndStake,
                   DaoStateService daoStateService) {
        this.cycle = cycle;
        this.cycleIndex = cycleIndex;
        this.cycleStartTime = cycleStartTime;
        this.proposals = proposals;
        this.evaluatedProposals = evaluatedProposals;
        this.decryptedVotesForCycle = decryptedVotesForCycle;

        numVotes = evaluatedProposals.stream()
                .mapToInt(e -> e.getProposalVoteResult().getNumActiveVotes())
                .sum();
        numAcceptedVotes = evaluatedProposals.stream()
                .mapToInt(e -> e.getProposalVoteResult().getNumActiveVotes())
                .sum();
        numRejectedVotes = evaluatedProposals.stream()
                .mapToInt(e -> e.getProposalVoteResult().getNumRejectedVotes())
                .sum();
        this.meritAndStake = meritAndStake;
        this.daoStateService = daoStateService;
    }

    public long getCycleStartTime() {
        // At a new cycle we have cycleStartTime 0 as the block is not processed yet.
        // To display a correct value we access again from the daoStateService
        if (cycleStartTime == 0)
            cycleStartTime = daoStateService.getBlockTimeAtBlockHeight(cycle.getHeightOfFirstBlock());
        return cycleStartTime;
    }
}
