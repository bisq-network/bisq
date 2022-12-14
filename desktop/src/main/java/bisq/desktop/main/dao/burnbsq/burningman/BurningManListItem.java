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

package bisq.desktop.main.dao.burnbsq.burningman;

import bisq.core.dao.burningman.BurningManPresentationService;
import bisq.core.dao.burningman.model.BurningManCandidate;
import bisq.core.dao.burningman.model.LegacyBurningMan;
import bisq.core.locale.Res;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.BsqFormatter;

import bisq.common.util.Tuple2;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
class BurningManListItem {
    private final BurningManCandidate burningManCandidate;
    private final String name, address, cappedBurnAmountShareAsString, compensationShareAsString,
            accumulatedDecayedBurnAmountAsBsq, burnTargetAsBsq, accumulatedBurnAmountAsBsq,
            accumulatedDecayedCompensationAmountAsBsq, accumulatedCompensationAmountAsBsq, expectedRevenueAsBsq, numIssuancesAsString;
    private final long burnTarget, maxBurnTarget, accumulatedDecayedBurnAmount, accumulatedBurnAmount,
            accumulatedDecayedCompensationAmount, accumulatedCompensationAmount, expectedRevenue;
    private final int numBurnOutputs, numIssuances;
    private final double cappedBurnAmountShare, burnAmountShare, compensationShare;

    BurningManListItem(BurningManPresentationService burningManPresentationService,
                       String name,
                       BurningManCandidate burningManCandidate,
                       BsqFormatter bsqFormatter) {
        this.burningManCandidate = burningManCandidate;

        this.name = name;
        address = burningManCandidate.getMostRecentAddress().orElse(Res.get("shared.na"));

        if (burningManCandidate instanceof LegacyBurningMan) {
            // Burn
            burnTarget = 0;
            burnTargetAsBsq = "";
            maxBurnTarget = 0;
            accumulatedBurnAmount = burningManCandidate.getAccumulatedBurnAmount();
            accumulatedBurnAmountAsBsq = bsqFormatter.formatCoinWithCode(accumulatedBurnAmount);
            accumulatedDecayedBurnAmount = 0;
            accumulatedDecayedBurnAmountAsBsq = "";

            burnAmountShare = burningManCandidate.getBurnAmountShare();

            cappedBurnAmountShare = burningManCandidate.getCappedBurnAmountShare();

            // LegacyBurningManForDPT is the one defined by DAO voting, so only that would receive BTC if new BM do not cover 100%.
            if (burningManPresentationService.getLegacyBurningManForDPT().equals(burningManCandidate)) {
                expectedRevenue = burningManPresentationService.getExpectedRevenue(burningManCandidate);
                cappedBurnAmountShareAsString = FormattingUtils.formatToPercentWithSymbol(burnAmountShare);
            } else {
                expectedRevenue = 0;
                cappedBurnAmountShareAsString = FormattingUtils.formatToPercentWithSymbol(0);
            }
            expectedRevenueAsBsq = bsqFormatter.formatCoinWithCode(expectedRevenue);
            numBurnOutputs = burningManCandidate.getBurnOutputModels().size();

            // There is no issuance for legacy BM
            accumulatedCompensationAmount = 0;
            accumulatedCompensationAmountAsBsq = "";
            accumulatedDecayedCompensationAmount = 0;
            accumulatedDecayedCompensationAmountAsBsq = "";
            compensationShare = 0;
            compensationShareAsString = "";
            numIssuances = 0;
            numIssuancesAsString = "";
        } else {
            // Burn
            Tuple2<Long, Long> burnTargetTuple = burningManPresentationService.getCandidateBurnTarget(burningManCandidate);
            burnTarget = burnTargetTuple.first;
            maxBurnTarget = burnTargetTuple.second;
            burnTargetAsBsq = Res.get("dao.burningman.burnTarget.fromTo", bsqFormatter.formatCoin(burnTarget), bsqFormatter.formatCoin(maxBurnTarget));
            accumulatedBurnAmount = burningManCandidate.getAccumulatedBurnAmount();
            accumulatedBurnAmountAsBsq = bsqFormatter.formatCoinWithCode(accumulatedBurnAmount);
            accumulatedDecayedBurnAmount = burningManCandidate.getAccumulatedDecayedBurnAmount();
            accumulatedDecayedBurnAmountAsBsq = bsqFormatter.formatCoinWithCode(accumulatedDecayedBurnAmount);
            burnAmountShare = burningManCandidate.getBurnAmountShare();
            cappedBurnAmountShare = burningManCandidate.getCappedBurnAmountShare();
            if (burnAmountShare != cappedBurnAmountShare) {
                cappedBurnAmountShareAsString = Res.get("dao.burningman.table.burnAmountShare.capped",
                        FormattingUtils.formatToPercentWithSymbol(cappedBurnAmountShare),
                        FormattingUtils.formatToPercentWithSymbol(burnAmountShare));
            } else {
                cappedBurnAmountShareAsString = FormattingUtils.formatToPercentWithSymbol(cappedBurnAmountShare);
            }
            expectedRevenue = burningManPresentationService.getExpectedRevenue(burningManCandidate);
            expectedRevenueAsBsq = bsqFormatter.formatCoinWithCode(expectedRevenue);
            numBurnOutputs = burningManCandidate.getBurnOutputModels().size();

            // Issuance
            accumulatedCompensationAmount = burningManCandidate.getAccumulatedCompensationAmount();
            accumulatedCompensationAmountAsBsq = bsqFormatter.formatCoinWithCode(accumulatedCompensationAmount);
            accumulatedDecayedCompensationAmount = burningManCandidate.getAccumulatedDecayedCompensationAmount();
            accumulatedDecayedCompensationAmountAsBsq = bsqFormatter.formatCoinWithCode(accumulatedDecayedCompensationAmount);
            compensationShare = burningManCandidate.getCompensationShare();
            compensationShareAsString = FormattingUtils.formatToPercentWithSymbol(compensationShare);
            numIssuances = burningManCandidate.getCompensationModels().size();
            numIssuancesAsString = String.valueOf(numIssuances);
        }
    }
}
