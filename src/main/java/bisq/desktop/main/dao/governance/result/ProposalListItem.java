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

import bisq.core.dao.role.BondedRole;
import bisq.core.dao.voting.proposal.Proposal;
import bisq.core.dao.voting.proposal.compensation.CompensationProposal;
import bisq.core.dao.voting.proposal.confiscatebond.ConfiscateBondProposal;
import bisq.core.dao.voting.proposal.param.ChangeParamProposal;
import bisq.core.dao.voting.proposal.role.BondedRoleProposal;
import bisq.core.dao.voting.voteresult.EvaluatedProposal;
import bisq.core.dao.voting.voteresult.ProposalVoteResult;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import org.bitcoinj.core.Coin;

import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.control.TableRow;

import lombok.Getter;

public class ProposalListItem {
    private final ProposalVoteResult proposalVoteResult;
    private final BsqFormatter bsqFormatter;
    private TableRow tableRow;
    @Getter
    private EvaluatedProposal evaluatedProposal;

    ProposalListItem(EvaluatedProposal evaluatedProposal, BsqFormatter bsqFormatter) {
        this.evaluatedProposal = evaluatedProposal;
        proposalVoteResult = evaluatedProposal.getProposalVoteResult();
        this.bsqFormatter = bsqFormatter;
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
        return evaluatedProposal.isAccepted() ? AwesomeIcon.OK_SIGN : AwesomeIcon.BAN_CIRCLE;
    }

    public String getColorStyleClass() {
        return evaluatedProposal.isAccepted() ? "dao-accepted-icon" : "dao-rejected-icon";
    }

    public String getColorStyle() {
        return evaluatedProposal.isAccepted() ? "-fx-text-fill: -bs-green;" : "-fx-text-fill: -bs-error-red;";
    }

    public String getIssuance() {
        Proposal proposal = evaluatedProposal.getProposal();
        switch (proposal.getType()) {
            case COMPENSATION_REQUEST:
                CompensationProposal compensationProposal = (CompensationProposal) proposal;
                Coin requestedBsq = evaluatedProposal.isAccepted() ? compensationProposal.getRequestedBsq() : Coin.ZERO;
                return Res.get("dao.results.proposals.table.issuance", bsqFormatter.formatCoinWithCode(requestedBsq));
            case BONDED_ROLE:
                BondedRoleProposal bondedRoleProposal = (BondedRoleProposal) proposal;
                BondedRole bondedRole = bondedRoleProposal.getBondedRole();
                String name = bondedRole.getName();
                String type = Res.get("dao.bond.bondedRoleType." + bondedRole.getBondedRoleType().name());
                return Res.get("dao.results.proposals.table.bondedRole", name, type);
            case REMOVE_ALTCOIN:
                // TODO
                return "N/A";
            case CHANGE_PARAM:
                ChangeParamProposal changeParamProposal = (ChangeParamProposal) proposal;
                return Res.get("dao.results.proposals.table.paramChange",
                        changeParamProposal.getParam().getDisplayString(),
                        changeParamProposal.getParamValue());
            case GENERIC:
                // TODO
                return "N/A";
            case CONFISCATE_BOND:
                ConfiscateBondProposal confiscateBondProposal = (ConfiscateBondProposal) proposal;
                // TODO add info to bond
                return Res.get("dao.results.proposals.table.confiscateBond", confiscateBondProposal.getTxId());
        }
        return "-";
    }
}
