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

package bisq.desktop.main.dao.results;

import bisq.core.dao.voting.voteresult.DecryptedVote;
import bisq.core.dao.voting.voteresult.EvaluatedProposal;

import javafx.scene.control.TableRow;

public class BaseResultsListItem {
    private TableRow tableRow;

    public void setTableRow(TableRow tableRow) {
        this.tableRow = tableRow;
    }

    public void resetTableRow() {
        if (tableRow != null) {
            tableRow.setStyle(null);
            tableRow.requestLayout();

        }
    }

    public void applyVoteAndProposal(DecryptedVote decryptedVote, EvaluatedProposal evaluatedProposal) {
        String rowBgColor = decryptedVote.getVote(evaluatedProposal.getProposalTxId())
                .map(booleanVote -> booleanVote.isAccepted() ?
                        "-fx-background-color: rgba(0, 255, 0, 0.4)" :
                        "-fx-background-color: rgba(255, 0, 0, 0.23)")
                .orElse("-fx-background-color: rgba(182, 182, 182, 0.4)");
        tableRow.setStyle(rowBgColor);
    }
}
