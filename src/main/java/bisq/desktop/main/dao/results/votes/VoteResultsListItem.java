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

package bisq.desktop.main.dao.results.votes;

import bisq.desktop.main.dao.results.BaseResultsListItem;

import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.voting.ballot.vote.BooleanVote;
import bisq.core.dao.voting.ballot.vote.LongVote;
import bisq.core.dao.voting.proposal.Proposal;
import bisq.core.dao.voting.voteresult.DecryptedVote;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import org.bitcoinj.core.Coin;

import com.google.common.base.Joiner;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Getter;

public class VoteResultsListItem extends BaseResultsListItem {
    private final BsqStateService bsqStateService;
    private final BsqFormatter bsqFormatter;
    private int id;
    @Getter
    private final DecryptedVote decryptedVote;

    public VoteResultsListItem(int id, DecryptedVote decryptedVote, BsqStateService bsqStateService, BsqFormatter bsqFormatter) {
        this.id = id;
        this.decryptedVote = decryptedVote;
        this.bsqStateService = bsqStateService;
        this.bsqFormatter = bsqFormatter;
    }

    public String getBlindVoteTxId() {
        return decryptedVote.getBlindVoteTxId();
    }

    public String getVoteRevealTxId() {
        return decryptedVote.getVoteRevealTxId();
    }

    public String getStake() {
        return bsqFormatter.formatCoinWithCode(getStakeAsCoin());
    }

    public String getStakeAndMerit() {
        return bsqFormatter.formatCoinWithCode(getStakeAndMeritAsCoin());
    }

    public Coin getStakeAndMeritAsCoin() {
        return getMeritAsCoin().add(getStakeAsCoin());
    }


    public Coin getStakeAsCoin() {
        return Coin.valueOf(decryptedVote.getStake());
    }

    public String getMerit() {
        return bsqFormatter.formatCoinWithCode(getMeritAsCoin());
    }

    public Coin getMeritAsCoin() {
        return Coin.valueOf(decryptedVote.getMerit(bsqStateService));
    }

    public String getNumAcceptedVotes() {
        return String.valueOf(getBooleanVoteStream()
                .filter(BooleanVote::isAccepted)
                .collect(Collectors.toList())
                .size());
    }

    public String getNumRejectedVotes() {
        return String.valueOf(getBooleanVoteStream()
                .filter(booleanVote -> !booleanVote.isAccepted())
                .collect(Collectors.toList())
                .size());
    }

    private Stream<BooleanVote> getBooleanVoteStream() {
        return decryptedVote.getBallotList().getList().stream()
                .filter(ballot -> ballot.getVote() instanceof BooleanVote)
                .map(ballot -> (BooleanVote) ballot.getVote());
    }

    public String getBallotList() {
        return Joiner.on(", ").join(decryptedVote.getBallotList().getList().stream()
                .map(ballot -> {
                    Proposal proposal = ballot.getProposal();
                    String proposalUid = proposal.getShortId();
                    if (ballot.getVote() instanceof BooleanVote)
                        return proposalUid + ": " + ((BooleanVote) ballot.getVote()).isAccepted();
                    else if (ballot.getVote() instanceof LongVote)
                        return proposalUid + ": " + ((LongVote) ballot.getVote()).getValue();
                    else
                        return proposalUid;
                })
                .collect(Collectors.toList()));
    }

    public String getId() {
        return Res.get("dao.results.votes.table.cell.id", id);
    }
}
