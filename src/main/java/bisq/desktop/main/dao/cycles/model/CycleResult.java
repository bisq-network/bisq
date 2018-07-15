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

package bisq.desktop.main.dao.cycles.model;

import bisq.core.dao.state.period.Cycle;
import bisq.core.dao.voting.proposal.Proposal;
import bisq.core.dao.voting.voteresult.EvaluatedProposal;
import bisq.core.util.BsqFormatter;

import java.util.List;

import lombok.Data;

@Data
public class CycleResult {
    private final Cycle cycle;
    private final int cycleIndex;
    private final int numVotes, numAcceptedVotes, numRejectedVotes;
    private final long stakeOfAcceptedVotes;
    private final long stakeOfRejectedVotes;
    private BsqFormatter bsqFormatter;
    private long cycleStartTime;

    // All available proposals of cycle
    private List<Proposal> proposals;

    // Proposals which ended up in voting
    private final List<EvaluatedProposal> evaluatedProposals;

    public CycleResult(Cycle cycle,
                       int cycleIndex,
                       long cycleStartTime,
                       List<Proposal> proposals,
                       List<EvaluatedProposal> evaluatedProposals) {
        this.cycle = cycle;
        this.cycleIndex = cycleIndex;
        this.cycleStartTime = cycleStartTime;
        this.proposals = proposals;
        this.evaluatedProposals = evaluatedProposals;

        numVotes = evaluatedProposals.stream()
                .mapToInt(e -> e.getProposalVoteResult().getNumActiveVotes())
                .sum();
        numAcceptedVotes = evaluatedProposals.stream()
                .mapToInt(e -> e.getProposalVoteResult().getNumActiveVotes())
                .sum();
        numRejectedVotes = evaluatedProposals.stream()
                .mapToInt(e -> e.getProposalVoteResult().getNumRejectedVotes())
                .sum();
        stakeOfAcceptedVotes = evaluatedProposals.stream()
                .mapToLong(e -> e.getProposalVoteResult().getStakeOfAcceptedVotes())
                .sum();
        stakeOfRejectedVotes = evaluatedProposals.stream()
                .mapToLong(e -> e.getProposalVoteResult().getStakeOfRejectedVotes())
                .sum();
    }

    public long getTotalStake() {
        return stakeOfAcceptedVotes + stakeOfRejectedVotes;
    }
}
