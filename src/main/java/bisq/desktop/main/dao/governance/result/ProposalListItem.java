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

import bisq.core.dao.governance.ballot.Ballot;
import bisq.core.dao.governance.ballot.vote.Vote;
import bisq.core.dao.governance.proposal.Proposal;
import bisq.core.dao.governance.proposal.compensation.CompensationProposal;
import bisq.core.dao.governance.proposal.confiscatebond.ConfiscateBondProposal;
import bisq.core.dao.governance.proposal.param.ChangeParamProposal;
import bisq.core.dao.governance.proposal.role.BondedRoleProposal;
import bisq.core.dao.governance.role.BondedRole;
import bisq.core.dao.governance.voteresult.EvaluatedProposal;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import org.bitcoinj.core.Coin;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.control.Label;
import javafx.scene.control.TableRow;

import lombok.Getter;

public class ProposalListItem {

    @Getter
    private EvaluatedProposal evaluatedProposal;
    @Getter
    private final Proposal proposal;
    private final Vote vote;
    private final BsqFormatter bsqFormatter;

    private TableRow tableRow;


    ProposalListItem(EvaluatedProposal evaluatedProposal, Ballot ballot, BsqFormatter bsqFormatter) {
        this.evaluatedProposal = evaluatedProposal;
        proposal = evaluatedProposal.getProposal();
        vote = ballot.getVote();

        this.bsqFormatter = bsqFormatter;
    }

    // If myVoteIcon would be set in constructor styles are not applied correctly
    public Label getMyVoteIcon() {
        Label myVoteIcon;
        if (vote != null) {
            if ((vote).isAccepted()) {
                myVoteIcon = AwesomeDude.createIconLabel(AwesomeIcon.THUMBS_UP);
                myVoteIcon.getStyleClass().addAll("icon", "dao-accepted-icon");
            } else {
                myVoteIcon = AwesomeDude.createIconLabel(AwesomeIcon.THUMBS_DOWN);
                myVoteIcon.getStyleClass().addAll("icon", "dao-rejected-icon");
            }
        } else {
            myVoteIcon = AwesomeDude.createIconLabel(AwesomeIcon.MINUS);
            myVoteIcon.getStyleClass().addAll("icon", "dao-ignored-icon");
        }
        return myVoteIcon;
    }

    public void setTableRow(TableRow tableRow) {
        this.tableRow = tableRow;
    }

    public void resetTableRow() {
        if (tableRow != null) {
            tableRow.setStyle(null);
            tableRow.requestLayout();
        }
    }

    public String getProposalOwnerName() {
        return evaluatedProposal.getProposal().getName();
    }

    public AwesomeIcon getIcon() {
        return evaluatedProposal.isAccepted() ? AwesomeIcon.OK_SIGN : AwesomeIcon.BAN_CIRCLE;
    }

    public String getColorStyleClass() {
        return evaluatedProposal.isAccepted() ? "dao-accepted-icon" : "dao-rejected-icon";
    }

    public String getDetails() {
        Proposal proposal = evaluatedProposal.getProposal();
        switch (proposal.getType()) {
            case COMPENSATION_REQUEST:
                CompensationProposal compensationProposal = (CompensationProposal) proposal;
                Coin requestedBsq = evaluatedProposal.isAccepted() ? compensationProposal.getRequestedBsq() : Coin.ZERO;
                return bsqFormatter.formatCoinWithCode(requestedBsq);
            case BONDED_ROLE:
                BondedRoleProposal bondedRoleProposal = (BondedRoleProposal) proposal;
                BondedRole bondedRole = bondedRoleProposal.getBondedRole();
                return Res.get("dao.bond.bondedRoleType." + bondedRole.getBondedRoleType().name());
            case REMOVE_ALTCOIN:
                // TODO
                return "N/A";
            case CHANGE_PARAM:
                ChangeParamProposal changeParamProposal = (ChangeParamProposal) proposal;
                return changeParamProposal.getParam().getDisplayString();
            case GENERIC:
                // TODO
                return "N/A";
            case CONFISCATE_BOND:
                ConfiscateBondProposal confiscateBondProposal = (ConfiscateBondProposal) proposal;
                // TODO add info to bond
                return confiscateBondProposal.getTxId();
        }
        return "-";
    }
}
