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

package bisq.desktop.main.dao.burnbsq.burningmen;

import bisq.core.dao.burningman.BurningManCandidate;
import bisq.core.dao.burningman.BurningManPresentationService;
import bisq.core.locale.Res;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.BsqFormatter;

import bisq.common.util.Tuple2;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
class BurningmenListItem {
    private final BurningManCandidate burningManCandidate;
    private final String name, address, burnAmountShareAsString, cappedBurnAmountShareAsString, compensationShareAsString,
            accumulatedDecayedBurnAmountAsBsq, burnTargetAsBsq, maxBurnTargetAsBsq, accumulatedBurnAmountAsBsq,
            accumulatedDecayedCompensationAmountAsBsq, accumulatedCompensationAmountAsBsq, expectedRevenueAsBsq;
    private final long burnTarget, maxBurnTarget, accumulatedDecayedBurnAmount, accumulatedBurnAmount,
            accumulatedDecayedCompensationAmount, accumulatedCompensationAmount, expectedRevenue;
    private final int numBurnOutputs, numIssuances;
    private final double cappedBurnAmountShare, burnAmountShare, compensationShare;

    BurningmenListItem(BurningManPresentationService burningManPresentationService,
                       String name,
                       BurningManCandidate burningManCandidate,
                       BsqFormatter bsqFormatter) {
        this.burningManCandidate = burningManCandidate;

        this.name = name;
        address = burningManCandidate.getMostRecentAddress().orElse(Res.get("shared.na"));

        // Burn
        Tuple2<Long, Long> burnTargetTuple = burningManPresentationService.getCandidateBurnTarget(burningManCandidate);
        burnTarget = burnTargetTuple.first;
        burnTargetAsBsq = bsqFormatter.formatCoin(burnTarget);
        maxBurnTarget = burnTargetTuple.second;
        maxBurnTargetAsBsq = bsqFormatter.formatCoin(maxBurnTarget);
        accumulatedBurnAmount = burningManCandidate.getAccumulatedBurnAmount();
        accumulatedBurnAmountAsBsq = bsqFormatter.formatCoinWithCode(accumulatedBurnAmount);
        accumulatedDecayedBurnAmount = burningManCandidate.getAccumulatedDecayedBurnAmount();
        accumulatedDecayedBurnAmountAsBsq = bsqFormatter.formatCoinWithCode(accumulatedDecayedBurnAmount);
        burnAmountShare = burningManCandidate.getBurnAmountShare();
        burnAmountShareAsString = FormattingUtils.formatToPercentWithSymbol(burnAmountShare);
        cappedBurnAmountShare = burningManCandidate.getCappedBurnAmountShare();
        cappedBurnAmountShareAsString = FormattingUtils.formatToPercentWithSymbol(cappedBurnAmountShare);
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
    }
}
