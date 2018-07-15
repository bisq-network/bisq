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

package bisq.desktop.main.dao.cycles;

import bisq.desktop.main.dao.cycles.model.CycleResult;

import bisq.core.dao.voting.proposal.compensation.CompensationProposal;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import org.bitcoinj.core.Coin;

import java.util.Date;

import lombok.Getter;

public class CyclesListItem {
    private BsqFormatter bsqFormatter;
    @Getter
    private CycleResult cycleResult;

    public CyclesListItem(CycleResult cycleResult,
                          BsqFormatter bsqFormatter) {
        this.cycleResult = cycleResult;
        this.bsqFormatter = bsqFormatter;
    }

    public String getCycle() {
        int displayIndex = cycleResult.getCycleIndex() + 1;
        String dateTime = bsqFormatter.formatDateTime(new Date(cycleResult.getCycleStartTime()));
        return Res.get("dao.results.results.table.item.cycle", displayIndex, dateTime);
    }

    public String getNumProposals() {
        return String.valueOf(cycleResult.getEvaluatedProposals().size());
    }

    public String getNumVotesAsString() {
        return String.valueOf(cycleResult.getNumVotes());
    }

    public String getStake() {
        return bsqFormatter.formatCoinWithCode(Coin.valueOf(cycleResult.getTotalStake()));
    }

    public String getIssuance() {
        long totalIssuance = cycleResult.getEvaluatedProposals().stream()
                .filter(e -> e.getProposal() instanceof CompensationProposal)
                .map(e -> (CompensationProposal) e.getProposal())
                .mapToLong(e -> e.getRequestedBsq().value)
                .sum();
        return bsqFormatter.formatCoinWithCode(Coin.valueOf(totalIssuance));
    }

    public Long getCycleStartTime() {
        return cycleResult.getCycleStartTime();
    }
}
