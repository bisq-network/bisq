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

package bisq.desktop.main.dao.proposal.result;

import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.voting.ballot.Ballot;
import bisq.core.dao.voting.ballot.vote.BooleanVote;
import bisq.core.dao.voting.proposal.Proposal;
import bisq.core.dao.voting.voteresult.DecryptedVote;
import bisq.core.util.BsqFormatter;

import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;

import de.jensd.fx.fontawesome.AwesomeIcon;

import java.util.Optional;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VoteListItem {
    @Getter
    private final BsqFormatter bsqFormatter;
    private final DecryptedVote decryptedVote;

    private final String proposalTxId;

    private long merit;
    private long stake;
    @Getter
    private String blindVoteTxId = "";
    @Getter
    private String voteRevealTxId = "";

    public VoteListItem(Proposal proposal,
                        DecryptedVote decryptedVote,
                        BsqStateService bsqStateService,
                        BsqFormatter bsqFormatter) {
        this.decryptedVote = decryptedVote;
        this.bsqFormatter = bsqFormatter;

        proposalTxId = proposal.getTxId();

        if (decryptedVote != null) {
            merit = decryptedVote.getMerit(bsqStateService);
            stake = decryptedVote.getStake();
            blindVoteTxId = decryptedVote.getBlindVoteTxId();
            voteRevealTxId = decryptedVote.getVoteRevealTxId();
        }
    }

    public Tuple2<AwesomeIcon, String> getIconStyleTuple() {
        Optional<Boolean> isAccepted;
        isAccepted = decryptedVote.getBallotList().stream()
                .filter(ballot -> ballot.getProposalTxId().equals(proposalTxId))
                .map(Ballot::getVote)
                .filter(vote -> vote instanceof BooleanVote)
                .map(vote -> (BooleanVote) vote)
                .map(BooleanVote::isAccepted)
                .findAny();
        if (isAccepted.isPresent()) {
            if (isAccepted.get())
                return new Tuple2<>(AwesomeIcon.THUMBS_UP, "dao-accepted-icon");
            else
                return new Tuple2<>(AwesomeIcon.THUMBS_DOWN, "dao-rejected-icon");
        } else {
            return new Tuple2<>(AwesomeIcon.MINUS, "dao-ignored-icon");
        }
    }

    public String getMerit() {
        return bsqFormatter.formatCoinWithCode(Coin.valueOf(merit));
    }

    public String getStake() {
        return bsqFormatter.formatCoinWithCode(Coin.valueOf(stake));
    }

    public String getMeritAndStake() {
        return bsqFormatter.formatCoinWithCode(Coin.valueOf(stake + merit));
    }
}
