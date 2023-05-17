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

package bisq.core.dao.burningman.model;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@EqualsAndHashCode(callSuper = true)
public final class LegacyBurningMan extends BurningManCandidate {
    public LegacyBurningMan(String address) {
        receiverAddress = mostRecentAddress = Optional.of(address);
    }

    public void applyBurnAmountShare(double burnAmountShare) {
        this.burnAmountShare = burnAmountShare;

        // We do not adjust burnAmountShare for legacy BM from capped BM
        this.adjustedBurnAmountShare = burnAmountShare;

        // We do not cap burnAmountShare for legacy BM
        this.cappedBurnAmountShare = burnAmountShare;
    }

    @Override
    public void calculateShares(double totalDecayedCompensationAmounts, double totalDecayedBurnAmounts) {
        // do nothing
    }

    @Override
    public void calculateCappedAndAdjustedShares(double sumAllCappedBurnAmountShares,
                                                 double sumAllNonCappedBurnAmountShares) {
        // do nothing
    }

    @Override
    public Set<String> getAllAddresses() {
        return getReceiverAddress().map(Set::of).orElse(new HashSet<>());
    }
}
