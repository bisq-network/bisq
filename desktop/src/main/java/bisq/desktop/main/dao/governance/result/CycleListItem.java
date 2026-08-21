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

import bisq.core.dao.governance.proposal.IssuanceProposal;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.locale.Res;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.FormattingUtils;

import org.bitcoinj.core.Coin;

import java.util.Date;

import lombok.Getter;

public class CycleListItem {
    private final BsqFormatter bsqFormatter;
    @Getter
    private ResultsOfCycle resultsOfCycle;

    CycleListItem(ResultsOfCycle resultsOfCycle,
                  BsqFormatter bsqFormatter) {
        this.resultsOfCycle = resultsOfCycle;
        this.bsqFormatter = bsqFormatter;
    }

    public String getCycle() {
        return Res.get("dao.results.results.table.item.cycle", getCycleIndex(), getCycleDateTime(true));
    }

    public String getCycleDateTime(boolean useLocaleAndLocalTimezone) {
        long cycleStartTime = resultsOfCycle.getCycleStartTime();
        return cycleStartTime > 0 ? FormattingUtils.formatDateTime(new Date(cycleStartTime), useLocaleAndLocalTimezone) : Res.get("shared.na");
    }

    public int getCycleIndex() {
        return resultsOfCycle.getCycleIndex() + 1;
    }

    public String getNumProposals() {
        return String.valueOf(resultsOfCycle.getEvaluatedProposals().size());
    }

    public String getNumVotesAsString() {
        return String.valueOf(resultsOfCycle.getNumVotes());
    }

    public String getMeritAndStake() {
        return bsqFormatter.formatCoinWithCode(Coin.valueOf(resultsOfCycle.getMeritAndStake()));
    }

    public String getIssuance() {
        long totalIssuance = resultsOfCycle.getEvaluatedProposals().stream()
                .filter(EvaluatedProposal::isAccepted)
                .filter(e -> e.getProposal() instanceof IssuanceProposal)
                .map(e -> (IssuanceProposal) e.getProposal())
                .mapToLong(e -> e.getRequestedBsq().value)
                .sum();
        return bsqFormatter.formatCoinWithCode(Coin.valueOf(totalIssuance));
    }

    public Long getCycleStartTime() {
        return resultsOfCycle.getCycleStartTime();
    }
}
