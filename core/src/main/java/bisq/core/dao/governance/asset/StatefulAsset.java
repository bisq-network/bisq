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

package bisq.core.dao.governance.asset;

import bisq.core.locale.CurrencyUtil;

import bisq.asset.Asset;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class StatefulAsset {
    private final Asset asset;
    @Setter
    private AssetState assetState = AssetState.UNDEFINED;
    private List<FeePayment> feePayments = new ArrayList<>();
    @Setter
    private long tradeVolume;
    @Setter
    private long lookBackPeriodInDays;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public StatefulAsset(Asset asset) {
        this.asset = asset;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getNameAndCode() {
        return CurrencyUtil.getNameAndCode(getTickerSymbol());
    }

    public String getTickerSymbol() {
        return asset.getTickerSymbol();
    }

    public void setFeePayments(List<FeePayment> feePayments) {
        this.feePayments = feePayments;
    }

    public Optional<FeePayment> getLastFeePayment() {
        return feePayments.isEmpty() ? Optional.empty() : Optional.of(feePayments.get(feePayments.size() - 1));
    }

    public long getTotalFeesPaid() {
        return feePayments.stream().mapToLong(FeePayment::getFee).sum();
    }

    public long getFeeOfTrialPeriod() {
        return getLastFeePayment()
                .map(FeePayment::getFee)
                .filter(e -> assetState == AssetState.IN_TRIAL_PERIOD)
                .orElse(0L);
    }

    public boolean isActive() {
        return !wasRemovedByVoting() && !isDeListed();
    }

    public boolean wasRemovedByVoting() {
        return assetState == AssetState.REMOVED_BY_VOTING;
    }

    private boolean isDeListed() {
        return assetState == AssetState.DE_LISTED;
    }


    @Override
    public String toString() {
        return "StatefulAsset{" +
                "\n     asset=" + asset +
                ",\n     assetState=" + assetState +
                ",\n     feePayments=" + feePayments +
                ",\n     tradeVolume=" + tradeVolume +
                ",\n     lookBackPeriodInDays=" + lookBackPeriodInDays +
                "\n}";
    }
}
