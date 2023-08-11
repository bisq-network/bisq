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

public class StagedPayoutTxParameters {
    // 10 days
    public static final long CLAIM_DELAY = 144 * 10;
    //todo find what is min value (we filter dust values in the wallet, so better not go that low)
    public static final long WARNING_TX_FEE_BUMP_OUTPUT_VALUE = 2000;
    public static final long REDIRECT_TX_FEE_BUMP_OUTPUT_VALUE = 2000;

    // todo find out size
    private static final long WARNING_TX_EXPECTED_SIZE = 1000;   // todo find out size
    private static final long CLAIM_TX_EXPECTED_SIZE = 1000;  // todo find out size
    // Min. fee rate for DPT. If fee rate used at take offer time was higher we use that.
    // We prefer a rather high fee rate to not risk that the DPT gets stuck if required fee rate would
    // spike when opening arbitration.
    private static final long MIN_TX_FEE_RATE = 10;

    public static long getWarningTxMiningFee(long depositTxFeeRate) {
        return getFeePerVByte(depositTxFeeRate) * StagedPayoutTxParameters.WARNING_TX_EXPECTED_SIZE;
    }

    public static long getClaimTxMiningFee(long depositTxFeeRate) {
        return getFeePerVByte(depositTxFeeRate) * StagedPayoutTxParameters.CLAIM_TX_EXPECTED_SIZE;
    }

    private static long getFeePerVByte(long depositTxFeeRate) {
        return Math.max(StagedPayoutTxParameters.MIN_TX_FEE_RATE, depositTxFeeRate);
    }
}
