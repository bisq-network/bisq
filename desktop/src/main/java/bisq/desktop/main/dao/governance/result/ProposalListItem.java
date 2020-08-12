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

import bisq.core.dao.governance.proposal.ProposalType;
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
import bisq.core.util.coin.BsqFormatter;

import org.bitcoinj.core.Coin;

import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

import javafx.geometry.Insets;

import lombok.Getter;

import org.jetbrains.annotations.NotNull;

public class ProposalListItem {

    @Getter
    private EvaluatedProposal evaluatedProposal;
    @Getter
    private final Proposal proposal;
    private final Vote vote;
    private final boolean isMyBallotIncluded;
    private final BsqFormatter bsqFormatter;

    private TableRow tableRow;


    ProposalListItem(EvaluatedProposal evaluatedProposal, Ballot ballot, boolean isMyBallotIncluded,
                     BsqFormatter bsqFormatter) {
        this.evaluatedProposal = evaluatedProposal;
        proposal = evaluatedProposal.getProposal();
        vote = ballot.getVote();
        this.isMyBallotIncluded = isMyBallotIncluded;
        this.bsqFormatter = bsqFormatter;
    }

    // If myVoteIcon would be set in constructor styles are not applied correctly
    public Label getMyVoteIcon() {
        Label myVoteIcon;
        if (vote != null) {
            if ((vote).isAccepted()) {
                myVoteIcon = getIcon(AwesomeIcon.THUMBS_UP, "dao-accepted-icon");
            } else {
                myVoteIcon = getIcon(AwesomeIcon.THUMBS_DOWN, "dao-rejected-icon");
            }
            if (!isMyBallotIncluded) {
                Label notIncluded = FormBuilder.getIcon(AwesomeIcon.BAN_CIRCLE);
                return new Label("", new HBox(10, new StackPane(myVoteIcon, notIncluded),
                        getIcon(AwesomeIcon.MINUS, "dao-ignored-icon")));
            }
        } else {
            myVoteIcon = getIcon(AwesomeIcon.MINUS, "dao-ignored-icon");
            if (!isMyBallotIncluded) {
                myVoteIcon.setPadding(new Insets(0, 0, 0, 25));
                return myVoteIcon;
            }
        }
        return myVoteIcon;
    }

    @NotNull
    private Label getIcon(AwesomeIcon awesomeIcon, String s) {
        Label myVoteIcon;
        myVoteIcon = FormBuilder.getIcon(awesomeIcon);
        myVoteIcon.getStyleClass().add(s);
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

    public boolean isAccepted() {
        return evaluatedProposal.isAccepted();
    }

    public String getColorStyleClass() {
        return evaluatedProposal.isAccepted() ? "dao-accepted-icon" : "dao-rejected-icon";
    }

    public String getDetails() {
        return ProposalListItem.getProposalDetails(evaluatedProposal, bsqFormatter);
    }

    public long getIssuedAmount() {
        if (evaluatedProposal.getProposal().getType() == ProposalType.COMPENSATION_REQUEST) {
            CompensationProposal compensationProposal = (CompensationProposal) proposal;
            Coin requestedBsq = evaluatedProposal.isAccepted() ? compensationProposal.getRequestedBsq() : Coin.ZERO;
            return requestedBsq.value;
        }
        return 0;
    }

    public String getThresholdAsString() {
        return (evaluatedProposal.getProposalVoteResult().getThreshold() / 100D) + "%";
    }

    public long getThreshold() {
        return evaluatedProposal.getProposalVoteResult().getThreshold();
    }

    public String getQuorumAsString() {
        return bsqFormatter.formatCoinWithCode(Coin.valueOf(evaluatedProposal.getProposalVoteResult().getQuorum()));
    }

    public long getQuorum() {
        return evaluatedProposal.getProposalVoteResult().getQuorum();
    }

    private static String getProposalDetails(EvaluatedProposal evaluatedProposal, BsqFormatter bsqFormatter) {
        return getProposalDetails(evaluatedProposal, bsqFormatter, true);
    }

    private static String getProposalDetails(EvaluatedProposal evaluatedProposal,
                                             BsqFormatter bsqFormatter,
                                             boolean useDisplayString) {
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
