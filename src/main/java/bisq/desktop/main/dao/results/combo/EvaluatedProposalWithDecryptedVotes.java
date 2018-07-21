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

import bisq.core.dao.voting.voteresult.DecryptedVote;
import bisq.core.dao.voting.voteresult.EvaluatedProposal;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

@Data
public class EvaluatedProposalWithDecryptedVotes {
    private final EvaluatedProposal evaluatedProposal;
    private Map<String, DecryptedVote> decryptedVotesByBlindVoteTxId = new HashMap<>();

    public EvaluatedProposalWithDecryptedVotes(EvaluatedProposal evaluatedProposal) {
        this.evaluatedProposal = evaluatedProposal;
    }

    public void addDecryptedVote(DecryptedVote decryptedVote) {
        decryptedVotesByBlindVoteTxId.put(decryptedVote.getBlindVoteTxId(), decryptedVote);
    }
}
