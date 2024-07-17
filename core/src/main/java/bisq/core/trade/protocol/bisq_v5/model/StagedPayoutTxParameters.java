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

package bisq.core.trade.protocol.bisq_v5.model;

import bisq.common.config.Config;

public class StagedPayoutTxParameters {
    // 10 days
    private static final long CLAIM_DELAY = 144 * 10;
    //todo find what is min value (we filter dust values in the wallet, so better not go that low)
    public static final long WARNING_TX_FEE_BUMP_OUTPUT_VALUE = 2000;
    public static final long REDIRECT_TX_FEE_BUMP_OUTPUT_VALUE = 2000;

    private static final long WARNING_TX_EXPECTED_WEIGHT = 722; // 125 tx bytes, 220-222 witness bytes
    private static final long CLAIM_TX_EXPECTED_WEIGHT = 520;   //  82 tx bytes, 191-192 witness bytes
    public static final long REDIRECT_TX_MIN_WEIGHT = 595;      //  82 tx bytes, 265-267 witness bytes

    // Min. fee rate for staged payout txs. If fee rate used at take offer time was higher we use that.
    // We prefer a rather high fee rate to not risk that the tx gets stuck if required fee rate would
    // spike when opening arbitration.
    private static final long MIN_TX_FEE_RATE = 10;

    public static long getClaimDelay() {
        return Config.baseCurrencyNetwork().isRegtest() ? 5 : CLAIM_DELAY;
    }

    public static long getWarningTxMiningFee(long depositTxFeeRate) {
        return (getFeePerVByte(depositTxFeeRate) * StagedPayoutTxParameters.WARNING_TX_EXPECTED_WEIGHT + 3) / 4;
    }

    public static long getClaimTxMiningFee(long txFeePerVByte) {
        return (txFeePerVByte * StagedPayoutTxParameters.CLAIM_TX_EXPECTED_WEIGHT + 3) / 4;
    }

    private static long getFeePerVByte(long depositTxFeeRate) {
        return Math.max(StagedPayoutTxParameters.MIN_TX_FEE_RATE, depositTxFeeRate);
    }
}
