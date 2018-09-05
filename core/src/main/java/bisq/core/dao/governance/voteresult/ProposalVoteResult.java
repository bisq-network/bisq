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

import bisq.core.dao.governance.proposal.Proposal;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Value
@Slf4j
public class ProposalVoteResult {
    private final Proposal proposal;
    private final long stakeOfAcceptedVotes;
    private final long stakeOfRejectedVotes;
    private final int numAcceptedVotes;
    private final int numRejectedVotes;
    private final int numIgnoredVotes;

    ProposalVoteResult(Proposal proposal, long stakeOfAcceptedVotes, long stakeOfRejectedVotes,
                       int numAcceptedVotes, int numRejectedVotes, int numIgnoredVotes) {
        this.proposal = proposal;
        this.stakeOfAcceptedVotes = stakeOfAcceptedVotes;
        this.stakeOfRejectedVotes = stakeOfRejectedVotes;
        this.numAcceptedVotes = numAcceptedVotes;
        this.numRejectedVotes = numRejectedVotes;
        this.numIgnoredVotes = numIgnoredVotes;
    }

    public int getNumActiveVotes() {
        return numAcceptedVotes + numRejectedVotes;
    }

    public long getQuorum() {
        // Quorum is sum of all votes independent if accepted or rejected.
        log.info("Quorum: proposalTxId: {}, totalStake: {}, stakeOfAcceptedVotes: {}, stakeOfRejectedVotes: {}",
                proposal.getTxId(), getTotalStake(), stakeOfAcceptedVotes, stakeOfRejectedVotes);
        return getTotalStake();
    }

    private long getTotalStake() {
        return stakeOfAcceptedVotes + stakeOfRejectedVotes;
    }

    public long getThreshold() {
        checkArgument(stakeOfAcceptedVotes >= 0, "stakeOfAcceptedVotes must not be negative");
        checkArgument(stakeOfRejectedVotes >= 0, "stakeOfRejectedVotes must not be negative");
        if (stakeOfAcceptedVotes == 0) {
            return 0;
        }
        return stakeOfAcceptedVotes * 10_000 / getTotalStake();
    }

    @Override
    public String toString() {
        return "ProposalVoteResult{" +
                "\n     proposal=" + proposal +
                ",\n     stakeOfAcceptedVotes=" + stakeOfAcceptedVotes +
                ",\n     stakeOfRejectedVotes=" + stakeOfRejectedVotes +
                ",\n     numAcceptedVotes=" + numAcceptedVotes +
                ",\n     numRejectedVotes=" + numRejectedVotes +
                ",\n     numIgnoredVotes=" + numIgnoredVotes +
                "\n}";
    }
}
