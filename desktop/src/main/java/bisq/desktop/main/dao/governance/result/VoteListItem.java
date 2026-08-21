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
import bisq.core.dao.state.model.governance.Ballot;
import bisq.core.dao.state.model.governance.DecryptedBallotsWithMerits;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.util.coin.BsqFormatter;

import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;

import de.jensd.fx.fontawesome.AwesomeIcon;

import java.util.Date;
import java.util.Optional;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VoteListItem {
    @Getter
    private final BsqFormatter bsqFormatter;
    private final DecryptedBallotsWithMerits decryptedBallotsWithMerits;

    private final String proposalTxId;

    private long merit;
    private long stake;
    @Getter
    private String blindVoteTxId = "";
    @Getter
    private String voteRevealTxId = "";
    @Getter
    private Date blindVoteDate;

    VoteListItem(Proposal proposal,
                 DecryptedBallotsWithMerits decryptedBallotsWithMerits,
                 DaoStateService daoStateService,
                 BsqFormatter bsqFormatter) {
        this.decryptedBallotsWithMerits = decryptedBallotsWithMerits;
        this.bsqFormatter = bsqFormatter;

        proposalTxId = proposal.getTxId();

        if (decryptedBallotsWithMerits != null) {
            merit = decryptedBallotsWithMerits.getMerit(daoStateService);
            stake = decryptedBallotsWithMerits.getStake();
            blindVoteTxId = decryptedBallotsWithMerits.getBlindVoteTxId();
            daoStateService.getTx(blindVoteTxId).ifPresent(tx -> blindVoteDate = new Date(tx.getTime()));
            voteRevealTxId = decryptedBallotsWithMerits.getVoteRevealTxId();
        }
    }

    public Tuple2<AwesomeIcon, String> getIconStyleTuple() {
        Optional<Boolean> isAccepted;
        isAccepted = decryptedBallotsWithMerits.getBallotList().stream()
                .filter(ballot -> ballot.getTxId().equals(proposalTxId))
                .map(Ballot::getVote)
                .map(vote -> vote != null && vote.isAccepted())
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
