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

import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
@Getter
public class StatefulAsset implements Comparable<StatefulAsset> {
    @Value
    public static class FeePayment {
        private String txId;
        private long fee;

        @Override
        public String toString() {
            return "FeePayment{" +
                    "\n     txId='" + txId + '\'' +
                    ",\n     fee=" + fee +
                    "\n}";
        }
    }

    private final Asset asset;
    // keep access private to ensure we cannot change state once remove by voting
    private AssetState assetState = AssetState.NOT_ACTIVATED;
    private List<FeePayment> feePayments = new ArrayList<>();

    public StatefulAsset(Asset asset) {
        this.asset = asset;
    }

    public boolean wasTerminated() {
        return assetState == AssetState.TERMINATED;
    }

    public String getTickerSymbol() {
        return asset.getTickerSymbol();
    }

    public void terminate() {
        assetState = AssetState.TERMINATED;
    }

    public void enableByFeePayment(FeePayment feePayment) {
        checkArgument(!wasTerminated(), "Cannot pay a fee for a removed asset");
        feePayments.add(feePayment);
        assetState = AssetState.ENABLED_BY_FEE_PAYMENT;
    }

    public void deListByInactivity() {
        checkArgument(!wasTerminated(), "Cannot pay a fee for a removed asset");
        assetState = AssetState.DE_LISTED_BY_INACTIVITY;
    }

    public long getActiveFee() {
        return feePayments.stream().mapToLong(FeePayment::getFee).sum();
    }

    public void addFeePayment(FeePayment feePayment) {
        log.error(feePayment.toString());
        feePayments.add(feePayment);
    }

    @Override
    public int compareTo(@NotNull StatefulAsset other) {
        return getNameAndCode().compareTo(other.getNameAndCode());
    }

    public String getNameAndCode() {
        return CurrencyUtil.getNameAndCode(getTickerSymbol());
    }

    @Override
    public String toString() {
        return "StatefulAsset{" +
                "\n     asset=" + asset +
                ",\n     assetState=" + assetState +
                ",\n     feePayments=" + feePayments +
                "\n}";
    }
}
