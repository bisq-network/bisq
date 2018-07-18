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

import bisq.desktop.main.dao.results.model.ResultsOfCycle;

import bisq.core.dao.voting.proposal.compensation.CompensationProposal;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import org.bitcoinj.core.Coin;

import java.util.Date;

import lombok.Getter;

public class ResultsListItem {
    private final BsqFormatter bsqFormatter;
    @Getter
    private ResultsOfCycle resultsOfCycle;

    public ResultsListItem(ResultsOfCycle resultsOfCycle,
                           BsqFormatter bsqFormatter) {
        this.resultsOfCycle = resultsOfCycle;
        this.bsqFormatter = bsqFormatter;
    }

    public String getCycle() {
        int displayIndex = resultsOfCycle.getCycleIndex() + 1;
        String dateTime = bsqFormatter.formatDateTime(new Date(resultsOfCycle.getCycleStartTime()));
        return Res.get("dao.results.results.table.item.cycle", displayIndex, dateTime);
    }

    public String getNumProposals() {
        return String.valueOf(resultsOfCycle.getEvaluatedProposals().size());
    }

    public String getNumVotesAsString() {
        return String.valueOf(resultsOfCycle.getNumVotes());
    }

    public String getStake() {
        return bsqFormatter.formatCoinWithCode(Coin.valueOf(resultsOfCycle.getTotalStake()));
    }

    public String getIssuance() {
        long totalIssuance = resultsOfCycle.getEvaluatedProposals().stream()
                .filter(e -> e.getProposal() instanceof CompensationProposal)
                .map(e -> (CompensationProposal) e.getProposal())
                .mapToLong(e -> e.getRequestedBsq().value)
                .sum();
        return bsqFormatter.formatCoinWithCode(Coin.valueOf(totalIssuance));
    }

    public Long getCycleStartTime() {
        return resultsOfCycle.getCycleStartTime();
    }
}
