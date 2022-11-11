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

/**
 * Contains all relevant data for a burningman candidate (any contributor who has made a compensation request or was
 * a receiver of a genesis output).
 */
@Slf4j
@Getter
@EqualsAndHashCode
public class BurningManCandidate {
    private final List<CompensationModel> compensationModels = new ArrayList<>();
    private long accumulatedCompensationAmount;
    private long accumulatedDecayedCompensationAmount;
    private double compensationShare;           // Share of accumulated decayed compensation amounts in relation to total issued amounts
    protected Optional<String> mostRecentAddress = Optional.empty();

    private final List<BurnOutputModel> burnOutputModels = new ArrayList<>();
    private long accumulatedBurnAmount;
    private long accumulatedDecayedBurnAmount;
    protected double burnAmountShare;             // Share of accumulated decayed burn amounts in relation to total burned amounts
    protected double cappedBurnAmountShare;       // Capped burnAmountShare. Cannot be larger than boostedCompensationShare

    BurningManCandidate() {
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
        // For genesis outputs we have same txId, so we use also output index to ensure deterministic sort order.
        // For normal comp requests its Optional.empty.
        compensationModels.sort(Comparator.comparing(CompensationModel::getTxId)
                .thenComparing(model -> model.getOutputIndex().orElse(-1)));

        accumulatedDecayedCompensationAmount += compensationModel.getDecayedAmount();
        accumulatedCompensationAmount += compensationModel.getAmount();

        mostRecentAddress = compensationModels.stream()
                .max(Comparator.comparing(CompensationModel::getHeight))
                .map(CompensationModel::getAddress);
    }

    public void calculateShares(double totalDecayedCompensationAmounts, double totalDecayedBurnAmounts) {
        compensationShare = totalDecayedCompensationAmounts > 0 ? accumulatedDecayedCompensationAmount / totalDecayedCompensationAmounts : 0;
        double maxBoostedCompensationShare = Math.min(BurningManService.MAX_BURN_SHARE, compensationShare * BurningManService.ISSUANCE_BOOST_FACTOR);

        burnAmountShare = totalDecayedBurnAmounts > 0 ? accumulatedDecayedBurnAmount / totalDecayedBurnAmounts : 0;
        cappedBurnAmountShare = Math.min(maxBoostedCompensationShare, burnAmountShare);
    }

    @Override
    public String toString() {
        return "BurningManCandidate{" +
                "\r\n     compensationModels=" + compensationModels +
                ",\r\n     accumulatedCompensationAmount=" + accumulatedCompensationAmount +
                ",\r\n     accumulatedDecayedCompensationAmount=" + accumulatedDecayedCompensationAmount +
                ",\r\n     compensationShare=" + compensationShare +
                ",\r\n     mostRecentAddress=" + mostRecentAddress +
                ",\r\n     burnOutputModels=" + burnOutputModels +
                ",\r\n     accumulatedBurnAmount=" + accumulatedBurnAmount +
                ",\r\n     accumulatedDecayedBurnAmount=" + accumulatedDecayedBurnAmount +
                ",\r\n     burnAmountShare=" + burnAmountShare +
                ",\r\n     cappedBurnAmountShare=" + cappedBurnAmountShare +
                "\r\n}";
    }
}
