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

@Value
public class EvaluatedProposal {
    private final boolean isAccepted;
    private final ProposalVoteResult proposalVoteResult;
    private final long requiredQuorum;
    private final long requiredThreshold;

    EvaluatedProposal(boolean isAccepted, ProposalVoteResult proposalVoteResult, long requiredQuorum, long requiredThreshold) {
        this.isAccepted = isAccepted;
        this.proposalVoteResult = proposalVoteResult;
        this.requiredQuorum = requiredQuorum;
        this.requiredThreshold = requiredThreshold;
    }

    public Proposal getProposal() {
        return proposalVoteResult.getProposal();
    }

    public String getProposalTxId() {
        return getProposal().getTxId();
    }

    @Override
    public String toString() {
        return "EvaluatedProposal{" +
                "\n     isAccepted=" + isAccepted +
                ",\n     proposalVoteResult=" + proposalVoteResult +
                ",\n     requiredQuorum=" + requiredQuorum +
                ",\n     requiredThreshold=" + requiredThreshold +
                "\n}";
    }
}
