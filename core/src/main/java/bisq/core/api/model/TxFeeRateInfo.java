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

    private final long stdTxFeeRate;
    private final long customTxFeeRate;
    private final boolean useCustomTxFeeRate;

    public TxFeeRateInfo(long stdTxFeeRate,
                         long customTxFeeRate,
                         boolean useCustomTxFeeRate) {
        this.stdTxFeeRate = stdTxFeeRate;
        this.customTxFeeRate = customTxFeeRate;
        this.useCustomTxFeeRate = useCustomTxFeeRate;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    //////////////////////////////////////////////////////////////////////////////////////

    @Override
    public bisq.proto.grpc.TxFeeRateInfo toProtoMessage() {
        return bisq.proto.grpc.TxFeeRateInfo.newBuilder()
                .setStdTxFeeRate(stdTxFeeRate)
                .setCustomTxFeeRate(customTxFeeRate)
                .setUseCustomTxFeeRate(useCustomTxFeeRate)
                .build();
    }

    @SuppressWarnings("unused")
    public static TxFeeRateInfo fromProto(bisq.proto.grpc.TxFeeRateInfo proto) {
        return new TxFeeRateInfo(proto.getStdTxFeeRate(),
                proto.getCustomTxFeeRate(),
                proto.getUseCustomTxFeeRate());
    }

    @Override
    public String toString() {
        return "TxFeeRateInfo{"
                + "stdTxFeeRate=" + stdTxFeeRate + " sats/byte"
                + ", customTxFeeRate=" + customTxFeeRate + " sats/byte"
                + ", useCustomTxFeeRate=" + useCustomTxFeeRate
                + "}";
    }
}
