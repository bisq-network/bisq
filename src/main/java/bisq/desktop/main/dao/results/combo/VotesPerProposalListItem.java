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

package bisq.desktop.main.dao.results.combo;

import bisq.core.dao.voting.ballot.Ballot;
import bisq.core.dao.voting.ballot.vote.BooleanVote;
import bisq.core.dao.voting.proposal.Proposal;
import bisq.core.dao.voting.voteresult.DecryptedVote;

import bisq.common.util.Tuple2;

import de.jensd.fx.fontawesome.AwesomeIcon;

import java.util.Map;
import java.util.Optional;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VotesPerProposalListItem {
    @Getter
    private final EvaluatedProposalWithDecryptedVotes evaluatedProposalWithDecryptedVotes;
    @Getter
    private final Proposal proposal;
    private final String proposalTxId;

    public VotesPerProposalListItem(EvaluatedProposalWithDecryptedVotes evaluatedProposalWithDecryptedVotes) {
        this.evaluatedProposalWithDecryptedVotes = evaluatedProposalWithDecryptedVotes;
        proposal = evaluatedProposalWithDecryptedVotes.getEvaluatedProposal().getProposal();
        proposalTxId = proposal.getTxId();
    }

    public String getProposalInfo() {
        return proposal.getName();
    }

    public Tuple2<AwesomeIcon, String> getIconStyleTuple(String blindVoteTxId) {
        Optional<Boolean> isAccepted = Optional.empty();
        Map<String, DecryptedVote> map = evaluatedProposalWithDecryptedVotes.getDecryptedVotesByBlindVoteTxId();
        if (map.containsKey(blindVoteTxId)) {
            DecryptedVote decryptedVote = map.get(blindVoteTxId);
            isAccepted = decryptedVote.getBallotList().stream()
                    .filter(ballot -> ballot.getProposalTxId().equals(proposalTxId))
                    .map(Ballot::getVote)
                    .filter(vote -> vote instanceof BooleanVote)
                    .map(vote -> (BooleanVote) vote)
                    .map(BooleanVote::isAccepted)
                    .findAny();
        }
        if (isAccepted.isPresent()) {
            if (isAccepted.get())
                return new Tuple2<>(AwesomeIcon.OK_SIGN, "dao-accepted-icon");
            else
                return new Tuple2<>(AwesomeIcon.REMOVE_SIGN, "dao-rejected-icon");
        } else {
            return new Tuple2<>(AwesomeIcon.MINUS_SIGN, "dao-ignored-icon");
        }
    }
}
