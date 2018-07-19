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

package bisq.desktop.main.dao.results.proposals;

import bisq.core.dao.voting.proposal.compensation.CompensationProposal;
import bisq.core.dao.voting.voteresult.EvaluatedProposal;
import bisq.core.dao.voting.voteresult.ProposalVoteResult;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import org.bitcoinj.core.Coin;

import de.jensd.fx.fontawesome.AwesomeIcon;

import lombok.Getter;

public class ProposalResultsListItem {
    private final ProposalVoteResult proposalVoteResult;
    private final BsqFormatter bsqFormatter;
    @Getter
    private EvaluatedProposal evaluatedProposal;

    public ProposalResultsListItem(EvaluatedProposal evaluatedProposal, BsqFormatter bsqFormatter) {
        this.evaluatedProposal = evaluatedProposal;
        proposalVoteResult = evaluatedProposal.getProposalVoteResult();
        this.bsqFormatter = bsqFormatter;
    }

    public String getProposalOwnerName() {
        return evaluatedProposal.getProposal().getName();
    }

    public String getProposalId() {
        return evaluatedProposal.getProposal().getShortId();
    }

    public String getAccepted() {
        return Res.get("dao.results.proposals.table.item.votes",
                bsqFormatter.formatCoinWithCode(Coin.valueOf(proposalVoteResult.getStakeOfAcceptedVotes())),
                String.valueOf(proposalVoteResult.getNumAcceptedVotes()));
    }

    public String getRejected() {
        return Res.get("dao.results.proposals.table.item.votes",
                bsqFormatter.formatCoinWithCode(Coin.valueOf(proposalVoteResult.getStakeOfRejectedVotes())),
                String.valueOf(proposalVoteResult.getNumRejectedVotes()));
    }

    public String getThreshold() {
        return proposalVoteResult.getThreshold() > 0 ? (proposalVoteResult.getThreshold() / 100D) + "%" : "";
    }

    public String getQuorum() {
        return bsqFormatter.formatCoinWithCode(Coin.valueOf(proposalVoteResult.getQuorum()));
    }

    public AwesomeIcon getIcon() {
        return evaluatedProposal.isAccepted() ? AwesomeIcon.OK_SIGN : AwesomeIcon.REMOVE_SIGN;
    }

    public String getColorStyleClass() {
        return evaluatedProposal.isAccepted() ? "dao-accepted-icon" : "dao-rejected-icon";
    }

    public String getColorStyle() {
        return evaluatedProposal.isAccepted() ? "-fx-text-fill: -bs-green;" : "-fx-text-fill: -bs-error-red;";
    }

    public String getIssuance() {
        switch (evaluatedProposal.getProposal().getType()) {
            case COMPENSATION_REQUEST:
                Coin requestedBsq = evaluatedProposal.isAccepted() ?
                        ((CompensationProposal) evaluatedProposal.getProposal()).getRequestedBsq() :
                        Coin.ZERO;
                return bsqFormatter.formatCoinWithCode(requestedBsq);
            case GENERIC:
            case CHANGE_PARAM:
            case REMOVE_ALTCOIN:
            default:
                return "";
        }

    }
}
