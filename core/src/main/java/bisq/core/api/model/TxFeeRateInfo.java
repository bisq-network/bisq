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

package bisq.core.api.model;

import bisq.common.Payload;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode
@Getter
public class TxFeeRateInfo implements Payload {

    private final boolean useCustomTxFeeRate;
    private final long customTxFeeRate;
    private final long minFeeServiceRate;
    private final long feeServiceRate;
    private final long lastFeeServiceRequestTs;

    public TxFeeRateInfo(boolean useCustomTxFeeRate,
                         long customTxFeeRate,
                         long minFeeServiceRate,
                         long feeServiceRate,
                         long lastFeeServiceRequestTs) {
        this.useCustomTxFeeRate = useCustomTxFeeRate;
        this.customTxFeeRate = customTxFeeRate;
        this.minFeeServiceRate = minFeeServiceRate;
        this.feeServiceRate = feeServiceRate;
        this.lastFeeServiceRequestTs = lastFeeServiceRequestTs;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    //////////////////////////////////////////////////////////////////////////////////////

    @Override
    public bisq.proto.grpc.TxFeeRateInfo toProtoMessage() {
        return bisq.proto.grpc.TxFeeRateInfo.newBuilder()
                .setUseCustomTxFeeRate(useCustomTxFeeRate)
                .setCustomTxFeeRate(customTxFeeRate)
                .setMinFeeServiceRate(minFeeServiceRate)
                .setFeeServiceRate(feeServiceRate)
                .setLastFeeServiceRequestTs(lastFeeServiceRequestTs)
                .build();
    }

    @SuppressWarnings("unused")
    public static TxFeeRateInfo fromProto(bisq.proto.grpc.TxFeeRateInfo proto) {
        return new TxFeeRateInfo(proto.getUseCustomTxFeeRate(),
                proto.getCustomTxFeeRate(),
                proto.getMinFeeServiceRate(),
                proto.getFeeServiceRate(),
                proto.getLastFeeServiceRequestTs());
    }

    @Override
    public String toString() {
        return "TxFeeRateInfo{" + "\n" +
                "  useCustomTxFeeRate=" + useCustomTxFeeRate + "\n" +
                ", customTxFeeRate=" + customTxFeeRate + " sats/byte" + "\n" +
                ", minFeeServiceRate=" + minFeeServiceRate + " sats/byte" + "\n" +
                ", feeServiceRate=" + feeServiceRate + " sats/byte" + "\n" +
                ", lastFeeServiceRequestTs=" + lastFeeServiceRequestTs + "\n" +
                '}';
    }
}
