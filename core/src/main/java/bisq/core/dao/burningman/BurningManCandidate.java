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

package bisq.core.dao.burningman;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@EqualsAndHashCode
public final class BurningManCandidate {
    private long accumulatedCompensationAmount;
    private long accumulatedDecayedCompensationAmount;
    private long accumulatedBurnAmount;
    private long accumulatedDecayedBurnAmount;
    private long allowedBurnAmount;
    private double issuanceShare;   // share of accumulated decayed issuance amounts in relation to total issued amounts
    private double boostedIssuanceShare;
    private double burnOutputShare; // share of accumulated decayed burn amounts in relation to total burned amounts
    private double effectiveBurnOutputShare; // limited to boostedIssuanceShare
    private final List<CompensationModel> compensationModels = new ArrayList<>();
    private final List<BurnOutputModel> burnOutputModels = new ArrayList<>();

    public BurningManCandidate() {
    }

    public void addBurnOutputModel(BurnOutputModel burnOutputModel) {
        if (burnOutputModels.contains(burnOutputModel)) {
            return;
        }
        burnOutputModels.add(burnOutputModel);
        burnOutputModels.sort(Comparator.comparing(BurnOutputModel::getTxId));

        accumulatedDecayedBurnAmount += burnOutputModel.getDecayedAmount();
        accumulatedBurnAmount += burnOutputModel.getAmount();
    }

    public void addCompensationModel(CompensationModel compensationModel) {
        if (compensationModels.contains(compensationModel)) {
            return;
        }

        compensationModels.add(compensationModel);
        compensationModels.sort(Comparator.comparing(CompensationModel::getTxId));

        accumulatedDecayedCompensationAmount += compensationModel.getDecayedAmount();
        accumulatedCompensationAmount += compensationModel.getAmount();
    }

    public void calculateShare(double totalIssuanceWeight, double totalBurnOutputWeight, long burnTarget) {
        issuanceShare = totalIssuanceWeight > 0 ? accumulatedDecayedCompensationAmount / totalIssuanceWeight : 0;
        boostedIssuanceShare = issuanceShare * BurningManService.ISSUANCE_BOOST_FACTOR;

        burnOutputShare = totalBurnOutputWeight > 0 ? accumulatedDecayedBurnAmount / totalBurnOutputWeight : 0;
        effectiveBurnOutputShare = Math.min(boostedIssuanceShare, burnOutputShare);

        double maxBurnAmount = burnTarget + BurningManService.BURN_TARGET_BOOST_AMOUNT;
        if (issuanceShare > 0 && maxBurnAmount > 0 && effectiveBurnOutputShare < boostedIssuanceShare) {
            long value = Math.round(boostedIssuanceShare * maxBurnAmount);
            // If below dust we set value to 0
            allowedBurnAmount = value < 546 ? 0 : value;
        } else {
            allowedBurnAmount = 0;
        }
    }

    public Optional<String> getMostRecentAddress() {
        return compensationModels.stream()
                .max(Comparator.comparing(CompensationModel::getHeight))
                .map(CompensationModel::getAddress);
    }

    @Override
    public String toString() {
        return "BurningManCandidate{" +
                "\r\n     accumulatedCompensationAmount=" + accumulatedCompensationAmount +
                ",\r\n     accumulatedDecayedCompensationAmount=" + accumulatedDecayedCompensationAmount +
                ",\r\n     accumulatedBurnAmount=" + accumulatedBurnAmount +
                ",\r\n     accumulatedDecayedBurnAmount=" + accumulatedDecayedBurnAmount +
                ",\r\n     allowedBurnAmount=" + allowedBurnAmount +
                ",\r\n     issuanceShare=" + issuanceShare +
                ",\r\n     boostedIssuanceShare=" + boostedIssuanceShare +
                ",\r\n     burnOutputShare=" + burnOutputShare +
                ",\r\n     effectiveBurnOutputShare=" + effectiveBurnOutputShare +
                ",\r\n     compensationModels=" + compensationModels +
                ",\r\n     burnOutputModels=" + burnOutputModels +
                "\r\n}";
    }
}
