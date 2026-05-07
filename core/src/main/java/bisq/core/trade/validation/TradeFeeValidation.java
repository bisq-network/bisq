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

import bisq.core.trade.TradeFeeFactory;

import org.bitcoinj.core.Coin;

import com.google.common.annotations.VisibleForTesting;

import static bisq.core.util.Validator.checkIsPositive;

public class TradeFeeValidation {

    /* --------------------------------------------------------------------- */
    // Takers trade fee
    /* --------------------------------------------------------------------- */

    public static Coin checkTakerFee(Coin takerFee, boolean isCurrencyForTakerFeeBtc, Coin tradeAmount) {
        checkIsPositive(takerFee, "takerFee");
        checkIsPositive(tradeAmount, "tradeAmount");
        Coin expectedTakerFee = TradeFeeFactory.getTakerFee(isCurrencyForTakerFeeBtc, tradeAmount);
        return checkTakerFee(takerFee, expectedTakerFee);
    }

    public static Coin checkTakerFee(Coin takerFee, Coin expectedTakerFee) {
        checkIsPositive(takerFee, "takerFee");
        checkIsPositive(expectedTakerFee, "expectedTakerFee");
        checkTakerFeeInTolerance(takerFee.getValue(), expectedTakerFee.getValue());
        return takerFee;
    }

    public static long checkTakerFee(long takerFee, boolean isCurrencyForTakerFeeBtc, Coin tradeAmount) {
        checkIsPositive(takerFee, "takerFee");
        checkIsPositive(tradeAmount, "tradeAmount");
        long expectedTakerFee = TradeFeeFactory.getTakerFee(isCurrencyForTakerFeeBtc, tradeAmount).value;
        return checkTakerFeeInTolerance(takerFee, expectedTakerFee);
    }

    // The taker fee is set when taking the offer. In very rare cases it could be that the taker fee just changed by
    // DAO voting, but maker and taker have different local block heights and use therefor a different value.
    // Thus, we allow a tolerance of 33% lower fee or 50% higher fee to avoid that older offers would get rejected
    // in trades.
    @VisibleForTesting
    static long checkTakerFeeInTolerance(long fee, long expectedFee) {
        return TradeValidationUtils.checkValueInTolerance(fee, expectedFee, TradeValidation.MAX_TAKER_FEE_DEVIATION_FACTOR);
    }


    /* --------------------------------------------------------------------- */
    // Makers trade fee
    /* --------------------------------------------------------------------- */

    public static Coin checkMakerFee(Coin makerFee, boolean isCurrencyForMakerFeeBtc, Coin tradeAmount) {
        checkIsPositive(makerFee, "makerFee");
        checkIsPositive(tradeAmount, "tradeAmount");
        Coin expectedMakerFee = TradeFeeFactory.getMakerFee(isCurrencyForMakerFeeBtc, tradeAmount);
        return checkMakerFee(makerFee, expectedMakerFee);
    }

    public static Coin checkMakerFee(Coin makerFee, Coin expectedMakerFee) {
        checkIsPositive(makerFee, "makerFee");
        checkIsPositive(expectedMakerFee, "expectedMakerFee");
        checkMakerFeeInTolerance(makerFee.getValue(), expectedMakerFee.getValue());
        return makerFee;
    }

    public static long checkMakerFee(long makerFee, boolean isCurrencyForMakerFeeBtc, Coin tradeAmount) {
        checkIsPositive(makerFee, "makerFee");
        checkIsPositive(tradeAmount, "tradeAmount");
        long expectedMakerFee = TradeFeeFactory.getMakerFee(isCurrencyForMakerFeeBtc, tradeAmount).value;
        return checkMakerFeeInTolerance(makerFee, expectedMakerFee);
    }

    // The maker fee is set in the offer. The offer can be old and the maker fee might have changed by DAO voting.
    // Thus, we allow a tolerance between half and double of the fee to avoid that older offers would get rejected
    // in trades.
    @VisibleForTesting
    static long checkMakerFeeInTolerance(long fee, long expectedFee) {
        return TradeValidationUtils.checkValueInTolerance(fee, expectedFee, TradeValidation.MAX_MAKER_FEE_DEVIATION_FACTOR);
    }
}
