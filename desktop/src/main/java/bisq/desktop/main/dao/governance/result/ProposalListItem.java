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

import bisq.desktop.util.FormBuilder;

import bisq.core.dao.state.model.governance.Ballot;
import bisq.core.dao.state.model.governance.ChangeParamProposal;
import bisq.core.dao.state.model.governance.CompensationProposal;
import bisq.core.dao.state.model.governance.ConfiscateBondProposal;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.ReimbursementProposal;
import bisq.core.dao.state.model.governance.RemoveAssetProposal;
import bisq.core.dao.state.model.governance.Role;
import bisq.core.dao.state.model.governance.RoleProposal;
import bisq.core.dao.state.model.governance.Vote;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import org.bitcoinj.core.Coin;

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
                myVoteIcon = FormBuilder.getIcon(AwesomeIcon.THUMBS_UP);
                myVoteIcon.getStyleClass().add("dao-accepted-icon");
            } else {
                myVoteIcon = FormBuilder.getIcon(AwesomeIcon.THUMBS_DOWN);
                myVoteIcon.getStyleClass().add("dao-rejected-icon");
            }
        } else {
            myVoteIcon = FormBuilder.getIcon(AwesomeIcon.MINUS);
            myVoteIcon.getStyleClass().add("dao-ignored-icon");
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
        return ProposalListItem.getProposalDetails(evaluatedProposal, bsqFormatter);
    }

    private static String getProposalDetails(EvaluatedProposal evaluatedProposal, BsqFormatter bsqFormatter) {
        return getProposalDetails(evaluatedProposal, bsqFormatter, true);
    }

    private static String getProposalDetails(EvaluatedProposal evaluatedProposal, BsqFormatter bsqFormatter, boolean useDisplayString) {
        Proposal proposal = evaluatedProposal.getProposal();
        switch (proposal.getType()) {
            case COMPENSATION_REQUEST:
                CompensationProposal compensationProposal = (CompensationProposal) proposal;
                Coin requestedBsq = evaluatedProposal.isAccepted() ? compensationProposal.getRequestedBsq() : Coin.ZERO;
                return bsqFormatter.formatCoinWithCode(requestedBsq);
            case REIMBURSEMENT_REQUEST:
                ReimbursementProposal reimbursementProposal = (ReimbursementProposal) proposal;
                requestedBsq = evaluatedProposal.isAccepted() ? reimbursementProposal.getRequestedBsq() : Coin.ZERO;
                return bsqFormatter.formatCoinWithCode(requestedBsq);
            case CHANGE_PARAM:
                ChangeParamProposal changeParamProposal = (ChangeParamProposal) proposal;
                return useDisplayString ? changeParamProposal.getParam().getDisplayString() : changeParamProposal.getParam().name();
            case BONDED_ROLE:
                RoleProposal roleProposal = (RoleProposal) proposal;
                Role role = roleProposal.getRole();
                String name = role.getBondedRoleType().name();
                return useDisplayString ? Res.get("dao.bond.bondedRoleType." + name) : name;
            case CONFISCATE_BOND:
                ConfiscateBondProposal confiscateBondProposal = (ConfiscateBondProposal) proposal;
                // TODO add info to bond
                return confiscateBondProposal.getTxId();
            case GENERIC:
                return proposal.getName();
            case REMOVE_ASSET:
                RemoveAssetProposal removeAssetProposal = (RemoveAssetProposal) proposal;
                return CurrencyUtil.getNameAndCode(removeAssetProposal.getTickerSymbol());
        }
        return "-";
    }
}
