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
    // Max change by factor 4 (expected / 4 or expected * 4). Peers get their fee rates from different pricenodes,
    // which average different mempool instances at different times. Versions up to v1.10.11 allow a factor of 2.
    public static final double MAX_FEE_DEVIATION_FACTOR = 4;
    // The taker checks its own trade tx fee against its own fee rate before it sends a take offer request. Makers up
    // to v1.10.11 drop a request outside a factor of 2 without a reply, so the taker keeps their factor.
    public static final double MAX_OWN_FEE_DEVIATION_FACTOR = 2;
    // The BSQ swap maker pays its own share of the miner fee at the fee rate of the taker's request. A wider
    // tolerance would let the taker raise the maker's fee further or delay the confirmation of the swap tx.
    public static final double MAX_BSQ_SWAP_FEE_RATE_DEVIATION_FACTOR = 2;

    // Bound the taker-supplied trade tx fee. A tiny value can leave the deposit tx
    // unconfirmable (locking maker funds); a huge value can drain miner fees from the maker.
    // The minimum is the trade tx fee at FeeService.MIN_TRADE_TX_FEE_RATE (2 sat/vB for 212 vB, 424 sat),
    // so the trade txs pay at least that rate. The maximum is 600 sat/vB over up to ~600 vB (headroom above
    // the stored average of taker-fee-tx ~192 vB and deposit-tx ~233 vB).
    // The tighter check is checkTradeTxFeeIsInTolerance; these are absolute sanity bounds.
    public static final long MIN_TRADE_TX_FEE_SAT =
            FeeService.MIN_TRADE_TX_FEE_RATE * TradeFeeFactory.AVERAGE_TRADE_TX_VSIZE;
    public static final long MAX_TRADE_TX_FEE_SAT = 360_000L;
    // Makers up to v1.10.11 accepted down to 250 sat, and such a trade can continue after an update. So the checks of
    // an existing trade use this bound: the trade object, also when the trade is loaded from disk, where one trade
    // that fails its check makes the whole trade file unreadable, and the buyer's check of the delayed payout tx. A
    // new take offer request is checked against MIN_TRADE_TX_FEE_SAT.
    public static final long MIN_STORED_TRADE_TX_FEE_SAT = 250L;

    private MinerFeeValidation() {
    }


    /* --------------------------------------------------------------------- */
    // Trade transaction fee
    /* --------------------------------------------------------------------- */

    public static Coin checkTradeTxFee(Coin tradeTxFee) {
        checkIsPositive(tradeTxFee, "tradeTxFee");
        checkTradeTxFeeBounds(tradeTxFee.getValue(), MIN_TRADE_TX_FEE_SAT);
        return tradeTxFee;
    }

    public static long checkTradeTxFee(long tradeTxFee) {
        checkIsPositive(tradeTxFee, "tradeTxFee");
        checkTradeTxFeeBounds(tradeTxFee, MIN_TRADE_TX_FEE_SAT);
        return tradeTxFee;
    }

    public static Coin checkStoredTradeTxFee(Coin tradeTxFee) {
        checkIsPositive(tradeTxFee, "tradeTxFee");
        checkTradeTxFeeBounds(tradeTxFee.getValue(), MIN_STORED_TRADE_TX_FEE_SAT);
        return tradeTxFee;
    }

    private static void checkTradeTxFeeBounds(long tradeTxFee, long minTradeTxFee) {
        checkArgument(tradeTxFee >= minTradeTxFee,
                "tradeTxFee too low (must be >= %s sat). Got: %s", minTradeTxFee, tradeTxFee);
        checkArgument(tradeTxFee <= MAX_TRADE_TX_FEE_SAT,
                "tradeTxFee too high (must be <= %s sat). Got: %s", MAX_TRADE_TX_FEE_SAT, tradeTxFee);
    }

    public static long checkStoredTradeTxFeeIsInTolerance(long tradeTxFee, FeeService feeService) {
        return checkTradeTxFeeIsInTolerance(Coin.valueOf(tradeTxFee), MIN_STORED_TRADE_TX_FEE_SAT, feeService,
                MAX_FEE_DEVIATION_FACTOR).getValue();
    }

    public static Coin checkTradeTxFeeIsInTolerance(Coin tradeTxFee, FeeService feeService) {
        return checkTradeTxFeeIsInTolerance(tradeTxFee, MIN_TRADE_TX_FEE_SAT, feeService, MAX_FEE_DEVIATION_FACTOR);
    }

    public static Coin checkOwnTradeTxFeeIsInTolerance(Coin tradeTxFee, FeeService feeService) {
        return checkTradeTxFeeIsInTolerance(tradeTxFee, MIN_TRADE_TX_FEE_SAT, feeService, MAX_OWN_FEE_DEVIATION_FACTOR);
    }

    private static Coin checkTradeTxFeeIsInTolerance(Coin tradeTxFee,
                                                     long minTradeTxFee,
                                                     FeeService feeService,
                                                     double factor) {
        checkIsPositive(tradeTxFee, "tradeTxFee");
        checkTradeTxFeeBounds(tradeTxFee.getValue(), minTradeTxFee);
        checkNotNull(feeService, "feeService must not be null");
        Coin txFeePerVbyte = checkIsPositive(feeService.getTxFeePerVbyte(), "txFeePerVbyte");
        Coin expectedTradeTxFee = TradeFeeFactory.getTradeTxFee(txFeePerVbyte);
        TradeValidation.checkValueInTolerance(tradeTxFee.getValue(), expectedTradeTxFee.getValue(), factor);
        return tradeTxFee;
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

    public static long checkBsqSwapMinerFeeRateIsInTolerance(long minerFeeRate, long expectedMinerFeeRate) {
        checkIsPositive(minerFeeRate, "minerFeeRate");
        checkIsPositive(expectedMinerFeeRate, "expectedMinerFeeRate");
        return TradeValidation.checkValueInTolerance(minerFeeRate, expectedMinerFeeRate,
                MAX_BSQ_SWAP_FEE_RATE_DEVIATION_FACTOR);
    }


    /* --------------------------------------------------------------------- */
    // Fee tolerance
    /* --------------------------------------------------------------------- */

    @VisibleForTesting
    static long checkFeeIsInTolerance(long actualValue, long expectedValue) {
        return TradeValidation.checkValueInTolerance(actualValue, expectedValue, MAX_FEE_DEVIATION_FACTOR);
    }
}
