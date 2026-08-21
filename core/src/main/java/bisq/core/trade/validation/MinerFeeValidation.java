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

package bisq.core.trade.validation;

import bisq.core.provider.fee.FeeService;
import bisq.core.trade.TradeFeeFactory;

import org.bitcoinj.core.Coin;

import com.google.common.annotations.VisibleForTesting;

import static bisq.core.util.Validator.checkIsPositive;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class MinerFeeValidation {
    public static final double MAX_FEE_DEVIATION_FACTOR = 2; // Max change by factor 2 (expected / 2 or expected * 2)

    // Bound the taker-supplied trade tx fee. A tiny value can leave the deposit tx
    // unconfirmable (locking maker funds); a huge value can drain miner fees from the maker.
    // Envelope derived from realistic miner-fee range 1..600 sat/vB over up to ~600 vB
    // (headroom above the stored average of taker-fee-tx ~192 vB and deposit-tx ~233 vB).
    // The tighter check is checkTradeTxFeeIsInTolerance; these are absolute sanity bounds.
    public static final long MIN_TRADE_TX_FEE_SAT = 250L;
    public static final long MAX_TRADE_TX_FEE_SAT = 360_000L;

    private MinerFeeValidation() {
    }


    /* --------------------------------------------------------------------- */
    // Trade transaction fee
    /* --------------------------------------------------------------------- */

    public static Coin checkTradeTxFee(Coin tradeTxFee) {
        checkIsPositive(tradeTxFee, "tradeTxFee");
        checkTradeTxFeeBounds(tradeTxFee.getValue());
        return tradeTxFee;
    }

    public static long checkTradeTxFee(long tradeTxFee) {
        checkIsPositive(tradeTxFee, "tradeTxFee");
        checkTradeTxFeeBounds(tradeTxFee);
        return tradeTxFee;
    }

    private static void checkTradeTxFeeBounds(long tradeTxFee) {
        checkArgument(tradeTxFee >= MIN_TRADE_TX_FEE_SAT,
                "tradeTxFee too low (must be >= %s sat). Got: %s", MIN_TRADE_TX_FEE_SAT, tradeTxFee);
        checkArgument(tradeTxFee <= MAX_TRADE_TX_FEE_SAT,
                "tradeTxFee too high (must be <= %s sat). Got: %s", MAX_TRADE_TX_FEE_SAT, tradeTxFee);
    }

    public static long checkTradeTxFeeIsInTolerance(long tradeTxFee, FeeService feeService) {
        return checkTradeTxFeeIsInTolerance(Coin.valueOf(tradeTxFee), feeService).getValue();
    }

    public static Coin checkTradeTxFeeIsInTolerance(Coin tradeTxFee, FeeService feeService) {
        checkTradeTxFee(tradeTxFee);
        checkNotNull(feeService, "feeService must not be null");
        Coin txFeePerVbyte = checkIsPositive(feeService.getTxFeePerVbyte(), "txFeePerVbyte");
        Coin expectedTradeTxFee = TradeFeeFactory.getTradeTxFee(txFeePerVbyte);
        return checkTradeTxFeeIsInTolerance(tradeTxFee, expectedTradeTxFee);
    }

    public static Coin checkTradeTxFeeIsInTolerance(Coin tradeTxFee, Coin expectedTradeTxFee) {
        checkTradeTxFee(tradeTxFee);
        checkIsPositive(expectedTradeTxFee, "expectedTradeTxFee");
        checkFeeIsInTolerance(tradeTxFee.getValue(), expectedTradeTxFee.getValue());
        return tradeTxFee;
    }


    /* --------------------------------------------------------------------- */
    // Miner fee rate
    /* --------------------------------------------------------------------- */

    public static long checkMinerFeeRateIsInTolerance(long minerFeeRate, long expectedMinerFeeRate) {
        checkIsPositive(minerFeeRate, "minerFeeRate");
        checkIsPositive(expectedMinerFeeRate, "expectedMinerFeeRate");
        return checkFeeIsInTolerance(minerFeeRate, expectedMinerFeeRate);
    }


    /* --------------------------------------------------------------------- */
    // Fee tolerance
    /* --------------------------------------------------------------------- */

    @VisibleForTesting
    static long checkFeeIsInTolerance(long actualValue, long expectedValue) {
        return TradeValidation.checkValueInTolerance(actualValue, expectedValue, MAX_FEE_DEVIATION_FACTOR);
    }
}
